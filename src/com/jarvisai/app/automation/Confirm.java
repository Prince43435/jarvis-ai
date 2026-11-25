package com.jarvisai.app.automation;

import android.app.Activity;
import android.app.AlertDialog;

import com.jarvisai.app.R;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Blocking confirmation gate for sensitive device actions. Tools run on background
 * threads, so the dialog is marshalled to whichever JARVIS activity is in front.
 * If no activity is visible the action is denied - never performed silently.
 */
public final class Confirm {

    private static WeakReference<Activity> current;

    private Confirm() {}

    public static void attach(Activity a) { current = new WeakReference<>(a); }

    public static void detach(Activity a) {
        if (current != null && current.get() == a) current = null;
    }

    public static boolean hasUi() {
        Activity a = current == null ? null : current.get();
        return a != null && !a.isFinishing() && !a.isDestroyed();
    }

    public static boolean ask(String title, String message) {
        Activity a = current == null ? null : current.get();
        if (a == null || a.isFinishing() || a.isDestroyed()) return false;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean result = new AtomicBoolean(false);
        a.runOnUiThread(() -> {
            try {
                new AlertDialog.Builder(a)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(R.string.yes, (d, w) -> { result.set(true); latch.countDown(); })
                        .setNegativeButton(R.string.no, (d, w) -> latch.countDown())
                        .setOnCancelListener(d -> latch.countDown())
                        .show();
            } catch (Exception e) {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return result.get();
    }
}
