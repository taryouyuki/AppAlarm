package com.taryo.appalarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Receives an alarm (or a snooze), shows the prompt, then re-arms the next occurrence. */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AppAlarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        long id = intent.getLongExtra("id", -1L);
        Log.i(TAG, "onReceive action=" + action + " id=" + id);

        if (AlarmScheduler.ACTION_DISMISS.equals(action)) {
            NotificationHelper.cancel(context, id);
            return;
        }

        AlarmStore store = new AlarmStore(context);
        AlarmEntry entry = store.find(id);
        if (entry == null) {
            Log.w(TAG, "unknown alarm id " + id);
            return;
        }

        if (AlarmScheduler.ACTION_SNOOZE_REQUEST.equals(action)) {
            // "○○分後に再通知": dismiss now, fire again later.
            int used = intent.getIntExtra("snooze", 1);
            NotificationHelper.cancel(context, id);
            AlarmScheduler.snooze(context, id, used);
            return;
        }

        if (AlarmScheduler.ACTION_SNOOZE.equals(action)) {
            // The wait is over: repeat the same prompt; the main schedule is untouched.
            int used = intent.getIntExtra("snooze", 0);
            NotificationHelper.show(context, entry, used);
            return;
        }

        if (!entry.enabled) {
            Log.i(TAG, "alarm disabled, ignoring");
            return;
        }

        if (entry.alarmMode) {
            // Ring like an alarm clock: the notification carries a full-screen intent
            // that raises WakeActivity (stop / snooze) even when the screen is locked.
            NotificationHelper.show(context, entry);
            if (canDrawOverlays(context) || !NotificationHelper.canUseFullScreen(context)) {
                // Start it ourselves so the alarm screen also appears while the phone is
                // unlocked (the system downgrades a full-screen intent to a heads-up then).
                Intent wake = new Intent(context, WakeActivity.class);
                wake.putExtra("id", entry.id);
                wake.putExtra("alarm", true);
                wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
                try {
                    context.startActivity(wake);
                } catch (Exception ex) {
                    Log.w(TAG, "alarm screen start failed: " + ex);
                }
            }
            Log.i(TAG, "alarm ringing for " + entry.pkg);
            advance(context, store, entry);
            return;
        }

        if (entry.autoOpen) {
            if (!canDrawOverlays(context)) {
                Log.w(TAG, "auto-open needs the overlay permission; falling back to a prompt");
            } else {
                // Always go through WakeActivity: it turns the screen on and plays the
                // alarm sound, which a plain startActivity from a receiver cannot do.
                Intent wake = new Intent(context, WakeActivity.class);
                wake.putExtra("id", entry.id);
                wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
                context.startActivity(wake);
                HistoryStore.record(context, entry, HistoryStore.ACTION_AUTO);
                Log.i(TAG, "wake activity for " + entry.pkg);
                advance(context, store, entry);
                return;
            }
        }

        NotificationHelper.show(context, entry);
        HistoryStore.record(context, entry, HistoryStore.ACTION_NOTIFY);
        advance(context, store, entry);
    }

    /** One-shot alarms switch themselves off; repeating ones are re-armed. */
    private static void advance(Context context, AlarmStore store, AlarmEntry entry) {
        if (entry.mode == AlarmEntry.MODE_ONCE || entry.mode == AlarmEntry.MODE_DATE) {
            entry.enabled = false;
            store.upsert(entry);
        } else {
            AlarmScheduler.schedule(context, entry);
        }
    }

    /** Display-over-other-apps granted: the documented exemption for background activity starts. */
    private static boolean canDrawOverlays(Context c) {
        return android.provider.Settings.canDrawOverlays(c);
    }

    /** Opens the target app directly. Returns false when it cannot be resolved. */
    private static boolean launchTarget(Context c, AlarmEntry e) {
        try {
            Intent launch = c.getPackageManager().getLaunchIntentForPackage(e.pkg);
            if (launch == null) {
                Log.w(TAG, "no launch intent for " + e.pkg);
                return false;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(launch);
            Log.i(TAG, "auto-opened " + e.pkg);
            return true;
        } catch (Exception ex) {
            Log.w(TAG, "auto-open failed: " + ex);
            return false;
        }
    }
}
