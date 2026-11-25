package com.jarvisai.app;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvisai.app.ai.AgentBrain;
import com.jarvisai.app.ai.LiveSession;
import com.jarvisai.app.ai.Prompts;
import com.jarvisai.app.automation.Confirm;
import com.jarvisai.app.automation.JarvisAccessibilityService;
import com.jarvisai.app.automation.OverlayService;
import com.jarvisai.app.memory.MemoryDB;
import com.jarvisai.app.ui.AuroraView;
import com.jarvisai.app.voice.VoiceEngine;

import java.util.List;

public class MainActivity extends BaseActivity {

    public static final String EXTRA_VOICE = "voice";

    private CharacterView character;
    private TextView statusText, captionUser, captionJarvis, micHint;
    private LinearLayout captionCard, chipsRow, inputRow, bottomPanel, micBox;
    private EditText input;
    private FrameLayout micButton;
    private ImageView micIcon;
    private HorizontalScrollView chipsScroll;

    private VoiceEngine voice;
    private AgentBrain brain;
    private LiveSession live;
    private int conv;
    private boolean busy, listening, speakingTts, liveFailed, autoStarted;
    private ObjectAnimator micPulse;
    private String streamBuffer = "";
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        conv = JarvisApp.get().db().ensureTodayConversation();
        prefs.setConversationId(conv);
        buildRoot();
        buildUi();
        initVoice();
        brain = new AgentBrain(this, conv);

        character.setState(CharacterView.HAPPY);
        main.postDelayed(() -> character.setState(CharacterView.IDLE), 900);

        if (!prefs.welcomeShown()) {
            prefs.setWelcomeShown(true);
            showCaption(null, Prompts.welcome(this));
        } else {
            showCaption(null, "");
        }

        main.postDelayed(this::maybeAskNotifications, 1600);

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_VOICE, false)) {
            main.postDelayed(this::onMicTap, 700);
        }
        if (!prefs.hasApiKey()) {
            statusText.setText("API key set nahi hai — Settings kholein");
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_VOICE, false)) {
            main.postDelayed(this::onMicTap, 400);
        }
    }

    // ---- UI -------------------------------------------------------------

    private void buildUi() {
        character = new CharacterView(this);
        character.setClickable(true);
        character.setOnClickListener(v -> {
            character.pulse();
            onMicTap();
        });
        screen.addView(character, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // top bar
        LinearLayout top = Ui.row(this);
        top.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.gravity = Gravity.TOP;
        screen.addView(top, tp);

        LinearLayout brand = Ui.column(this);
        LinearLayout brandRow = Ui.row(this);
        TextView title = Ui.bold(this, "JARVIS", 20f, Theme.text());
        title.setLetterSpacing(0.18f);
        brandRow.addView(title);
        TextView ai = Ui.pill(this, "AI", Theme.accent());
        LinearLayout.LayoutParams aip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aip.leftMargin = Ui.dp(this, 8);
        brandRow.addView(ai, aip);
        brand.addView(brandRow);
        statusText = Ui.text(this, getString(R.string.status_idle), 10.5f, Theme.muted());
        statusText.setLetterSpacing(0.05f);
        brand.addView(statusText);
        top.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        top.addView(Ui.iconButton(this, R.drawable.ic_list, 42, Theme.text(), v -> openMenu()));
        top.addView(Ui.hspace(this, 4));
        top.addView(Ui.iconButton(this, R.drawable.ic_settings, 42, Theme.text(), v -> go(SettingsActivity.class)));

        // bottom panel
        bottomPanel = Ui.column(this);
        bottomPanel.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 14));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.gravity = Gravity.BOTTOM;

        GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Theme.alpha(Theme.bg(), 0.97f), Theme.alpha(Theme.bg(), 0.75f), Color.TRANSPARENT});
        View scrimView = new View(this);
        scrimView.setBackground(scrim);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 300));
        sp.gravity = Gravity.BOTTOM;
        screen.addView(scrimView, sp);
        screen.addView(bottomPanel, bp);

        serverOnTop(top);

        // caption
        captionCard = Ui.glass(this);
        captionCard.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = Ui.dp(this, 10);
        bottomPanel.addView(captionCard, cp);
        captionUser = Ui.text(this, "", 11f, Theme.accent());
        captionUser.setVisibility(View.GONE);
        captionCard.addView(captionUser);
        captionJarvis = Ui.text(this, "", 13.5f, Theme.text());
        captionJarvis.setMaxLines(4);
        captionJarvis.setEllipsize(android.text.TextUtils.TruncateAt.END);
        captionCard.addView(captionJarvis);

        // chips
        chipsScroll = new HorizontalScrollView(this);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsRow = Ui.row(this);
        chipsScroll.addView(chipsRow);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chp.bottomMargin = Ui.dp(this, 10);
        bottomPanel.addView(chipsScroll, chp);
        String[][] chips = {
                {"Aaj ka plan", "Aaj ka plan banao aur batao kya karna hai"},
                {"Battery", "Meri battery aur phone ka status batao"},
                {"YouTube", "YouTube kholo"},
                {"Yaad rakho", "Yaad rakho ki mujhe subah 7 baje uthna pasand hai"},
                {"Screen dekho", "Meri screen par kya hai? Screen padho"},
                {"Code help", "Mujhe ek Android app ka project structure banao"}
        };
        for (String[] chip : chips) {
            TextView t = Ui.pill(this, chip[0], Theme.muted());
            t.setTextColor(Theme.text());
            t.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 8);
            chipsRow.addView(t, lp);
            t.setOnClickListener(v -> {
                hideChips();
                sendTyped(chip[1], true, true);
            });
        }

        // input row
        inputRow = Ui.row(this);
        inputRow.setVisibility(View.GONE);
        inputRow.setBackground(Util.roundedStroke(Theme.isDark() ? 0xE6101728 : 0xEEFFFFFF,
                Theme.stroke(), 22, 1, this));
        inputRow.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.bottomMargin = Ui.dp(this, 8);
        bottomPanel.addView(inputRow, ip);

        input = new EditText(this);
        input.setHint(R.string.type_message);
        input.setTextColor(Theme.text());
        input.setHintTextColor(Theme.faint());
        input.setTextSize(14f);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(null);
        input.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        input.setMaxLines(4);
        inputRow.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout sendBtn = Ui.iconButton(this, R.drawable.ic_send, 40, Theme.accent(), v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            input.setText("");
            hideKeyboardRow();
            sendTyped(t, true, false);
        });
        inputRow.addView(sendBtn);

        // controls
        LinearLayout controls = Ui.row(this);
        controls.setGravity(Gravity.CENTER);
        bottomPanel.addView(controls);

        micBox = new LinearLayout(this);
        micBox.setGravity(Gravity.CENTER);

        controls.addView(controlButton(R.drawable.ic_chat, "Chat", v -> go(ChatActivity.class)));
        controls.addView(controlButton(R.drawable.ic_camera, "Camera", v -> openVision()));
        controls.addView(micBox, new LinearLayout.LayoutParams(0,
                Ui.dp(this, 92), 1f));
        controls.addView(controlButton(R.drawable.ic_image, "Screen", v -> analyzeScreen()));
        controls.addView(controlButton(R.drawable.ic_keyboard, "Type", v -> toggleKeyboardRow()));

        micButton = new FrameLayout(this);
        GradientDrawable ring = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x553FE0FF, 0x338B6BFF});
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(Ui.dp(this, 1.5f), 0x883FE0FF);
        micButton.setBackground(ring);
        micIcon = Ui.icon(this, R.drawable.ic_mic, 30, Theme.text());
        FrameLayout.LayoutParams mip = new FrameLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
        mip.gravity = Gravity.CENTER;
        micButton.addView(micIcon, mip);
        LinearLayout.LayoutParams mbp = new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76));
        micBox.addView(micButton, mbp);
        micButton.setOnClickListener(v -> onMicTap());

        micHint = Ui.text(this, "Tap to talk · interrupt anytime", 10.5f, Theme.faint());
        micHint.setGravity(Gravity.CENTER);
        micHint.setPadding(0, Ui.dp(this, 6), 0, 0);
        bottomPanel.addView(micHint);

        updateMicVisual(false);
    }

    /** Helper to keep the bottom scrim behind the panel. */
    private void serverOnTop(LinearLayout top) {
        // top bar must sit above the character
        top.bringToFront();
    }

    private View controlButton(int icon, String label, View.OnClickListener click) {
        LinearLayout col = Ui.column(this);
        col.setGravity(Gravity.CENTER);
        FrameLayout btn = Ui.iconButton(this, icon, 46, Theme.text(), click);
        col.addView(btn);
        TextView t = Ui.text(this, label, 9.5f, Theme.faint());
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 2);
        col.addView(t, lp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(cp);
        return col;
    }

    @Override
    protected void onInsets(int top, int bottom) {
        if (bottomPanel != null) {
            bottomPanel.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16),
                    Ui.dp(this, 14) + bottom);
        }
    }

    // ---- Voice engine ---------------------------------------------------

    private void initVoice() {
        voice = new VoiceEngine(this, new VoiceEngine.Listener() {
            @Override public void onReady(boolean ok, String message) {
                if (!ok && message != null) toast(message);
            }
            @Override public void onListening() {
                listening = true;
                busy = false;
                character.setState(CharacterView.LISTENING);
                statusText.setText(R.string.status_listening);
                updateMicVisual(true);
                hideChips();
            }
            @Override public void onPartial(String text) {
                if (text != null && !text.isEmpty()) showCaption(text, null);
            }
            @Override public void onHeard(String text) {
                listening = false;
                updateMicVisual(false);
                showCaption("You", text);
                sendTyped(text, true, true);
            }
            @Override public void onSpeechStart() {
                speakingTts = true;
                character.setState(CharacterView.SPEAKING);
                statusText.setText(R.string.status_speaking);
                updateMicVisual(true);
            }
            @Override public void onSpeechDone() {
                speakingTts = false;
                updateMicVisual(false);
                if (!busy) {
                    character.setState(prefs.autoListen() && !listening ? CharacterView.IDLE : CharacterView.IDLE);
                    statusText.setText(R.string.status_idle);
                }
                if (prefs.autoListen() && !autoStarted) {
                    autoStarted = true;
                    main.postDelayed(() -> { autoStarted = false; if (!busy && !isFinishing()) onMicTap(); }, 700);
                }
            }
            @Override public void onError(String message) {
                listening = false;
                busy = false;
                updateMicVisual(false);
                character.setState(CharacterView.CONFUSED);
                main.postDelayed(() -> character.setState(CharacterView.IDLE), 1600);
                statusText.setText(R.string.status_idle);
                if (message != null) showHint(message);
            }
        });
        voice.init();
    }

    // ---- Live session ---------------------------------------------------

    private void startLive() {
        if (live != null) live.stop();
        liveFailed = false;
        hideChips();
        live = new LiveSession(this, conv, new LiveSession.Listener() {
            @Override public void onState(int state) {
                switch (state) {
                    case LiveSession.CONNECTING:
                        character.setState(CharacterView.THINKING);
                        statusText.setText(R.string.status_connecting);
                        updateMicVisual(true);
                        break;
                    case LiveSession.LISTENING:
                        character.setState(CharacterView.LISTENING);
                        statusText.setText(R.string.status_listening);
                        updateMicVisual(true);
                        break;
                    case LiveSession.SPEAKING:
                        character.setState(CharacterView.SPEAKING);
                        statusText.setText(R.string.status_speaking);
                        updateMicVisual(true);
                        break;
                    case LiveSession.THINKING:
                        character.setState(CharacterView.THINKING);
                        statusText.setText(R.string.status_thinking);
                        updateMicVisual(true);
                        break;
                    default:
                        character.setState(CharacterView.IDLE);
                        statusText.setText(R.string.status_idle);
                        updateMicVisual(false);
                }
            }
            @Override public void onUserText(String text) {
                if (text != null && !text.isEmpty()) showCaption(text, null);
            }
            @Override public void onAssistantText(String text) {
                if (text != null && !text.isEmpty()) showCaption(null, text);
            }
            @Override public void onTurn(String userText, String assistantText) {
                if (userText != null && !userText.isEmpty()) captionUser.setText("You · " + userText);
                if (assistantText != null && !assistantText.isEmpty()) captionJarvis.setText(assistantText);
            }
            @Override public void onAudioLevel(float level) {
                character.setAmplitude(level);
            }
            @Override public void onError(String message) {
                liveFailed = true;
                if (live != null) { live.stop(); live = null; }
                character.setState(CharacterView.CONFUSED);
                statusText.setText(R.string.status_idle);
                updateMicVisual(false);
                showHint(message == null ? "Live voice fail hui. Classic mode try karein." : message);
                main.postDelayed(() -> character.setState(CharacterView.IDLE), 2000);
            }
        });
        live.start();
    }

    private void stopLive() {
        if (live != null) {
            live.stop();
            live = null;
        }
    }

    // ---- Interaction ----------------------------------------------------

    private void onMicTap() {
        character.pulse();
        if (live != null && live.state() != LiveSession.CLOSED) {
            stopLive();
            character.setState(CharacterView.IDLE);
            statusText.setText(R.string.status_idle);
            updateMicVisual(false);
            return;
        }
        if (speakingTts) {
            voice.stopSpeaking();
            speakingTts = false;
            character.setState(CharacterView.IDLE);
            statusText.setText(R.string.status_idle);
            updateMicVisual(false);
            return;
        }
        if (busy) {
            if (brain != null) brain.cancel();
            busy = false;
            character.setState(CharacterView.IDLE);
            statusText.setText(R.string.status_idle);
            updateMicVisual(false);
            showHint("Ruk gayi. Bataiye, kya karna hai?");
            return;
        }
        if (!prefs.hasApiKey()) {
            showHint("Pehle Gemini API key set karein.");
            go(SettingsActivity.class);
            return;
        }
        if (!hasMic()) {
            askMic();
            showHint("Microphone permission chahiye.");
            return;
        }
        if (!Net.online(this)) {
            showHint("Internet nahi hai. Offline mode mein sirf device actions chalenge.");
            character.setState(CharacterView.SAD);
            main.postDelayed(() -> character.setState(CharacterView.IDLE), 1600);
            return;
        }
        autoStarted = false;
        if (prefs.liveVoice() && !liveFailed) {
            startLive();
        } else {
            voice.stopSpeaking();
            listening = true;
            character.setState(CharacterView.LISTENING);
            statusText.setText(R.string.status_listening);
            updateMicVisual(true);
            hideChips();
            voice.listen();
        }
    }

    private void sendTyped(String text, boolean speak, boolean voiceStyle) {
        if (text == null || text.trim().isEmpty()) return;
        if (!prefs.hasApiKey()) {
            showHint("Gemini API key set nahi hai.");
            return;
        }
        busy = true;
        streamBuffer = "";
        character.setState(CharacterView.THINKING);
        statusText.setText(R.string.status_thinking);
        updateMicVisual(true);
        hideChips();
        showCaption("You", text);
        captionJarvis.setText("…");

        if (brain == null) brain = new AgentBrain(this, conv);
        brain.send(text, null, voiceStyle, new AgentBrain.Listener() {
            @Override public void onStatus(String s) {
                if (s != null && !s.isEmpty()) statusText.setText(s);
            }
            @Override public void onDelta(String t) {
                if (t == null) return;
                if (streamBuffer.isEmpty()) captionJarvis.setText("");
                streamBuffer += t;
                captionJarvis.setText(streamBuffer);
            }
            @Override public void onTool(String name, String message, boolean ok) {
                if (!streamBuffer.isEmpty()) {
                    streamBuffer += "\n";
                }
                streamBuffer += (ok ? "✓ " : "✕ ") + message;
                captionJarvis.setText(streamBuffer);
            }
            @Override public void onFinal(String text) {
                busy = false;
                captionJarvis.setText(text);
                statusText.setText(R.string.status_idle);
                if (speak && voice != null) {
                    voice.speak(text);
                } else {
                    updateMicVisual(false);
                    character.setState(CharacterView.IDLE);
                }
                prefs.setWelcomeShown(true);
            }
            @Override public void onError(String message) {
                busy = false;
                updateMicVisual(false);
                character.setState(CharacterView.CONFUSED);
                main.postDelayed(() -> character.setState(CharacterView.IDLE), 1800);
                showHint(message);
                statusText.setText(R.string.status_idle);
            }
        });
    }

    private void openVision() {
        startActivity(new Intent(this, VisionActivity.class));
    }

    private void analyzeScreen() {
        if (JarvisAccessibilityService.get() == null) {
            showHint("Screen dekhne ke liye Accessibility on karein (Permissions).");
            go(PermissionsActivity.class);
            return;
        }
        showHint("Screen padh rahi hoon…");
        new Thread(() -> {
            String summary;
            JarvisAccessibilityService svc = JarvisAccessibilityService.get();
            summary = svc == null ? "" : svc.summarize(80, 1800);
            runOnUiThread(() -> {
                if (summary.isEmpty()) {
                    showHint("Screen par koi readable text nahi mila.");
                } else {
                    sendTyped("Ye meri screen ka text hai. Analyze karke batao kya ho raha hai aur main kya kar sakta hoon:\n" + summary,
                            true, false);
                }
            });
        }, "screen-read").start();
    }

    private void toggleKeyboardRow() {
        if (inputRow.getVisibility() == View.VISIBLE) {
            hideKeyboardRow();
        } else {
            inputRow.setVisibility(View.VISIBLE);
            input.requestFocus();
            Util.showKeyboard(input);
            chipsScroll.setVisibility(View.GONE);
        }
    }

    private void hideKeyboardRow() {
        inputRow.setVisibility(View.GONE);
        Util.hideKeyboard(input);
    }

    private void hideChips() {
        if (chipsScroll != null) chipsScroll.setVisibility(View.GONE);
    }

    private void showCaption(String user, String jarvis) {
        if (user != null) {
            String text = user.trim();
            if (text.isEmpty()) {
                captionUser.setVisibility(View.GONE);
            } else {
                String prefix = "You · ";
                captionUser.setText(text.startsWith(prefix) ? text : prefix + text);
                captionUser.setVisibility(View.VISIBLE);
            }
        }
        if (jarvis != null) {
            captionJarvis.setText(jarvis);
            captionJarvis.setVisibility(jarvis.isEmpty() ? View.GONE : View.VISIBLE);
            if (jarvis.isEmpty()) {
                captionCard.setVisibility(View.GONE);
                return;
            }
        }
        captionCard.setVisibility(View.VISIBLE);
    }

    private void showHint(String message) {
        if (message == null || message.trim().isEmpty()) return;
        showCaption(captionUser.getVisibility() == View.VISIBLE ? captionUser.getText().toString() : null, message);
    }

    private void updateMicVisual(boolean active) {
        if (micButton == null) return;
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                active ? new int[]{0xAA3FE0FF, 0x888B6BFF, 0x66FF4FD8}
                        : new int[]{0x553FE0FF, 0x338B6BFF});
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(Ui.dp(this, 1.5f), active ? 0xFF3FE0FF : 0x883FE0FF);
        micButton.setBackground(g);
        micIcon.setImageResource(active ? R.drawable.ic_stop : R.drawable.ic_mic);
        micIcon.setColorFilter(active ? 0xFFFFFFFF : Theme.text());
        if (active) {
            if (micPulse == null) {
                micPulse = ObjectAnimator.ofPropertyValuesHolder(micButton,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f));
                micPulse.setDuration(700);
                micPulse.setRepeatMode(ObjectAnimator.REVERSE);
                micPulse.setRepeatCount(ObjectAnimator.INFINITE);
            }
            if (!micPulse.isStarted()) micPulse.start();
        } else {
            if (micPulse != null) {
                micPulse.cancel();
                micButton.setScaleX(1f);
                micButton.setScaleY(1f);
            }
        }
    }

    // ---- Menu -----------------------------------------------------------

    private void openMenu() {
        String orb = prefs.overlayEnabled() ? "Hide floating orb" : "Show floating orb";
        String[] items = {"Chat", "History", "AI Memory", "Automation", "Settings", "Permissions",
                orb, "New conversation"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("JARVIS")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: go(ChatActivity.class); break;
                        case 1: go(HistoryActivity.class); break;
                        case 2: go(MemoryActivity.class); break;
                        case 3: go(AutomationActivity.class); break;
                        case 4: go(SettingsActivity.class); break;
                        case 5: go(PermissionsActivity.class); break;
                        case 6: toggleOrb(); break;
                        case 7:
                            conv = JarvisApp.get().db().ensureTodayConversation();
                            brain = new AgentBrain(this, conv);
                            showCaption(null, "Nayi baat shuru. Bataiye?");
                            break;
                    }
                })
                .show();
    }

    private void toggleOrb() {
        if (!prefs.overlayEnabled()) {
            if (!canOverlay()) {
                showHint("Floating orb ke liye 'Display over other apps' permission chahiye.");
                openOverlaySettings();
                return;
            }
            prefs.setOverlayEnabled(true);
            startForegroundService(new Intent(this, OverlayService.class));
            showHint("Floating orb on hai. Kisi bhi app ke upar tap karke JARVIS bula sakte hain.");
        } else {
            prefs.setOverlayEnabled(false);
            stopService(new Intent(this, OverlayService.class));
            showHint("Floating orb off hai.");
        }
    }

    // ---- Lifecycle ------------------------------------------------------

    private void maybeAskNotifications() {
        if (prefs.notifAsked()) return;
        if (hasNotifications()) { prefs.setNotifAsked(true); return; }
        prefs.setNotifAsked(true);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Notifications")
                .setMessage("Reminders aur alarms ke liye JARVIS ko notification permission chahiye. Allow karein?")
                .setPositiveButton("Allow", (d, w) -> askNotifications())
                .setNegativeButton("Not now", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Confirm.attach(this);
        if (statusText != null && !busy && !speakingTts) {
            statusText.setText(Net.online(this) ? getString(R.string.status_idle) : "Offline — internet check karein");
        }
        if (JarvisAccessibilityService.get() == null) {
            // keep silent; the Permissions screen explains this
        }
    }

    @Override
    protected void onPause() {
        Confirm.detach(this);
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopLive();
        if (voice != null) voice.stopListening();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (voice != null) voice.shutdown();
        if (brain != null) brain.shutdown();
        if (live != null) live.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (inputRow != null && inputRow.getVisibility() == View.VISIBLE) {
            hideKeyboardRow();
            return;
        }
        moveTaskToBack(true);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 101) {
            if (results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                main.postDelayed(this::onMicTap, 200);
            } else {
                showHint("Microphone permission ke bina voice kaam nahi karegi. Permissions screen se de sakte hain.");
            }
        }
    }

    private void toast(String s) { Util.toast(this, s); }
}
