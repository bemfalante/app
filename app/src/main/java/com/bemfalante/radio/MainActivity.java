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
import android.widget.ImageButton;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private RadioService radioService;
    private boolean isBound = false;
    private MaterialButton btnPlayPause;
    private ProgressBar loadingIndicator;
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
        MaterialButton btnStop = findViewById(R.id.btn_stop);
        ImageButton btnInstagram = findViewById(R.id.btn_instagram);
        loadingIndicator = findViewById(R.id.loading_indicator);

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
                btnPlayPause.setText(R.string.pause);
                btnPlayPause.setEnabled(true);
                loadingIndicator.setVisibility(View.GONE);
            } else if (radioService.isPreparing()) {
                btnPlayPause.setText("Carregando...");
                btnPlayPause.setEnabled(false);
                loadingIndicator.setVisibility(View.VISIBLE);
            } else {
                btnPlayPause.setText(R.string.play);
                btnPlayPause.setEnabled(true);
                loadingIndicator.setVisibility(View.GONE);
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
