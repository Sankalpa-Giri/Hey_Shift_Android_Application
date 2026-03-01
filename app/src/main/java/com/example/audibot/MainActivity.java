package com.example.audibot;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AnticipateInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.ArrayList;
import java.util.List;
import android.util.Log;

import android.content.SharedPreferences;

import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.lang.reflect.Type;

import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;

import ai.picovoice.porcupine.*;
import ai.picovoice.porcupine.PorcupineManager;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity {

    private boolean keepSplashScreen = true;

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    RecyclerView recyclerView;
    ChatAdapter adapter;
    List<Message> messageList = new ArrayList<>();
    SharedPreferences prefs;
    TextToSpeech tts;
    static final int SPEECH_REQUEST = 100;
    Gson gson = new Gson();
    PorcupineManager porcupineManager;
    boolean isListeningCommand = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Splash install
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerView = findViewById(R.id.chat_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ChatAdapter(messageList);
        recyclerView.setAdapter(adapter);

        prefs = getSharedPreferences("chat_prefs", MODE_PRIVATE);
        loadChat();


        // ⭐ ONE FINAL SYSTEM CONFIG (fixes notch + keyboard + toolbar)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        // apply safe padding automatically
        View root = findViewById(R.id.main);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {

            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            v.setPadding(0, top, 0, bottom);

            return insets;
        });

        // Splash delay
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> keepSplashScreen = false,
                800
        );

        splashScreen.setKeepOnScreenCondition(() -> keepSplashScreen);

        // Splash exit animation
        splashScreen.setOnExitAnimationListener(splashScreenView -> {
            ObjectAnimator slideUp = ObjectAnimator.ofFloat(
                    splashScreenView.getView(),
                    View.TRANSLATION_Y,
                    0f,
                    -splashScreenView.getView().getHeight()
            );

            slideUp.setInterpolator(new AnticipateInterpolator());
            slideUp.setDuration(220L);

            slideUp.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    splashScreenView.remove();
                }
            });

            slideUp.start();
        });

        // Drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_drawer);

        findViewById(R.id.btn_menu).setOnClickListener(v -> drawerLayout.open());

        findViewById(R.id.btn_add).setOnClickListener(v -> {

            messageList.clear();
            adapter.notifyDataSetChanged();

            prefs.edit().clear().apply(); // clear storage
        });


        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawers();
            return true;
        });

        // Back press modern
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (drawerLayout.isOpen()) {
                            drawerLayout.closeDrawers();
                        } else {
                            finish();
                        }
                    }
                });

        // Chat send
        EditText messageBox = findViewById(R.id.edit_message);
        ImageButton sendBtn = findViewById(R.id.btn_send);

        sendBtn.setOnClickListener(v -> {
            String msg = messageBox.getText().toString().trim();
            sendMessage(msg);
        });


        //Mic Button
        tts = new TextToSpeech(this, status -> {
            if(status != TextToSpeech.ERROR){
                tts.setLanguage(Locale.US);
            }
        });

        ImageButton micBtn = findViewById(R.id.btn_mic);

        micBtn.setOnClickListener(v -> {

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");

            try{
                startActivityForResult(intent, SPEECH_REQUEST);
            }catch(Exception e){
                Toast.makeText(this,"Voice not supported",Toast.LENGTH_SHORT).show();
            }
        });

        //porcupine
        // 🎤 Ask mic permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        try{
            porcupineManager = new PorcupineManager.Builder()
                    .setAccessKey("Ds8IRQaxtEDaYC7s8eHaP05GQqW0w0S5mswUmUeokAs7tuarf8bPFg==")
                    .setKeywordPath("hey-shift_en_android_v4_0_0.ppn") // ONLY filename
                    .setSensitivity(0.7f)
                    .build(getApplicationContext(), keywordIndex -> {

                        runOnUiThread(() -> {
                            Toast.makeText(this,"Wake word detected",Toast.LENGTH_SHORT).show();
                            startVoiceCommand();
                        });
                    });

            porcupineManager.start();

            Log.d("PORCUPINE","Wake word engine started");

        }catch(Exception e){
            Log.e("PORCUPINE","Error: "+e.toString());
        }


    }

    private void sendToBackend(String msg){

        new Thread(() -> {
            try{
                Log.d("API_TEST","Sending: "+msg);

                // 🔴 CHANGE THIS TO YOUR PC IP IF USING REAL PHONE
                java.net.URL url = new java.net.URL(" https://phasic-laborious-joey.ngrok-free.dev/voice");


                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) url.openConnection();

                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type","application/json");

                String json = "{\"text\":\""+msg+"\"}";

                java.io.OutputStream os = conn.getOutputStream();
                os.write(json.getBytes());
                os.flush();
                os.close();

                // read response
                java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream())
                );

                StringBuilder response = new StringBuilder();
                String line;

                while((line = br.readLine()) != null){
                    response.append(line);
                }
                br.close();

                android.util.Log.d("FASTAPI", response.toString());

                // parse JSON safely
                org.json.JSONObject obj = new org.json.JSONObject(response.toString());

                String reply = obj.optString("reply","No reply");

                // show reply in chat
                runOnUiThread(() -> {
                    messageList.add(new Message(reply,false));
                    adapter.notifyItemInserted(messageList.size()-1);
                    recyclerView.scrollToPosition(messageList.size()-1);
                    //Mic Button
                    tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null);

                    saveChat();
                });

            }catch(Exception e){
                Log.e("API_TEST","ERROR: "+e.toString());

                android.util.Log.e("FASTAPI_ERROR", e.toString());
            }
        }).start();
    }

    private void loadChat(){
        String json = prefs.getString("chat_data", null);

        if(json != null){
            Type type = new TypeToken<ArrayList<Message>>(){}.getType();
            messageList = gson.fromJson(json, type);

            adapter = new ChatAdapter(messageList);
            recyclerView.setAdapter(adapter);
            recyclerView.scrollToPosition(messageList.size()-1);
        }
    }

    private void saveChat(){
        SharedPreferences.Editor editor = prefs.edit();
        String json = gson.toJson(messageList);
        editor.putString("chat_data", json);
        editor.apply();
    }

    //Mic Button
    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);

        if(requestCode==SPEECH_REQUEST && resultCode==RESULT_OK && data!=null){

            ArrayList<String> result =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            assert result != null;
            String spokenText = result.get(0);
            isListeningCommand = false;

            sendMessage(spokenText); // send to backend
        }
    }



    //Mic Button
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 🔊 stop text-to-speech
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        // 🎤 stop wake word engine
        try {
            if (porcupineManager != null) {
                porcupineManager.stop();
                porcupineManager.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    //Send function for both mic and send button
    private void sendMessage(String msg){

        if(msg == null || msg.trim().isEmpty()) return;

        // show user message
        messageList.add(new Message(msg,true));
        adapter.notifyItemInserted(messageList.size()-1);
        recyclerView.scrollToPosition(messageList.size()-1);

        saveChat();  // if using chat persistence
        sendToBackend(msg);

        EditText messageBox = findViewById(R.id.edit_message);
        messageBox.setText("");
    }

    //porcupine
    private void startVoiceCommand(){

        if(isListeningCommand) return;
        isListeningCommand = true;

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Yes?");

        try{
            startActivityForResult(intent, SPEECH_REQUEST);
        }catch(Exception e){
            isListeningCommand = false;
        }
    }

}
