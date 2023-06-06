package com.github.arjunphull.sunoaudiobookplayer.datamodel;

import android.net.Uri;

import com.github.arjunphull.sunoaudiobookplayer.file.Audiobook;
import com.github.arjunphull.sunoaudiobookplayer.file.Database;
import com.github.arjunphull.sunoaudiobookplayer.file.TrackInfo;

import java.io.File;

public class AudiobookDataModel {
    private String mTitle;
    private String mAuthor;
    private final int mLength;
    private TrackInfo[] mTrackInfoArray;
    private int mCurrentTrackNum;
    private int mCurrentPositionMs;
    private File mCoverArtFile;
    private ElapsedTime mElapsedTime;

    private AudiobookDataModel(String title, String author, TrackInfo[] trackInfoArray, int currentTrackNum, int currentPosition, String coverArtPath) {
        mTitle = title;
        mAuthor = author;
        mTrackInfoArray = trackInfoArray;
        mCurrentTrackNum = currentTrackNum;
        mCurrentPositionMs = currentPosition;
        mCoverArtFile = coverArtPath == null ? null : new File(coverArtPath);
        mElapsedTime = new ElapsedTime();
        int length = 0;
        for (TrackInfo t : trackInfoArray) {
            length += t.getLength();
        }
        mLength = length;
    }

    public static AudiobookDataModel createAudiobook(String author, String title, Audiobook fileData) {
        String coverArtFilePath = null;
        File coverArtFile = fileData.getCoverArtFile();
        if (coverArtFile != null && coverArtFile.exists()) {
            coverArtFilePath = coverArtFile.getAbsolutePath();
        }

        return new AudiobookDataModel(title, author, fileData.tracksToArray(), fileData.getCurrentTrack(), fileData.getCurrentPosition(), coverArtFilePath);
    }

    public void updateAuthorAndTitle(String author, String title) {
        mAuthor = author;
        mTitle = title;
        if (mCoverArtFile != null) {
            mCoverArtFile = new File(Database.getInstance(null).getDataDir(), author + System.getProperty("file.separator") + title + System.getProperty("file.separator") + mCoverArtFile.getName());
        }
    }

    protected TrackInfo[] getTrackInfoArray() {
        return mTrackInfoArray;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getAuthor() {
        return mAuthor;
    }

    public String getChapter() {
        return mTrackInfoArray[mCurrentTrackNum].getChapter();
    }

    public int getLength() {
        return mLength;
    }

    public int getElapsedSeconds() {
        return mElapsedTime.getElapsedSeconds(this);
    }

    public String getCurrentTrackLengthFormatted() {
        int trackLength = mTrackInfoArray[mCurrentTrackNum].getLength() / 1000;
        int eh = trackLength / 3600;
        int em = trackLength % 3600 / 60;
        int es = trackLength % 60;

        return String.format("%dh:%dm:%ds", eh, em, es);
    }

    public String getCurrentTrackPositionFormatted() {
        int elapsed = getCurrentPosition() / 1000;
        int eh = elapsed / 3600;
        int em = elapsed % 3600 / 60;
        int es = elapsed % 60;
        return String.format("%dh:%dm:%ds", eh, em, es);
    }

    public String getRemainingTimeFormatted() {
        int remainingSeconds = mLength / 1000 - mElapsedTime.getElapsedSeconds(this);

        int eh = remainingSeconds / 3600;
        int em = remainingSeconds % 3600 / 60;
        int es = remainingSeconds % 60;

        return String.format("%dh:%dm:%ds remaining", eh, em, es);
    }

    public void seek(int milliseconds) {
        int newPosition = mCurrentPositionMs + milliseconds;
        if (newPosition < 0) {
            if (mCurrentTrackNum == 0) {
                mCurrentPositionMs = 0;
            }

            while (mCurrentTrackNum > 0) {
                mCurrentTrackNum--;
                mCurrentPositionMs = getCurrentTrackLength() + newPosition;
                if (mCurrentPositionMs >= 0) {
                    break;
                } else {
                    newPosition = mCurrentPositionMs;
                }
            }
        } else if (newPosition > getCurrentTrackLength()) {
            if (mCurrentTrackNum == mTrackInfoArray.length - 1) {
                mCurrentPositionMs = getCurrentTrackLength();
            }

            while (mCurrentTrackNum < mTrackInfoArray.length - 1) {
                mCurrentPositionMs = newPosition - getCurrentTrackLength();
                mCurrentTrackNum++;
                if (mCurrentPositionMs <= getCurrentTrackLength()) {
                    break;
                } else {
                    newPosition = mCurrentPositionMs;
                }
            }
        } else {
            mCurrentPositionMs = newPosition;
        }
    }

    public void seek(float trackPercent) {
        if (trackPercent < 0 || trackPercent > 1) {
            return;
        }

        mCurrentPositionMs = (int) (trackPercent * getCurrentTrackLength());
    }

    public String getCoverArtPath() {
        return mCoverArtFile == null ? null : mCoverArtFile.getPath();
    }

    public int getCurrentTrack() {
        return mCurrentTrackNum;
    }

    public void setCurrentTrack(int track) {
        if (track < 0) {
            mCurrentTrackNum = 0;
        } else if (track >= mTrackInfoArray.length) {
            mCurrentTrackNum = mTrackInfoArray.length - 1;
        } else {
            mCurrentTrackNum = track;
        }
    }

    public Uri getCurrentTrackUri() {
        return mTrackInfoArray[mCurrentTrackNum].getUri();
    }

    public int getCurrentPosition() {
        return mCurrentPositionMs;
    }

    public void setCurrentPosition(int position) {
        mCurrentPositionMs = position;
    }

    public int getCurrentTrackLength() {
        return getTrackLengthMs(mCurrentTrackNum);
    }

    public int getNumChapters() {
        return mTrackInfoArray.length;
    }

    public int getTrackLengthMs(int track) {
        return mTrackInfoArray[track].getLength();
    }
}
