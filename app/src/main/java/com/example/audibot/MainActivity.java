package com.example.audibot;

import android.animation.*;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.view.animation.AnticipateInterpolator;
import android.widget.*;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.*;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.*;
import com.google.android.material.navigation.NavigationView;
import com.google.gson.Gson;
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.*;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import ai.picovoice.porcupine.PorcupineManager;
import ai.picovoice.porcupine.PorcupineException;

import org.json.*;
import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private boolean keepSplashScreen = true;

    DrawerLayout      drawerLayout;
    NavigationView    navigationView;
    RecyclerView      recyclerView;
    ChatAdapter       adapter;
    List<Message>     messageList = new ArrayList<>();
    SharedPreferences prefs, authPrefs;
    TextToSpeech      tts;
    Gson              gson        = new Gson();

    PorcupineManager  porcupineManager;
    private boolean   porcupineRunning = false;

    private SpeechRecognizer speechRecognizer;
    // FIX: Use an atomic flag + lock to prevent concurrent startListening calls
    // that caused the mic-tap crash (ERROR_RECOGNIZER_BUSY → destroy → null → NPE)
    private volatile boolean isListening      = false;
    private volatile boolean isInitializing   = false;  // guard against re-entrant init

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    static final int LOCATION_PERMISSION_REQUEST = 200;
    static final int RECORD_AUDIO_REQUEST        = 1;

    LocationManager locationManager;
    double currentLatitude  = 0.0;
    double currentLongitude = 0.0;

    String  currentSessionId;
    private boolean isSendingMessage = false;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        authPrefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
        if (authPrefs.getString("token", null) == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.chat_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messageList);
        recyclerView.setAdapter(adapter);

        prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE);

        drawerLayout   = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_drawer);
        updateDrawerHeader();

        String resumeId = getIntent().getStringExtra("resume_session_id");
        if (resumeId != null) {
            currentSessionId = resumeId;
            prefs.edit().putString("current_session_id", resumeId).apply();
            loadSessionMessages(resumeId);
        } else {
            currentSessionId = prefs.getString("current_session_id", null);
            if (currentSessionId == null) {
                currentSessionId = UUID.randomUUID().toString();
                prefs.edit().putString("current_session_id", currentSessionId).apply();
            }
            loadChat();
        }

        requestLocationPermission();

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            int top  = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int imeH = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navH = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, top, 0, Math.max(imeH, navH));
            return insets;
        });

        new Handler(Looper.getMainLooper()).postDelayed(() -> keepSplashScreen = false, 800);
        splashScreen.setKeepOnScreenCondition(() -> keepSplashScreen);
        splashScreen.setOnExitAnimationListener(sv -> {
            ObjectAnimator anim = ObjectAnimator.ofFloat(sv.getView(), View.TRANSLATION_Y,
                    0f, -sv.getView().getHeight());
            anim.setInterpolator(new AnticipateInterpolator());
            anim.setDuration(220L);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) { sv.remove(); }
            });
            anim.start();
        });

        // Toolbar buttons
        findViewById(R.id.btn_menu).setOnClickListener(v -> drawerLayout.open());
        findViewById(R.id.btn_add).setOnClickListener(v -> startNewChat());
        findViewById(R.id.btn_chat_history).setOnClickListener(v ->
                startActivity(new Intent(this, ChatHistoryActivity.class)));

        // Drawer
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_logout) {
                authPrefs.edit().clear().apply();
                prefs.edit().clear().apply();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
            } else if (id == R.id.nav_chat_history) {
                startActivity(new Intent(this, ChatHistoryActivity.class));
            }
            drawerLayout.closeDrawers();
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (drawerLayout.isOpen()) drawerLayout.closeDrawers();
                else finish();
            }
        });

        EditText msgBox = findViewById(R.id.edit_message);
        findViewById(R.id.btn_send).setOnClickListener(v ->
                sendMessage(msgBox.getText().toString().trim()));

        // FIX: Mic button — always dispatch via mainHandler, add null-check guard
        findViewById(R.id.btn_mic).setOnClickListener(v ->
                mainHandler.post(this::startListening));

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) tts.setLanguage(Locale.US);
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            initSpeechRecognizer();
            // FIX: Delay Porcupine init slightly so SpeechRecognizer finishes
            // setting up first — they both grab the audio focus and racing
            // causes "PorcupineException: mic in use" on first launch.
            mainHandler.postDelayed(this::initPorcupine, 600);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_REQUEST);
        }
    }

    // ── SpeechRecognizer ─────────────────────────────────────────────────────

    /**
     * MUST be called on the main thread.
     * Guards against re-entrant calls with isInitializing flag.
     */
    private void initSpeechRecognizer() {
        if (isInitializing) return;
        isInitializing = true;

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer not available on this device");
            isInitializing = false;
            return;
        }
        // FIX: Fully destroy old instance before creating a new one
        if (speechRecognizer != null) {
            try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            speechRecognizer = null;
        }
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        } catch (Exception e) {
            Log.e(TAG, "createSpeechRecognizer failed: " + e.getMessage());
            isInitializing = false;
            return;
        }
        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override public void onReadyForSpeech(Bundle p) {
                isListening = true;
                setMicAlpha(0.5f);
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v)  {}
            @Override public void onBufferReceived(byte[] b) {}

            @Override public void onEndOfSpeech() {
                setMicAlpha(1.0f);
            }

            @Override public void onError(int error) {
                isListening = false;
                setMicAlpha(1.0f);

                // FIX: Safely destroy before scheduling reinit — prevent NPE on
                // rapid successive taps that triggered the original crash
                if (speechRecognizer != null) {
                    try { speechRecognizer.destroy(); } catch (Exception ignored) {}
                    speechRecognizer = null;
                }
                isInitializing = false;
                // Reinit after a safe delay
                mainHandler.postDelayed(() -> initSpeechRecognizer(), 500);
                // Give mic back to Porcupine after recognizer has settled
                mainHandler.postDelayed(() -> resumePorcupine(), 800);

                // Only show toast for user-visible errors
                String msg;
                switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        msg = "Couldn't understand. Please try again."; break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        msg = "No speech detected. Tap mic and try again."; break;
                    case SpeechRecognizer.ERROR_NETWORK:
                        msg = "Network error. Check your connection."; break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        // Suppress toast for busy — it's transient and confusing
                        return;
                    default:
                        msg = "Voice error (" + error + "). Tap mic to retry.";
                }
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }

            @Override public void onResults(Bundle results) {
                isListening    = false;
                isInitializing = false;
                setMicAlpha(1.0f);
                mainHandler.postDelayed(() -> resumePorcupine(), 600);

                ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0).trim();
                    if (!text.isEmpty()) sendMessage(text);
                }
            }

            @Override public void onPartialResults(Bundle partial) {}
            @Override public void onEvent(int t, Bundle b) {}
        });
        isInitializing = false;
    }

    private void setMicAlpha(float alpha) {
        ImageButton mic = findViewById(R.id.btn_mic);
        if (mic != null) mic.setAlpha(alpha);
    }

    private void startListening() {
        // FIX: Strict main-thread guard — SpeechRecognizer will throw
        // RuntimeException("SpeechRecognizer should be used only from the main thread")
        // if called from any other thread. This caused the silent crash.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::startListening);
            return;
        }

        // FIX: Bail if already listening OR if we're mid-initialization
        if (isListening || isInitializing) return;

        // FIX: Removed getActiveNetworkInfo() — it requires ACCESS_NETWORK_STATE
        // permission and throws RemoteException on Android 15 (Samsung One UI 7)
        // if that permission is missing, killing the app instantly on mic tap.
        // SpeechRecognizer already returns ERROR_NETWORK if there's no internet,
        // so this check was redundant anyway.

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_REQUEST);
            return;
        }

        // FIX: If recognizer was destroyed (e.g. after an error), reinit synchronously
        // before starting. Without this, tapping mic after an error → NPE crash.
        if (speechRecognizer == null) {
            initSpeechRecognizer();
            if (speechRecognizer == null) {
                Toast.makeText(this, "Speech recognition not available",
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Pause Porcupine FIRST so it releases the mic hardware
        pausePorcupine();

        if (tts != null && tts.isSpeaking()) tts.stop();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        // FIX: Set calling package — required on MIUI, OnePlus, Samsung OneUI.
        // Without this the system kills the app instead of routing to the recognizer.
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());

        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            // FIX: Catch any lingering "recognizer busy" native exception
            Log.e(TAG, "startListening failed: " + e.getMessage());
            isListening    = false;
            isInitializing = false;
            if (speechRecognizer != null) {
                try { speechRecognizer.destroy(); } catch (Exception ignored) {}
                speechRecognizer = null;
            }
            mainHandler.postDelayed(this::initSpeechRecognizer, 400);
            mainHandler.postDelayed(this::resumePorcupine, 600);
            Toast.makeText(this, "Mic busy, please try again.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopListening() {
        if (speechRecognizer != null && isListening) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
            isListening = false;
        }
    }

    // ── Porcupine wake word ───────────────────────────────────────────────────
    //
    // FIX: The .ppn asset is named "hey-shift_en_android_v4_0_0.ppn" but
    // build.gradle uses porcupine-android:3.0.1.  The SDK version and the
    // .ppn model version MUST match — a v4 .ppn with a v3 SDK throws
    // "PorcupineException: model and library version mismatch" and the wake
    // word never starts.
    //
    // TWO valid options — pick ONE:
    //
    // Option A (recommended): Keep porcupine-android:3.0.1 in build.gradle
    //   AND rename/replace the asset to a v3-compatible .ppn file.
    //   Name it: hey-shift_en_android_v3_0_0.ppn
    //   Then change PORCUPINE_MODEL_FILE below to match.
    //
    // Option B: Bump build.gradle to porcupine-android:4.0.1 (or latest 4.x)
    //   AND keep the existing hey-shift_en_android_v4_0_0.ppn asset.
    //
    // This file assumes Option B (v4 SDK + v4 model) because the asset is
    // already v4.  Change the constant below if you go with Option A.
    //
    // build.gradle line to change for Option B:
    //   implementation("ai.picovoice:porcupine-android:4.0.1")
    //
    private static final String PORCUPINE_MODEL_FILE = "hey-shift.ppn";

    private void initPorcupine() {
        try {
            porcupineManager = new PorcupineManager.Builder()
                    .setAccessKey("Ds8IRQaxtEDaYC7s8eHaP05GQqW0w0S5mswUmUeokAs7tuarf8bPFg==")
                    .setKeywordPath(PORCUPINE_MODEL_FILE)
                    .setSensitivity(0.7f)
                    .build(getApplicationContext(), keywordIndex ->
                            mainHandler.post(() -> {
                                Toast.makeText(this, "Hey Shift!", Toast.LENGTH_SHORT).show();
                                if (tts != null && tts.isSpeaking()) tts.stop();
                                startListening();
                            }));
            porcupineManager.start();
            porcupineRunning = true;
            Log.d(TAG, "Porcupine wake word started");
        } catch (PorcupineException e) {
            Log.e(TAG, "Porcupine init failed: " + e.getMessage());
            // Non-fatal — app works without wake word
            // FIX: Only show toast if the message is meaningful (not null)
            String reason = e.getMessage() != null ? e.getMessage() : "unknown error";
            Toast.makeText(this, "Wake word unavailable: " + reason,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void pausePorcupine() {
        if (porcupineManager == null || !porcupineRunning) return;
        try {
            porcupineManager.stop();
            porcupineRunning = false;
            Log.d(TAG, "Porcupine paused");
        } catch (PorcupineException e) {
            Log.e(TAG, "Porcupine stop error: " + e.getMessage());
        }
    }

    private void resumePorcupine() {
        if (porcupineManager == null || porcupineRunning) return;
        try {
            porcupineManager.start();
            porcupineRunning = true;
            Log.d(TAG, "Porcupine resumed");
        } catch (PorcupineException e) {
            Log.e(TAG, "Porcupine start error: " + e.getMessage());
        }
    }

    // ── onNewIntent ───────────────────────────────────────────────────────────

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String resumeId = intent.getStringExtra("resume_session_id");
        if (resumeId != null) {
            currentSessionId = resumeId;
            prefs.edit().putString("current_session_id", resumeId).apply();
            messageList.clear();
            adapter.notifyDataSetChanged();
            loadSessionMessages(resumeId);
            if (drawerLayout.isOpen()) drawerLayout.closeDrawers();
        }
    }

    // ── GPS ───────────────────────────────────────────────────────────────────

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        else startLocationUpdates();
    }

    private void startLocationUpdates() {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            LocationListener ll = loc -> {
                currentLatitude  = loc.getLatitude();
                currentLongitude = loc.getLongitude();
            };
            String provider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (locationManager.isProviderEnabled(provider)) {
                locationManager.requestLocationUpdates(provider, 5000, 10, ll);
                Location last = locationManager.getLastKnownLocation(provider);
                if (last != null) {
                    currentLatitude  = last.getLatitude();
                    currentLongitude = last.getLongitude();
                }
            }
        } catch (SecurityException e) { Log.e(TAG, "GPS permission denied", e); }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == LOCATION_PERMISSION_REQUEST
                && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            startLocationUpdates();

        if (req == RECORD_AUDIO_REQUEST
                && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            mainHandler.post(() -> {
                initSpeechRecognizer();
                // FIX: Delay porcupine so SpeechRecognizer wins the mic first
                mainHandler.postDelayed(MainActivity.this::initPorcupine, 600);
            });
        }
    }

    // ── Session ───────────────────────────────────────────────────────────────

    private void startNewChat() {
        currentSessionId = UUID.randomUUID().toString();
        prefs.edit().putString("current_session_id", currentSessionId)
                .remove("chat_data").apply();
        messageList.clear();
        adapter.notifyDataSetChanged();
        if (drawerLayout.isOpen()) drawerLayout.closeDrawers();
    }

    private void loadSessionMessages(String sessionId) {
        new Thread(() -> {
            try {
                String token = authPrefs.getString("token", "");
                URL    url   = new URL(Constants.BASE_URL.trim() + "/chat/session/" + sessionId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("ngrok-skip-browser-warning", "69420");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(15_000);

                int code = conn.getResponseCode();
                InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                if (code != 200) { Log.e(TAG, "SESSION_LOAD HTTP " + code); return; }

                JSONArray msgs  = new JSONObject(sb.toString()).getJSONArray("messages");
                List<Message> loaded = new ArrayList<>();
                for (int i = 0; i < msgs.length(); i++) {
                    JSONObject m = msgs.getJSONObject(i);
                    loaded.add(new Message(m.getString("text"),
                            m.getString("role").equals("user")));
                }
                runOnUiThread(() -> {
                    messageList.clear();
                    messageList.addAll(loaded);
                    adapter.notifyDataSetChanged();
                    if (!messageList.isEmpty())
                        recyclerView.scrollToPosition(messageList.size() - 1);
                });
            } catch (Exception e) { Log.e(TAG, "SESSION_LOAD failed", e); }
        }).start();
    }

    private void updateDrawerHeader() {
        try {
            View header = navigationView.getHeaderView(0);
            if (header == null) return;
            TextView tvName    = header.findViewById(R.id.profile_name);
            TextView tvId      = header.findViewById(R.id.profile_id);
            TextView tvInitial = header.findViewById(R.id.profile_initial);
            String name   = authPrefs.getString("name", "User");
            String userId = authPrefs.getString("user_id", "");
            if (tvName    != null) tvName.setText(name);
            if (tvId      != null) tvId.setText("ID: " +
                    userId.substring(0, Math.min(8, userId.length())));
            if (tvInitial != null && !name.isEmpty())
                tvInitial.setText(String.valueOf(name.charAt(0)).toUpperCase());
        } catch (Exception e) { Log.e(TAG, "Drawer header update failed", e); }
    }

    // ── Backend ───────────────────────────────────────────────────────────────

    private void sendToBackend(String msg) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String token = authPrefs.getString("token", "");
                URL    url   = new URL(Constants.BASE_URL.trim() + "/voice");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("ngrok-skip-browser-warning", "69420");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(30_000);

                JSONObject body = new JSONObject();
                body.put("text",       msg);
                body.put("session_id", currentSessionId);
                if (currentLatitude != 0.0 && currentLongitude != 0.0) {
                    body.put("latitude",  currentLatitude);
                    body.put("longitude", currentLongitude);
                }

                byte[] bytes = body.toString().getBytes("UTF-8");
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }

                int code = conn.getResponseCode();
                InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                if (code == 200) {
                    String reply = new JSONObject(sb.toString()).optString("reply", "No reply");
                    runOnUiThread(() -> {
                        removeTypingIndicator();
                        messageList.add(new Message(reply, false));
                        adapter.notifyItemInserted(messageList.size() - 1);
                        recyclerView.scrollToPosition(messageList.size() - 1);
                        if (tts != null) tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null);
                        saveChat();
                    });
                } else {
                    Log.e(TAG, "API error " + code);
                    final int fc = code;
                    runOnUiThread(() -> {
                        removeTypingIndicator();
                        Toast.makeText(this, "Server error " + fc, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (java.net.MalformedURLException e) {
                runOnUiThread(() -> { removeTypingIndicator();
                    Toast.makeText(this, "Invalid URL — check Constants.java",
                            Toast.LENGTH_LONG).show(); });
            } catch (java.net.SocketTimeoutException e) {
                runOnUiThread(() -> { removeTypingIndicator();
                    Toast.makeText(this, "Request timed out. Is the server running?",
                            Toast.LENGTH_LONG).show(); });
            } catch (Exception e) {
                Log.e(TAG, "sendToBackend error", e);
                runOnUiThread(() -> { removeTypingIndicator();
                    Toast.makeText(this, "Connection failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show(); });
            } finally {
                runOnUiThread(() -> isSendingMessage = false);
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // ── Typing indicator ──────────────────────────────────────────────────────

    private void addTypingIndicator() {
        messageList.add(new Message("…", false));
        adapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
    }

    private void removeTypingIndicator() {
        if (!messageList.isEmpty()) {
            Message last = messageList.get(messageList.size() - 1);
            if (!last.isUser() && "…".equals(last.getText())) {
                int idx = messageList.size() - 1;
                messageList.remove(idx);
                adapter.notifyItemRemoved(idx);
            }
        }
    }

    // ── Local chat ────────────────────────────────────────────────────────────

    private void loadChat() {
        String json = prefs.getString("chat_data", null);
        if (json == null) return;
        Type type = new TypeToken<ArrayList<Message>>() {}.getType();
        List<Message> loaded = gson.fromJson(json, type);
        if (loaded != null) {
            messageList.addAll(loaded);
            adapter.notifyDataSetChanged();
            if (!messageList.isEmpty())
                recyclerView.scrollToPosition(messageList.size() - 1);
        }
    }

    private void saveChat() {
        prefs.edit().putString("chat_data", gson.toJson(messageList)).apply();
    }

    private void sendMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        if (isSendingMessage) return;
        isSendingMessage = true;
        messageList.add(new Message(msg, true));
        adapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        saveChat();
        addTypingIndicator();
        sendToBackend(msg);
        EditText editText = findViewById(R.id.edit_message);
        if (editText != null) editText.setText("");
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        stopListening();
        pausePorcupine();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            // FIX: Only resume Porcupine if SpeechRecognizer is not active
            if (!isListening) resumePorcupine();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);

        if (tts != null) { tts.stop(); tts.shutdown(); }

        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.destroy();
            } catch (Exception ignored) {}
            speechRecognizer = null;
        }

        try {
            if (porcupineManager != null) {
                porcupineManager.stop();
                porcupineManager.delete();
            }
        } catch (Exception e) { Log.e(TAG, "Porcupine shutdown error", e); }
    }
}