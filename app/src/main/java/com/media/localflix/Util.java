package com.media.localflix;

import android.content.Context;
import android.content.SharedPreferences;
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
