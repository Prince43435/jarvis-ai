package com.jarvisai.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import com.jarvisai.app.ui.AuroraView;

/** Shared plumbing: theme, edge-to-edge layout, navigation and permission checks. */
public abstract class BaseActivity extends Activity {

    protected Prefs prefs;
    protected FrameLayout screen;
    protected AuroraView aurora;
    protected int insetTop, insetBottom;

    @Override
    protected void onCreate(Bundle b) {
        Theme.apply(this);
        super.onCreate(b);
        prefs = Prefs.get(this);
        Theme.decorate(this);
    }

    protected FrameLayout buildRoot() {
        FrameLayout root = new FrameLayout(this);
        aurora = new AuroraView(this);
        root.addView(aurora, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screen = new FrameLayout(this);
        root.addView(screen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insetTop = bars.top;
                insetBottom = bars.bottom;
            } else {
                insetTop = insets.getSystemWindowInsetTop();
                insetBottom = insets.getSystemWindowInsetBottom();
            }
            screen.setPadding(0, insetTop, 0, 0);
            onInsets(insetTop, insetBottom);
            return insets;
        });
        setContentView(root);
        return root;
    }

    protected void onInsets(int top, int bottom) {}

    protected void go(Class<?> cls) {
        startActivity(new Intent(this, cls));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    protected void go(Intent i) {
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ---- Permissions ----------------------------------------------------

    protected boolean granted(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    protected boolean hasMic() { return granted(android.Manifest.permission.RECORD_AUDIO); }
    protected boolean hasCamera() { return granted(android.Manifest.permission.CAMERA); }
    protected boolean hasNotifications() {
        if (Build.VERSION.SDK_INT >= 33) return granted("android.permission.POST_NOTIFICATIONS");
        return NotificationManagerCompatEnabled();
    }

    private boolean NotificationManagerCompatEnabled() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    protected boolean canOverlay() { return Settings.canDrawOverlays(this); }

    protected boolean accessibilityOn() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String me = getPackageName() + "/" + com.jarvisai.app.automation.JarvisAccessibilityService.class.getName();
            for (String s : enabled.split(":")) {
                if (s.equalsIgnoreCase(me)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    protected boolean notificationAccessOn() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return false;
            for (String pkg : (Build.VERSION.SDK_INT >= 23 ? new java.util.ArrayList<String>() : new java.util.ArrayList<String>())) {
                if (getPackageName().equals(pkg)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    protected boolean exactAlarmsOn() {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    protected boolean batteryUnrestricted() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    protected void askMic() {
        requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 101);
    }

    protected void askCamera() {
        requestPermissions(new String[]{android.Manifest.permission.CAMERA}, 102);
    }

    protected void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 103);
        } else {
            openNotificationSettings();
        }
    }

    protected void openNotificationSettings() {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(i);
    }

    protected void openOverlaySettings() {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    protected void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    protected void openNotificationAccessSettings() {
        startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
    }

    protected void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    protected void openBatterySettings() {
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        try { startActivity(i); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
    }

    protected void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
