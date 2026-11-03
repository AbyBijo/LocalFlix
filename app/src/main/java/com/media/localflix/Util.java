package com.media.localflix;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;
import java.util.Locale;

public class Util {

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static String time(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long q = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, q);
        return String.format(Locale.US, "%d:%02d", m, q);
    }

    public static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1048576L) return String.format(Locale.US, "%.0f KB", b / 1024.0);
        if (b < 1073741824L) return String.format(Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(Locale.US, "%.2f GB", b / 1073741824.0);
    }

    public static String speed(float s) {
        String t = String.format(Locale.US, "%.2f", s);
        if (t.endsWith("0")) t = t.substring(0, t.length() - 1);
        return t + "x";
    }

    // Iteration 3 — life: one place for all motion, so every element
    // animates the same way instead of each screen inventing its own.
    public static void fadeIn(View v, long delayMs) {
        if (v == null) return;
        v.setAlpha(0f);
        v.animate().cancel();
        v.animate().alpha(1f).setDuration(220).setStartDelay(delayMs).start();
    }

    public static void slideFadeIn(View v, long delayMs, Context c) {
        if (v == null) return;
        v.setAlpha(0f);
        v.setTranslationY(dp(c, 10));
        v.animate().cancel();
        v.animate().alpha(1f).translationY(0f).setDuration(240).setStartDelay(delayMs).start();
    }

    public static void pop(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setScaleX(1.25f);
        v.setScaleY(1.25f);
        v.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
    }

    // Press feedback: card/button sinks slightly while touched, springs
    // back on release. Returning false keeps the normal click working.
    public static void pressFeedback(final View v) {
        if (v == null) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    view.animate().cancel();
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    view.animate().cancel();
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                }
                return false;
            }
        });
    }

    // Heavier card: solid panel + a hairline light edge + soft elevation,
    // so cards read as objects instead of flat rectangles.
    public static GradientDrawable cardBackground(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, 10));
        g.setStroke(dp(c, 1), 0x22FFFFFF);
        return g;
    }

    public static GradientDrawable libraryBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0xFF0A0A0A, 0xFF151515});
    }

    public static GradientDrawable rounded(Context c, int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static TextView pill(Context c, String text) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextColor(0xFFFFFFFF);
        v.setTextSize(13);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setPadding(dp(c, 12), dp(c, 7), dp(c, 12), dp(c, 7));
        v.setBackground(rounded(c, 0x33FFFFFF, 18));
        v.setClickable(true);
        return v;
    }

    public static ImageView iconBtn(Context c, int type, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(new Icon(type, 0xFFFFFFFF, dp(c, sizeDp), false));
        int p = dp(c, 8);
        v.setPadding(p, p, p, p);
        v.setBackground(rounded(c, 0x33FFFFFF, 20));
        v.setClickable(true);
        return v;
    }

    public static boolean isFav(SharedPreferences p, long id) {
        return ("," + p.getString("favs", "") + ",").contains("," + id + ",");
    }

    public static boolean toggleFav(SharedPreferences p, long id) {
        boolean fav = isFav(p, id);
        StringBuilder sb = new StringBuilder();
        for (String s : p.getString("favs", "").split(",")) {
            if (s.length() == 0 || s.equals(String.valueOf(id))) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        if (!fav) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        p.edit().putString("favs", sb.toString()).apply();
        return !fav;
    }
}
