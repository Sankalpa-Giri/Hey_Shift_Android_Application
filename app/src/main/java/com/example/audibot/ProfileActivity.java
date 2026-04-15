package com.example.audibot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    TextView tvName, tvEmail, tvUserId, tvCreated, tvAvatarInitial;
    SharedPreferences authPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        authPrefs       = getSharedPreferences("auth_prefs", MODE_PRIVATE);
        tvName          = findViewById(R.id.tv_profile_name);
        tvEmail         = findViewById(R.id.tv_profile_email);
        tvUserId        = findViewById(R.id.tv_profile_id);
        tvCreated       = findViewById(R.id.tv_profile_created);
        tvAvatarInitial = findViewById(R.id.tv_avatar_initial);

        Toolbar toolbar = findViewById(R.id.toolbar_profile);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("Profile");
            }
        }

        if (findViewById(R.id.btn_back) != null)
            findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        Button logoutBtn = findViewById(R.id.btn_profile_logout);
        if (logoutBtn != null)
            logoutBtn.setOnClickListener(v -> {
                authPrefs.edit().clear().apply();
                getSharedPreferences("chat_prefs", MODE_PRIVATE).edit().clear().apply();
                startActivity(new Intent(this, LoginActivity.class));
                finishAffinity();
            });

        // Show cached values instantly — screen is never blank
        String cachedName = authPrefs.getString("name", "User");
        if (tvName          != null) tvName.setText(cachedName);
        if (tvEmail         != null) tvEmail.setText(authPrefs.getString("email", "—"));
        if (tvUserId        != null) tvUserId.setText(authPrefs.getString("user_id", "—"));
        if (tvAvatarInitial != null && !cachedName.isEmpty())
            tvAvatarInitial.setText(String.valueOf(cachedName.charAt(0)).toUpperCase());

        loadProfile();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    private void loadProfile() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String token = authPrefs.getString("token", "");
                Log.d(TAG, "GET /auth/me  token_length=" + token.length());

                URL url = new URL(Constants.BASE_URL + "/auth/me");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("ngrok-skip-browser-warning", "69420");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream(),
                        StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                String body = sb.toString();
                Log.d(TAG, "HTTP " + code + " -> " + body);

                if (code == 200) {
                    // Safety: ngrok sometimes returns HTML interstitial even on 200
                    if (body.trim().startsWith("<")) {
                        Log.e(TAG, "Got HTML instead of JSON — ngrok interstitial");
                        runOnUiThread(() -> Toast.makeText(this,
                                "Network error — restart ngrok and try again",
                                Toast.LENGTH_LONG).show());
                        return;
                    }

                    JSONObject data = new JSONObject(body);
                    String name    = data.optString("name",       "User");
                    String email   = data.optString("email",      "—");
                    String userId  = data.optString("user_id",    "—");
                    String raw     = data.optString("created_at", "");
                    String created = raw.length() >= 16
                            ? raw.replace("T", " ").substring(0, 16) : raw;

                    authPrefs.edit()
                            .putString("name",    name)
                            .putString("email",   email)
                            .putString("user_id", userId)
                            .apply();

                    final String fName    = name;
                    final String fEmail   = email;
                    final String fUserId  = userId;
                    final String fCreated = created.isEmpty() ? "—" : created;

                    runOnUiThread(() -> {
                        if (tvName          != null) tvName.setText(fName);
                        if (tvEmail         != null) tvEmail.setText(fEmail);
                        if (tvUserId        != null) tvUserId.setText(fUserId);
                        if (tvCreated       != null) tvCreated.setText(fCreated);
                        if (tvAvatarInitial != null && !fName.isEmpty())
                            tvAvatarInitial.setText(
                                    String.valueOf(fName.charAt(0)).toUpperCase());
                    });

                } else {
                    Log.e(TAG, "HTTP " + code + " -> " + body);
                    runOnUiThread(() -> Toast.makeText(this,
                            "Could not load profile (HTTP " + code + ")",
                            Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                Log.e(TAG, "loadProfile exception", e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}