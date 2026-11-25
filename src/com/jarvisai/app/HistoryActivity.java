package com.jarvisai.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ScrollView;

import com.jarvisai.app.ai.AgentBrain;
import com.jarvisai.app.memory.MemoryDB;
import com.jarvisai.app.automation.Confirm;
import com.jarvisai.app.automation.ReminderReceiver;
import com.jarvisai.app.ui.AuroraView;

import java.util.List;

/** Conversation history view with clear/delete options. */
public class HistoryActivity extends BaseActivity {

    private TextView status;
    private LinearLayout list;
    private ScrollView scroll;
    private int conv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        conv = getIntent().getIntExtra(ChatActivity.EXTRA_CONV, prefs.conversationId());
        if (conv <= 0) conv = JarvisApp.get().db().ensureTodayConversation();
        prefs.setConversationId(conv);
        buildRoot();
        buildUi();
        loadHistory();
        Confirm.attach(this);
    }

    private void buildUi() {
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        screen.addView(col, cp);

        String title = "History";
        try {
            for (MemoryDB.Conversation c : JarvisApp.get().db().conversations()) {
                if (c.id == conv) { title = "Conversation " + c.title; break; }
            }
        } catch (Exception ignored) {}
        col.addView(Ui.topBar(this, title, "JARVIS", true, null));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = Ui.column(this);
        list.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        scroll.addView(list);
        col.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Delete button
        LinearLayout actions = Ui.row(this);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        actions.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        TextView clear = Ui.primaryButton(this, "Clear conversation history", v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Clear conversation?")
                    .setMessage("Ye kya yaad delete karna hai? Kitni history saaf karni hai?")
                    .setPositiveButton("Clear all messages", (d, w) -> {
                        JarvisApp.get().db().clearMessages();
                        loadHistory();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        actions.addView(clear, ap);
        TextView clearAll = Ui.ghostButton(this, "Clear all memories", v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Clear all memories?")
                    .setMessage("Kya aap yeh chahte hain ki JARVIS poori tarah se apni yaadein bhul jaye?")
                    .setPositiveButton("Delete everything", (d, w) -> {
                        JarvisApp.get().db().clearAll();
                        loadHistory();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        actions.addView(clearAll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(actions);
    }

    private void loadHistory() {
        list.removeAllViews();
        List<MemoryDB.Message> msgs = JarvisApp.get().db().messages(conv, 200);
        if (msgs.isEmpty()) {
            addSystemNote("Is conversation mein koi message nahi hai. Shuru karein!");
        } else {
            String lastDay = "";
            for (MemoryDB.Message m : msgs) {
                String day = new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                        .format(new java.util.Date(m.ts));
                if (!day.equals(lastDay)) {
                    addDay(day);
                    lastDay = day;
                }
                addBubble(m.role, m.text, m.ts);
            }
        }
        Ui.scrollToBottom(scroll);
    }

    private void addDay(String day) {
        TextView t = Ui.text(this, day, 10.5f, Theme.faint());
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 8));
        list.addView(t);
    }

    private void addBubble(String role, String text, long ts) {
        LinearLayout holder = Ui.row(this);
        holder.setGravity("user".equals(role) ? Gravity.END : Gravity.START);
        holder.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));

        LinearLayout bubble = Ui.column(this);
        bubble.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        int bg = "user".equals(role)
                ? (Theme.isDark() ? 0xFF17304E : 0xFFDCEBFF)
                : (Theme.isDark() ? 0xFF111A2E : 0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable g = Util.roundedStroke(bg,
                "user".equals(role) ? Theme.alpha(Theme.accent(), 0.35f) : Theme.stroke(), 18, 1, this);
        bubble.setBackground(g);

        TextView body = Ui.text(this, text == null ? "" : text, 14f,
                "user".equals(role) ? (Theme.isDark() ? 0xFFEAF6FF : 0xFF0B2C4A) : Theme.text());
        body.setTextIsSelectable(true);
        bubble.addView(body);

        if (ts > 0) {
            TextView time = Ui.text(this, Util.time(ts), 9.5f, Theme.faint());
            time.setGravity("user".equals(role) ? Gravity.END : Gravity.START);
            time.setPadding(0, Ui.dp(this, 4), 0, 0);
            bubble.addView(time);
        }

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.leftMargin = "user".equals(role) ? Ui.dp(this, 46) : 0;
        bp.rightMargin = "user".equals(role) ? 0 : Ui.dp(this, 46);
        holder.addView(bubble, bp);
        list.addView(holder);
        Ui.scrollToBottom(scroll);
    }

    private void addSystemNote(String text) {
        TextView t = Ui.text(this, text, 12f, Theme.muted());
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24));
        list.addView(t);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Confirm.attach(this);
    }

    @Override
    protected void onPause() {
        Confirm.detach(this);
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Confirm.detach(this);
        super.onDestroy();
    }
}