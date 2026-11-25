package com.jarvisai.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Hand-drawn 2D anime assistant rendered entirely with Canvas primitives.
 * Everything (hair, eyes, lips, outfit, holographic rings) animates at runtime:
 * blinks, breathing, head sway, visemes, four emotions and a listening glow.
 */
public class CharacterView extends View {

    public static final int IDLE = 0, LISTENING = 1, THINKING = 2, SPEAKING = 3,
            HAPPY = 4, SAD = 5, CONFUSED = 6, SURPRISED = 7;

    private static final float DW = 1000f, DH = 1500f;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path face = new Path(), neck = new Path(), earL = new Path(), earR = new Path();
    private final Path hairBack = new Path(), hairBack2 = new Path();
    private final Path body = new Path(), collar = new Path(), shirt = new Path();
    private final Path bangs = new Path(), lockL = new Path(), lockR = new Path(), ahoge = new Path();
    private final Path circlet = new Path(), seamL = new Path(), seamR = new Path();
    private final Path browPath = new Path(), mouthPath = new Path(), eyeLash = new Path();
    private final Path clipEye = new Path(), gem = new Path(), ringPath = new Path();

    private Shader skinShader, skinShadeShader, hairShader, hairLightShader, outfitShader,
            glowShader, glowShader2, irisShader, blushShader, lipShader;

    private float scale = 1f, ox = 0f, oy = 0f;
    private int hairStyle, eyeStyle;
    private int state = IDLE;

    private float time = 0f;
    private long last = 0L;
    private float bob = 0f, sway = 0f;

    // Animated expression parameters
    private float eyeOpen = 1f, eyeOpenT = 1f;
    private float browRaise = 0f, browRaiseT = 0f;
    private float browAngle = 0f, browAngleT = 0f;
    private float mouthCurve = 0.3f, mouthCurveT = 0.3f;
    private float mouthOpen = 0f, mouthOpenT = 0f;
    private float blush = 0.2f, blushT = 0.2f;
    private float tilt = 0f, tiltT = 0f;
    private float eyeShiftX = 0f, eyeShiftXT = 0f;
    private float eyeShiftY = 0f, eyeShiftYT = 0f;
    private float glow = 0.4f, glowT = 0.4f;
    private float pulse = 0f;

    private float amplitude = 0f;
    private float nextBlink = 2.5f;
    private float blinkT = -1f;

    private final float[] stars = new float[64];
    private final float[] starSeed = new float[32];

    private static final int[] IRIS_LIGHT = {0xFF8FF3FF, 0xFFC8B5FF, 0xFFFFE09A};
    private static final int[] IRIS_MID = {0xFF2FC8EE, 0xFF8B6BFF, 0xFFF0A93A};
    private static final int[] IRIS_DARK = {0xFF0D4C6B, 0xFF2C1C66, 0xFF6B3A08};
    private static final int[] ACCENTS = {0xFF3FE0FF, 0xFF8B6BFF, 0xFFFFC24B};

    public CharacterView(Context c) { this(c, null); }

    public CharacterView(Context c, AttributeSet attrs) {
        super(c, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        hairStyle = Prefs.get(c).hairStyle();
        eyeStyle = Prefs.get(c).eyeStyle();
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < stars.length; i += 2) {
            stars[i] = r.nextFloat();
            stars[i + 1] = r.nextFloat();
        }
        for (int i = 0; i < starSeed.length; i++) starSeed[i] = r.nextFloat() * 6.28f;
        start();
    }

    private void start() {
        last = System.nanoTime();
        postInvalidateOnAnimation();
    }

    public void setState(int s) {
        if (state == s) return;
        state = s;
        switch (s) {
            case LISTENING:
                eyeOpenT = 1.06f; browRaiseT = 0.35f; mouthCurveT = 0.25f; mouthOpenT = 0.05f;
                tiltT = 1.5f; glowT = 0.95f; eyeShiftXT = 0f; eyeShiftYT = 0f; blushT = 0.3f;
                browAngleT = 0f;
                break;
            case THINKING:
                eyeOpenT = 0.82f; browRaiseT = 0.15f; browAngleT = -6f; mouthCurveT = 0.05f;
                mouthOpenT = 0f; tiltT = -3f; glowT = 0.7f; eyeShiftXT = 6f; eyeShiftYT = -5f;
                blushT = 0.12f;
                break;
            case SPEAKING:
                eyeOpenT = 0.98f; browRaiseT = 0.22f; mouthCurveT = 0.35f; tiltT = 0.5f;
                glowT = 0.9f; eyeShiftXT = 0f; eyeShiftYT = 0f; blushT = 0.28f; browAngleT = 0f;
                break;
            case HAPPY:
                eyeOpenT = 0.28f; browRaiseT = 0.5f; mouthCurveT = 0.95f; mouthOpenT = 0.3f;
                tiltT = 2.5f; glowT = 1f; blushT = 0.65f; eyeShiftXT = 0f; eyeShiftYT = 0f;
                browAngleT = 3f;
                break;
            case SAD:
                eyeOpenT = 0.62f; browRaiseT = -0.1f; browAngleT = 12f; mouthCurveT = -0.75f;
                mouthOpenT = 0f; tiltT = -4f; glowT = 0.22f; blushT = 0.08f;
                eyeShiftXT = 0f; eyeShiftYT = 4f;
                break;
            case CONFUSED:
                eyeOpenT = 0.9f; browRaiseT = 0.2f; browAngleT = -14f; mouthCurveT = -0.25f;
                mouthOpenT = 0.04f; tiltT = 6f; glowT = 0.5f; eyeShiftXT = -6f; eyeShiftYT = -2f;
                blushT = 0.18f;
                break;
            case SURPRISED:
                eyeOpenT = 1.28f; browRaiseT = 0.9f; browAngleT = 0f; mouthCurveT = 0f;
                mouthOpenT = 0.6f; tiltT = 0f; glowT = 1f; blushT = 0.25f;
                eyeShiftXT = 0f; eyeShiftYT = 0f;
                break;
            default:
                eyeOpenT = 1f; browRaiseT = 0f; browAngleT = 0f; mouthCurveT = 0.32f;
                mouthOpenT = 0f; tiltT = 0f; glowT = 0.4f; blushT = 0.2f;
                eyeShiftXT = 0f; eyeShiftYT = 0f;
        }
    }

    public int getState() { return state; }

    public void setAmplitude(float a) {
        amplitude = Math.max(0f, Math.min(1f, a));
    }

    public void pulse() {
        pulse = 1f;
        if (state == IDLE) setState(LISTENING);
    }

    public void refreshLook() {
        hairStyle = Prefs.get(getContext()).hairStyle();
        eyeStyle = Prefs.get(getContext()).eyeStyle();
        buildPaths();
        buildShaders();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == 0 || h == 0) return;
        scale = Math.max(w / DW, h / DH) * 1.02f;
        ox = (w - DW * scale) / 2f;
        oy = h - (DH - 60) * scale;
        buildPaths();
        buildShaders();
    }

    private void buildPaths() {
        face.reset();
        face.moveTo(366, 402);
        face.cubicTo(354, 512, 404, 606, 500, 664);
        face.cubicTo(596, 606, 646, 512, 634, 402);
        face.cubicTo(610, 306, 560, 278, 500, 278);
        face.cubicTo(440, 278, 390, 306, 366, 402);
        face.close();

        neck.reset();
        neck.moveTo(452, 610);
        neck.cubicTo(452, 690, 444, 726, 424, 760);
        neck.lineTo(576, 760);
        neck.cubicTo(556, 726, 548, 690, 548, 610);
        neck.close();

        earL.reset();
        earL.addOval(new RectF(334, 466, 376, 552), Path.Direction.CW);
        earR.reset();
        earR.addOval(new RectF(624, 466, 666, 552), Path.Direction.CW);

        hairBack.reset();
        hairBack2.reset();
        if (hairStyle == 1) {
            // bob
            hairBack.moveTo(330, 560);
            hairBack.cubicTo(276, 430, 300, 268, 424, 226);
            hairBack.cubicTo(498, 200, 560, 220, 616, 250);
            hairBack.cubicTo(716, 300, 730, 440, 674, 570);
            hairBack.cubicTo(690, 660, 676, 740, 640, 792);
            hairBack.cubicTo(600, 726, 572, 660, 566, 596);
            hairBack.lineTo(434, 596);
            hairBack.cubicTo(428, 660, 400, 726, 360, 792);
            hairBack.cubicTo(324, 740, 310, 660, 330, 560);
            hairBack.close();
        } else if (hairStyle == 2) {
            // twin tails
            hairBack.moveTo(342, 540);
            hairBack.cubicTo(292, 420, 306, 272, 424, 230);
            hairBack.cubicTo(498, 204, 556, 222, 612, 252);
            hairBack.cubicTo(712, 302, 726, 432, 672, 560);
            hairBack.cubicTo(690, 640, 686, 706, 656, 748);
            hairBack.cubicTo(612, 700, 584, 650, 574, 596);
            hairBack.lineTo(426, 596);
            hairBack.cubicTo(416, 650, 388, 700, 344, 748);
            hairBack.cubicTo(314, 706, 310, 640, 342, 540);
            hairBack.close();
            hairBack2.moveTo(300, 470);
            hairBack2.cubicTo(212, 560, 196, 780, 258, 1000);
            hairBack2.cubicTo(286, 1078, 330, 1130, 344, 1190);
            hairBack2.cubicTo(370, 1080, 352, 900, 340, 780);
            hairBack2.cubicTo(330, 660, 328, 560, 300, 470);
            hairBack2.close();
            hairBack2.moveTo(700, 470);
            hairBack2.cubicTo(788, 560, 804, 780, 742, 1000);
            hairBack2.cubicTo(714, 1078, 670, 1130, 656, 1190);
            hairBack2.cubicTo(630, 1080, 648, 900, 660, 780);
            hairBack2.cubicTo(670, 660, 672, 560, 700, 470);
            hairBack2.close();
        } else {
            // long
            hairBack.moveTo(320, 640);
            hairBack.cubicTo(258, 470, 296, 268, 430, 224);
            hairBack.cubicTo(500, 200, 560, 218, 618, 248);
            hairBack.cubicTo(730, 306, 748, 496, 682, 660);
            hairBack.cubicTo(726, 830, 760, 1010, 730, 1200);
            hairBack.cubicTo(660, 1120, 614, 1010, 590, 900);
            hairBack.cubicTo(578, 820, 572, 720, 566, 648);
            hairBack.lineTo(434, 648);
            hairBack.cubicTo(428, 720, 422, 820, 410, 900);
            hairBack.cubicTo(386, 1010, 340, 1120, 270, 1200);
            hairBack.cubicTo(240, 1010, 274, 830, 320, 640);
            hairBack.close();
        }

        bangs.reset();
        bangs.moveTo(352, 436);
        bangs.cubicTo(346, 318, 414, 258, 500, 256);
        bangs.cubicTo(586, 258, 654, 318, 648, 436);
        bangs.cubicTo(620, 368, 596, 344, 566, 372);
        bangs.cubicTo(548, 314, 512, 302, 496, 352);
        bangs.cubicTo(470, 306, 434, 316, 418, 372);
        bangs.cubicTo(392, 342, 372, 366, 352, 436);
        bangs.close();

        lockL.reset();
        lockL.moveTo(352, 380);
        lockL.cubicTo(318, 500, 300, 680, 316, 900);
        lockL.cubicTo(336, 830, 372, 700, 400, 560);
        lockL.cubicTo(408, 500, 396, 430, 386, 396);
        lockL.close();

        lockR.reset();
        lockR.moveTo(648, 380);
        lockR.cubicTo(682, 500, 700, 680, 684, 900);
        lockR.cubicTo(664, 830, 628, 700, 600, 560);
        lockR.cubicTo(592, 500, 604, 430, 614, 396);
        lockR.close();

        ahoge.reset();
        ahoge.moveTo(506, 250);
        ahoge.cubicTo(520, 186, 570, 172, 566, 140);
        ahoge.cubicTo(590, 176, 556, 214, 528, 246);

        body.reset();
        body.moveTo(318, 1500);
        body.cubicTo(312, 1130, 338, 900, 420, 800);
        body.cubicTo(450, 772, 474, 752, 500, 752);
        body.cubicTo(526, 752, 550, 772, 580, 800);
        body.cubicTo(662, 900, 688, 1130, 682, 1500);
        body.close();

        collar.reset();
        collar.moveTo(414, 780);
        collar.cubicTo(444, 812, 470, 830, 500, 830);
        collar.cubicTo(530, 830, 556, 812, 586, 780);
        collar.cubicTo(566, 858, 538, 892, 500, 892);
        collar.cubicTo(462, 892, 434, 858, 414, 780);
        collar.close();

        shirt.reset();
        shirt.moveTo(456, 820);
        shirt.lineTo(500, 880);
        shirt.lineTo(544, 820);
        shirt.cubicTo(530, 900, 470, 900, 456, 820);
        shirt.close();

        circlet.reset();
        circlet.moveTo(382, 380);
        circlet.cubicTo(420, 336, 580, 336, 618, 380);

        seamL.reset();
        seamL.moveTo(410, 830);
        seamL.cubicTo(372, 900, 352, 1060, 350, 1250);
        seamR.reset();
        seamR.moveTo(590, 830);
        seamR.cubicTo(628, 900, 648, 1060, 650, 1250);

        gem.reset();
        gem.moveTo(500, 322);
        gem.lineTo(512, 348);
        gem.lineTo(500, 374);
        gem.lineTo(488, 348);
        gem.close();

        ringPath.reset();
        ringPath.addOval(new RectF(212, 200, 788, 776), Path.Direction.CW);
    }

    private void buildShaders() {
        skinShader = new LinearGradient(360, 300, 640, 700,
                new int[]{0xFFFFEDE3, 0xFFFBD5C6, 0xFFF3BCAA}, new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP);
        skinShadeShader = new LinearGradient(340, 500, 500, 760,
                new int[]{0x00E9A28F, 0x66E9A28F}, null, Shader.TileMode.CLAMP);
        int[] hair = hairPalette();
        hairShader = new LinearGradient(320, 220, 700, 1150,
                new int[]{hair[0], hair[1], hair[2]}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        hairLightShader = new LinearGradient(400, 240, 620, 700,
                new int[]{hair[2], hair[1]}, null, Shader.TileMode.CLAMP);
        outfitShader = new LinearGradient(320, 740, 690, 1500,
                new int[]{0xFF16223F, 0xFF0A1020, 0xFF04070F}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
        glowShader = new RadialGradient(500, 470, 380,
                new int[]{Theme.alpha(ACCENTS[eyeStyle], 0.42f), Theme.alpha(0xFF8B6BFF, 0.14f), Color.TRANSPARENT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        glowShader2 = new RadialGradient(0, 0, 120,
                new int[]{Theme.alpha(ACCENTS[eyeStyle], 0.55f), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP);
        irisShader = new RadialGradient(0, -2, 21,
                new int[]{IRIS_LIGHT[eyeStyle], IRIS_MID[eyeStyle], IRIS_DARK[eyeStyle]},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        blushShader = new RadialGradient(0, 0, 46,
                new int[]{0x66FF7E9B, 0x00FF7E9B}, null, Shader.TileMode.CLAMP);
        lipShader = new LinearGradient(460, 560, 540, 610,
                new int[]{0xFFF2929B, 0xFFD96E7E}, null, Shader.TileMode.CLAMP);
    }

    private int[] hairPalette() {
        switch (hairStyle) {
            case 1: return new int[]{0xFF3B4166, 0xFF1E2238, 0xFF10121F};
            case 2: return new int[]{0xFF5A3550, 0xFF3A2236, 0xFF221026};
            default: return new int[]{0xFF565A96, 0xFF2A2F5E, 0xFF141634};
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1_000_000_000f);
        last = now;
        time += dt;
        update(dt);

        canvas.save();
        canvas.translate(ox, oy);
        canvas.scale(scale, scale);

        drawGlow(canvas);
        drawRings(canvas);

        // body (breathing)
        float breathe = (float) Math.sin(time * 1.15f) * 4f;
        canvas.save();
        canvas.translate(0, breathe * 0.35f);
        drawBody(canvas);
        canvas.restore();

        drawHead(canvas);
        drawParticles(canvas);

        canvas.restore();
        postInvalidateOnAnimation();
    }

    private void update(float dt) {
        float k = 1f - (float) Math.exp(-dt * 6.5f);
        eyeOpen += (eyeOpenT - eyeOpen) * k;
        browRaise += (browRaiseT - browRaise) * k;
        browAngle += (browAngleT - browAngle) * k;
        mouthCurve += (mouthCurveT - mouthCurve) * k;
        mouthOpen += (mouthOpenT - mouthOpen) * k;
        blush += (blushT - blush) * k;
        tilt += (tiltT - tilt) * k;
        eyeShiftX += (eyeShiftXT - eyeShiftX) * k;
        eyeShiftY += (eyeShiftYT - eyeShiftY) * k;
        glow += (glowT - glow) * k;

        bob = (float) Math.sin(time * 1.15f) * 5f;
        sway = (float) Math.sin(time * 0.63f) * 1.6f;

        if (state == SPEAKING) {
            float amp = amplitude > 0.02f ? amplitude : 0.55f;
            float wave = 0.5f + 0.5f * (float) Math.sin(time * 15.5f);
            float wave2 = 0.5f + 0.5f * (float) Math.sin(time * 9.1f + 1.3f);
            mouthOpen = Math.max(mouthOpen, (0.12f + 0.78f * amp) * (0.45f * wave + 0.55f * wave2));
        }
        if (pulse > 0f) pulse = Math.max(0f, pulse - dt * 1.6f);

        // Blink scheduling
        nextBlink -= dt;
        if (nextBlink <= 0f && blinkT < 0f) {
            blinkT = 0f;
            nextBlink = 2.2f + (float) Math.random() * 3.4f;
        }
        if (blinkT >= 0f) {
            blinkT += dt;
            float p = blinkT / 0.16f;
            if (p >= 1f) { blinkT = -1f; eyeOpen = eyeOpenT; }
            else {
                float closed = Math.abs(1f - 2f * p); // 1 -> 0 -> 1
                eyeOpen = Math.max(0.03f, eyeOpen * (0.2f + 0.8f * closed));
            }
        }
    }

    private void drawGlow(Canvas canvas) {
        float g = glow + pulse * 0.4f;
        fill.setShader(null);
        fill.setAlpha((int) (255 * Math.min(1f, g)));
        fill.setShader(glowShader);
        canvas.drawCircle(500, 470, 400, fill);
        fill.setShader(null);
        fill.setAlpha(255);

        // soft aura under the bust
        fill.setShader(glowShader2);
        canvas.save();
        canvas.translate(500, 1180);
        canvas.scale(2.6f, 1.1f);
        fill.setAlpha((int) (70 * g));
        canvas.drawCircle(0, 0, 120, fill);
        canvas.restore();
        fill.setShader(null);
        fill.setAlpha(255);
    }

    private void drawRings(Canvas canvas) {
        int accent = ACCENTS[eyeStyle];
        float rot = time * 9f;
        stroke.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 3; i++) {
            canvas.save();
            canvas.rotate(rot * (i % 2 == 0 ? 1 : -1.4f) + i * 40f, 500, 470);
            stroke.setColor(Theme.alpha(accent, 0.10f + 0.05f * i + glow * 0.06f));
            stroke.setStrokeWidth(2.2f);
            RectF r = new RectF(500 - 300 - i * 42, 470 - 300 - i * 42, 500 + 300 + i * 42, 470 + 300 + i * 42);
            canvas.drawArc(r, 12, 120, false, stroke);
            canvas.drawArc(r, 192, 74, false, stroke);
            canvas.restore();
        }
        // orbiting sparks
        for (int i = 0; i < 4; i++) {
            float a = time * (0.5f + i * 0.13f) + i * 1.7f;
            float rad = 300 + i * 36;
            float x = 500 + (float) Math.cos(a) * rad;
            float y = 470 + (float) Math.sin(a) * rad * 0.96f;
            fill.setShader(null);
            fill.setColor(Theme.alpha(accent, 0.5f + 0.35f * glow));
            canvas.drawCircle(x, y, 3.2f + (i % 2) * 1.6f, fill);
        }
    }

    private void drawBody(Canvas canvas) {
        // back hair behind shoulders
        fill.setShader(hairShader);
        canvas.drawPath(hairBack2, fill);
        fill.setShader(null);

        fill.setShader(outfitShader);
        canvas.drawPath(body, fill);
        fill.setShader(null);

        // collar
        fill.setColor(0xFF1B2646);
        canvas.drawPath(collar, fill);
        fill.setColor(0xFF0A1528);
        canvas.drawPath(shirt, fill);

        // glowing seams
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.4f);
        stroke.setColor(Theme.alpha(ACCENTS[eyeStyle], 0.32f + 0.28f * glow));
        canvas.drawPath(seamL, stroke);
        canvas.drawPath(seamR, stroke);
        stroke.setStrokeWidth(1.2f);
        stroke.setColor(Theme.alpha(0xFF8B6BFF, 0.3f));
        canvas.drawPath(collar, stroke);

        // chest core
        fill.setShader(null);
        fill.setColor(Theme.alpha(ACCENTS[eyeStyle], 0.10f + 0.10f * glow));
        canvas.drawCircle(500, 1030, 62 + 4 * glow, fill);
        stroke.setStrokeWidth(2f);
        stroke.setColor(Theme.alpha(ACCENTS[eyeStyle], 0.55f));
        canvas.drawCircle(500, 1030, 44, stroke);
        canvas.save();
        canvas.rotate(time * 40f, 500, 1030);
        stroke.setStrokeWidth(3f);
        stroke.setColor(Theme.alpha(ACCENTS[eyeStyle], 0.8f));
        canvas.drawArc(new RectF(478, 1008, 522, 1052), 20, 70, false, stroke);
        canvas.drawArc(new RectF(478, 1008, 522, 1052), 200, 70, false, stroke);
        canvas.restore();
        fill.setColor(Theme.alpha(ACCENTS[eyeStyle], 0.9f));
        canvas.drawCircle(500, 1030, 6 + 2 * glow, fill);

        // shoulder emblem
        stroke.setColor(Theme.alpha(0xFFFFFFFF, 0.10f));
        stroke.setStrokeWidth(1.4f);
        canvas.drawLine(360, 940, 420, 1180, stroke);
        canvas.drawLine(640, 940, 580, 1180, stroke);
    }

    private void drawHead(Canvas canvas) {
        canvas.save();
        canvas.rotate(tilt + sway, 500, 720);
        canvas.translate(0, bob);

        // hair behind face
        fill.setShader(hairShader);
        canvas.drawPath(hairBack, fill);
        fill.setShader(null);

        // earrings
        drawGlowDot(canvas, 348, 556, 16, 0.7f);
        drawGlowDot(canvas, 652, 556, 16, 0.7f);
        fill.setColor(0xFFE9F6FF);
        canvas.drawCircle(348, 556, 5.4f, fill);
        canvas.drawCircle(652, 556, 5.4f, fill);

        // neck
        fill.setShader(skinShadeShader);
        canvas.drawPath(neck, fill);
        fill.setShader(null);
        fill.setColor(0xFFF0B9A7);
        canvas.drawPath(neck, fill);
        fill.setAlpha(60);
        fill.setColor(0xFFB57A6A);
        Path neckShade = new Path();
        neckShade.moveTo(462, 640);
        neckShade.cubicTo(470, 700, 480, 740, 500, 752);
        neckShade.lineTo(520, 752);
        neckShade.cubicTo(520, 700, 512, 660, 508, 628);
        neckShade.close();
        canvas.drawPath(neckShade, fill);
        fill.setAlpha(255);

        // ears
        fill.setColor(0xFFF6C7B6);
        canvas.drawPath(earL, fill);
        canvas.drawPath(earR, fill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f);
        stroke.setColor(0x33B57A6A);
        canvas.drawArc(new RectF(344, 486, 368, 534), -80, 160, false, stroke);
        canvas.drawArc(new RectF(632, 486, 656, 534), 100, 160, false, stroke);

        // face
        fill.setShader(skinShader);
        canvas.drawPath(face, fill);
        fill.setShader(null);

        // temple shading
        fill.setColor(0x1AB57A6A);
        canvas.drawOval(new RectF(354, 380, 396, 600), fill);
        canvas.drawOval(new RectF(604, 380, 646, 600), fill);

        drawEyes(canvas);
        drawNose(canvas);
        drawMouth(canvas);

        // blush
        fill.setShader(blushShader);
        fill.setAlpha((int) (255 * Math.min(1f, blush * 1.6f)));
        canvas.save();
        canvas.translate(424, 548);
        canvas.drawCircle(0, 0, 46, fill);
        canvas.restore();
        canvas.save();
        canvas.translate(576, 548);
        canvas.drawCircle(0, 0, 46, fill);
        canvas.restore();
        fill.setAlpha(255);
        fill.setShader(null);

        // hair front
        fill.setShader(hairLightShader);
        canvas.drawPath(bangs, fill);
        canvas.drawPath(lockL, fill);
        canvas.drawPath(lockR, fill);
        fill.setShader(null);
        // hair highlights
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(3.4f);
        stroke.setColor(Theme.alpha(0xFFD9DBFF, 0.20f));
        Path hl = new Path();
        hl.moveTo(430, 300);
        hl.cubicTo(470, 330, 500, 380, 512, 440);
        canvas.drawPath(hl, stroke);
        Path hl2 = new Path();
        hl2.moveTo(590, 306);
        hl2.cubicTo(556, 338, 540, 386, 536, 446);
        canvas.drawPath(hl2, stroke);
        stroke.setStrokeWidth(2f);
        stroke.setColor(Theme.alpha(0xFFD9DBFF, 0.14f));
        Path hl3 = new Path();
        hl3.moveTo(352, 470);
        hl3.cubicTo(336, 600, 328, 760, 336, 860);
        canvas.drawPath(hl3, stroke);
        Path hl4 = new Path();
        hl4.moveTo(648, 470);
        hl4.cubicTo(664, 600, 672, 760, 664, 860);
        canvas.drawPath(hl4, stroke);

        // ahoge
        canvas.save();
        canvas.rotate((float) Math.sin(time * 1.6f) * 4f, 506, 250);
        stroke.setStrokeWidth(9f);
        stroke.setColor(hairPalette()[1]);
        canvas.drawPath(ahoge, stroke);
        stroke.setStrokeWidth(4f);
        stroke.setColor(Theme.alpha(0xFFD9DBFF, 0.25f));
        canvas.drawPath(ahoge, stroke);
        canvas.restore();

        // circlet + gem
        stroke.setStrokeWidth(6f);
        stroke.setColor(Theme.alpha(ACCENTS[eyeStyle], 0.18f + 0.2f * glow));
        canvas.drawPath(circlet, stroke);
        stroke.setStrokeWidth(1.8f);
        stroke.setColor(Theme.alpha(ACCENTS[eyeStyle], 0.95f));
        canvas.drawPath(circlet, stroke);
        fill.setShader(glowShader2);
        fill.setAlpha((int) (150 + 80 * glow));
        canvas.save();
        canvas.translate(500, 348);
        canvas.scale(0.5f, 0.5f);
        canvas.drawCircle(0, 0, 120, fill);
        canvas.restore();
        fill.setShader(null);
        fill.setAlpha(255);
        fill.setColor(ACCENTS[eyeStyle]);
        canvas.drawPath(gem, fill);
        fill.setColor(0xFFFFFFFF);
        Path gemHi = new Path();
        gemHi.moveTo(500, 330);
        gemHi.lineTo(506, 346);
        gemHi.lineTo(500, 358);
        gemHi.close();
        fill.setAlpha(190);
        canvas.drawPath(gemHi, fill);
        fill.setAlpha(255);

        drawBrows(canvas);
        canvas.restore();
    }

    private void drawGlowDot(Canvas canvas, float x, float y, float r, float alpha) {
        fill.setShader(glowShader2);
        fill.setAlpha((int) (alpha * 180));
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(r / 120f, r / 120f);
        canvas.drawCircle(0, 0, 120, fill);
        canvas.restore();
        fill.setAlpha(255);
        fill.setShader(null);
    }

    private void drawEyes(Canvas canvas) {
        drawEye(canvas, 447, 456, -1);
        drawEye(canvas, 553, 456, 1);
    }

    private void drawEye(Canvas canvas, float cx, float cy, int side) {
        float open = Math.max(0.02f, Math.min(1.35f, eyeOpen));
        float h = 25f * open;
        canvas.save();
        canvas.translate(cx, cy);

        if (open < 0.10f) {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(5f);
            stroke.setColor(0xFF232742);
            eyeLash.reset();
            eyeLash.moveTo(-40, 2);
            eyeLash.quadTo(0, 16, 40, -2);
            canvas.drawPath(eyeLash, stroke);
            canvas.restore();
            return;
        }

        // clip to the almond
        clipEye.reset();
        clipEye.moveTo(-42, 0);
        clipEye.cubicTo(-28, -h * 1.5f, 28, -h * 1.5f, 42, 0);
        clipEye.cubicTo(28, h * 1.1f, -28, h * 1.1f, -42, 0);
        clipEye.close();
        canvas.save();
        canvas.clipPath(clipEye);
        fill.setShader(null);
        fill.setColor(0xFFFBFDFF);
        canvas.drawRect(-50, -60, 50, 60, fill);

        // iris
        float ix = eyeShiftX * 0.9f;
        float iy = eyeShiftY * 0.55f - 1f;
        canvas.save();
        canvas.translate(ix, iy);
        fill.setShader(irisShader);
        canvas.drawCircle(0, 0, 19.5f, fill);
        fill.setShader(null);
        fill.setColor(0xFF0A1224);
        canvas.drawCircle(0, 0, 8.4f, fill);
        fill.setColor(0xFFFFFFFF);
        canvas.drawCircle(-7f, -8f, 4.8f, fill);
        fill.setAlpha(190);
        canvas.drawCircle(7f, 6f, 2.4f, fill);
        fill.setAlpha(255);
        // iris ring
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.4f);
        stroke.setColor(Theme.alpha(IRIS_LIGHT[eyeStyle], 0.9f));
        canvas.drawCircle(0, 0, 19.5f, stroke);
        canvas.restore();

        // upper shading inside the eye
        fill.setShader(null);
        fill.setColor(0x22234060);
        canvas.drawRect(-50, -60, 50, -h * 0.35f, fill);
        canvas.restore();

        // lashes
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(5.2f);
        stroke.setColor(0xFF1C2038);
        eyeLash.reset();
        eyeLash.moveTo(-44, 1);
        eyeLash.cubicTo(-30, -h * 1.55f, 28, -h * 1.55f, 44, -1);
        canvas.drawPath(eyeLash, stroke);
        stroke.setStrokeWidth(3.2f);
        eyeLash.reset();
        eyeLash.moveTo(36, -h * 0.6f);
        eyeLash.lineTo(52, -h * 1.05f);
        canvas.drawPath(eyeLash, stroke);
        stroke.setStrokeWidth(1.6f);
        stroke.setColor(0x44A06A6A);
        eyeLash.reset();
        eyeLash.moveTo(-36, h * 0.55f);
        eyeLash.quadTo(0, h * 1.05f, 36, h * 0.5f);
        canvas.drawPath(eyeLash, stroke);

        // sparkle dot
        fill.setColor(0xCCFFFFFF);
        canvas.drawCircle(-16, -h * 0.55f, 2.4f, fill);
        canvas.restore();
    }

    private void drawBrows(Canvas canvas) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(7f);
        stroke.setColor(hairPalette()[2]);
        for (int side = -1; side <= 1; side += 2) {
            canvas.save();
            float cx = 500 + side * 55;
            float cy = 398 - browRaise * 12f;
            canvas.translate(cx, cy);
            canvas.rotate((browAngle + (side < 0 ? -2 : 2)) * side * 0.55f);
            browPath.reset();
            browPath.moveTo(-33, 4);
            browPath.cubicTo(-12, -8 - browRaise * 3, 12, -8 - browRaise * 3, 33, 2);
            canvas.drawPath(browPath, stroke);
            canvas.restore();
        }
    }

    private void drawNose(Canvas canvas) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.4f);
        stroke.setColor(0x55B07362);
        Path nose = new Path();
        nose.moveTo(500, 500);
        nose.cubicTo(506, 516, 508, 524, 500, 530);
        canvas.drawPath(nose, stroke);
    }

    private void drawMouth(Canvas canvas) {
        float open = Math.max(0f, mouthOpen);
        float curve = mouthCurve;
        float w = 30f + curve * 5f;
        float h = open * 30f;

        canvas.save();
        canvas.translate(500, 588);

        if (h < 2.2f) {
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(4f);
            stroke.setColor(0xFFCE6A78);
            mouthPath.reset();
            mouthPath.moveTo(-w, -2f);
            mouthPath.quadTo(0, curve * 13f + 2f, w, -2f);
            canvas.drawPath(mouthPath, stroke);
        } else {
            mouthPath.reset();
            mouthPath.moveTo(-w, 0);
            mouthPath.quadTo(0, -h - 4f - curve * 2f, w, 0);
            mouthPath.quadTo(0, h + 3f + curve * 3f, -w, 0);
            mouthPath.close();
            fill.setShader(null);
            fill.setColor(0xFF6A2433);
            canvas.drawPath(mouthPath, fill);
            // teeth
            if (h > 7f) {
                fill.setColor(0xFFFDF7F7);
                RectF teeth = new RectF(-w * 0.66f, -h * 0.82f, w * 0.66f, -h * 0.12f);
                canvas.drawRoundRect(teeth, 5, 5, fill);
            }
            // tongue
            if (h > 13f) {
                fill.setColor(0xFFD46A7C);
                RectF tongue = new RectF(-w * 0.42f, h * 0.16f, w * 0.42f, h * 0.86f);
                canvas.drawOval(tongue, fill);
            }
            // lips
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(2.6f);
            stroke.setColor(0xFFE38390);
            mouthPath.reset();
            mouthPath.moveTo(-w, 0);
            mouthPath.quadTo(0, -h - 4f - curve * 2f, w, 0);
            mouthPath.quadTo(0, h + 3f + curve * 3f, -w, 0);
            mouthPath.close();
            canvas.drawPath(mouthPath, stroke);
        }
        // lower lip highlight
        if (curve > 0.5f && h < 4f) {
            stroke.setStrokeWidth(1.4f);
            stroke.setColor(0x66FFC2CC);
            mouthPath.reset();
            mouthPath.moveTo(-14, 6);
            mouthPath.quadTo(0, 8 + curve * 4f, 14, 6);
            canvas.drawPath(mouthPath, stroke);
        }
        canvas.restore();
    }

    private void drawParticles(Canvas canvas) {
        for (int i = 0; i < stars.length; i += 2) {
            float bx = stars[i];
            float by = stars[i + 1];
            float px = bx * 1000f + (float) Math.sin(time * 0.4f + starSeed[i / 2]) * 18f;
            float py = (by * 1300f - (time * (12f + (i % 5) * 7f)) % 1300f);
            if (py < 0) py += 1300f;
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * 1.7f + starSeed[i / 2]));
            float size = 1.4f + (i % 3) * 0.9f;
            fill.setShader(null);
            fill.setColor(Color.argb((int) (90 * tw * (0.4f + glow)), 190, 235, 255));
            canvas.drawCircle(px, py, size, fill);
        }
    }
}
