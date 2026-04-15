package com.example.audibot;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    EditText emailInput, passwordInput;
    Button loginBtn;
    TextView goToRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("auth_prefs", MODE_PRIVATE);
        if (prefs.getString("token", null) != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        emailInput    = findViewById(R.id.input_email);
        passwordInput = findViewById(R.id.input_password);
        loginBtn      = findViewById(R.id.btn_login);
        goToRegister  = findViewById(R.id.tv_go_register);

        loginBtn.setOnClickListener(v -> {
            String email    = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            loginBtn.setEnabled(false);
            loginBtn.setText("Logging in...");
            loginUser(email, password);
        });

        goToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void loginUser(String email, String password) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);
                String bodyStr = body.toString();

                Log.d(TAG, "POST /auth/login  body=" + bodyStr);

                URL url = new URL(Constants.BASE_URL + "/auth/login");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("ngrok-skip-browser-warning", "69420");

                byte[] bytes = bodyStr.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream(),
                        StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                String responseStr = sb.toString();
                Log.d(TAG, "HTTP " + code + " -> " + responseStr);

                if (code == 200) {
                    JSONObject response = new JSONObject(responseStr);
                    String token  = response.getString("access_token");
                    String userId = response.getString("user_id");
                    String name   = response.optString("name", "User");

                    getSharedPreferences("auth_prefs", MODE_PRIVATE).edit()
                            .putString("token",   token)
                            .putString("user_id", userId)
                            .putString("name",    name)
                            .apply();

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Welcome back, " + name + "!",
                                Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    });
                } else {
                    String detail = "Login failed (code " + code + ")";
                    try {
                        detail = new JSONObject(responseStr).optString("detail", detail);
                    } catch (Exception ignored) {}
                    final String msg = detail;
                    runOnUiThread(() -> {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        loginBtn.setEnabled(true);
                        loginBtn.setText("Login");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "loginUser exception", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    loginBtn.setEnabled(true);
                    loginBtn.setText("Login");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}