package com.jarvisai.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;



import com.jarvisai.app.ai.AgentBrain;

import com.jarvisai.app.memory.MemoryDB;
import com.jarvisai.app.ai.GeminiClient;
import com.jarvisai.app.voice.VoiceEngine;
import com.jarvisai.app.automation.Confirm;
import com.jarvisai.app.automation.ScreenCapture;

import org.json.JSONObject;

import java.util.ArrayList;

/** Camera vision activity: launch camera or pick from gallery, analyze via Gemini. */
public class VisionActivity extends BaseActivity {

    private static final int REQUEST_CAMERA = 201;
    private static final int REQUEST_GALLERY = 202;

    private LinearLayout list;
    private ScrollView scroll;
    private TextView status;
    private ImageView preview;
    private LinearLayout previewRow;
    private byte[] pendingJpeg;
    private int conv;
    private AgentBrain brain;
    private VoiceEngine voice;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        conv = getIntent().getIntExtra(ChatActivity.EXTRA_CONV, prefs.conversationId());
        if (conv <= 0) conv = JarvisApp.get().db().ensureTodayConversation();
        prefs.setConversationId(conv);
        buildRoot();
        buildUi();
        voice = new VoiceEngine(this, null);
        Confirm.attach(this);
    }

    private void buildUi() {
        LinearLayout col = Ui.column(this);
        col.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        screen.addView(col, cp);

        String title = "Camera Vision";
        col.addView(Ui.topBar(this, title, "JARVIS", true, null));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = Ui.column(this);
        list.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        scroll.addView(list);
        col.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Capture controls
        LinearLayout controls = Ui.row(this);
        controls.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        controls.setBackground(Util.rounded(Theme.isDark() ? 0x0DFFFFFF : 0x08000000, 16, this));

        TextView camBtn = Ui.primaryButton(this, "Take photo", v -> {
            if (granted(Manifest.permission.CAMERA)) {
                Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (i.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(i, REQUEST_CAMERA);
                } else {
                    toast("Camera app nahi mila.");
                }
            } else {
                askCamera();
            }
        });
        TextView galBtn = Ui.primaryButton(this, "From gallery", v -> {
            if (granted(Manifest.permission.RECORD_AUDIO)) { // just check we have some permission
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(i, REQUEST_GALLERY);
                } catch (Exception e) {
                    toast("Gallery nahi khuli.");
                }
            } else {
                askMic(); // reuse ask
            }
        });
        controls.addView(camBtn);
        controls.addView(galBtn, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(controls);

        // Preview row (initially hidden)
        previewRow = Ui.row(this);
        previewRow.setVisibility(View.GONE);
        previewRow.setBackground(Util.roundedStroke(Theme.isDark() ? 0x14FFFFFF : 0x0A000000,
                Theme.stroke(), 16, 1, this));
        previewRow.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        LinearLayout.LayoutParams prp = new LinearLayout.LayoutParams(0,
                Ui.dp(this, 220), 1f);
        prp.topMargin = Ui.dp(this, 8);
        col.addView(previewRow, prp);

        // Status
        status = Ui.text(this, "", 11f, Theme.accent());
        status.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 4));
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(this, 4);
        col.addView(status, slp);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_CAMERA) {
            if (data != null && data.getExtras() != null) {
                Bitmap bmp = (Bitmap) data.getExtras().get("data");
                if (bmp != null) {
                    pendingJpeg = Images.jpegFromUri(this, android.net.Uri.parse(""), 1280, 82);
                    bmp.recycle();
                }
            }
        } else if (requestCode == REQUEST_GALLERY) {
            if (data != null && data.getData() != null) {
                pendingJpeg = Images.jpegFromUri(this, data.getData(), 1280, 82);
            }
        }

        if (pendingJpeg != null) {
            Bitmap b = android.graphics.BitmapFactory.decodeByteArray(pendingJpeg, 0, pendingJpeg.length);
            if (preview != null) preview.setImageBitmap(b);

            previewRow.setVisibility(View.VISIBLE);
            status.setText("Image captured. Analyzing…");
            new Thread(() -> analyzeImage()).start();
        } else {
            toast("Image read nahi hui.");
        }
    }

    private void analyzeImage() {
        runOnUiThread(() -> status.setText("Gemini se analyze ho raha hai…"));
        try {
            String text = GeminiClient.validate(prefs.apiKey(), prefs.textModel());
            if (text != null) {
                runOnUiThread(() -> {
                    status.setText("API key fail: " + text);
                    toast(text);
                });
                return;
            }
            JSONObject req = GeminiClient.inlineImage(pendingJpeg);
            // Use generate with inline image
            String url = Net.BASE + "/models/" + GeminiClient.cleanModel(prefs.textModel())
                    + ":generateContent";
            String raw = Net.post(url, prefs.apiKey(),
                    "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"inlineData\":{\"mimeType\":\"image/jpeg\",\"data\":\"" +
                    android.util.Base64.encodeToString(pendingJpeg, android.util.Base64.NO_WRAP) +
                    "\"}}]},{\"role\":\"model\",\"parts\":[{\"text\":\"Describe is picture behta hun, kya dikh raha hai aur main kya kar sakti hoon.\"}]},{\"role\":\"user\",\"parts\":[{\"text\":\"Reply in Hinglish, 2-3 sentences.\"}]}]}", 120);
            JSONObject resp = new JSONObject(raw);
            String answer = GeminiClient.extractText(resp);
            if (answer == null || answer.trim().isEmpty()) {
                answer = GeminiClient.blockReason(resp);
                if (answer == null) answer = "Main image samajh nahi payi. Dobara try karein.";
            }
            final String finalAnswer = answer;
            runOnUiThread(() -> {
                status.setText("");
                addVisionResult(finalAnswer);
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                status.setText("Error: " + e.getClass().getSimpleName());
                toast("Analysis fail: " + e.getMessage());
            });
        }
    }

    private void addSystemNote(String text) {
        TextView t = Ui.text(this, text, 12f, Theme.muted());
        t.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        list.addView(t);
    }

    private void addVisionResult(String text) {
        list.removeAllViews();
        addSystemNote("Vision result:");

        TextView result = Ui.text(this, text, 13f, Theme.text());
        result.setTextIsSelectable(true);
        result.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        android.graphics.drawable.GradientDrawable g = Util.roundedStroke(Theme.isDark() ? 0xFF17304E : 0xFFDCEBFF,
                Theme.alpha(Theme.accent(), 0.35f), 20, 1, this);
        result.setBackground(g);
        list.addView(result);

        // Buttons
        LinearLayout actions = Ui.row(this);
        actions.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        actions.setBackground(Util.rounded(Theme.isDark() ? 0x0DFFFFFF : 0x08000000, 12, this));

        TextView askFollow = Ui.primaryButton(this, "Ask follow-up", v -> {
            String q = Ui.text(this, "", 13f, Theme.muted()).getText().toString(); // placeholder
            // Just send to main chat
            sendToMain(text);
        });
        TextView saveMem = Ui.ghostButton(this, "Save to memory", v -> {
            int id = JarvisApp.get().memory().remember("vision", text, "observation", 2);
            Util.toast(this, "Memory save ho gaya (id " + id + ")");
        });
        TextView done = Ui.ghostButton(this, "Close", v -> finish());

        actions.addView(askFollow);
        actions.addView(saveMem, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(done);
        screen.addView(actions);
    }

    private void sendToMain(String text) {
        // Switch back to main and send the analysis as a message
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
