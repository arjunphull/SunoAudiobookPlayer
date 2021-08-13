package com.github.arjunphull.sunoaudiobookplayer.audio;

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
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;

import com.github.arjunphull.sunoaudiobookplayer.R;
import com.github.arjunphull.sunoaudiobookplayer.datamodel.AudiobookDataModel;
import com.github.arjunphull.sunoaudiobookplayer.ui.PlayerActivity;
import com.github.arjunphull.sunoaudiobookplayer.ui.RecyclerViewAdapter;

public class PlayerService extends Service implements AudioManager.OnAudioFocusChangeListener {
    private static final String CHANNEL_NAME = "suno";
    private static final String CHANNEL_ID = "suno_1";
    private static final int NOTIFICATION_ID = 35;

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public PlayerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return PlayerService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();
    private final AudiobookDataModel mDataModel = RecyclerViewAdapter.getSelectedAudiobook();

    private OnPlaybackChangeListener mPlaybackChangeListener;
    private MediaPlayer mMediaPlayer;
    private long mPauseTime;
    private AudioManager mAudioManager;
    private boolean mPlaybackDelayed;
    private boolean mPlaybackAuthorized;
    private boolean mResumeOnFocusGain;

    public PlayerService(){}

    @Override
    public void onCreate() {
        super.onCreate();

        mPlaybackDelayed = false;
        mPlaybackAuthorized = false;
        mResumeOnFocusGain = false;

        // initializing variables for audio focus and playback management
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .setWillPauseWhenDucked(true)
                .build();

        // requesting audio focus and processing the response
        int res = mAudioManager.requestAudioFocus(focusRequest);
        if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            mPlaybackAuthorized = false;
        } else if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mPlaybackAuthorized = true;
        } else if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
            mPlaybackDelayed = true;
            mPlaybackAuthorized = false;
        }

        initMediaPlayer();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        Intent playerActivityIntent = new Intent(getApplicationContext(), PlayerActivity.class);
        playerActivityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 1, playerActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
        Notification notification = new Notification.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle(mDataModel.getTitle())
                .setContentIntent(pendingIntent)
                .setSmallIcon(R.drawable.ic_default_cover)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .build();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        startForeground(NOTIFICATION_ID, notification);

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void dispose() {
        mMediaPlayer.stop();
        mMediaPlayer.release();
        stopForeground(true);
        stopSelf();
    }

    public void setOnPlaybackChangeListener(OnPlaybackChangeListener onPlaybackChangeListener) {
        mPlaybackChangeListener = onPlaybackChangeListener;
    }

    public void handlePlaybackChange() {
        if (mPlaybackChangeListener != null) {
            mPlaybackChangeListener.onPlaybackChange();
        }
    }

    public void togglePlayPause() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            mPauseTime = System.currentTimeMillis();
            handlePlaybackChange();
        } else if (mPlaybackAuthorized) {
            int rewind = 0;
            if (mPauseTime > 0) {
                long now = System.currentTimeMillis();
                int diff = (int) (now - mPauseTime);

                if (diff > 30000) {
                    rewind = 3000;
                } else if (diff > 20000) {
                    rewind = 2500;
                } else if (diff > 10000) {
                    rewind = 2000;
                } else if (diff > 5000) {
                    rewind = 1200;
                } else {
                    rewind = 500;
                }
            }

            mMediaPlayer.seekTo(mMediaPlayer.getCurrentPosition() - rewind);
            mMediaPlayer.start();
            handlePlaybackChange();
        }
    }

    public void setPlaybackSpeed(float multiple) {
        mMediaPlayer.setPlaybackParams(mMediaPlayer.getPlaybackParams().setSpeed(multiple));
    }

    public int isPlaying() {
        try {
            if (mMediaPlayer.isPlaying()) {
                return 1;
            } else {
                return 0;
            }
        } catch (IllegalStateException e) {
            return -1;
        }
    }

    public int getCurrentPosition() {
        return mMediaPlayer.getCurrentPosition();
    }

    public boolean next() {
        int currentTrack = mDataModel.getCurrentTrack();
        int newTrack = currentTrack + 1;
        mDataModel.setCurrentTrack(newTrack);
        mDataModel.setCurrentPosition(0);
        mMediaPlayer.stop();
        mMediaPlayer.release();
        initMediaPlayer();
        // play next chapter unless the book finished
        if (mDataModel.getCurrentTrack() == newTrack) {
            mMediaPlayer.start();
            return true;
        } else {
            mDataModel.setCurrentPosition(mDataModel.getCurrentTrackLength());
        }
        return false;
    }

    public boolean previous() {
        mMediaPlayer.stop();
        mMediaPlayer.release();
        int currentTrack = mDataModel.getCurrentTrack();
        int newTrack = currentTrack - 1;
        mDataModel.setCurrentTrack(newTrack);
        mDataModel.setCurrentPosition(0);
        initMediaPlayer();
        // play previous chapter unless the book finished
        if (mDataModel.getCurrentTrack() == newTrack || mDataModel.getCurrentTrack() == 0) {
            mMediaPlayer.start();
            return true;
        }
        return false;
    }

    public void seek(int value) {
        mMediaPlayer.pause();
        mDataModel.setCurrentPosition(mMediaPlayer.getCurrentPosition());
        int currentTrack = mDataModel.getCurrentTrack();
        mDataModel.seek(value);
        // release the media player and load new track if necessary
        if (currentTrack != mDataModel.getCurrentTrack()) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            initMediaPlayer();
        } else {
            mMediaPlayer.seekTo(mDataModel.getCurrentPosition());
        }
        mMediaPlayer.start();
    }

    public void seekTo(int msec) {
        mMediaPlayer.seekTo(msec);
    }

    private void initMediaPlayer() {
        mPauseTime = -1;
        mMediaPlayer = MediaPlayer.create(this, mDataModel.getCurrentTrackUri());
        mMediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mMediaPlayer.setOnErrorListener((mp, what, extra) -> true);

        // seek to 1 second earlier than latched
        mMediaPlayer.seekTo(mDataModel.getCurrentPosition() - 1000);
        mMediaPlayer.setOnCompletionListener(mp -> {
            int currentTrack = mDataModel.getCurrentTrack();
            int newTrack = currentTrack + 1;
            mDataModel.setCurrentTrack(newTrack);

            // automatically play next chapter unless the book finished
            if (mDataModel.getCurrentTrack() == newTrack) {
                mMediaPlayer.release();
                mDataModel.setCurrentPosition(0);
                initMediaPlayer();
                mMediaPlayer.start();
            }
        });
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                mPlaybackAuthorized = true;
                if (mPlaybackDelayed || mResumeOnFocusGain) {
                    mPlaybackDelayed = false;
                    mResumeOnFocusGain = false;
                    if (isPlaying() == 0) {
                        togglePlayPause();
                    }
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                mPlaybackAuthorized = false;
                mPlaybackDelayed = false;
                mResumeOnFocusGain = false;
                if (isPlaying() > 0) {
                    togglePlayPause();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                mResumeOnFocusGain = isPlaying() > 0;
                mPlaybackDelayed = false;
                if (mResumeOnFocusGain) {
                    togglePlayPause();
                }
                break;
        }
    }
}
