package com.bemfalante.radio;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private RadioService radioService;
    private boolean isBound = false;
    private ImageButton btnPlayPause;
    private ProgressBar loadingIndicator;
    private LinearLayout onAirContainer;
    private Animation blinkAnimation;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            updateUI();
            handler.postDelayed(this, 1000);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            RadioService.RadioBinder binder = (RadioService.RadioBinder) service;
            radioService = binder.getService();
            isBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnPlayPause = findViewById(R.id.btn_play_pause);
        ImageButton btnStop = findViewById(R.id.btn_stop);
        ImageButton btnInstagram = findViewById(R.id.btn_instagram);
        loadingIndicator = findViewById(R.id.loading_indicator);
        onAirContainer = findViewById(R.id.on_air_container);

        blinkAnimation = new AlphaAnimation(0.0f, 1.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setStartOffset(20);
        blinkAnimation.setRepeatMode(Animation.REVERSE);
        blinkAnimation.setRepeatCount(Animation.INFINITE);

        btnPlayPause.setOnClickListener(v -> {
            if (isBound) {
                if (radioService.isPlaying()) {
                    radioService.pauseRadio();
                } else {
                    radioService.playRadio();
                }
                updateUI();
            }
        });

        btnStop.setOnClickListener(v -> {
            if (isBound) {
                radioService.stopRadio();
                updateUI();
            }
        });

        btnInstagram.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.instagram_link)));
            startActivity(intent);
        });

        Intent intent = new Intent(this, RadioService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void updateUI() {
        if (isBound) {
            if (radioService.isPlaying()) {
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                btnPlayPause.setEnabled(true);
                loadingIndicator.setVisibility(View.GONE);
                onAirContainer.setVisibility(View.VISIBLE);
                if (onAirContainer.getAnimation() == null) {
                    onAirContainer.startAnimation(blinkAnimation);
                }
            } else if (radioService.isPreparing()) {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                btnPlayPause.setEnabled(false);
                loadingIndicator.setVisibility(View.VISIBLE);
                onAirContainer.setVisibility(View.GONE);
                onAirContainer.clearAnimation();
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                btnPlayPause.setEnabled(true);
                loadingIndicator.setVisibility(View.GONE);
                onAirContainer.setVisibility(View.GONE);
                onAirContainer.clearAnimation();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
        handler.post(updateTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }
}
