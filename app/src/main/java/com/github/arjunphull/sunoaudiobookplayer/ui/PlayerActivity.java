package com.github.arjunphull.sunoaudiobookplayer.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.github.arjunphull.sunoaudiobookplayer.R;
import com.github.arjunphull.sunoaudiobookplayer.audio.OnPlaybackChangeListener;
import com.github.arjunphull.sunoaudiobookplayer.audio.PlayerService;
import com.github.arjunphull.sunoaudiobookplayer.datamodel.AudiobookDataModel;
import com.github.arjunphull.sunoaudiobookplayer.file.Database;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlayerActivity extends AppCompatActivity implements OnPlaybackChangeListener {
    private static final int KEYCODE_MEDIA_PLAY = 126;
    private static final int KEYCODE_MEDIA_PAUSE = 127;
    private static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    private static final int KEYCODE_MEDIA_NEXT = 87;
    private static final int KEYCODE_MEDIA_SKIP_FORWARD = 272;
    private static final int KEYCODE_MEDIA_PREVIOUS= 88;
    private static final int KEYCODE_MEDIA_SKIP_BACKWARD = 273;
    private static final int KEYCODE_MEDIA_FAST_FORWARD = 90;
    private static final int KEYCODE_MEDIA_REWIND = 89;

    private final AudiobookDataModel mDataModel = RecyclerViewAdapter.getSelectedAudiobook();
    private PlayerService mPlayerService;
    private SeekBar mSeekBar;
    private Button mPlaybackSpeedButton;
    private ImageButton mPlayPauseBtn;
    private TextView mChapterTv;
    private TextView mPositionTv;
    private TextView mLengthTv;
    private Handler mUiHandler;
    private ScheduledExecutorService mExecutor;
    private ServiceConnection mConnection;

    private void shutdown() {
        if (!mExecutor.isTerminated()) {
            mExecutor.shutdownNow();
            mDataModel.setCurrentPosition(mPlayerService.getCurrentPosition());
            unbindService(mConnection);
            mPlayerService.dispose();
            try {
                Database.getInstance(this).updateLocation(mDataModel);
            } catch (IOException e) {
                // do nothing
            }
            while (!mExecutor.isTerminated()) {
                // busy loop
            }
        }
    }

    private void initExecutor() {
        mExecutor = Executors.newSingleThreadScheduledExecutor();
        Runnable waitTask = () -> mUiHandler.sendEmptyMessage(0);
        mExecutor.scheduleWithFixedDelay(waitTask, 0, 1, TimeUnit.SECONDS);
    }

    private void updateUi() {
        if (!mExecutor.isTerminated()) {
            mDataModel.setCurrentPosition(mPlayerService.getCurrentPosition());
            mSeekBar.setProgress((int) (mSeekBar.getMax() * (mPlayerService.getCurrentPosition() / (float) mDataModel.getCurrentTrackLength())));
            mChapterTv.setText(mDataModel.getNumChapters() == 1 ? "" : mDataModel.getChapter());
            mPositionTv.setText(mDataModel.getCurrentTrackPositionFormatted());
            mLengthTv.setText(mDataModel.getCurrentTrackLengthFormatted());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        mSeekBar = findViewById(R.id.seekBar);
        mPlayPauseBtn = findViewById(R.id.play_pause_button);
        mPositionTv = findViewById(R.id.tvPosition);
        mChapterTv = findViewById(R.id.tvChapter);
        mPlaybackSpeedButton = findViewById(R.id.btnPlaybackSpeed);
        mLengthTv = findViewById(R.id.tvLength);
        ((TextView) findViewById(R.id.tvTitle)).setText(mDataModel.getTitle());
        ((TextView) findViewById(R.id.tvAuthor)).setText(mDataModel.getAuthor());

        if (mDataModel.getCoverArtPath() != null) {
            ImageView coverImageView = findViewById(R.id.playerCoverImageView);
            coverImageView.setImageBitmap(BitmapFactory.decodeFile(mDataModel.getCoverArtPath()));
        }

        // instantiate the handler for signalling UI updates
        mUiHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                updateUi();
            }
        };

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { /* do nothing */ }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { /* do nothing */ }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mPlayerService.isPlaying() > 0) {
                    mPlayerService.togglePlayPause();
                }
                mDataModel.seek((seekBar.getProgress() / (float) seekBar.getMax()));
                mPlayerService.seekTo(mDataModel.getCurrentPosition());
                mPlayerService.togglePlayPause();
            }
        });

        // Defines callbacks for service binding, passed to bindService()
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                // Because we have bound to an explicit
                // service that is running in our own process, we can
                // cast its IBinder to a concrete class and directly access it.
                PlayerService.LocalBinder binder = (PlayerService.LocalBinder) service;
                mPlayerService = binder.getService();
                mPlayerService.setOnPlaybackChangeListener(PlayerActivity.this);

                initExecutor();
                updateUi();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mExecutor.shutdownNow();
            }
        };

        // start foreground service
        Intent intent = new Intent(this, PlayerService.class);
        bindService(intent, mConnection, BIND_AUTO_CREATE);
        startForegroundService(intent);
    }

    @Override
    public void onBackPressed() {
        shutdown();
        finish();
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KEYCODE_MEDIA_PLAY:
            case KEYCODE_MEDIA_PAUSE:
            case KEYCODE_MEDIA_PLAY_PAUSE:
                onPlayPauseButtonClick(null);
                break;
            case KEYCODE_MEDIA_NEXT:
            case KEYCODE_MEDIA_SKIP_FORWARD:
                onSkipFwdButtonClick(null);
                break;
            case KEYCODE_MEDIA_PREVIOUS:
            case KEYCODE_MEDIA_SKIP_BACKWARD:
                onSkipBackButtonClick(null);
                break;
            case KEYCODE_MEDIA_FAST_FORWARD:
                onSeekFwdButtonClick(null);
                break;
            case KEYCODE_MEDIA_REWIND:
                onSeekBackButtonClick(null);
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void onPlaybackSpeedButtonClick(View v) {
        if (mPlaybackSpeedButton.getText().toString().equals("1.0x")) {
            mPlayerService.setPlaybackSpeed(1.1f);
            mPlaybackSpeedButton.setText("1.1x");
        } else if (mPlaybackSpeedButton.getText().toString().equals("1.1x")) {
            mPlayerService.setPlaybackSpeed(1.2f);
            mPlaybackSpeedButton.setText("1.2x");
        } else if (mPlaybackSpeedButton.getText().toString().equals("1.2x")) {
            mPlayerService.setPlaybackSpeed(1.3f);
            mPlaybackSpeedButton.setText("1.3x");
        } else if (mPlaybackSpeedButton.getText().toString().equals("1.3x")) {
            mPlayerService.setPlaybackSpeed(1.4f);
            mPlaybackSpeedButton.setText("1.4x");
        } else if (mPlaybackSpeedButton.getText().toString().equals("1.4x")) {
            mPlayerService.setPlaybackSpeed(1.5f);
            mPlaybackSpeedButton.setText("1.5x");
        } else {
            mPlayerService.setPlaybackSpeed(1.0f);
            mPlaybackSpeedButton.setText("1.0x");
        }
    }

    public void onPlayPauseButtonClick(View v) {
        mPlayerService.togglePlayPause();
        if (mPlayerService.isPlaying() == 0) {
            // save our current position
            try {
                Database.getInstance(this).updateLocation(mDataModel);
            } catch (IOException e) {
                // do nothing
            }
        }
    }

    public void onSkipBackButtonClick(View v) {
        boolean isPlaying = mPlayerService.previous();
        mPlayPauseBtn.setImageResource(isPlaying ? R.drawable.ic_baseline_pause_circle_outline_24 : R.drawable.ic_baseline_play_circle_outline_24);
        updateUi();
    }

    public void onSkipFwdButtonClick(View v) {
        boolean isPlaying = mPlayerService.next();
        mPlayPauseBtn.setImageResource(isPlaying ? R.drawable.ic_baseline_pause_circle_outline_24 : R.drawable.ic_baseline_play_circle_outline_24);
        updateUi();
    }

    public void onSeekBackButtonClick(View v) {
        mPlayerService.seek(-10000);
        mPlayPauseBtn.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
        updateUi();
    }

    public void onSeekLongBackButtonClick(View v) {
        mPlayerService.seek(-30000);
        mPlayPauseBtn.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
        updateUi();
    }

    public void onSeekFwdButtonClick(View v) {
        mPlayerService.seek(10000);
        mPlayPauseBtn.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
        updateUi();
    }

    public void onSeekLongFwdButtonClick(View v) {
        mPlayerService.seek(30000);
        mPlayPauseBtn.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
        updateUi();
    }

    @Override
    public void onPlaybackChange() {
        if (mPlayerService.isPlaying() == 0) {
            mPlayPauseBtn.setImageResource(R.drawable.ic_baseline_play_circle_outline_24);
        } else {
            mPlayPauseBtn.setImageResource(R.drawable.ic_baseline_pause_circle_outline_24);
        }
    }
}