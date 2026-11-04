package com.taryo.appalarm;

import android.content.Context;
import android.content.SharedPreferences;

/** App-wide preferences (currently just how long "5分後に再通知" waits). */
public final class AppSettings {

    private static final String PREFS = "appalarm_settings";
    private static final String KEY_SNOOZE_MINUTES = "snooze_minutes";
    private static final String KEY_MAX_SNOOZE = "max_snooze";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_DYNAMIC = "dynamic_color";
    private static final String KEY_FADE = "fade_enabled";
    private static final String KEY_FADE_SECONDS = "fade_seconds";
    private static final String KEY_HISTORY = "history_enabled";

    public static final int[] FADE_CHOICES = {5, 10, 20, 30};

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int DEFAULT_SNOOZE_MINUTES = 5;
    public static final int[] SNOOZE_CHOICES = {5, 10, 15, 30, 60};

    /** 0 means "no limit"; {@link #SNOOZE_NONE} disables the snooze button entirely. */
    public static final int DEFAULT_MAX_SNOOZE = 3;
    public static final int SNOOZE_NONE = -2;
    public static final int SNOOZE_UNLIMITED = 0;
    public static final int[] SNOOZE_LIMIT_CHOICES = {1, 2, 3, 5, SNOOZE_UNLIMITED, SNOOZE_NONE};

    private AppSettings() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int snoozeMinutes(Context c) {
        int v = prefs(c).getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES);
        if (v < 1) v = DEFAULT_SNOOZE_MINUTES;
        return v;
    }

    public static void setSnoozeMinutes(Context c, int minutes) {
        prefs(c).edit().putInt(KEY_SNOOZE_MINUTES, minutes).apply();
    }

    public static int maxSnooze(Context c) {
        return prefs(c).getInt(KEY_MAX_SNOOZE, DEFAULT_MAX_SNOOZE);
    }

    public static void setMaxSnooze(Context c, int value) {
        prefs(c).edit().putInt(KEY_MAX_SNOOZE, value).apply();
    }

    public static int themeMode(Context c) {
        return prefs(c).getInt(KEY_THEME, THEME_SYSTEM);
    }

    public static void setThemeMode(Context c, int mode) {
        prefs(c).edit().putInt(KEY_THEME, mode).apply();
    }

    /** Material You colours from the system palette (Android 12+). */
    public static boolean useDynamicColor(Context c) {
        return prefs(c).getBoolean(KEY_DYNAMIC, true);
    }

    public static void setUseDynamicColor(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_DYNAMIC, on).apply();
    }

    public static boolean fadeEnabled(Context c) {
        return prefs(c).getBoolean(KEY_FADE, true);
    }

    public static void setFadeEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_FADE, on).apply();
    }

    public static int fadeSeconds(Context c) {
        int v = prefs(c).getInt(KEY_FADE_SECONDS, 10);
        return v < 1 ? 10 : v;
    }

    public static void setFadeSeconds(Context c, int seconds) {
        prefs(c).edit().putInt(KEY_FADE_SECONDS, seconds).apply();
    }

    public static boolean historyEnabled(Context c) {
        return prefs(c).getBoolean(KEY_HISTORY, true);
    }

    public static void setHistoryEnabled(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_HISTORY, on).apply();
    }
}
