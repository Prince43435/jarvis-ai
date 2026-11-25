package com.jarvisai.app.automation;

import android.graphics.Bitmap;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Blocking helper to grab a screenshot through the accessibility service. */
public final class ScreenCapture {

    private ScreenCapture() {}

    public static Bitmap capture(long timeoutMs) {
        JarvisAccessibilityService svc = JarvisAccessibilityService.get();
        if (svc == null) return null;
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Bitmap> out = new AtomicReference<>();
        try {
            svc.screenshot(bmp -> {
                out.set(bmp);
                latch.countDown();
            });
            latch.await(Math.max(500, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {}
        return out.get();
    }
}
