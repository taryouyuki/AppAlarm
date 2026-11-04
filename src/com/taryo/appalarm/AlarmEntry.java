package com.taryo.appalarm;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One scheduled "open this app?" alarm. */
public class AlarmEntry {
    public static final int MODE_ONCE = 0;
    public static final int MODE_DAILY = 1;
    public static final int MODE_WEEKLY = 2;
    public static final int MODE_DATE = 3;
    /** a different time for each weekday */
    public static final int MODE_PERDAY = 4;

    public static final int SOUND_DEFAULT = 0;
    public static final int SOUND_SILENT = 1;
    public static final int SOUND_CUSTOM = 2;
    public static final int SOUND_VOICE = 3;

    public long id;
    public int hour;
    public int minute;
    public String pkg = "";
    public String label = "";
    public int mode = MODE_DAILY;
    /** bit 0 = Monday ... bit 6 = Sunday */
    public int days = 0x7f;
    public boolean skipHolidays = false;
    public boolean enabled = true;
    /** true = open the target app without asking. */
    public boolean autoOpen = false;
    /** only used when mode == MODE_DATE */
    public int year;
    public int month;
    public int day;
    /** per-weekday times (index 0 = Monday); -1 means "no alarm that day" */
    public int[] perDayHour = {-1, -1, -1, -1, -1, -1, -1};
    public int[] perDayMinute = {0, 0, 0, 0, 0, 0, 0};
    /** ring like an alarm clock (full-screen stop/snooze) instead of a quiet notification */
    public boolean alarmMode = false;
    /** free-form tag used to group alarms in the list ("" = none) */
    public String group = "";
    /** ringtone used while the alarm screen is ringing; empty = system default alarm sound */
    public String alarmSoundUri = "";
    /** minutes until the snooze re-notification; -1 = follow the app setting */
    public int snoozeMinutes = -1;
    /** how many snoozes are allowed; -1 = follow the app setting, 0 = unlimited */
    public int maxSnooze = -1;

    // notification style
    public int soundMode = SOUND_DEFAULT;
    public String soundUri = "";
    public String voiceText = "アプリを開く時間です";

    public String timeText() {
        return String.format(java.util.Locale.JAPAN, "%02d:%02d", hour, minute);
    }

    /** The time this alarm actually fires on the given day (per-weekday mode varies). */
    public String timeTextFor(java.time.LocalDate day) {
        if (mode == MODE_PERDAY) {
            int idx = day.getDayOfWeek().getValue() - 1;
            if (perDayHour[idx] >= 0) {
                return String.format(java.util.Locale.JAPAN, "%02d:%02d",
                        perDayHour[idx], perDayMinute[idx]);
            }
        }
        return timeText();
    }

    public String daySummary() {
        if (mode == MODE_ONCE) return "1回だけ";
        if (mode == MODE_DATE) {
            if (year <= 0) return "日付が未設定";
            return String.format(java.util.Locale.JAPAN, "%d/%02d/%02d のみ", year, month, day);
        }
        if (mode == MODE_PERDAY) return perDaySummary();
        if (mode == MODE_DAILY) return skipHolidays ? "毎日（祝日を除く）" : "毎日";
        StringBuilder sb = new StringBuilder();
        String[] names = {"月", "火", "水", "木", "金", "土", "日"};
        for (int i = 0; i < 7; i++) {
            if ((days & (1 << i)) != 0) sb.append(names[i]);
        }
        String base = sb.toString() + "曜";
        return skipHolidays ? base + "（祝日を除く）" : base;
    }

    /** "月火水木金 07:00・土日 09:00" style summary. */
    private String perDaySummary() {
        String[] names = {"月", "火", "水", "木", "金", "土", "日"};
        StringBuilder out = new StringBuilder();
        boolean[] used = new boolean[7];
        for (int i = 0; i < 7; i++) {
            if (perDayHour[i] < 0) continue;
            if (used[i]) continue;
            StringBuilder days = new StringBuilder();
            for (int j = i; j < 7; j++) {
                if (perDayHour[j] == perDayHour[i] && perDayMinute[j] == perDayMinute[i]) {
                    days.append(names[j]);
                    used[j] = true;
                }
            }
            if (out.length() > 0) out.append("・");
            out.append(days).append(" ");
            out.append(String.format(java.util.Locale.JAPAN, "%02d:%02d", perDayHour[i], perDayMinute[i]));
        }
        if (out.length() == 0) return "曜日ごとの時刻（未設定）";
        return out.toString();
    }

    public String soundSummary() {
        switch (soundMode) {
            case SOUND_SILENT:
                return "無音";
            case SOUND_CUSTOM:
                return soundUri.isEmpty() ? "通知音（未選択）" : "通知音";
            case SOUND_VOICE:
                return "読み上げ";
            default:
                return "標準の通知音";
        }
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("h", hour);
            o.put("m", minute);
            o.put("pkg", pkg == null ? "" : pkg);
            o.put("label", label == null ? "" : label);
            o.put("mode", mode);
            o.put("days", days);
            o.put("hol", skipHolidays);
            o.put("en", enabled);
            o.put("auto", autoOpen);
            o.put("y", year);
            o.put("mo", month);
            o.put("d", day);
            o.put("alarm", alarmMode);
            o.put("grp", group == null ? "" : group);
            o.put("asuri", alarmSoundUri == null ? "" : alarmSoundUri);
            o.put("smin", snoozeMinutes);
            o.put("smax", maxSnooze);
            JSONArray ph = new JSONArray();
            JSONArray pm = new JSONArray();
            for (int i = 0; i < 7; i++) {
                ph.put(perDayHour[i]);
                pm.put(perDayMinute[i]);
            }
            o.put("ph", ph);
            o.put("pm", pm);
            o.put("smode", soundMode);
            o.put("suri", soundUri == null ? "" : soundUri);
            o.put("vtext", voiceText == null ? "" : voiceText);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static AlarmEntry fromJson(JSONObject o) {
        AlarmEntry e = new AlarmEntry();
        e.id = o.optLong("id", 0L);
        e.hour = o.optInt("h", 7);
        e.minute = o.optInt("m", 0);
        e.pkg = o.optString("pkg", "");
        e.label = o.optString("label", "");
        e.mode = o.optInt("mode", MODE_DAILY);
        e.days = o.optInt("days", 0x7f);
        e.skipHolidays = o.optBoolean("hol", false);
        e.enabled = o.optBoolean("en", true);
        e.autoOpen = o.optBoolean("auto", false);
        e.year = o.optInt("y", 0);
        e.month = o.optInt("mo", 0);
        e.day = o.optInt("d", 0);
        e.alarmMode = o.optBoolean("alarm", o.optBoolean("fs", false));
        e.group = o.optString("grp", "");
        e.alarmSoundUri = o.optString("asuri", "");
        e.snoozeMinutes = o.optInt("smin", -1);
        e.maxSnooze = o.optInt("smax", -1);
        JSONArray ph = o.optJSONArray("ph");
        JSONArray pm = o.optJSONArray("pm");
        if (ph != null && pm != null && ph.length() == 7 && pm.length() == 7) {
            for (int i = 0; i < 7; i++) {
                e.perDayHour[i] = ph.optInt(i, -1);
                e.perDayMinute[i] = pm.optInt(i, 0);
            }
        }
        e.soundMode = o.optInt("smode", SOUND_DEFAULT);
        e.soundUri = o.optString("suri", "");
        e.voiceText = o.optString("vtext", "アプリを開く時間です");
        return e;
    }
}
