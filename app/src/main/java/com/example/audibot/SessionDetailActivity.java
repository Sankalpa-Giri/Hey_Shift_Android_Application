package com.example.audibot;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows all messages in a session.
 * FIX: Adds back button (toolbar) and delete-session option (menu).
 */
public class SessionDetailActivity extends AppCompatActivity {

    private static final String TAG = "SessionDetail";

    RecyclerView  recyclerView;
    ChatAdapter   adapter;
    List<Message> messages = new ArrayList<>();

    String sessionId;
    String sessionTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_detail);

        sessionId    = getIntent().getStringExtra("session_id");
        sessionTitle = getIntent().getStringExtra("session_title");
        if (sessionTitle == null || sessionTitle.isEmpty())
            sessionTitle = "Chat";

        // FIX: Toolbar with back button
        Toolbar toolbar = findViewById(R.id.toolbar_session_detail);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(sessionTitle);
            }
        }

        recyclerView = findViewById(R.id.recycler_session_detail);
        if (recyclerView == null) { finish(); return; }
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages);
        recyclerView.setAdapter(adapter);

        // FIX: Resume session button — opens this session in MainActivity
        findViewById(R.id.btn_resume_session).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("resume_session_id", sessionId);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        if (sessionId != null) loadMessages();
    }

    // FIX: Back arrow press
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // FIX: Delete option in action bar menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_session_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_delete_session) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
            .setTitle("Delete session")
            .setMessage("Delete this entire conversation?")
            .setPositiveButton("Delete", (d, w) -> deleteSession())
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void deleteSession() {
        new Thread(() -> {
            try {
                String token = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                        .getString("token", "");
                URL url = new URL(Constants.BASE_URL.trim() + "/chat/session/" + sessionId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Session deleted", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } else {
                    runOnUiThread(() ->
                        Toast.makeText(this, "Failed to delete (HTTP " + code + ")",
                                Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteSession failed", e);
                runOnUiThread(() ->
                    Toast.makeText(this, "Error: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void loadMessages() {
        new Thread(() -> {
            try {
                String token = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                        .getString("token", "");
                URL url = new URL(Constants.BASE_URL.trim() + "/chat/session/" + sessionId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("ngrok-skip-browser-warning", "true");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(15_000);

                int code = conn.getResponseCode();
                InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                if (code != 200) {
                    Log.e(TAG, "HTTP " + code + ": " + sb);
                    return;
                }

                JSONArray arr = new JSONObject(sb.toString()).getJSONArray("messages");
                List<Message> loaded = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.getJSONObject(i);
                    loaded.add(new Message(
                            m.optString("text", ""),
                            m.optString("role", "bot").equals("user")));
                }

                runOnUiThread(() -> {
                    messages.clear();
                    messages.addAll(loaded);
                    adapter.notifyDataSetChanged();
                    if (!messages.isEmpty())
                        recyclerView.scrollToPosition(messages.size() - 1);
                });

            } catch (Exception e) {
                Log.e(TAG, "loadMessages failed", e);
            }
        }).start();
    }
}