package com.jarvisai.app;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jarvisai.app.ai.AgentBrain;
import com.jarvisai.app.memory.MemoryDB;
import com.jarvisai.app.voice.VoiceEngine;

import java.util.List;

/** Full conversation screen with text, voice and image input. */
public class ChatActivity extends BaseActivity {

    public static final String EXTRA_CONV = "conv";

    private ScrollView scroll;
    private LinearLayout list;
    private EditText input;
    private TextView status, attachLabel;
    private ImageView attachThumb;
    private LinearLayout attachRow;
    private LinearLayout typingRow;

    private AgentBrain brain;
    private VoiceEngine voice;
    private int conv;
    private boolean busy, voiceSend;

    private byte[] pendingJpeg;
    private TextView streamingBubble;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        conv = getIntent().getIntExtra(EXTRA_CONV, prefs.conversationId());
        if (conv <= 0) conv = JarvisApp.get().db().ensureTodayConversation();
        prefs.setConversationId(conv);
        buildRoot();
        buildUi();
        loadMessages();
        brain = new AgentBrain(this, conv);
        initVoice();
    }

    private void buildUi() {
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        screen.addView(col, cp);

        String title = "Chat";
        try {
            for (MemoryDB.Conversation c : JarvisApp.get().db().conversations()) {
                if (c.id == conv) { title = c.title; break; }
            }
        } catch (Exception ignored) {}
        LinearLayout top = Ui.topBar(this, title, "JARVIS · " + prefs.textModel(), true, null);
        top.addView(Ui.iconButton(this, R.drawable.ic_history, 42, Theme.muted(), v -> go(HistoryActivity.class)));
        col.addView(top);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = Ui.column(this);
        list.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        scroll.addView(list);
        col.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // attachment preview
        attachRow = Ui.row(this);
        attachRow.setVisibility(View.GONE);
        attachRow.setBackground(Util.roundedStroke(Theme.isDark() ? 0x14FFFFFF : 0x0A000000,
                Theme.stroke(), 16, 1, this));
        attachRow.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        attachThumb = new ImageView(this);
        attachThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                Ui.dp(this, 42), Ui.dp(this, 42));
        tp.rightMargin = Ui.dp(this, 10);
        attachRow.addView(attachThumb, tp);
        attachLabel = Ui.text(this, "Image attached", 12f, Theme.muted());
        attachRow.addView(attachLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView remove = Ui.text(this, "✕", 17f, Theme.muted());
        remove.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        remove.setOnClickListener(v -> clearAttachment());
        attachRow.addView(remove);
        LinearLayout.LayoutParams arp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        arp.bottomMargin = Ui.dp(this, 6);
        col.addView(attachRow, arp);

        // status
        status = Ui.text(this, "", 11f, Theme.accent());
        status.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        status.setVisibility(View.GONE);
        col.addView(status);

        // input bar
        LinearLayout bar = Ui.row(this);
        bar.setBackground(Util.roundedStroke(Theme.isDark() ? 0xE6101728 : 0xEEFFFFFF,
                Theme.stroke(), 24, 1, this));
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 5));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.bottomMargin = Ui.dp(this, 8);
        col.addView(bar, bp);

        bar.addView(Ui.iconButton(this, R.drawable.ic_image, 38, Theme.muted(), v -> pickImage()));
        input = new EditText(this);
        input.setHint(R.string.type_message);
        input.setTextColor(Theme.text());
        input.setHintTextColor(Theme.faint());
        input.setTextSize(14f);
        input.setBackground(null);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(5);
        input.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        bar.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(Ui.iconButton(this, R.drawable.ic_mic, 38, Theme.muted(), v -> onMic()));
        bar.addView(Ui.iconButton(this, R.drawable.ic_send, 40, Theme.accent(), v -> sendNow()));

        list.setOnClickListener(v -> Util.hideKeyboard(input));
    }

    private void loadMessages() {
        list.removeAllViews();
        List<MemoryDB.Message> msgs = JarvisApp.get().db().messages(conv, 120);
        if (msgs.isEmpty()) {
            addSystemNote("Nayi baat shuru karein. Type karein, bolein, ya camera se image bhejein.");
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

    private void addSystemNote(String text) {
        TextView t = Ui.text(this, text, 12f, Theme.muted());
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 24), Ui.dp(this, 20), Ui.dp(this, 24));
        list.addView(t);
    }

    private void addBubble(String role, String text, long ts) {
        boolean user = "user".equals(role);
        LinearLayout holder = Ui.row(this);
        holder.setGravity(user ? Gravity.END : Gravity.START);
        holder.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 3));

        LinearLayout bubble = Ui.column(this);
        bubble.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        int bg = user
                ? (Theme.isDark() ? 0xFF17304E : 0xFFDCEBFF)
                : (Theme.isDark() ? 0xFF111A2E : 0xFFFFFFFF);
        android.graphics.drawable.GradientDrawable g = Util.roundedStroke(bg,
                user ? Theme.alpha(Theme.accent(), 0.35f) : Theme.stroke(), 18, 1, this);
        bubble.setBackground(g);

        TextView body = Ui.text(this, text == null ? "" : text, 14f,
                user ? (Theme.isDark() ? 0xFFEAF6FF : 0xFF0B2C4A) : Theme.text());
        body.setTextIsSelectable(true);
        bubble.addView(body);

        if (ts > 0) {
            TextView time = Ui.text(this, Util.time(ts), 9.5f, Theme.faint());
            time.setGravity(user ? Gravity.END : Gravity.START);
            time.setPadding(0, Ui.dp(this, 4), 0, 0);
            bubble.addView(time);
        }

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.leftMargin = user ? Ui.dp(this, 46) : 0;
        bp.rightMargin = user ? 0 : Ui.dp(this, 46);
        holder.addView(bubble, bp);
        list.addView(holder);
        Ui.scrollToBottom(scroll);
    }

    private TextView addStreamingBubble() {
        LinearLayout holder = Ui.row(this);
        holder.setGravity(Gravity.START);
        LinearLayout bubble = Ui.column(this);
        bubble.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        bubble.setBackground(Util.roundedStroke(Theme.isDark() ? 0xFF111A2E : 0xFFFFFFFF,
                Theme.stroke(), 18, 1, this));
        TextView body = Ui.text(this, "…", 14f, Theme.text());
        body.setTextIsSelectable(true);
        bubble.addView(body);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.rightMargin = Ui.dp(this, 46);
        holder.addView(bubble, bp);
        list.addView(holder);
        Ui.scrollToBottom(scroll);
        return body;
    }

    private void setBusy(boolean b) {
        busy = b;
        status.setVisibility(b ? View.VISIBLE : View.GONE);
        if (!b) status.setText("");
    }

    private void sendNow() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() && pendingJpeg == null) return;
        input.setText("");
        Util.hideKeyboard(input);
        send(text, pendingJpeg, false);
    }

    private void send(String text, byte[] jpeg, boolean speakReply) {
        if (busy) {
            Util.toast(this, "Ek jawab ka intezaar karein.");
            return;
        }
        if (!prefs.hasApiKey()) {
            Util.toast(this, "Pehle Settings mein Gemini API key add karein.");
            go(SettingsActivity.class);
            return;
        }
        if (!Net.online(this)) {
            addSystemNote("Internet nahi hai. Connection check karke dobara bhejein.");
            return;
        }
        clearAttachment();
        if (text.isEmpty()) text = "Is image ko dekho aur vistaar se batao isme kya hai.";
        addBubble("user", text, System.currentTimeMillis());
        JarvisApp.get().db().addMessage(conv, "user", text, jpeg == null ? "" : "image");
        streamingBubble = addStreamingBubble();
        setBusy(true);
        status.setText(R.string.status_thinking);

        final boolean speak = speakReply;
        final StringBuilder streamed = new StringBuilder();
        brain.send(text, jpeg, speak, new AgentBrain.Listener() {
            @Override public void onStatus(String s) {
                if (s != null && !s.isEmpty()) status.setText(s);
            }
            @Override public void onDelta(String t) {
                if (t == null) return;
                streamed.append(t);
                if (streamingBubble != null) streamingBubble.setText(streamed.toString());
                Ui.scrollToBottom(scroll);
            }
            @Override public void onTool(String name, String message, boolean ok) {
                if (streamingBubble != null && !streamed.toString().isEmpty()) {
                    streamed.append("\n");
                }
                if (streamingBubble != null) {
                    streamingBubble.setText(streamed + (ok ? "✓ " : "✕ ") + message);
                }
                Ui.scrollToBottom(scroll);
            }
            @Override public void onFinal(String text) {
                setBusy(false);
                if (streamingBubble != null) {
                    list.removeView((View) streamingBubble.getParent().getParent());
                    streamingBubble = null;
                }
                addBubble("assistant", text, System.currentTimeMillis());
                JarvisApp.get().db().addMessage(conv, "assistant", text, "");
                if (speak) voice.speak(text);
            }
            @Override public void onError(String message) {
                setBusy(false);
                if (streamingBubble != null) {
                    list.removeView((View) streamingBubble.getParent().getParent());
                    streamingBubble = null;
                }
                addSystemNote(message == null ? "Error aaya." : message);
            }
        });
    }

    // ---- Image ---------------------------------------------------------

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, 310);
        } catch (Exception e) {
            Util.toast(this, "Gallery nahi khuli.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 310 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            pendingJpeg = Images.jpegFromUri(this, uri, 1280, 82);
            if (pendingJpeg == null) {
                Util.toast(this, "Image read nahi hui.");
                return;
            }
            Bitmap bmp = android.graphics.BitmapFactory.decodeByteArray(pendingJpeg, 0, pendingJpeg.length);
            attachThumb.setImageBitmap(bmp);
            attachLabel.setText(displayName(uri) + " · " + (pendingJpeg.length / 1024) + " KB");
            attachRow.setVisibility(View.VISIBLE);
            input.requestFocus();
        }
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {}
        return "Image";
    }

    private void clearAttachment() {
        pendingJpeg = null;
        if (attachRow != null) attachRow.setVisibility(View.GONE);
    }

    // ---- Voice ---------------------------------------------------------

    private void initVoice() {
        voice = new VoiceEngine(this, new VoiceEngine.Listener() {
            @Override public void onReady(boolean ok, String message) {
                if (!ok && message != null) Util.toast(ChatActivity.this, message);
            }
            @Override public void onListening() {
                status.setVisibility(View.VISIBLE);
                status.setText(R.string.status_listening);
            }
            @Override public void onPartial(String text) {
                if (text != null && !text.isEmpty() && !busy) {
                    input.setText(text);
                    input.setSelection(input.getText().length());
                }
            }
            @Override public void onHeard(String text) {
                status.setVisibility(View.GONE);
                if (busy) return;
                input.setText(text);
                voiceSend = true;
                send(text, pendingJpeg, true);
            }
            @Override public void onSpeechStart() {}
            @Override public void onSpeechDone() {}
            @Override public void onError(String message) {
                status.setVisibility(View.GONE);
                if (message != null) Util.toast(ChatActivity.this, message);
            }
        });
        voice.init();
    }

    private void onMic() {
        if (busy) {
            if (brain != null) brain.cancel();
            setBusy(false);
            if (streamingBubble != null) {
                list.removeView((View) streamingBubble.getParent().getParent());
                streamingBubble = null;
            }
            Util.toast(this, "Ruk gayi.");
            return;
        }
        if (voice.isSpeaking()) {
            voice.stopSpeaking();
            return;
        }
        if (!hasMic()) {
            askMic();
            return;
        }
        Util.hideKeyboard(input);
        voice.listen();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == 101 && results.length > 0
                && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            voice.listen();
        } else if (code == 101) {
            Util.toast(this, "Microphone permission ke bina voice nahi chalegi.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.jarvisai.app.automation.Confirm.attach(this);
    }

    @Override
    protected void onPause() {
        com.jarvisai.app.automation.Confirm.detach(this);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (voice != null) {
            voice.stopSpeaking();
            voice.stopListening();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (voice != null) voice.shutdown();
        if (brain != null) brain.shutdown();
        super.onDestroy();
    }
}
