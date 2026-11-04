package com.taryo.appalarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Schedules and cancels the exact alarms that fire our notifications. */
public final class AlarmScheduler {

    public static final String ACTION_FIRE = "com.taryo.appalarm.FIRE";
    public static final String ACTION_SNOOZE = "com.taryo.appalarm.SNOOZE";
    public static final String ACTION_SNOOZE_REQUEST = "com.taryo.appalarm.SNOOZE_REQUEST";
    public static final String ACTION_DISMISS = "com.taryo.appalarm.DISMISS";

    /** How long the "再通知" button waits (per-alarm override, else the app setting). */
    public static long snoozeMillis(Context c, AlarmEntry e) {
        int minutes = e != null && e.snoozeMinutes > 0
                ? e.snoozeMinutes : AppSettings.snoozeMinutes(c);
        return minutes * 60L * 1000L;
    }

    public static int maxSnooze(Context c, AlarmEntry e) {
        return e != null && e.maxSnooze >= 0 ? e.maxSnooze : AppSettings.maxSnooze(c);
    }

    private AlarmScheduler() {
    }

    private static int requestCode(long id) {
        return (int) (id & 0x7fffffff);
    }

    private static PendingIntent pending(Context c, long id, String action, int offset) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(action);
        i.putExtra("id", id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, requestCode(id) + offset, i, flags);
    }

    public static PendingIntent fireIntent(Context c, long id) {
        return pending(c, id, ACTION_FIRE, 0);
    }

    public static void cancel(Context c, long id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(fireIntent(c, id));
        am.cancel(pending(c, id, ACTION_SNOOZE_REQUEST, 2));
        am.cancel(pending(c, id, ACTION_SNOOZE, 3));
    }

    /** PendingIntent behind the notification's "5分後に再通知" button. */
    public static PendingIntent snoozeRequestIntent(Context c, long id) {
        return pending(c, id, ACTION_SNOOZE_REQUEST, 2);
    }

    public static long schedule(Context c, AlarmEntry e) {
        cancel(c, e.id);
        if (!e.enabled) return 0L;
        long at = NextTime.nextTrigger(e, System.currentTimeMillis());
        if (at <= 0L) return 0L;
        set(c, at, pending(c, e.id, ACTION_FIRE, 0));
        return at;
    }

    /** One-off re-notification a few minutes later; the normal schedule is untouched. */
    public static void snooze(Context c, long id, int snoozesUsed) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        AlarmEntry entry = new AlarmStore(c).find(id);
        long at = System.currentTimeMillis() + snoozeMillis(c, entry);
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION_SNOOZE);
        i.putExtra("id", id);
        i.putExtra("snooze", snoozesUsed);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(c, requestCode(id) + 3, i, flags);
        set(c, at, pi);
    }

    private static void set(Context c, long at, PendingIntent pi) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (SecurityException ex) {
            // Exact alarms not permitted: fall back to an inexact alarm.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static boolean canScheduleExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    public static void rescheduleAll(Context c) {
        AlarmStore store = new AlarmStore(c);
        for (AlarmEntry e : store.all()) {
            if (e.enabled) schedule(c, e);
        }
    }
}
