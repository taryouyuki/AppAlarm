package com.taryo.appalarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Home-screen widget showing the next alarm. Tapping it opens the app. */
public class WidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        updateAll(context, manager, appWidgetIds);
    }

    /** Called whenever the alarm list changes. */
    public static void refresh(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        ComponentName cn = new ComponentName(c, WidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(cn);
        if (ids != null && ids.length > 0) updateAll(c, manager, ids);
    }

    private static void updateAll(Context c, AppWidgetManager manager, int[] ids) {
        AlarmStore store = new AlarmStore(c);
        long now = System.currentTimeMillis();
        AlarmEntry best = null;
        long bestAt = 0L;
        for (AlarmEntry e : store.all()) {
            if (!e.enabled) continue;
            long at = NextTime.nextTrigger(e, now);
            if (at <= 0L) continue;
            if (best == null || at < bestAt) {
                best = e;
                bestAt = at;
            }
        }
        for (int id : ids) {
            manager.updateAppWidget(id, build(c, best, bestAt));
        }
    }

    private static RemoteViews build(Context c, AlarmEntry e, long at) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_alarm);

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 0, open, flags));

        if (e == null) {
            v.setTextViewText(R.id.widget_caption, "次のアプリ");
            v.setTextViewText(R.id.widget_time, "--:--");
            v.setTextViewText(R.id.widget_label, "アラームなし");
            return v;
        }

        LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault());
        boolean today = t.toLocalDate().equals(java.time.LocalDate.now());
        String caption = today ? "今日" : (t.getMonthValue() + "/" + t.getDayOfMonth());
        v.setTextViewText(R.id.widget_caption, "次のアプリ ・ " + caption);
        v.setTextViewText(R.id.widget_time,
                String.format(java.util.Locale.JAPAN, "%02d:%02d", t.getHour(), t.getMinute()));
        v.setTextViewText(R.id.widget_label, e.label);
        return v;
    }
}
