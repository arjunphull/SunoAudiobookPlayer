package com.github.arjunphull.sunoaudiobookplayer.file;

import java.io.File;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;

public class Audiobook {
    private int mCurrentTrack;
    private int mCurrentPosition;
    private File mCoverArtFile;
    private SortedSet<TrackInfo> mTracks;

    public Audiobook() {
        mCurrentTrack = 0;
        mCurrentPosition = 0;
        mCoverArtFile = null;
        mTracks = new TreeSet<>(new SortByTrackNum());
    }

    public TrackInfo[] tracksToArray() {
        TrackInfo[] array = new TrackInfo[mTracks.size()];
        mTracks.toArray(array);
        return array;
    }

    public void addTrack(TrackInfo track) {
        mTracks.add(track);
    }

    public TrackInfo getFirstTrack() {
        return mTracks.first();
    }

    public int getCurrentTrack() {
        return mCurrentTrack;
    }

    public void setCurrentTrack(int track) {
        mCurrentTrack = track;
    }

    public int getCurrentPosition() {
        return mCurrentPosition;
    }

    public void setCurrentPosition(int position) {
        mCurrentPosition = position;
    }

    public File getCoverArtFile() {
        return mCoverArtFile;
    }

    public void setCoverArtFile(File file) {
        mCoverArtFile = file;
    }

    public String TracksToJsonArray() {
        StringJoiner jsonSj = new StringJoiner(",", "tracks: [", "]");
        for (TrackInfo track : mTracks) {
            jsonSj.add(track.toJson());
        }
        return jsonSj.toString();
    }

    private class SortByTrackNum implements Comparator<TrackInfo> {
        public int compare(TrackInfo e1, TrackInfo e2) {
            if (e1.getTrackNum() > e2.getTrackNum()) {
                return 1;
            } else if (e1.getTrackNum() < e2.getTrackNum()) {
                return -1;
            } else {
                return 0;
            }
        }
    }
}
