package com.jarvisai.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvisai.app.ai.AgentBrain;
import com.jarvisai.app.automation.Confirm;
import com.jarvisai.app.memory.MemoryDB;
import com.jarvisai.app.ui.AuroraView;

import java.util.List;

/** Saved memories: search, edit, and delete remembered facts. */
public class MemoryActivity extends BaseActivity {

    private TextView status;
    private LinearLayout list;
    private ScrollView scroll;
    private EditText searchInput;
    private int conv;
    private AgentBrain brain;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        conv = getIntent().getIntExtra(ChatActivity.EXTRA_CONV, prefs.conversationId());
        if (conv <= 0) conv = JarvisApp.get().db().ensureTodayConversation();
        prefs.setConversationId(conv);
        buildRoot();
        buildUi();
        loadMemories();
        brain = new AgentBrain(this, conv);
        Confirm.attach(this);
    }

    private void buildUi() {
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        screen.addView(col, cp);

        String title = "Memory";
        col.addView(Ui.topBar(this, title, "JARVIS", true, null));

        // Search row
        LinearLayout searchRow = Ui.row(this);
        searchRow.setBackground(Util.roundedStroke(Theme.isDark() ? 0x14FFFFFF : 0x0A000000,
                Theme.stroke(), 20, 1, this));
        searchRow.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));

        searchInput = new EditText(this);
        searchInput.setHint("Search memories…");
        searchInput.setTextColor(Theme.text());
        searchInput.setHintTextColor(Theme.faint());
        searchInput.setTextSize(14f);
        searchInput.setBackground(null);
        searchInput.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        searchRow.addView(searchInput, ip);

        TextView searchBtn = Ui.text(this, "↑", 18f, Theme.muted());
        searchBtn.setOnClickListener(v -> doSearch(searchInput.getText().toString().trim()));
        searchRow.addView(searchBtn, new LinearLayout.LayoutParams(
                Ui.dp(this, 36), Ui.dp(this, 36)));

        col.addView(searchRow);

        // List
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        list = Ui.column(this);
        list.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        sv.addView(list);
        col.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Action
        TextView newNote = Ui.primaryButton(this, "New note", v -> addNewNotePrompt());
        col.addView(newNote);
    }

    private void addNewNotePrompt() {
        Ui.prompt(this, "Note title", "Title", null, false,
                s -> Ui.prompt(this, "Note body", "Body", s, true,
                        sb -> {
                            int id = JarvisApp.get().db().addNote(s, sb);
                            Util.toast(this, "Note save ho gaya: " + s);
                            loadMemories();
                        }));
    }

    private void doSearch(String query) {
        list.removeAllViews();
        if (query.isEmpty()) {
            loadMemories();
            return;
        }
        // Search notes
        List<MemoryDB.Note> notes = JarvisApp.get().db().notes();
        boolean found = false;
        for (MemoryDB.Note n : notes) {
            if (n.title.toLowerCase().contains(query.toLowerCase()) ||
                    n.body.toLowerCase().contains(query.toLowerCase())) {
                addMemoryNote(n.title, n.body, n.id);
                found = true;
            }
        }
        if (!found) {
            addSystemNote("Is query se koi memory nahi mili.");
        }
        Ui.scrollToBottom(scroll);
    }

    private void loadMemories() {
        list.removeAllViews();
        List<MemoryDB.Memory> mems = JarvisApp.get().db().memories();
        if (mems.isEmpty()) {
            addSystemNote("Abhi koi saved memory nahi hai. Baatein yaad rakh kar change karein.");
        } else {
            String lastCat = "";
            for (MemoryDB.Memory m : mems) {
                // Simple grouping by category display
                if (!m.category.equals(lastCat)) {
                    addCategoryHeader(m.category);
                    lastCat = m.category;
                }
                addMemoryRow(m.key, m.value, m.id, m.importance);
            }
        }
        Ui.scrollToBottom(scroll);
    }

    private void addCategoryHeader(String cat) {
        TextView h = Ui.text(this, cat == null ? "General" : cat, 11f, Theme.muted());
        h.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 4));
        h.setTypeface(null, android.graphics.Typeface.BOLD);
        list.addView(h);
    }

    private void addMemoryRow(String key, String value, int id, int importance) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        row.setBackground(Util.rounded(Theme.isDark() ? 0x0DFFFFFF : 0x08000000, 12, this));

        TextView k = Ui.text(this, (key == null || key.isEmpty()) ? "Fact" : key, 12f, Theme.text());
        k.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView v = Ui.text(this, value == null ? "" : Util.truncate(value, 240), 11f,
                Theme.muted());

        // Delete button
        TextView del = Ui.ghostButton(this, "×", dummy -> {});
        del.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        del.setOnClickListener(dvv -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Forget this memory?")
                    .setMessage("Kya yeh memory delete karna hai?")
                    .setPositiveButton("Delete", (d, w) -> {
                        JarvisApp.get().db().deleteMemory(id);
                        loadMemories();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.END;
        row.addView(k, lp);
        row.addView(v, rp);
        row.addView(del, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        list.addView(row);
        Ui.scrollToBottom(scroll);
    }

    private void addMemoryNote(String title, String body, int id) {
        addSystemNote(title + ": " + (body == null ? "" : Util.truncate(body, 120)));
    }

    private void addSystemNote(String text) {
        TextView t = Ui.text(this, text, 12f, Theme.muted());
        t.setGravity(android.view.Gravity.CENTER);
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