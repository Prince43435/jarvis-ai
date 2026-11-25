package com.jarvisai.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;



import com.jarvisai.app.automation.Confirm;
import com.jarvisai.app.R;

import java.util.ArrayList;
import java.util.List;

/** Permissions overview and grant points. */
public class PermissionsActivity extends BaseActivity {

    private LinearLayout list;
    private ScrollView scroll;

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

        String title = "Permissions";
        col.addView(Ui.topBar(this, title, "JARVIS", true, null));

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = Ui.column(this);
        list.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        scroll.addView(list);
        col.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Permission rows
        String[][] perms = {
                {"Microphone", Manifest.permission.RECORD_AUDIO, "Voice input and conversation"},
                {"Camera", Manifest.permission.CAMERA, "Vision / object detection"},
                {"Display over other apps", Manifest.permission.SYSTEM_ALERT_WINDOW, "Floating orb over other apps"},
                {"Notifications", "android.permission.POST_NOTIFICATIONS", "Reminders and alarms"},
                {"Accessibility automation", "android.permission.BIND_ACCESSIBILITY_SERVICE", "Screen actions (tap/type/scroll)"},
                {"Notification listener", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "Read notifications"},
                {"Exact alarms", "android.permission.SCHEDULE_EXACT_ALARM", "Precise reminders"},
                {"Ignore battery optimisation", "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "Background reliability"},
        };

        for (String[] p : perms) {
            LinearLayout row = Ui.row(this);
            row.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            row.setBackground(Util.rounded(Theme.isDark() ? 0x0DFFFFFF : 0x08000000, 12, this));

            TextView name = Ui.text(this, p[0], 13f, Theme.text());
            TextView desc = Ui.text(this, p[2], 11f, Theme.muted());

            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dl.gravity = Gravity.END;

            row.addView(name, nl);
            row.addView(desc, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.icon(this, R.drawable.ic_add, 20, Theme.faint()), dl);
            col.addView(row);
        }

        // Action buttons row
        LinearLayout actions = Ui.row(this);
        actions.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        actions.setBackground(Util.rounded(Theme.isDark() ? 0x14FFFFFF : 0xCCFFFFFF, 20, this));

        int requestCode = 1000;
        String[] requestPerms = new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.SYSTEM_ALERT_WINDOW,
                "android.permission.POST_NOTIFICATIONS"
        };

        TextView btn1 = Ui.primaryButton(this, "Grant microphone & camera", v -> {
            requestPermissions(requestPerms, requestCode);
        });
        TextView btn2 = Ui.primaryButton(this, "Grant overlay permission", v -> {
            if (this.checkSelfPermission(Manifest.permission.SYSTEM_ALERT_WINDOW)
                    != PackageManager.PERMISSION_GRANTED) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } else {
                Util.toast(this, "Overlay permission already granted.");
            }
        });
        TextView btn3 = Ui.primaryButton(this, "Grant accessibility & notifications", v -> {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Open settings?")
                    .setMessage("JARVIS needs these permissions enabled for full functionality.")
                    .setPositiveButton("Open notifications", (d, w) -> {
                        openNotificationAccessSettings();
                    })
                    .setPositiveButton("Open accessibility", (d, w) -> {
                        openAccessibilitySettings();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        TextView btn4 = Ui.ghostButton(this, "Check status", v -> checkAndShow());

        actions.addView(btn1);
        actions.addView(btn2, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(btn3, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(btn4);
        col.addView(actions);
    }

    private void maybeAskNotifications() {
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 103);
    }

    private void checkAndShow() {
        List<String> missing = new ArrayList<>();
        if (granted(Manifest.permission.RECORD_AUDIO)) {
            // ok
        } else missing.add("Microphone");
        if (granted(Manifest.permission.CAMERA)) {
            // ok
        } else missing.add("Camera");
        if (!canOverlay()) missing.add("Display over other apps");
        if (!hasNotifications()) missing.add("Notifications");
        if (!accessibilityOn()) missing.add("Accessibility");
        if (!notificationAccessOn()) missing.add("Notification listener");

        StringBuilder sb = new StringBuilder();
        for (String m : missing) sb.append(m).append("\n");
        if (sb.length() > 0) {
            Util.toast(this, "Missing: " + sb.toString());
            openMissingSettings(missing);
        } else {
            Util.toast(this, "All critical permissions granted.");
        }
    }

    private void openMissingSettings(List<String> missing) {
        if (missing.contains("Microphone")) askMic();
        if (missing.contains("Camera")) askCamera();
        if (missing.contains("Display over other apps")) openOverlaySettings();
        if (missing.contains("Notifications")) maybeAskNotifications();
        if (missing.contains("Accessibility")) openAccessibilitySettings();
        if (missing.contains("Notification listener")) openNotificationAccessSettings();
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