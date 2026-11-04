package com.taryo.appalarm;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Persists alarms as a JSON array in SharedPreferences. */
public class AlarmStore {
    private static final String PREFS = "appalarm";
    private static final String KEY = "items";

    private final SharedPreferences prefs;
    private final Context context;

    public AlarmStore(Context c) {
        context = c.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Raw JSON array, used for backup/restore. */
    public String exportJson() {
        return prefs.getString(KEY, "[]");
    }

    /** Replaces every alarm with the given JSON array. Returns false when it is invalid. */
    public boolean importJson(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                if (!(arr.get(i) instanceof JSONObject)) return false;
            }
            prefs.edit().putString(KEY, arr.toString()).apply();
            WidgetProvider.refresh(context);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public List<AlarmEntry> all() {
        List<AlarmEntry> out = new ArrayList<>();
        String raw = prefs.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(AlarmEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, new Comparator<AlarmEntry>() {
            @Override
            public int compare(AlarmEntry a, AlarmEntry b) {
                if (a.hour != b.hour) return a.hour - b.hour;
                return a.minute - b.minute;
            }
        });
        return out;
    }

    public AlarmEntry find(long id) {
        for (AlarmEntry e : all()) {
            if (e.id == id) return e;
        }
        return null;
    }

    public void save(List<AlarmEntry> items) {
        JSONArray arr = new JSONArray();
        for (AlarmEntry e : items) arr.put(e.toJson());
        prefs.edit().putString(KEY, arr.toString()).apply();
        WidgetProvider.refresh(context);
    }

    public void upsert(AlarmEntry entry) {
        List<AlarmEntry> items = all();
        boolean replaced = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == entry.id) {
                items.set(i, entry);
                replaced = true;
                break;
            }
        }
        if (!replaced) items.add(entry);
        save(items);
    }

    public void delete(long id) {
        List<AlarmEntry> items = all();
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).id == id) items.remove(i);
        }
        save(items);
    }
}
