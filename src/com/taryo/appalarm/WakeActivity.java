package com.taryo.appalarm;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * アラーム鳴動画面。画面を点灯し、アラーム音をループ再生して「停止」「スヌーズ」を出す。
 * 停止すると対象アプリを起動する（ロック中は解除後にアプリが見える）。
 */
public class WakeActivity extends Activity {

    private AlarmEntry entry;
    private MediaPlayer player;
    private Vibrator vibrator;
    private boolean alarmMode;
    private int snoozesUsed;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable fadeStep;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        long id = getIntent().getLongExtra("id", -1L);
        snoozesUsed = getIntent().getIntExtra("snooze", 0);
        entry = new AlarmStore(this).find(id);
        if (entry == null) {
            finish();
            return;
        }
        alarmMode = getIntent().getBooleanExtra("alarm", entry.alarmMode);

        wakeScreen();
        if (alarmMode) {
            startRinging();
            showAlarmUi();
        } else if (entry.autoOpen) {
            tryOpen();
        } else {
            showPrompt();
        }
    }

    /** Forces the display on when the alarm fires while the phone is asleep. */
    @SuppressWarnings("deprecation")
    private void wakeScreen() {
        try {
            android.os.PowerManager pm =
                    (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null || pm.isInteractive()) return;
            android.os.PowerManager.WakeLock wl = pm.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                            | android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | android.os.PowerManager.ON_AFTER_RELEASE, "AppAlarm:wake");
            wl.acquire(10000L);
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ ringing

    private void startRinging() {
        if (entry.soundMode != AlarmEntry.SOUND_SILENT) {
            Uri uri;
            if (!entry.alarmSoundUri.isEmpty()) {
                uri = Uri.parse(entry.alarmSoundUri);
            } else if (entry.soundMode == AlarmEntry.SOUND_CUSTOM && !entry.soundUri.isEmpty()) {
                uri = Uri.parse(entry.soundUri);
            } else {
                uri = Settings.System.DEFAULT_ALARM_ALERT_URI;
            }
            try {
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                player.setDataSource(this, uri);
                player.setLooping(true);
                player.prepare();
                if (AppSettings.fadeEnabled(this)) {
                    player.setVolume(0.1f, 0.1f);
                }
                player.start();
                if (AppSettings.fadeEnabled(this)) startFade();
            } catch (Exception ex) {
                player = null;
            }
        }

        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = {0, 700, 500};
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                } else {
                    vibrator.vibrate(pattern, 0);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Raises the alarm volume gradually so it does not blast at full volume. */
    private void startFade() {
        final int seconds = AppSettings.fadeSeconds(this);
        final long start = System.currentTimeMillis();
        fadeStep = new Runnable() {
            @Override
            public void run() {
                if (player == null) return;
                float progress = (System.currentTimeMillis() - start) / (seconds * 1000f);
                float volume = Math.min(1f, 0.1f + 0.9f * progress);
                try {
                    player.setVolume(volume, volume);
                } catch (Exception ignored) {
                }
                if (progress < 1f) handler.postDelayed(this, 400L);
            }
        };
        handler.postDelayed(fadeStep, 400L);
    }

    private void stopRinging() {
        try {
            if (fadeStep != null) {
                handler.removeCallbacks(fadeStep);
                fadeStep = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (vibrator != null) {
                vibrator.cancel();
                vibrator = null;
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ UI

    private void showAlarmUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(0xF5121722);
        root.setPadding(dp(28), dp(90), dp(28), dp(40));

        TextView time = new TextView(this);
        time.setText(entry.timeTextFor(java.time.LocalDate.now()));
        time.setTextSize(64);
        time.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        time.setTextColor(0xFFFFFFFF);
        time.setGravity(Gravity.CENTER);
        root.addView(time);

        TextView label = new TextView(this);
        label.setText("「" + entry.label + "」を開きますか？");
        label.setTextSize(18);
        label.setTextColor(0xFFD5DAE3);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.topMargin = dp(12);
        root.addView(label, labelLp);

        LinearLayout spacer = new LinearLayout(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1f));
        root.addView(spacer);

        Button stop = new Button(this);
        stop.setText("停止");
        stop.setAllCaps(false);
        stop.setTextSize(20);
        stop.setTextColor(0xFF111827);
        stop.setBackground(Ui.rounded(0xFFFFFFFF, dp(18)));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRinging();
                HistoryStore.record(WakeActivity.this, entry, HistoryStore.ACTION_STOP);
                openTarget();
            }
        });
        root.addView(stop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        Button snooze = new Button(this);
        snooze.setText("スヌーズ（" + snoozeMinutes() + "分後）");
        snooze.setAllCaps(false);
        snooze.setTextSize(16);
        snooze.setTextColor(0xFFD5DAE3);
        snooze.setBackground(Ui.roundedStroke(0x00000000, dp(18), 0xFF4A5468, dp(1)));
        LinearLayout.LayoutParams snoozeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64));
        snoozeLp.topMargin = dp(12);
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRinging();
                HistoryStore.record(WakeActivity.this, entry, HistoryStore.ACTION_SNOOZE);
                AlarmScheduler.snooze(WakeActivity.this, entry.id, snoozesUsed + 1);
                NotificationHelper.cancel(WakeActivity.this, entry.id);
                finish();
            }
        });
        boolean snoozeAllowed = AlarmScheduler.maxSnooze(this, entry) != AppSettings.SNOOZE_NONE;
        snooze.setVisibility(snoozeAllowed ? View.VISIBLE : View.GONE);
        root.addView(snooze, snoozeLp);

        TextView hint = new TextView(this);
        hint.setText("停止するとアプリを開きます（ロック中は解除後）");
        hint.setTextSize(12);
        hint.setTextColor(0xFF8A93A6);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(16);
        root.addView(hint, hintLp);

        setContentView(root);
    }

    private int snoozeMinutes() {
        return entry.snoozeMinutes > 0 ? entry.snoozeMinutes : AppSettings.snoozeMinutes(this);
    }

    private void showPrompt() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xF2121722);
        root.setPadding(dp(28), dp(40), dp(28), dp(40));

        TextView title = new TextView(this);
        title.setText("「" + entry.label + "」を開きますか？");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("時刻になりました");
        sub.setTextSize(14);
        sub.setTextColor(0xFFB9C0CC);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(12);
        root.addView(sub, subLp);

        Button open = new Button(this);
        open.setText("開く");
        open.setAllCaps(false);
        open.setTextSize(16);
        open.setTextColor(0xFF111827);
        open.setBackground(Ui.rounded(0xFFFFFFFF, dp(14)));
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        openLp.topMargin = dp(28);
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTarget();
            }
        });
        root.addView(open, openLp);

        Button later = new Button(this);
        later.setText("あとで");
        later.setAllCaps(false);
        later.setTextSize(14);
        later.setTextColor(0xFFB9C0CC);
        later.setBackgroundColor(0x00000000);
        LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        laterLp.topMargin = dp(8);
        later.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        root.addView(later, laterLp);

        setContentView(root);
    }

    // ------------------------------------------------------------------ actions

    private void tryOpen() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isKeyguardLocked()) {
            openTarget();
            return;
        }
        km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override
            public void onDismissSucceeded() {
                openTarget();
            }

            @Override
            public void onDismissCancelled() {
                finish();
            }

            @Override
            public void onDismissError() {
                finish();
            }
        });
    }

    private void openTarget() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(entry.pkg);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(launch);
            }
        } catch (Exception ignored) {
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        stopRinging();
        super.onDestroy();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
