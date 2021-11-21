package com.github.arjunphull.sunoaudiobookplayer.file;

import java.io.File;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;

public class Audiobook {
    private final String mAuthor;
    private final String mTitle;
    private int mCurrentTrack;
    private int mCurrentPosition;
    private File mCoverArtFile;
    private SortedSet<TrackInfo> mTracks;
    private boolean mMultipleDisks;
    private boolean mValid;

    public Audiobook(String author, String title) {
        mAuthor = author;
        mTitle = title;
        mCurrentTrack = 0;
        mCurrentPosition = 0;
        mCoverArtFile = null;
        mTracks = new TreeSet<>(new SortByTrackNum());
        mMultipleDisks = false;
        mValid = true;
    }

    public TrackInfo[] tracksToArray() {
        TrackInfo[] array = new TrackInfo[mTracks.size()];
        mTracks.toArray(array);
        return array;
    }

    public String getAuthor() {
        return mAuthor;
    }

    public String getTitle() {
        return mTitle;
    }

    public void addTrack(TrackInfo track) {
        if (!mValid) {
            return;
        }
        // if the track number exists, assume that we're dealing with multiple disks;
        // in this case, order by the track title
        boolean added = mTracks.add(track);
        if (!added) {
            if (mMultipleDisks) {
                mValid = false;
            } else {
                mMultipleDisks = true;
                SortedSet<TrackInfo> tracks = mTracks;
                mTracks = new TreeSet<>(new SortByChapter());
                mValid &= mTracks.addAll(tracks);
                mValid &= mTracks.add(track);
            }
        }
    }

    public TrackInfo getFirstTrack() {
        return mTracks.first();
    }

    public void sanitize() {
        if (mMultipleDisks) {
            int trackNum = 0;
            for (TrackInfo t : mTracks) {
                t.setTrackNum(++trackNum);
            }
        }
    }

    public boolean isValid() {
        return mValid && !mTracks.isEmpty();
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

    private static class SortByTrackNum implements Comparator<TrackInfo> {
        public int compare(TrackInfo e1, TrackInfo e2) {
            return Integer.compare(e1.getTrackNum(), e2.getTrackNum());
        }
    }

    private static class SortByChapter implements Comparator<TrackInfo> {
        public int compare(TrackInfo e1, TrackInfo e2) {
            return e1.getChapter().compareTo(e2.getChapter());
        }
    }
}
