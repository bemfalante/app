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
import android.content.res.ColorStateList;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

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
    private int lastState = -1; // 0: Idle, 1: Preparing, 2: Playing
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
            Toast.makeText(MainActivity.this, "Serviço Conectado à UI", Toast.LENGTH_SHORT).show();
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
            int currentState;
            if (radioService.isPlaying()) {
                currentState = 2;
            } else if (radioService.isPreparing()) {
                currentState = 1;
            } else {
                currentState = 0;
            }

            // SÓ ATUALIZA A UI SE HOUVER MUDANÇA REAL DE ESTADO
            if (lastState != currentState) {
                Toast.makeText(this, "Mudança de Estado: " + lastState + " -> " + currentState, Toast.LENGTH_SHORT).show();
                if (currentState == 2) {
                    btnPlayPause.setImageResource(R.drawable.ic_pause);
                    btnPlayPause.setEnabled(true);
                    loadingIndicator.setVisibility(View.GONE);
                    onAirDot.setVisibility(View.VISIBLE);
                    if (onAirDot.getAnimation() == null) {
                        onAirDot.startAnimation(blinkAnimation);
                    }
                    tvStatus.setText(R.string.status_on_air);
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.dark_red));

                } else if (currentState == 1) {
                    btnPlayPause.setImageResource(R.drawable.ic_play);
                    btnPlayPause.setEnabled(false); // Evita múltiplos cliques enquanto prepara
                    loadingIndicator.setVisibility(View.VISIBLE);
                    onAirDot.setVisibility(View.GONE);
                    onAirDot.clearAnimation();
                    tvStatus.setText(R.string.status_tuning);
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.orange));

                } else {
                    btnPlayPause.setImageResource(R.drawable.ic_play);
                    btnPlayPause.setEnabled(true);
                    loadingIndicator.setVisibility(View.GONE);
                    onAirDot.setVisibility(View.GONE);
                    onAirDot.clearAnimation();
                    tvStatus.setText(R.string.status_press_play);
                    tvStatus.setTextColor(ContextCompat.getColor(this, R.color.orange));
                }
                lastState = currentState;
            }
        } else {
            // Estado inicial antes do service conectar
            if (lastState != 0) {
                tvStatus.setText(R.string.status_press_play);
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.orange));
                onAirDot.setVisibility(View.GONE);
                lastState = 0;
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
