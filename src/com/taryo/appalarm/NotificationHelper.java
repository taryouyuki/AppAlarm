package com.taryo.appalarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/** Builds the notification (and the alarm screen) for a firing alarm. */
public final class NotificationHelper {

    private static final String CHANNEL_PREFIX = "appalarm_";
    private static final String VOICE_CHANNEL = "appalarm_voice";
    private static final String TAG = "AppAlarm";

    private NotificationHelper() {
    }

    public static void ensureChannel(Context c) {
        voiceChannel(c);
    }

    private static NotificationChannel voiceChannel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = nm.getNotificationChannel(VOICE_CHANNEL);
        if (ch == null) {
            ch = new NotificationChannel(VOICE_CHANNEL, "音声の読み上げ", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("読み上げ中だけ表示される控えめな通知です");
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
        }
        return ch;
    }

    /**
     * Notification channels own their sound on Android 8+, so the channel id is derived
     * from the chosen sound. Alarm-mode channels are silent because the ringing is done
     * by WakeActivity on the alarm stream.
     */
    private static String channelFor(Context c, AlarmEntry e) {
        String key;
        switch (e.soundMode) {
            case AlarmEntry.SOUND_SILENT:
                key = "silent";
                break;
            case AlarmEntry.SOUND_VOICE:
                key = "voice";
                break;
            case AlarmEntry.SOUND_CUSTOM:
                key = "custom:" + e.soundUri;
                break;
            default:
                key = "default";
                break;
        }
        if (e.alarmMode) key = key + ":alarm";
        String id = CHANNEL_PREFIX + Integer.toHexString(key.hashCode());
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(id) != null) return id;

        NotificationChannel ch = new NotificationChannel(id, "アプリ起動の確認", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("指定した時刻に対象アプリを開くか確認します");
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        if (e.alarmMode) {
            ch.setSound(null, null);
            ch.enableVibration(false);
        } else if (e.soundMode == AlarmEntry.SOUND_DEFAULT) {
            ch.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, attrs);
            ch.enableVibration(true);
        } else if (e.soundMode == AlarmEntry.SOUND_CUSTOM && !e.soundUri.isEmpty()) {
            ch.setSound(Uri.parse(e.soundUri), attrs);
            ch.enableVibration(true);
        } else {
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setVibrationPattern(null);
        }
        ch.setShowBadge(true);
        ch.setBypassDnd(false);
        nm.createNotificationChannel(ch);

        for (NotificationChannel other : nm.getNotificationChannels()) {
            String oid = other.getId();
            if (!oid.startsWith(CHANNEL_PREFIX)) continue;
            if (oid.equals(id) || oid.equals(VOICE_CHANNEL)) continue;
            nm.deleteNotificationChannel(oid);
        }
        return id;
    }

    public static void show(Context c, AlarmEntry e, int snoozesUsed) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String channelId = channelFor(c, e);

        int base = (int) (e.id & 0x7fffffff);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;

        Intent launch = c.getPackageManager().getLaunchIntentForPackage(e.pkg);
        PendingIntent openPi = null;
        String title = "「" + e.label + "」を開きますか？";
        String text = e.timeTextFor(java.time.LocalDate.now()) + " のアラーム";

        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            openPi = PendingIntent.getActivity(c, base + 1, launch, flags);
        } else {
            title = "「" + e.label + "」が見つかりません";
            text = "アプリがアンインストールされた可能性があります";
        }

        int limit = AlarmScheduler.maxSnooze(c, e);
        boolean canSnooze = limit != AppSettings.SNOOZE_NONE && (limit <= 0 || snoozesUsed < limit);

        Intent snooze = new Intent(c, AlarmReceiver.class);
        snooze.setAction(AlarmScheduler.ACTION_SNOOZE_REQUEST);
        snooze.putExtra("id", e.id);
        snooze.putExtra("snooze", snoozesUsed + 1);
        PendingIntent snoozePi = PendingIntent.getBroadcast(c, base + 2, snooze, flags);

        Notification.Builder b = new Notification.Builder(c, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .setShowWhen(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(openPi != null ? openPi : snoozePi);

        if (!e.alarmMode && !(e.soundMode == AlarmEntry.SOUND_SILENT
                || e.soundMode == AlarmEntry.SOUND_VOICE)) {
            b.setDefaults(Notification.DEFAULT_VIBRATE);
        }

        // Alarm mode: when the overlay permission is missing the system's full-screen
        // intent is the only way to raise our screen. With the permission the receiver
        // starts WakeActivity directly, which also works while the phone is in use.
        if (e.alarmMode && canUseFullScreen(c) && !Settings.canDrawOverlays(c)) {
            Intent wake = new Intent(c, WakeActivity.class);
            wake.putExtra("id", e.id);
            wake.putExtra("alarm", true);
            wake.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
            PendingIntent wakePi = PendingIntent.getActivity(c, base + 5, wake, flags);
            b.setFullScreenIntent(wakePi, true);
        }

        if (openPi != null) {
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(c, android.R.drawable.ic_menu_view), "開く", openPi).build());
        }
        if (canSnooze) {
            int minutes = e.snoozeMinutes > 0 ? e.snoozeMinutes : AppSettings.snoozeMinutes(c);
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(c, android.R.drawable.ic_popup_reminder),
                    minutes + "分後に再通知", snoozePi).build());
        } else {
            b.setSubText(limit == AppSettings.SNOOZE_NONE ? "再通知はオフです" : "再通知の上限に達しました");
        }

        nm.notify(base, b.build());

        if (e.soundMode == AlarmEntry.SOUND_VOICE) {
            startVoice(c, e.voiceText);
        }
    }

    public static void show(Context c, AlarmEntry e) {
        show(c, e, 0);
    }

    public static void cancel(Context c, long id) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel((int) (id & 0x7fffffff));
    }

    private static void startVoice(Context c, String text) {
        if (text == null || text.trim().isEmpty()) return;
        Intent i = new Intent(c, VoiceService.class).putExtra("text", text);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ex) {
            android.util.Log.w(TAG, "voice start failed: " + ex);
        }
    }

    public static Notification buildSpeakingNotification(Context c, String text) {
        voiceChannel(c);
        return new Notification.Builder(c, VOICE_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("読み上げ中")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    public static boolean canUseFullScreen(Context c) {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.canUseFullScreenIntent();
    }

    public static boolean canPostNotifications(Context c) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == PackageManager.PERMISSION_GRANTED;
    }
}
