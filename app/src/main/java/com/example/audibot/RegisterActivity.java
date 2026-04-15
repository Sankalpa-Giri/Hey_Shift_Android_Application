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

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    EditText nameInput, emailInput, passwordInput;
    Button registerBtn;
    TextView goToLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        nameInput     = findViewById(R.id.input_name);
        emailInput    = findViewById(R.id.input_email);
        passwordInput = findViewById(R.id.input_password);
        registerBtn   = findViewById(R.id.btn_register);
        goToLogin     = findViewById(R.id.tv_go_login);

        registerBtn.setOnClickListener(v -> {
            String name     = nameInput.getText().toString().trim();
            String email    = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            registerBtn.setEnabled(false);
            registerBtn.setText("Creating account...");
            registerUser(name, email, password);
        });

        goToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser(String name, String email, String password) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject body = new JSONObject();
                body.put("name",     name);
                body.put("email",    email);
                body.put("password", password);
                String bodyStr = body.toString();

                Log.d(TAG, "POST /auth/register  body=" + bodyStr);

                URL url = new URL(Constants.BASE_URL + "/auth/register");
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
                    String uname  = response.optString("name", name);

                    getSharedPreferences("auth_prefs", MODE_PRIVATE).edit()
                            .putString("token",   token)
                            .putString("user_id", userId)
                            .putString("name",    uname)
                            .apply();

                    runOnUiThread(() -> {
                        Toast.makeText(this, "Account created! Welcome " + uname,
                                Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, MainActivity.class));
                        finish();
                    });
                } else {
                    String detail = "Registration failed (code " + code + ")";
                    try {
                        detail = new JSONObject(responseStr).optString("detail", detail);
                    } catch (Exception ignored) {}
                    final String msg = detail;
                    runOnUiThread(() -> {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                        registerBtn.setEnabled(true);
                        registerBtn.setText("Create Account");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "registerUser exception", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    registerBtn.setEnabled(true);
                    registerBtn.setText("Create Account");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }
}