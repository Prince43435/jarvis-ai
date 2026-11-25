package com.jarvisai.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvisai.app.ai.AgentBrain;
import com.jarvisai.app.memory.MemoryDB;
import com.jarvisai.app.automation.Confirm;

import java.util.List;

/** Automation: reminders, alarms, tasks, notes, floating orb. */
public class AutomationActivity extends BaseActivity {

    private ScrollView scroll;
    private LinearLayout list;
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
        brain = new AgentBrain(this, conv);
        Confirm.attach(this);
    }

    private void buildUi() {
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        screen.addView(col, cp);

        String title = "Automation";
        col.addView(Ui.topBar(this, title, "JARVIS", true, null));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = Ui.column(this);
        list.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        scroll.addView(list);
        col.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        loadSections();
    }

    private void loadSections() {
        list.removeAllViews();

        // Reminders
        addSectionTitle("Reminders");
        addReminders();

        // Alarms
        addSectionTitle("System Alarms");
        addAlarms();

        // Tasks
        addSectionTitle("Tasks");
        addTasks();

        // Notes
        addSectionTitle("Notes");
        addNotes();

        // Floating orb
        addSectionTitle("Floating Orb");
        addOrb();

        // Help
        addSectionTitle("Help");
        addHelp();

        Ui.scrollToBottom(scroll);
    }

    private void addSectionTitle(String title) {
        LinearLayout t = Ui.sectionTitle(this, title);
        list.addView(t);
    }

    private void addReminders() {
        List<MemoryDB.Reminder> reminders = JarvisApp.get().db().reminders(false);
        if (reminders.isEmpty()) {
            addSystemNote("Koi reminder set karne ke liye, bolein");
        } else {
            for (MemoryDB.Reminder r : reminders) {
                LinearLayout row = Ui.row(this);
                row.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
                row.setBackground(Util.rounded(Theme.isDark() ? 0x0DFFFFFF : 0x08000000, 12, this));

                TextView body = Ui.text(this, r.text, 13f, Theme.text());
                TextView when = Ui.text(this, Util.friendlyDate(r.triggerAt), 10.5f, Theme.faint());

                LinearLayout col2 = Ui.column(this);
                col2.addView(body);
                col2.addView(when, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                row.addView(col2, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView done = Ui.pill(this, "Done", Theme.GREEN);
                done.setOnClickListener(v -> {
                    JarvisApp.get().db().markReminderDone(r.id);
                    loadSections();
                });
                row.addView(done, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));

                TextView del = Ui.pill(this, "×", Theme.faint());
                del.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
                del.setOnClickListener(v -> {
                    JarvisApp.get().db().deleteReminder(r.id);
                    loadSections();
                });
                row.addView(del);

                list.addView(row);
            }
        }

        TextView addBtn = Ui.primaryButton(this, "+ Add reminder", v -> {
            Ui.prompt(this, "Reminder", "Text", null, false,
                    s -> Ui.prompt(this, "Minutes from now", "e.g. 30", "30", false,
                            min -> {
                                try {
                                    int m = Integer.parseInt(min);
                                    if (m > 0) {
                                        sendToJson("add a reminder to remind me: " + s + " in " + m + " minutes");
                                        loadSections();
                                    }
                                } catch (Exception ignored) {}
                            }));
        });
        list.addView(addBtn);
    }

    private void addAlarms() {
        // Use the set_alarm tool via AI or show info
        TextView info = Ui.text(this,
                "System alarms par awas fa pine ke liye, merge karein: \"Awaasein\" aur koi time bataen. " +
                "JARVIS inse Clock app par alarm set karega.",
                12f, Theme.muted());
        info.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        list.addView(info);
    }

    private void addTasks() {
        List<MemoryDB.Task> tasks = JarvisApp.get().db().tasks(false);
        if (tasks.isEmpty()) {
            addSystemNote("Koi task set karne ke liye, bolein");
        } else {
            for (MemoryDB.Task t : tasks) {
                LinearLayout row = Ui.row(this);
                row.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
                row.setBackground(Util.rounded(Theme.isDark() ? 0x0DFFFFFF : 0x08000000, 12, this));

                TextView title = Ui.text(this, t.title, 13f, Theme.text());
                LinearLayout col2 = Ui.column(this);
                col2.addView(title);
                row.addView(col2, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView done = Ui.pill(this, "Complete", Theme.GREEN);
                done.setOnClickListener(v -> {
                    JarvisApp.get().db().toggleTask(t.id);
                    loadSections();
                });
                row.addView(done);

                TextView del = Ui.pill(this, "Delete", Theme.faint());
                del.setOnClickListener(v -> {
                    JarvisApp.get().db().deleteTask(t.id);
                    loadSections();
                });
                row.addView(del, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));

                list.addView(row);
            }
        }

        TextView addBtn = Ui.primaryButton(this, "+ Add task", v -> {
            Ui.prompt(this, "Task", "Title", null, false,
                    s -> {
                        if (!s.isEmpty()) {
                            sendToJson("add task: " + s);
                            loadSections();
                        }
                    });
        });
        list.addView(addBtn);
    }

    private void addNotes() {
        List<MemoryDB.Note> notes = JarvisApp.get().db().notes();
        if (notes.isEmpty()) {
            addSystemNote("Koi note save karne ke liye, bolein");
        } else {
            for (MemoryDB.Note n : notes) {
                LinearLayout row = Ui.row(this);
                row.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
                row.setBackground(Util.rounded(Theme.isDark() ? 0x0DFFFFFF : 0x08000000, 12, this));

                TextView title = Ui.text(this, n.title, 13f, Theme.text());
                TextView body = Ui.text(this, n.body == null ? "" : Util.truncate(n.body, 100), 10.5f, Theme.muted());
                LinearLayout col2 = Ui.column(this);
                col2.addView(title);
                col2.addView(body, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                row.addView(col2, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                TextView del = Ui.pill(this, "×", Theme.faint());
                del.setOnClickListener(v -> {
                    JarvisApp.get().db().deleteNote(n.id);
                    loadSections();
                });
                row.addView(del);

                list.addView(row);
            }
        }
    }

    private void addOrb() {
        if (prefs.overlayEnabled()) {
            addSystemNote("Floating orb hai. Kisi bhi app ke upar JARVIS bula sakte hain.");
        } else {
            TextView btn = Ui.primaryButton(this, "Show floating orb", v -> toggleOrb());
            list.addView(btn);
        }
    }

    private void toggleOrb() {
        if (!prefs.overlayEnabled()) {
            if (!canOverlay()) {
                Util.toast(this, "Display over other apps permission chahiye.");
                openOverlaySettings();
                return;
            }
            prefs.setOverlayEnabled(true);
            startForegroundService(new android.content.Intent(this,
                    com.jarvisai.app.automation.OverlayService.class));
            loadSections();
        } else {
            prefs.setOverlayEnabled(false);
            stopService(new android.content.Intent(this,
                    com.jarvisai.app.automation.OverlayService.class));
            loadSections();
        }
    }

    private void addHelp() {
        addSystemNote("Bovelength: \"remind me\", \"add task\", \"save memory\", \"tell me a joke\", " +
                "\"screen dekho\", \"open whatsapp\", \"device info\".");
    }

    private void addSystemNote(String text) {
        TextView t = Ui.text(this, text, 12f, Theme.muted());
        t.setGravity(android.view.Gravity.CENTER);
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16));
        list.addView(t);
    }

    private void sendToJson(String prompt) {
        sendTyped(prompt);
    }

    private void sendTyped(String text) {
        if (brain != null) {
            brain.send(text, null, false, new AgentBrain.Listener() {
                @Override public void onStatus(String s) {}
                @Override public void onDelta(String t) {}
                @Override public void onTool(String name, String message, boolean ok) {}
                @Override public void onFinal(String t) {
                    Util.toast(AutomationActivity.this, t);
                    loadSections();
                }
                @Override public void onError(String message) {}
            });
        }
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