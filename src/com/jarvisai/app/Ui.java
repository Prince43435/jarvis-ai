package com.jarvisai.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

/** View factory: keeps every screen visually consistent without XML layouts. */
public final class Ui {

    private Ui() {}

    public static int dp(Context c, float v) { return Theme.dp(c, v); }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static Space space(Context c, int heightDp) {
        Space s = new Space(c);
        s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return s;
    }

    public static View hspace(Context c, int widthDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, widthDp), 1));
        return v;
    }

    public static TextView text(Context c, CharSequence s, float sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setLineSpacing(dp(c, 2), 1f);
        return t;
    }

    public static TextView bold(Context c, CharSequence s, float sizeSp, int color) {
        TextView t = text(c, s, sizeSp, color);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView label(Context c, CharSequence s, int color) {
        TextView t = text(c, s, 11f, color);
        t.setAllCaps(true);
        t.setLetterSpacing(0.14f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView mono(Context c, CharSequence s, float sizeSp, int color) {
        TextView t = text(c, s, sizeSp, color);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    public static GradientDrawable cardBg(Context c) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.isDark() ? 0xF20E1526 : 0xFFFFFFFF, Theme.isDark() ? 0xF20A0F1D : 0xFFF7F9FF});
        d.setCornerRadius(dp(c, 22));
        d.setStroke(dp(c, 1), Theme.stroke());
        return d;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(cardBg(c));
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        l.setElevation(dp(c, 2));
        return l;
    }

    public static LinearLayout glass(Context c) {
        LinearLayout l = column(c);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Theme.isDark() ? 0xE60B1122 : 0xF2FFFFFF, Theme.isDark() ? 0xD9060A14 : 0xE6FFFFFF});
        d.setCornerRadius(dp(c, 26));
        d.setStroke(dp(c, 1), Theme.isDark() ? 0x33FFFFFF : 0xFFFFFFFF);
        l.setBackground(d);
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        return l;
    }

    public static RippleDrawable ripple(int maskColor, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(maskColor), content, null);
    }

    public static ImageView icon(Context c, int res, int sizeDp, int tint) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(tint);
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    public static FrameLayout iconButton(Context c, int res, int sizeDp, int tint, View.OnClickListener click) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable circle = Util.circle(Theme.isDark() ? 0x1AFFFFFF : 0x14000000);
        f.setBackground(ripple(Theme.isDark() ? 0x33FFFFFF : 0x22000000, circle));
        f.setOnClickListener(click);
        f.setClickable(true);
        int s = dp(c, sizeDp);
        f.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        ImageView iv = icon(c, res, (int) (sizeDp * 0.52f), tint);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(c, (int) (sizeDp * 0.52f)), dp(c, (int) (sizeDp * 0.52f)));
        p.gravity = Gravity.CENTER;
        iv.setLayoutParams(p);
        f.addView(iv);
        return f;
    }

    public static TextView primaryButton(Context c, String label, View.OnClickListener click) {
        TextView t = bold(c, label, 15f, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF3FE0FF, 0xFF7C6BFF});
        g.setCornerRadius(dp(c, 18));
        t.setBackground(ripple(0x55FFFFFF, g));
        t.setPadding(dp(c, 18), dp(c, 15), dp(c, 18), dp(c, 15));
        t.setClickable(true);
        t.setOnClickListener(click);
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    public static TextView ghostButton(Context c, String label, View.OnClickListener click) {
        TextView t = bold(c, label, 14f, Theme.text());
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = Util.roundedStroke(Theme.isDark() ? 0x14FFFFFF : 0x0F000000,
                Theme.stroke(), 18, 1, c);
        t.setBackground(ripple(Theme.isDark() ? 0x33FFFFFF : 0x22000000, g));
        t.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        t.setClickable(true);
        t.setOnClickListener(click);
        return t;
    }

    public static TextView pill(Context c, String label, int color) {
        TextView t = bold(c, label, 11f, color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
        GradientDrawable g = Util.rounded(Theme.alpha(color, 0.14f), 100, c);
        g.setStroke(dp(c, 1), Theme.alpha(color, 0.35f));
        t.setBackground(g);
        return t;
    }

    public static View divider(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        p.topMargin = dp(c, 10);
        p.bottomMargin = dp(c, 10);
        v.setLayoutParams(p);
        v.setBackgroundColor(Theme.isDark() ? 0x14FFFFFF : 0x11000000);
        return v;
    }

    /** Top bar with optional back arrow, title, subtitle and an optional trailing view. */
    public static LinearLayout topBar(Activity a, String title, String subtitle, boolean back, View trailing) {
        LinearLayout bar = row(a);
        bar.setPadding(dp(a, 6), dp(a, 8), dp(a, 12), dp(a, 8));
        if (back) {
            bar.addView(iconButton(a, R.drawable.ic_back, 42, Theme.text(), v -> a.finish()));
            bar.addView(hspace(a, 4));
        } else {
            View orb = new View(a);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF3FE0FF, 0xFF8B6BFF});
            g.setShape(GradientDrawable.OVAL);
            orb.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(a, 30), dp(a, 30));
            lp.leftMargin = dp(a, 6);
            lp.rightMargin = dp(a, 10);
            bar.addView(orb, lp);
        }
        LinearLayout titles = column(a);
        TextView t = bold(a, title, 19f, Theme.text());
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(a, subtitle, 11.5f, Theme.muted());
            s.setSingleLine(true);
            titles.addView(s);
        }
        bar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null) bar.addView(trailing);
        return bar;
    }

    public static LinearLayout sectionTitle(Context c, String title) {
        LinearLayout l = row(c);
        l.setPadding(0, dp(c, 16), 0, dp(c, 6));
        l.addView(label(c, title, Theme.muted()));
        return l;
    }

    public static LinearLayout settingRow(Context c, int iconRes, String title, String subtitle,
                                          boolean chevron, View.OnClickListener click) {
        LinearLayout r = row(c);
        r.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        GradientDrawable g = Util.rounded(Theme.isDark() ? 0x0DFFFFFF : 0x08000000, 16, c);
        r.setBackground(ripple(Theme.isDark() ? 0x22FFFFFF : 0x15000000, g));
        r.setClickable(true);
        if (click != null) r.setOnClickListener(click);
        if (iconRes != 0) {
            FrameLayout iconBox = new FrameLayout(c);
            GradientDrawable ib = Util.rounded(Theme.alpha(Theme.accent(), 0.12f), 12, c);
            iconBox.setBackground(ib);
            ImageView iv = icon(c, iconRes, 18, Theme.accent());
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(c, 18), dp(c, 18));
            ip.gravity = Gravity.CENTER;
            iconBox.addView(iv, ip);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(c, 36), dp(c, 36));
            bp.rightMargin = dp(c, 12);
            r.addView(iconBox, bp);
        }
        LinearLayout col = column(c);
        TextView t = bold(c, title, 14.5f, Theme.text());
        col.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = text(c, subtitle, 11.5f, Theme.muted());
            s.setMaxLines(2);
            col.addView(s);
        }
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (chevron) r.addView(icon(c, R.drawable.ic_chevron, 16, Theme.faint()));
        return r;
    }

    public static TextView valueBadge(Context c, String value, int color) {
        TextView t = bold(c, value, 12f, color);
        t.setPadding(dp(c, 10), dp(c, 5), dp(c, 10), dp(c, 5));
        t.setBackground(Util.rounded(Theme.alpha(color, 0.13f), 100, c));
        return t;
    }

    // ---- Dialogs -------------------------------------------------------

    public static AlertDialog.Builder dialog(Context c, String title, String message) {
        AlertDialog.Builder b = new AlertDialog.Builder(c);
        if (title != null) b.setTitle(title);
        if (message != null) b.setMessage(message);
        return b;
    }

    public static void confirm(Activity a, String title, String message, String yes, Runnable onYes) {
        AlertDialog d = new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(yes, (x, w) -> onYes.run())
                .setNegativeButton(R.string.cancel, null)
                .create();
        d.show();
    }

    public interface TextCallback { void onText(String text); }

    public static void prompt(Activity a, String title, String hint, String initial, boolean multiline, TextCallback cb) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setText(initial == null ? "" : initial);
        e.setTextColor(Theme.text());
        e.setHintTextColor(Theme.faint());
        e.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT);
        e.setBackground(Util.roundedStroke(Theme.isDark() ? 0x14FFFFFF : 0x0A000000, Theme.stroke(), 14, 1, a));
        e.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        FrameLayout wrap = new FrameLayout(a);
        int pad = dp(a, 18);
        wrap.setPadding(pad, dp(a, 8), pad, 0);
        wrap.addView(e);
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton(R.string.save, (d, w) -> cb.onText(e.getText().toString().trim()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    public static void info(Activity a, String title, String message) {
        new AlertDialog.Builder(a).setTitle(title).setMessage(message)
                .setPositiveButton(R.string.close, null).show();
    }

    public static LinearLayout progressRow(Context c, String text) {
        LinearLayout r = row(c);
        android.widget.ProgressBar p = new android.widget.ProgressBar(c);
        p.setIndeterminate(true);
        p.setIndeterminateTintList(ColorStateList.valueOf(Theme.accent()));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(c, 18), dp(c, 18));
        pp.rightMargin = dp(c, 10);
        r.addView(p, pp);
        r.addView(Ui.text(c, text, 12.5f, Theme.muted()));
        return r;
    }

    public static void scrollToBottom(ScrollView sv) {
        if (sv == null) return;
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "J";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.toString();
    }
}
