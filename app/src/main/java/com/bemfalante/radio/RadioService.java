package com.bemfalante.radio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RadioService extends Service implements AudioManager.OnAudioFocusChangeListener {

    private static final String CHANNEL_ID = "RadioServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String STREAM_URL = "https://stream.zeno.fm/f718a010rxhvv";

    public static final String ACTION_PLAY = "PLAY";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_STOP = "STOP";

    private MediaPlayer mediaPlayer;
    private final IBinder binder = new RadioBinder();
    private boolean isPlaying = false;
    private boolean isPreparing = false;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private WifiManager.WifiLock wifiLock;
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private boolean shouldRetry = false;

    public class RadioBinder extends Binder {
        RadioService getService() {
            return RadioService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Toast.makeText(this, "Service Criado", Toast.LENGTH_SHORT).show();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        wifiLock = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RadioService:WifiLock");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopRadio();
                stopSelf();
            } else if (ACTION_PAUSE.equals(action)) {
                pauseRadio();
            } else if (ACTION_PLAY.equals(action)) {
                playRadio();
            }
        }
        return START_STICKY;
    }

    public void playRadio() {
        shouldRetry = true;
        retryHandler.removeCallbacksAndMessages(null);

        if (isPlaying || isPreparing) {
            Toast.makeText(this, "Já está tocando ou carregando", Toast.LENGTH_SHORT).show();
            return;
        }

        if (requestAudioFocus()) {
            Toast.makeText(this, "Foco de áudio obtido", Toast.LENGTH_SHORT).show();

            isPreparing = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, getNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, getNotification());
            }

            releaseMediaPlayer();

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
            try {
                Toast.makeText(this, "Conectando ao Zeno...", Toast.LENGTH_SHORT).show();
                mediaPlayer.setDataSource(STREAM_URL);
                mediaPlayer.setOnPreparedListener(mp -> {
                    Toast.makeText(this, "Conectado!", Toast.LENGTH_SHORT).show();
                    isPreparing = false;
                    try {
                        mp.start();
                        isPlaying = true;
                        if (!wifiLock.isHeld()) wifiLock.acquire();
                        updateNotification();
                    } catch (Exception e) {
                        Toast.makeText(this, "Erro ao iniciar áudio", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                        handleRetry();
                    }
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    Toast.makeText(this, "Erro MediaPlayer: " + what, Toast.LENGTH_SHORT).show();
                    handleRetry();
                    return true;
                });
                mediaPlayer.setOnCompletionListener(mp -> {
                    Toast.makeText(this, "Streaming concluído", Toast.LENGTH_SHORT).show();
                    handleRetry();
                });
                mediaPlayer.prepareAsync();
            } catch (Exception e) {
                Toast.makeText(this, "Falha no DataSource", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                handleRetry();
            }
        } else {
            Toast.makeText(this, "Falha no Foco de áudio", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleRetry() {
        Toast.makeText(this, "Agendando reconexão em 10s...", Toast.LENGTH_SHORT).show();
        isPreparing = false;
        isPlaying = false;
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        releaseMediaPlayer();
        updateNotification();
        if (shouldRetry) {
            retryHandler.postDelayed(this::playRadio, 10000);
        }
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setOnPreparedListener(null);
                mediaPlayer.setOnErrorListener(null);
                mediaPlayer.setOnCompletionListener(null);
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaPlayer = null;
        }
    }

    public void pauseRadio() {
        shouldRetry = false;
        retryHandler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            if (wifiLock.isHeld()) wifiLock.release();
            updateNotification();
        }
    }

    public void stopRadio() {
        shouldRetry = false;
        retryHandler.removeCallbacksAndMessages(null);
        releaseMediaPlayer();
        isPlaying = false;
        isPreparing = false;
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        abandonAudioFocus();
        stopForeground(true);
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean isPreparing() {
        return isPreparing;
    }

    private boolean requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(this)
                    .build();
            return audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        } else {
            return audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            }
        } else {
            audioManager.abandonAudioFocus(this);
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                pauseRadio();
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                playRadio();
                break;
        }
    }

    private void updateNotification() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(NOTIFICATION_ID, getNotification());
    }

    private Notification getNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Intent playPauseIntent = new Intent(this, RadioService.class);
        playPauseIntent.setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent playPausePendingIntent = PendingIntent.getService(this, 0, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, RadioService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String contentText = isPreparing ? "Carregando..." : (isPlaying ? "Tocando agora..." : "Pausado");

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Rádio TV Bem Falante")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_radio_small)
                .setContentIntent(pendingIntent)
                .addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        isPlaying ? "Pause" : "Play", playPausePendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1))
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Radio Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        stopRadio();
        super.onDestroy();
    }
}
