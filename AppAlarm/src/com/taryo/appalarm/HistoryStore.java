package com.taryo.appalarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Keeps a short log of what happened each time an alarm fired. */
public final class HistoryStore {

    private static final String PREFS = "appalarm_history";
    private static final String KEY = "items";
    private static final int MAX = 300;

    public static final String ACTION_STOP = "stop";
    public static final String ACTION_SNOOZE = "snooze";
    public static final String ACTION_AUTO = "auto";
    public static final String ACTION_NOTIFY = "notify";

    private HistoryStore() {
    }

    public static class Record {
        public long time;
        public String label = "";
        public String action = ACTION_NOTIFY;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void record(Context c, AlarmEntry e, String action) {
        if (!AppSettings.historyEnabled(c)) return;
        try {
            SharedPreferences p = prefs(c);
            JSONArray arr = new JSONArray(p.getString(KEY, "[]"));
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("l", e == null ? "" : e.label);
            o.put("a", action);
            JSONArray out = new JSONArray();
            out.put(o);
            for (int i = 0; i < arr.length() && i < MAX - 1; i++) out.put(arr.get(i));
            p.edit().putString(KEY, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static List<Record> recent(Context c) {
        List<Record> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Record r = new Record();
                r.time = o.optLong("t", 0L);
                r.label = o.optString("l", "");
                r.action = o.optString("a", ACTION_NOTIFY);
                out.add(r);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static int[] counts(Context c) {
        int stop = 0, snooze = 0, auto = 0, notify = 0;
        for (Record r : recent(c)) {
            if (ACTION_STOP.equals(r.action)) stop++;
            else if (ACTION_SNOOZE.equals(r.action)) snooze++;
            else if (ACTION_AUTO.equals(r.action)) auto++;
            else notify++;
        }
        return new int[]{stop, snooze, auto, notify};
    }

    public static void clear(Context c) {
        prefs(c).edit().putString(KEY, "[]").apply();
    }

    public static String formatTime(long millis) {
        return new SimpleDateFormat("M/d HH:mm", Locale.JAPAN).format(new Date(millis));
    }

    public static String actionLabel(String action) {
        if (ACTION_STOP.equals(action)) return "停止";
        if (ACTION_SNOOZE.equals(action)) return "スヌーズ";
        if (ACTION_AUTO.equals(action)) return "自動で起動";
        return "通知";
    }
}
