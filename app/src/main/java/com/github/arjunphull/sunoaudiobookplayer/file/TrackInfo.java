package com.github.arjunphull.sunoaudiobookplayer.file;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.StringJoiner;

public class TrackInfo {
    private int mNum;
    private int mLength;
    private String mAuthor;
    private String mTitle;
    private String mChapter;
    private Uri mUri;
    private DocumentFile mDir;
    private boolean mRecalculateLength;

    public TrackInfo() {
        mNum = -1;
        mLength = -1;
        mChapter = null;
        mUri = null;
        mDir = null;
        mRecalculateLength = false;
    }

    public int getTrackNum() {
        return mNum;
    }

    public void setTrackNum(int num) {
        mNum = num;
    }

    public int getLength() {
        return mLength;
    }

    public void setLength(int length) {
        mLength = length;
    }

    public String getAuthor() {
        return mAuthor;
    }

    public void setAuthor(String author) {
        mAuthor = author;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getChapter() {
        return mChapter;
    }

    public void setChapter(String chapter) {
        mChapter = chapter;
    }

    public Uri getUri() {
        return mUri;
    }

    public void setUri(Uri uri) {
        mUri = uri;
    }

    public DocumentFile getDir() {
        return mDir;
    }

    public void setDir(DocumentFile df) {
        mDir = df;
    }

    public void setRecalculateLengthTrue() {
        mRecalculateLength = true;
    }

    public boolean validate(Context context) {
        boolean ret = mNum != -1;
        ret &= mLength != -1;
        ret &= mChapter != null;
        ret &= mTitle != null;
        ret &= mAuthor != null;
        ret &= mUri != null;
        if (ret && mRecalculateLength) {
            MediaPlayer player = MediaPlayer.create(context, mUri);
            mLength = player.getDuration();
            player.release();
        }
        return ret;
    }

    public String toJson() {
        StringJoiner sj = new StringJoiner(",", "{", "}");
        sj.add("\"num\": " + mNum);
        sj.add("\"length\": " + mLength);
        sj.add("\"chapter\": \"" + mChapter + "\"");
        sj.add("\"uri\": \"" + mUri.toString() + "\"");
        return sj.toString();
    }
}