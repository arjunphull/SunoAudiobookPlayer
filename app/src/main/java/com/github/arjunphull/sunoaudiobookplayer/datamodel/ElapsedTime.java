package com.github.arjunphull.sunoaudiobookplayer.datamodel;

import com.github.arjunphull.sunoaudiobookplayer.file.TrackInfo;

class ElapsedTime {
    private long mElapsedTime;
    private int mCurrentTrack;
    private int mCurrentPos;

    protected ElapsedTime() {
        mElapsedTime = -1;
    }

    protected int getElapsedSeconds(AudiobookDataModel audiobook) {
        if (mElapsedTime == -1 || mCurrentTrack != audiobook.getCurrentTrack()) {
            mElapsedTime = 0;
            mCurrentTrack = audiobook.getCurrentTrack();
            TrackInfo[] trackInfoArr = audiobook.getTrackInfoArray();
            for (int i = 0; i < mCurrentTrack; i++) {
                mElapsedTime += trackInfoArr[i].getLength();
            }
        } else {
            mElapsedTime -= mCurrentPos;
        }
        mCurrentPos = audiobook.getCurrentPosition();
        mElapsedTime += mCurrentPos;

        return (int) (mElapsedTime / 1000);
    }
}
