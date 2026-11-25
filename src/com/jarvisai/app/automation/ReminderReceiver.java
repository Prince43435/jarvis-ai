package com.jarvisai.app.automation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.jarvisai.app.MainActivity;
import com.jarvisai.app.R;
import com.jarvisai.app.Util;
import com.jarvisai.app.memory.MemoryDB;

/** Delivers reminder notifications and handles done / snooze actions. */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String CHANNEL = "jarvis_reminders";

    @Override
    public void onReceive(Context c, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        int id = intent.getIntExtra("id", -1);
        String text = intent.getStringExtra("text");

        MemoryDB db = com.jarvisai.app.JarvisApp.get().db();
        MemoryDB.Reminder r = id > 0 ? db.reminder(id) : null;
        if (r != null && text == null) text = r.text;

        if (AlarmScheduler.ACTION_DONE.equals(action)) {
            if (r != null) db.markReminderDone(r.id);
            cancel(c, id);
            return;
        }
        if (AlarmScheduler.ACTION_SNOOZE.equals(action)) {
            if (r != null) {
                long when = System.currentTimeMillis() + 10 * 60 * 1000L;
                db.updateReminderTrigger(r.id, when);
                r.triggerAt = when;
                AlarmScheduler.schedule(c, r);
                cancel(c, id);
                Util.toast(c, "Snoozed 10 minutes");
            }
            return;
        }

        // FIRE
        if (text == null) text = "Reminder";
        show(c, id, text);
        if (r != null) {
            db.markReminderDone(r.id);
            if (r.repeat != null && !r.repeat.isEmpty()) {
                long next = nextOccurrence(r.triggerAt, r.repeat);
                if (next > 0) {
                    db.updateReminderTrigger(r.id, next);
                    r.triggerAt = next;
                    AlarmScheduler.schedule(c, r);
                }
            }
        }
    }

    private static long nextOccurrence(long from, String rule) {
        long day = 86400000L;
        switch (rule) {
            case "daily": return from + day;
            case "weekdays": {
                long n = from + day;
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTimeInMillis(n);
                int d = cal.get(java.util.Calendar.DAY_OF_WEEK);
                while (d == java.util.Calendar.SATURDAY || d == java.util.Calendar.SUNDAY) {
                    n += day;
                    cal.setTimeInMillis(n);
                    d = cal.get(java.util.Calendar.DAY_OF_WEEK);
                }
                return n;
            }
            case "weekly": return from + 7 * day;
            default: return 0;
        }
    }

    public static void ensureChannel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.reminder_channel),
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("JARVIS reminders and alarms");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 250, 150, 250});
            ch.setShowBadge(true);
            nm.createNotificationChannel(ch);
        }
    }

    private void show(Context c, int id, String text) {
        ensureChannel(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent open = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(c, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent done = PendingIntent.getBroadcast(c, id * 10 + 1,
                new Intent(c, ReminderReceiver.class).setAction(AlarmScheduler.ACTION_DONE)
                        .putExtra("id", id).putExtra("text", text),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent snooze = PendingIntent.getBroadcast(c, id * 10 + 2,
                new Intent(c, ReminderReceiver.class).setAction(AlarmScheduler.ACTION_SNOOZE)
                        .putExtra("id", id).putExtra("text", text),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle("JARVIS reminder")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(content)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .addAction(new Notification.Action.Builder(null, c.getString(R.string.reminder_action_done), done).build())
                .addAction(new Notification.Action.Builder(null, c.getString(R.string.reminder_action_snooze), snooze).build());
        try {
            nm.notify(1000 + id, b.build());
        } catch (SecurityException ignored) {}
    }

    private void cancel(Context c, int id) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(1000 + id);
    }
}
