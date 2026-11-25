package com.jarvisai.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvisai.app.ai.GeminiClient;

public class SetupActivity extends BaseActivity {

    private EditText keyInput;
    private TextView testStatus;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (prefs.onboarded() && prefs.hasApiKey()) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        buildRoot();
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout col = Ui.column(this);
        int pad = Ui.dp(this, 22);
        col.setPadding(pad, Ui.dp(this, 12), pad, Ui.dp(this, 28));
        scroll.addView(col);
        screen.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        CharacterView character = new CharacterView(this);
        character.setState(CharacterView.HAPPY);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 300));
        cp.topMargin = Ui.dp(this, 4);
        col.addView(character, cp);

        TextView title = Ui.bold(this, getString(R.string.setup_title), 30f, Theme.text());
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        TextView sub = Ui.text(this, getString(R.string.setup_sub), 13.5f, Theme.muted());
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 16));
        col.addView(sub);

        LinearLayout card = Ui.card(this);
        col.addView(card);

        TextView label = Ui.label(this, getString(R.string.setup_key_label), Theme.accent());
        card.addView(label);

        keyInput = new EditText(this);
        keyInput.setHint(R.string.setup_key_hint);
        keyInput.setText(prefs.apiKey());
        keyInput.setTextSize(13.5f);
        keyInput.setTextColor(Theme.text());
        keyInput.setHintTextColor(Theme.faint());
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setSingleLine(true);
        keyInput.setBackground(Util.roundedStroke(Theme.isDark() ? 0x14FFFFFF : 0x0A000000,
                Theme.stroke(), 14, 1, this));
        keyInput.setPadding(Ui.dp(this, 12), Ui.dp(this, 11), Ui.dp(this, 12), Ui.dp(this, 11));
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        kp.topMargin = Ui.dp(this, 8);
        card.addView(keyInput, kp);

        TextView help = Ui.text(this, getString(R.string.setup_key_help), 11.5f, Theme.faint());
        help.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));
        card.addView(help);

        LinearLayout actions = Ui.row(this);
        TextView test = Ui.ghostButton(this, "Test key", v -> testKey());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.rightMargin = Ui.dp(this, 8);
        actions.addView(test, tp);
        TextView skip = Ui.ghostButton(this, getString(R.string.setup_skip), v -> {
            prefs.setOnboarded(true);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        actions.addView(skip, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(actions);

        testStatus = Ui.text(this, "", 12f, Theme.muted());
        testStatus.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(testStatus);

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(this, 16);
        TextView start = Ui.primaryButton(this, getString(R.string.setup_start), v -> startNow());
        col.addView(start, sp);

        // Language
        LinearLayout langCard = Ui.card(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 14);
        col.addView(langCard, lp);
        langCard.addView(Ui.label(this, "Language", Theme.muted()));
        LinearLayout chips = Ui.row(this);
        chips.setPadding(0, Ui.dp(this, 8), 0, 0);
        String[][] langs = {{"hinglish", "Hinglish"}, {"hindi", "हिंदी"}, {"english", "English"}};
        for (String[] l : langs) {
            TextView chip = Ui.pill(this, l[1],
                    prefs.language().equals(l[0]) ? Theme.accent() : Theme.muted());
            chip.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
            LinearLayout.LayoutParams cp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp2.rightMargin = Ui.dp(this, 8);
            chips.addView(chip, cp2);
            chip.setOnClickListener(v -> {
                prefs.setLanguage(l[0]);
                for (int i = 0; i < chips.getChildCount(); i++) {
                    TextView c = (TextView) chips.getChildAt(i);
                    c.setTextColor(Theme.muted());
                    c.setBackground(Util.rounded(Theme.alpha(Theme.muted(), 0.12f), 100, this));
                }
                chip.setTextColor(Theme.accent());
                chip.setBackground(Util.rounded(Theme.alpha(Theme.accent(), 0.14f), 100, this));
            });
        }
        langCard.addView(chips);

        TextView note = Ui.text(this,
                "JARVIS needs microphone, camera, notification and (optional) accessibility permissions. " +
                        "You can grant them from the Permissions screen — nothing is enabled silently.",
                11.5f, Theme.faint());
        note.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4), 0);
        col.addView(note);

        TextView disclaimer = Ui.text(this, getString(R.string.disclaimer), 10.5f, Theme.faint());
        disclaimer.setGravity(Gravity.CENTER);
        disclaimer.setPadding(0, Ui.dp(this, 18), 0, 0);
        col.addView(disclaimer);
    }

    private void testKey() {
        String key = keyInput.getText().toString().trim();
        if (key.length() < 20) {
            testStatus.setTextColor(Theme.RED);
            testStatus.setText(getString(R.string.setup_error));
            return;
        }
        testStatus.setTextColor(Theme.muted());
        testStatus.setText("Testing…");
        new Thread(() -> {
            String err = GeminiClient.validate(key, prefs.textModel());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (err == null) {
                    testStatus.setTextColor(Theme.GREEN);
                    testStatus.setText("Key works. " + prefs.textModel() + " ready.");
                    prefs.setApiKey(key);
                } else {
                    testStatus.setTextColor(Theme.RED);
                    testStatus.setText(err);
                }
            });
        }, "setup-test").start();
    }

    private void startNow() {
        String key = keyInput.getText().toString().trim();
        if (key.length() < 20) {
            testStatus.setTextColor(Theme.RED);
            testStatus.setText(getString(R.string.setup_error));
            return;
        }
        prefs.setApiKey(key);
        prefs.setOnboarded(true);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
