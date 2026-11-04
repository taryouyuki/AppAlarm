package com.taryo.appalarm;

import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * Speaks the user's message for the "音声でお知らせ" mode.
 * Runs as a short-lived foreground service so the process is not killed mid-utterance.
 */
public class VoiceService extends Service {

    private static final String TAG = "AppAlarmVoice";
    private static final int FG_ID = 990001;

    private TextToSpeech tts;
    private boolean finished;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String text = intent == null ? null : intent.getStringExtra("text");
        if (text == null || text.trim().isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(FG_ID, NotificationHelper.buildSpeakingNotification(this, text));
        Log.i(TAG, "speaking: " + text);

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TTS init failed: " + status);
                    finish();
                    return;
                }
                int lang = tts.setLanguage(Locale.JAPAN);
                Log.i(TAG, "TTS lang result=" + lang + " (unsupported="
                        + TextToSpeech.LANG_NOT_SUPPORTED + ", missing="
                        + TextToSpeech.LANG_MISSING_DATA + ")");
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault());
                }
                // Play through the notification stream so the hardware volume keys
                // and the notification slider control the volume.
                tts.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build());
                tts.setSpeechRate(0.95f);
                tts.setPitch(1.0f);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        Log.i(TAG, "utterance start");
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        Log.i(TAG, "utterance done");
                        finish();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.w(TAG, "utterance error");
                        finish();
                    }
                });
                int r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "appalarm");
                Log.i(TAG, "speak() returned " + r);
            }
        });

        // Safety net: never hold the foreground slot for long.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 20000L);

        return START_NOT_STICKY;
    }

    private synchronized void finish() {
        if (finished) return;
        finished = true;
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
                tts = null;
            }
        } catch (Exception ignored) {
        }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        try {
            if (tts != null) tts.shutdown();
        } catch (Exception ignored) {
        }
        tts = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
