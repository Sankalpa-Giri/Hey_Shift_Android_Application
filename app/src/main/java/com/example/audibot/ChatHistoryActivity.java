package com.example.audibot;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.*;

import org.json.*;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

/**
 * FIX: Added Toolbar with back button.
 * FIX: Added swipe-to-delete on sessions.
 * FIX: SessionAdapter click → SessionDetailActivity (which has delete + back).
 */
public class ChatHistoryActivity extends AppCompatActivity {

    private static final String TAG = "ChatHistory";

    RecyclerView  recyclerView;
    LinearLayout  layoutEmpty;
    List<SessionItem> sessions = new ArrayList<>();
    SessionAdapter    adapter;

    // ── Data model ────────────────────────────────────────────────────────────
    static class SessionItem {
        String sessionId, title, preview, timestamp;
        int    messageCount;
        SessionItem(String sid, String t, String p, String ts, int mc) {
            sessionId = sid; title = t; preview = p; timestamp = ts; messageCount = mc;
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────
    class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            TextView tvTitle, tvPreview, tvTime, tvCount;
            VH(View v) {
                super(v);
                tvTitle   = v.findViewById(R.id.tv_session_title);
                tvPreview = v.findViewById(R.id.tv_session_preview);
                tvTime    = v.findViewById(R.id.tv_session_time);
                tvCount   = v.findViewById(R.id.tv_session_count);
            }
        }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_session, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            SessionItem item = sessions.get(pos);
            if (h.tvTitle != null)
                h.tvTitle.setText(item.title.isEmpty()
                        ? "Session " + item.sessionId.substring(0, Math.min(8, item.sessionId.length()))
                        : item.title);
            if (h.tvPreview != null)
                h.tvPreview.setText(item.preview.isEmpty() ? "No messages yet" : item.preview);
            if (h.tvTime != null)
                h.tvTime.setText(formatTimestamp(item.timestamp));
            if (h.tvCount != null)
                h.tvCount.setText(item.messageCount + " msg" + (item.messageCount == 1 ? "" : "s"));

            // FIX: Click → SessionDetailActivity (has its own back + delete)
            h.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ChatHistoryActivity.this, SessionDetailActivity.class);
                intent.putExtra("session_id", item.sessionId);
                intent.putExtra("session_title", item.title);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() { return sessions == null ? 0 : sessions.size(); }

        public void removeAt(int pos) {
            sessions.remove(pos);
            notifyItemRemoved(pos);
            if (sessions.isEmpty()) showEmpty(true);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_history);

        // FIX: Toolbar with back button
        Toolbar toolbar = findViewById(R.id.toolbar_chat_history);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("Chat History");
            }
        }

        recyclerView = findViewById(R.id.recycler_sessions);
        layoutEmpty  = findViewById(R.id.layout_empty);

        if (recyclerView == null) {
            Log.e(TAG, "recycler_sessions not found in layout");
            finish();
            return;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        sessions = new ArrayList<>();
        adapter  = new SessionAdapter();
        recyclerView.setAdapter(adapter);

        // FIX: Swipe left to delete
        ItemTouchHelper.SimpleCallback swipeCb = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT) {
            @Override public boolean onMove(RecyclerView rv,
                    RecyclerView.ViewHolder vh, RecyclerView.ViewHolder t) { return false; }

            @Override public void onSwiped(RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAdapterPosition();
                SessionItem item = sessions.get(pos);
                new AlertDialog.Builder(ChatHistoryActivity.this)
                    .setTitle("Delete session")
                    .setMessage("Delete this conversation?")
                    .setPositiveButton("Delete", (d, w) -> deleteSession(item.sessionId, pos))
                    .setNegativeButton("Cancel", (d, w) -> adapter.notifyItemChanged(pos))
                    .setOnCancelListener(d -> adapter.notifyItemChanged(pos))
                    .show();
            }
        };
        new ItemTouchHelper(swipeCb).attachToRecyclerView(recyclerView);

        loadSessions();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    @Override
    protected void onResume() {
        super.onResume();
        loadSessions(); // Refresh list after returning from SessionDetailActivity
    }

    // ── Delete ────────────────────────────────────────────────────────────────
    private void deleteSession(String sessionId, int pos) {
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
                runOnUiThread(() -> {
                    if (code == 200) {
                        adapter.removeAt(pos);
                        Toast.makeText(this, "Session deleted", Toast.LENGTH_SHORT).show();
                    } else {
                        adapter.notifyItemChanged(pos);
                        Toast.makeText(this, "Failed to delete (HTTP " + code + ")",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "deleteSession failed", e);
                runOnUiThread(() -> {
                    adapter.notifyItemChanged(pos);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showEmpty(boolean empty) {
        recyclerView.setVisibility(empty ? View.GONE  : View.VISIBLE);
        if (layoutEmpty != null)
            layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    // ── Network ───────────────────────────────────────────────────────────────
    private void loadSessions() {
        new Thread(() -> {
            try {
                String token = getSharedPreferences("auth_prefs", MODE_PRIVATE)
                        .getString("token", "");
                URL url = new URL(Constants.BASE_URL.trim() + "/chat/sessions");
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
                    runOnUiThread(() -> Toast.makeText(this,
                            "Failed to load history (HTTP " + code + ")",
                            Toast.LENGTH_SHORT).show());
                    return;
                }

                JSONArray arr = new JSONObject(sb.toString()).getJSONArray("sessions");
                List<SessionItem> loaded = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    loaded.add(new SessionItem(
                            o.optString("session_id",""),
                            o.optString("title",""),
                            o.optString("preview",""),
                            o.optString("timestamp",""),
                            o.optInt("message_count", 0)));
                }

                runOnUiThread(() -> {
                    sessions.clear();
                    sessions.addAll(loaded);
                    adapter.notifyDataSetChanged();
                    showEmpty(sessions.isEmpty());
                });

            } catch (MalformedURLException e) {
                Log.e(TAG, "Bad URL", e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Invalid server URL — check Constants.java", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Log.e(TAG, "loadSessions failed", e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Could not load history: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String formatTimestamp(String iso) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            SimpleDateFormat out = new SimpleDateFormat("MMM d, h:mm a", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            out.setTimeZone(TimeZone.getDefault());
            String clean = iso.replaceAll("\\.\\d+","").replace("Z","");
            Date d = in.parse(clean);
            return d != null ? out.format(d) : iso;
        } catch (Exception e) { return iso; }
    }
}