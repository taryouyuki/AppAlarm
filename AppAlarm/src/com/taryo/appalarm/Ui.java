package com.taryo.appalarm;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;

/**
 * Colours for the whole app.
 *
 * The palette is resolved once per activity from the theme preference (system / light /
 * dark) and, on Android 12+, from the system's Material You accent colour.
 */
public final class Ui {

    public static int BG = 0xFFF4F5F7;
    public static int CARD = 0xFFFFFFFF;
    public static int TEXT = 0xFF111827;
    public static int MUTED = 0xFF6B7280;
    public static int ACCENT = 0xFF2563EB;
    public static int ACCENT_SOFT = 0xFFDBEAFE;
    public static int BORDER = 0xFFD1D5DB;
    public static boolean dark = false;

    private Ui() {
    }

    public static boolean isDark(Context c) {
        int mode = AppSettings.themeMode(c);
        if (mode == AppSettings.THEME_DARK) return true;
        if (mode == AppSettings.THEME_LIGHT) return false;
        int uiMode = c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Applies the palette. Call before building any view. */
    public static void applyTheme(Context c) {
        dark = isDark(c);
        int accent = 0;
        if (Build.VERSION.SDK_INT >= 31 && AppSettings.useDynamicColor(c)) {
            accent = systemColor(c, "system_accent1_500", 0);
        }
        if (accent == 0) accent = 0xFF2563EB;

        if (dark) {
            BG = 0xFF111418;
            CARD = 0xFF1B1F24;
            TEXT = 0xFFF1F3F6;
            MUTED = 0xFF9AA3AF;
            BORDER = 0xFF2C323A;
            ACCENT = Build.VERSION.SDK_INT >= 31 && AppSettings.useDynamicColor(c)
                    ? lighten(systemColor(c, "system_accent1_300", accent)) : 0xFF7FA6F5;
            ACCENT_SOFT = blend(ACCENT, CARD, 0.22f);
        } else {
            BG = 0xFFF4F5F7;
            CARD = 0xFFFFFFFF;
            TEXT = 0xFF111827;
            MUTED = 0xFF6B7280;
            BORDER = 0xFFD1D5DB;
            ACCENT = accent;
            ACCENT_SOFT = blend(ACCENT, 0xFFFFFFFF, 0.14f);
        }
    }

    /** Applies the palette and makes the system bars match. */
    public static void applyTheme(Activity a) {
        applyTheme((Context) a);
        Window w = a.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(BG));
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        View decor = w.getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private static int systemColor(Context c, String name, int fallback) {
        try {
            int id = c.getResources().getIdentifier(name, "color", "android");
            if (id == 0) return fallback;
            return c.getResources().getColor(id, c.getTheme());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static int lighten(int color) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * 1.25));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * 1.25));
        int b = Math.min(255, (int) ((color & 0xFF) * 1.25));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Blends {@code color} over {@code base} with the given alpha. */
    public static int blend(int color, int base, float alpha) {
        int r = (int) (((color >> 16) & 0xFF) * alpha + ((base >> 16) & 0xFF) * (1 - alpha));
        int g = (int) (((color >> 8) & 0xFF) * alpha + ((base >> 8) & 0xFF) * (1 - alpha));
        int b = (int) ((color & 0xFF) * alpha + (base & 0xFF) * (1 - alpha));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable roundedStroke(int color, int radiusPx, int strokeColor, int strokeWidthPx) {
        GradientDrawable g = rounded(color, radiusPx);
        g.setStroke(strokeWidthPx, strokeColor);
        return g;
    }

    public static int sp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics()));
    }
}
