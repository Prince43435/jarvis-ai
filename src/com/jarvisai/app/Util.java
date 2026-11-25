package com.jarvisai.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.format.DateFormat;
import android.util.Base64;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class Util {

    private Util() {}

    public static int dp(Context c, float v) { return Theme.dp(c, v); }
    public static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }

    public static void toast(Context c, String msg) {
        if (msg == null) return;
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static void vibrate(Context c, long ms) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    public static void copy(Context c, String label, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
            toast(c, "Copied");
        }
    }

    public static void hideKeyboard(View v) {
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    public static void showKeyboard(View v) {
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager) v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
    }

    public static GradientDrawable rounded(int fill, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(c, radiusDp));
        d.setColor(fill);
        return d;
    }

    public static GradientDrawable roundedStroke(int fill, int strokeColor, float radiusDp, float strokeDp, Context c) {
        GradientDrawable d = rounded(fill, radiusDp, c);
        d.setStroke(Math.max(1, dp(c, strokeDp)), strokeColor);
        return d;
    }

    public static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    public static GradientDrawable gradient(int[] colors, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    public static Shader linear(int from, int to) {
        return new LinearGradient(0, 0, 0, 1, from, to, Shader.TileMode.CLAMP);
    }

    public static String time(long ts) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(ts));
    }

    public static String dayTime(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ts);
        boolean sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR);
        if (sameDay) return time(ts);
        return new SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(new Date(ts));
    }

    public static String clock(long ts) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    public static String friendlyDate(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(ts);
        long dayDiff = (now.get(Calendar.YEAR) * 366L + now.get(Calendar.DAY_OF_YEAR))
                - (then.get(Calendar.YEAR) * 366L + then.get(Calendar.DAY_OF_YEAR));
        String hm = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(ts));
        if (dayDiff == 0) return "today " + hm;
        if (dayDiff == 1) return "yesterday " + hm;
        if (dayDiff == -1) return "tomorrow " + hm;
        return new SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(new Date(ts)) + " · " + hm;
    }

    public static String nowForPrompt() {
        SimpleDateFormat f = new SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a z", Locale.getDefault());
        return f.format(new Date());
    }

    public static String hhmm(int hour, int minute) {
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    public static String base64(byte[] data) { return Base64.encodeToString(data, Base64.NO_WRAP); }
    public static byte[] unbase64(String data) { return Base64.decode(data, Base64.NO_WRAP); }

    public static byte[] jpeg(Bitmap bmp, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }

    public static Bitmap scale(Bitmap src, int maxSide) {
        int w = src.getWidth(), h = src.getHeight();
        int max = Math.max(w, h);
        if (max <= maxSide) return src;
        float f = maxSide / (float) max;
        return Bitmap.createScaledBitmap(src, Math.round(w * f), Math.round(h * f), true);
    }

    public static Bitmap drawableToBitmap(android.graphics.drawable.Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, c.getWidth(), c.getHeight());
        d.draw(c);
        return b;
    }

    public static int blendColors(int c1, int c2, float ratio) {
        float r = Math.max(0, Math.min(1, ratio));
        return Color.rgb(
                (int) (Color.red(c1) * (1 - r) + Color.red(c2) * r),
                (int) (Color.green(c1) * (1 - r) + Color.green(c2) * r),
                (int) (Color.blue(c1) * (1 - r) + Color.blue(c2) * r));
    }

    public static boolean openIntent(Context c, Intent i, String error) {
        try {
            if (i.resolveActivity(c.getPackageManager()) == null) {
                toast(c, error);
                return false;
            }
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            toast(c, error + " (" + e.getClass().getSimpleName() + ")");
            return false;
        }
    }

    public static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    public static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public static String safe(String s) { return s == null ? "" : s; }

    public static boolean is24h(Context c) { return DateFormat.is24HourFormat(c); }

    public static Uri fileUri(Context c, java.io.File f) {
        return Uri.fromFile(f);
    }
}
