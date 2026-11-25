package com.jarvisai.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.jarvisai.app.Theme;

/** Slow-moving aurora + starfield used behind every JARVIS screen. */
public class AuroraView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader cyan, violet, magenta;
    private int w, h;
    private long start = System.nanoTime();
    private float[] stars;
    private float density = 2f;

    public AuroraView(Context c) { this(c, null); }

    public AuroraView(Context c, AttributeSet attrs) {
        super(c, attrs);
        density = c.getResources().getDisplayMetrics().density;
        starPaint.setColor(0xFFFFFFFF);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = nw; h = nh;
        int a = Theme.isDark() ? 78 : 40;
        cyan = new RadialGradient(0, 0, Math.max(w, h) * 0.55f,
                new int[]{Color.argb(a, 63, 224, 255), Color.argb(a / 2, 63, 224, 255), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        violet = new RadialGradient(0, 0, Math.max(w, h) * 0.6f,
                new int[]{Color.argb(a, 139, 107, 255), Color.argb(a / 2, 139, 107, 255), Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        magenta = new RadialGradient(0, 0, Math.max(w, h) * 0.45f,
                new int[]{Color.argb(a / 2, 255, 79, 216), Color.argb(a / 4, 255, 79, 216), Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        stars = new float[120];
        java.util.Random r = new java.util.Random(42);
        for (int i = 0; i < stars.length; i += 2) {
            stars[i] = r.nextFloat() * w;
            stars[i + 1] = r.nextFloat() * h;
        }
    }

    public void resetTime() { start = System.nanoTime(); invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        if (w == 0 || h == 0) return;
        float t = (System.nanoTime() - start) / 1_000_000_000f;
        canvas.drawColor(Theme.bg());

        // Aurora blobs.
        paint.setShader(violet);
        canvas.save();
        canvas.translate(w * (0.72f + 0.05f * (float) Math.sin(t * 0.23)),
                h * (0.12f + 0.03f * (float) Math.cos(t * 0.19)));
        canvas.drawCircle(0, 0, Math.max(w, h) * 0.6f, paint);
        canvas.restore();

        paint.setShader(cyan);
        canvas.save();
        canvas.translate(w * (0.18f + 0.06f * (float) Math.cos(t * 0.17)),
                h * (0.32f + 0.04f * (float) Math.sin(t * 0.27)));
        canvas.drawCircle(0, 0, Math.max(w, h) * 0.55f, paint);
        canvas.restore();

        paint.setShader(magenta);
        canvas.save();
        canvas.translate(w * (0.55f + 0.08f * (float) Math.sin(t * 0.13 + 1.4f)),
                h * (0.88f + 0.03f * (float) Math.cos(t * 0.21)));
        canvas.drawCircle(0, 0, Math.max(w, h) * 0.5f, paint);
        canvas.restore();
        paint.setShader(null);

        // Stars.
        for (int i = 0; i < stars.length; i += 2) {
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 1.3 + i));
            int alpha = (int) ((Theme.isDark() ? 110 : 30) * tw);
            starPaint.setColor(Color.argb(alpha, 210, 235, 255));
            float size = (i % 3 == 0 ? 2.1f : 1.2f) * density * 0.6f;
            canvas.drawCircle(stars[i], stars[i + 1], size, starPaint);
        }

        // Fine horizon glow.
        paint.setShader(new LinearGradient(0, h * 0.62f, 0, h,
                Color.TRANSPARENT, Theme.isDark() ? 0x331A2C6B : 0x22000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, h * 0.62f, w, h, paint);
        paint.setShader(null);

        postInvalidateOnAnimation();
    }
}
