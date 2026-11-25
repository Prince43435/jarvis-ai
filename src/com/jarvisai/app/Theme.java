package com.jarvisai.app;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * Central palette. JARVIS has a dark "holographic" theme and a light theme.
 * All screens read colours from here so a theme switch is instant.
 */
public final class Theme {

    public static final int CYAN = 0xFF3FE0FF;
    public static final int VIOLET = 0xFF8B6BFF;
    public static final int MAGENTA = 0xFFFF4FD8;
    public static final int GREEN = 0xFF38E0A2;
    public static final int AMBER = 0xFFFFC24B;
    public static final int RED = 0xFFFF5C7A;

    private static boolean dark = true;

    private Theme() {}

    public static void setDark(boolean value) { dark = value; }
    public static boolean isDark() { return dark; }

    public static int bg()      { return dark ? 0xFF05070F : 0xFFF2F5FE; }
    public static int bg2()     { return dark ? 0xFF080C18 : 0xFFE7EDFC; }
    public static int surface() { return dark ? 0xFF0C1224 : 0xFFFFFFFF; }
    public static int surface2(){ return dark ? 0xFF131B33 : 0xFFEDF2FF; }
    public static int stroke()  { return dark ? 0xFF1E2A4A : 0xFFD3DDF4; }
    public static int text()    { return dark ? 0xFFEDF3FF : 0xFF0A1024; }
    public static int muted()   { return dark ? 0xFF8FA1C7 : 0xFF5B6B8F; }
    public static int faint()   { return dark ? 0xFF5F7095 : 0xFF8C9BBC; }
    public static int glass()   { return dark ? 0x14FFFFFF : 0xCCFFFFFF; }
    public static int glassStrong() { return dark ? 0xCC0E162B : 0xF2FFFFFF; }
    public static int scrim()   { return dark ? 0xB3050A14 : 0x990F1830; }
    public static int accent()  { return CYAN; }
    public static int accent2() { return VIOLET; }

    /** Call before super.onCreate so the activity uses the right base theme. */
    public static void apply(Activity a) {
        Prefs p = Prefs.get(a);
        String mode = p.themeMode();
        boolean useDark;
        if ("light".equals(mode)) {
            useDark = false;
        } else if ("dark".equals(mode)) {
            useDark = true;
        } else {
            int night = a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            useDark = night != Configuration.UI_MODE_NIGHT_NO;
        }
        setDark(useDark);
        a.setTheme(useDark ? R.style.Theme_Jarvis : R.style.Theme_Jarvis_Light);
    }

    public static void decorate(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(bg());
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
        int flags = w.getDecorView().getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        w.getDecorView().setSystemUiVisibility(flags);
    }

    public static int alpha(int color, float factor) {
        int a = (int) (Color.alpha(color) * Math.max(0f, Math.min(1f, factor)));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int blend(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.argb(
                (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
                (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
