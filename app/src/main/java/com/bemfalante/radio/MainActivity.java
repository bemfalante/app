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
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private RadioService radioService;
    private boolean isBound = false;
    private ImageButton btnPlayPause;
    private ProgressBar loadingIndicator;
    private TextView tvStatus;
    private LinearLayout statusContainer;
    private View onAirDot;
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
        tvStatus = findViewById(R.id.tv_status);
        statusContainer = findViewById(R.id.status_container);
        onAirDot = findViewById(R.id.on_air_dot);

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
                tvStatus.setText(R.string.status_on_air);
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.dark_red));
                statusContainer.getBackground().setTint(ContextCompat.getColor(this, R.color.orange_70));
                onAirDot.setVisibility(View.VISIBLE);
                if (onAirDot.getAnimation() == null) {
                    onAirDot.startAnimation(blinkAnimation);
                }
            } else if (radioService.isPreparing()) {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                btnPlayPause.setEnabled(false);
                loadingIndicator.setVisibility(View.VISIBLE);
                tvStatus.setText(R.string.status_tuning);
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.orange));
                statusContainer.getBackground().setTint(ContextCompat.getColor(this, R.color.dark_red_70));
                onAirDot.setVisibility(View.GONE);
                onAirDot.clearAnimation();
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                btnPlayPause.setEnabled(true);
                loadingIndicator.setVisibility(View.GONE);
                tvStatus.setText(R.string.status_press_play);
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.orange));
                statusContainer.getBackground().setTint(ContextCompat.getColor(this, R.color.dark_red_70));
                onAirDot.setVisibility(View.GONE);
                onAirDot.clearAnimation();
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
