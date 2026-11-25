package com.jarvisai.app.automation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.jarvisai.app.MainActivity;
import com.jarvisai.app.Prefs;
import com.jarvisai.app.R;

/** Draggable floating orb shown over other apps (requires the overlay permission). */
public class OverlayService extends Service {

    public static final String CHANNEL = "jarvis_orb";
    public static final int NOTIF_ID = 7001;

    private WindowManager wm;
    private OrbView orb;
    private WindowManager.LayoutParams lp;
    private int startX, startY;
    private float downX, downY;
    private boolean moved;
    private long lastTap;
    private boolean added;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForegroundSafely();
    }

    private void startForegroundSafely() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.orb_channel),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent hide = PendingIntent.getBroadcast(this, 3,
                new Intent(this, OrbActionReceiver.class).setAction(OrbActionReceiver.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_orb)
                .setContentTitle(getString(R.string.orb_title))
                .setContentText(getString(R.string.orb_text))
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(Notification.PRIORITY_LOW)
                .addAction(new Notification.Action.Builder(null, "Hide orb", hide).build());
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, b.build());
            }
        } catch (Exception e) {
            try { startForeground(NOTIF_ID, b.build()); } catch (Exception ignored) {}
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!added) addOrb();
        return START_STICKY;
    }

    private void addOrb() {
        if (wm == null) return;
        Prefs p = Prefs.get(this);
        int size = dp(64);
        lp = new WindowManager.LayoutParams(
                size, size,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = p.overlayX();
        lp.y = p.overlayY();
        if (lp.x == 0 && lp.y == 0) {
            lp.x = dp(16);
            lp.y = dp(220);
        }

        orb = new OrbView(this);
        orb.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                        if (moved) {
                            lp.x = startX + (int) dx;
                            lp.y = startY + (int) dy;
                            try { wm.updateViewLayout(orb, lp); } catch (Exception ignored) {}
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            long now = System.currentTimeMillis();
                            if (now - lastTap < 320) {
                                open(false);
                                lastTap = 0;
                            } else {
                                lastTap = now;
                                orb.postDelayed(() -> {
                                    if (lastTap != 0) {
                                        lastTap = 0;
                                        open(true);
                                    }
                                }, 330);
                            }
                        } else {
                            Prefs.get(OverlayService.this).setOverlayPos(lp.x, lp.y);
                        }
                        return true;
                }
                return false;
            }
        });
        try {
            wm.addView(orb, lp);
            added = true;
        } catch (Exception e) {
            Toast.makeText(this, "Overlay could not be shown", Toast.LENGTH_SHORT).show();
            stopSelf();
        }
    }

    private void open(boolean voice) {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_VOICE, voice);
        startActivity(i);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        if (orb != null && added && wm != null) {
            try { wm.removeView(orb); } catch (Exception ignored) {}
            added = false;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
