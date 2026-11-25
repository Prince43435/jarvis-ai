package com.jarvisai.app.automation;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.jarvisai.app.memory.MemoryDB;

import java.util.List;

/** Schedules reminders through AlarmManager with exact-alarm fallback. */
public final class AlarmScheduler {

    public static final String ACTION_FIRE = "com.jarvisai.app.action.REMINDER_FIRE";
    public static final String ACTION_DONE = "com.jarvisai.app.action.REMINDER_DONE";
    public static final String ACTION_SNOOZE = "com.jarvisai.app.action.REMINDER_SNOOZE";

    private AlarmScheduler() {}

    public static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }

    public static boolean schedule(Context c, MemoryDB.Reminder r) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return false;
            PendingIntent pi = pending(c, r.id, r.requestCode, r.text, PendingIntent.FLAG_UPDATE_CURRENT);
            long when = Math.max(System.currentTimeMillis() + 1000, r.triggerAt);
            if (canExact(c)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void cancel(Context c, MemoryDB.Reminder r) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = pending(c, r.id, r.requestCode, r.text, PendingIntent.FLAG_NO_CREATE);
            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        } catch (Exception ignored) {}
    }

    private static PendingIntent pending(Context c, int id, int requestCode, String text, int extraFlags) {
        Intent i = new Intent(c, ReminderReceiver.class)
                .setAction(ACTION_FIRE)
                .putExtra("id", id)
                .putExtra("text", text);
        return PendingIntent.getBroadcast(c, requestCode, i,
                PendingIntent.FLAG_IMMUTABLE | extraFlags);
    }

    public static int rescheduleAll(Context c) {
        int n = 0;
        try {
            List<MemoryDB.Reminder> list = com.jarvisai.app.JarvisApp.get().db().reminders(false);
            long now = System.currentTimeMillis();
            for (MemoryDB.Reminder r : list) {
                if (r.triggerAt > now && schedule(c, r)) n++;
            }
        } catch (Exception ignored) {}
        return n;
    }
}
