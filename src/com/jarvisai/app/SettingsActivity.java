package com.jarvisai.app;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvisai.app.automation.Confirm;

import java.util.ArrayList;
import java.util.List;

/** Settings: API key, models, voice, theme, permissions. */
public class SettingsActivity extends BaseActivity {

    private List<TextView> keyDisplayList = new ArrayList<>();
    private List<TextView> voiceDisplayList = new ArrayList<TextView>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildRoot();
        buildUi();
        Confirm.attach(this);
    }

    private void buildUi() {
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        screen.addView(col, cp);

        String title = "Settings";
        col.addView(Ui.topBar(this, title, "JARVIS", true, null));

        // API Key Card
        addKeyCard(col);
        // Models Card
        addModelCard(col);
        // Voice Card
        addVoiceCard(col);
        // Behaviour Card
        addBehaviourCard(col);
        // About Card
        addAboutCard(col);
    }

    private void addKeyCard(LinearLayout col) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = Ui.dp(this, 6);
        col.addView(card, cp);

        // Row: label + input
        LinearLayout keyRow = Ui.row(this);
        keyRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        keyRow.setBackground(Util.roundedStroke(Theme.isDark() ? 0x14FFFFFF : 0x0A000000,
                Theme.stroke(), 14, 1, this));

        TextView label = Ui.label(this, "GEMINI API KEY", Theme.muted());
        keyRow.addView(label, new LinearLayout.LayoutParams(Ui.dp(this, 50), ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText keyInput = new EditText(this);
        keyInput.setHint("AIza...");
        keyInput.setTextColor(Theme.text());
        keyInput.setHintTextColor(Theme.faint());
        keyInput.setTextSize(13f);
        keyInput.setBackground(null);
        keyInput.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        String existingKey = prefs.apiKey();
        if (existingKey != null && existingKey.length() > 6) {
            keyInput.setText(existingKey);
            keyInput.setSelection(existingKey.length());
        }
        keyRow.addView(keyInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(keyRow);

        TextView hint = Ui.text(this, "aistudio.google.com/apikey · Encrypted on this device", 10f, Theme.faint());
        hint.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), 0);
        card.addView(hint);
    }

    private void addModelCard(LinearLayout col) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = Ui.dp(this, 10);
        col.addView(card, cp);

        card.addView(Ui.label(this, "MODELS", Theme.muted()));
        card.addView(Ui.text(this, "Text model", 12f, Theme.muted()),
                new LinearLayout.LayoutParams(Ui.dp(this, 80), ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(createChip(prefs.textModel()));

        card.addView(Ui.text(this, "Live voice model", 12f, Theme.muted()),
                new LinearLayout.LayoutParams(Ui.dp(this, 80), ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(createChip(prefs.liveModel()));
    }

    private TextView createChip(String current) {
        LinearLayout chips = Ui.row(this);
        TextView lastChip = null;
        String[] options;
        String prefix;
        if (current.equals("gemini-2.5-flash")) {
            options = new String[]{"gemini-2.5-flash", "gemini-1.5-flash"};
            prefix = "gemini-2.5-flash";
        } else if (current.contains("native-audio")) {
            options = new String[]{"gemini-2.5-flash-native-audio-preview-09-2025", "gemini-2.0-flash-exp"};
            prefix = "gemini-2.5-flash-native-audio";
        } else {
            options = new String[]{current, "gemini-1.5-flash", "gemini-2.5-flash"};
            prefix = current;
        }
        // Use default options when applicable
        if (options[0].isEmpty()) options = new String[]{"gemini-2.5-flash"};
        for (final String opt : options) {
            final TextView chip = Ui.pill(this, opt,
                    opt.equals(current) ? Theme.accent() : Theme.muted());
            chip.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 6);
            chips.addView(chip, lp);
            lastChip = chip;
            chip.setOnClickListener(v -> {
                for (TextView c : voiceDisplayList) c.setTextColor(Theme.muted());
                chip.setTextColor(Theme.accent());
                // Update prefs
                updatePrefsForModel(opt);
            });
        }
        return lastChip;
    }

    private void updatePrefsForModel(String opt) {
        if (opt.contains("native-audio")) {
            prefs.setLiveModel(opt);
        } else if (opt.contains("2.5")) {
            prefs.setTextModel(opt);
        } else {
            prefs.setTextModel(opt);
        }
    }

    private void addVoiceCard(LinearLayout col) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = Ui.dp(this, 10);
        col.addView(card, cp);

        card.addView(Ui.label(this, "VOICE & SPEECH", Theme.muted()));
        card.addView(Ui.text(this, "TTS Voice", 12f, Theme.muted()),
                new LinearLayout.LayoutParams(Ui.dp(this, 80), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout voiceChip = Ui.row(this);
        voiceChip.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        for (final String voice : new String[]{"Aoede", "Nisha", "Rita", "Kalpana", "Default"}) {
            final TextView chip = Ui.pill(this, voice,
                    voice.equals(prefs.liveVoiceName()) ? Theme.accent() : Theme.muted());
            chip.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 6);
            voiceChip.addView(chip, lp);
            chip.setOnClickListener(v -> {
                for (TextView c : voiceDisplayList) c.setTextColor(Theme.muted());
                chip.setTextColor(Theme.accent());
                if (!"Default".equals(voice)) prefs.setLiveVoiceName(voice);
            });
        }
        card.addView(voiceChip);
    }

    private void addBehaviourCard(LinearLayout col) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = Ui.dp(this, 10);
        col.addView(card, cp);

        card.addView(Ui.label(this, "BEHAVIOUR", Theme.muted()));
        addBehaviourToggle(card, "Auto listen after reply", prefs.autoListen(), v -> prefs.setAutoListen(v));
        addBehaviourToggle(card, "Auto memory capture", prefs.autoMemory(), v -> prefs.setAutoMemory(v));
        addBehaviourToggle(card, "Confirm sensitive actions", prefs.confirmActions(), v -> prefs.setConfirmActions(v));
    }

    private void addBehaviourToggle(LinearLayout card, String label, boolean current, PrefsToggler toggler) {
        LinearLayout row = Ui.row(this);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        final TextView toggle = Ui.pill(this, current ? "On" : "Off", current ? Theme.accent() : Theme.muted());
        toggle.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
        toggle.setOnClickListener(v -> {
            boolean next = !prefs.autoListen();
            toggle.setText(next ? "On" : "Off");
            toggle.setTextColor(next ? Theme.accent() : Theme.muted());
            if (toggler != null) toggler.set(next);
        });
        row.addView(Ui.text(this, label, 13f, Theme.text()), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(toggle);
        card.addView(row);
    }

    private interface PrefsToggler {
        void set(boolean value);
    }

    private void addAboutCard(LinearLayout col) {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = Ui.dp(this, 10);
        col.addView(card, cp);

        card.addView(Ui.label(this, "ABOUT", Theme.muted()));
        card.addView(Ui.text(this, "Version 1.0.0", 12f, Theme.muted()));
        card.addView(Ui.text(this, "Data encrypted with Android Keystore", 10.5f, Theme.faint()));
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
    protected void onDestroy() {
        Confirm.detach(this);
        super.onDestroy();
    }
}