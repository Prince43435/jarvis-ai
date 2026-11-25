package com.jarvisai.app.automation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;

/** The floating gradient orb. Cheap to draw: three shaders and a sine wave. */
public class OrbView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wave = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long start = System.nanoTime();
    private float density = 2f;
    private boolean active;

    public OrbView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1.6f * density);
        wave.setStyle(Paint.Style.STROKE);
        wave.setStrokeWidth(2.2f * density);
        wave.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setActive(boolean a) {
        active = a;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float t = (System.nanoTime() - start) / 1_000_000_000f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.1f);
        float base = Math.min(w, h) * 0.5f;

        // Halo
        paint.setShader(new RadialGradient(cx, cy, base,
                new int[]{Color.argb(120, 63, 224, 255), Color.argb(40, 139, 107, 255), Color.TRANSPARENT},
                new float[]{0.42f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, base, paint);
        paint.setShader(null);

        // Rotating ring
        ring.setShader(new SweepGradient(cx, cy,
                new int[]{0x003FE0FF, 0xFF3FE0FF, 0x008B6BFF, 0xFFFF4FD8, 0x003FE0FF},
                new float[]{0f, 0.22f, 0.5f, 0.78f, 1f}));
        canvas.save();
        canvas.rotate(t * 55f, cx, cy);
        canvas.drawCircle(cx, cy, base * 0.74f, ring);
        canvas.restore();
        ring.setShader(null);

        // Core orb
        float r = base * 0.52f + base * 0.03f * pulse;
        paint.setShader(new LinearGradient(cx - r, cy - r, cx + r, cy + r,
                new int[]{0xFF5BEAFF, 0xFF7C6BFF, 0xFFFF5FD9}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);

        // Inner depth
        paint.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r,
                new int[]{0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);

        // Equalizer bars
        int bars = 5;
        float span = r * 1.1f;
        wave.setColor(0xFFFFFFFF);
        for (int i = 0; i < bars; i++) {
            float x = cx - span / 2f + (span / (bars - 1)) * i;
            float amp = (float) (0.22f + 0.78f * Math.abs(Math.sin(t * (2.4f + i * 0.35f) + i)));
            float half = r * 0.30f * amp + density;
            canvas.drawLine(x, cy - half, x, cy + half, wave);
        }

        postInvalidateOnAnimation();
    }
}
