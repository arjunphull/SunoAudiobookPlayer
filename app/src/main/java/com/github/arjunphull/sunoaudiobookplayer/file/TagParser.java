package com.github.arjunphull.sunoaudiobookplayer.file;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.SparseArray;

import androidx.documentfile.provider.DocumentFile;

import com.github.arjunphull.sunoaudiobookplayer.R;
import com.github.arjunphull.sunoaudiobookplayer.util.CollectionUtil;
import com.github.arjunphull.sunoaudiobookplayer.util.MutableBoolean;
import com.github.arjunphull.sunoaudiobookplayer.util.Triplet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TagParser {

    static {
        System.loadLibrary("sunotagparser");
    }

    private final String mFinished;

    private Thread mTakeTagInfoThread;
    private Thread mGetTagInfoThread;
    private List<Triplet<ParcelFileDescriptor, Uri, DocumentFile>> mFileBuf;
    private final File mFifo;
    private final File mDataDir;
    private final Context mContext;
    private final Pattern mTrackNumPattern;

    TagParser(Context context, File fifo) {
        mContext = context;
        mFinished = "~";
        mTakeTagInfoThread = null;
        mGetTagInfoThread = null;
        mFileBuf = new ArrayList<>();
        mFifo = fifo;
        mDataDir = Database.getInstance(context).getDataDir();
        mTrackNumPattern = Pattern.compile("\\d+");
    }

    private static native byte[] getCoverArt(int fileDescriptor);

    public static Bitmap parseCoverArt(int fileDescriptor) {
        byte[] bytes = getCoverArt(fileDescriptor);
        if (bytes.length == 0) {
            return null;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private native String getTagInfo(String args);

    private void readFifo(List<String> buffer, MutableBoolean finishedProducing) {
        synchronized (mFifo) {
            try (Reader fr = new FileReader(mFifo);
                 BufferedReader br = new BufferedReader(fr)) {
                while (true) {
                    String rawInfo = br.readLine();
                    synchronized (buffer) {
                        if (rawInfo.equals(mFinished)) {
                            finishedProducing.setTrue();
                        } else {
                            buffer.add(rawInfo);
                        }
                        buffer.notifyAll();
                    }

                    if (finishedProducing.get()) {
                        break;
                    }
                }
            } catch (IOException e) {
                //TODO handle error (this should throw so that the LibraryActivity that started this can catch the exception and handle by deleting the database)
            }
        }
    }

    private void takeTagInfo(Map<String, Map<String, Audiobook>> hierarchicalData) throws InterruptedException, IllegalArgumentException {
        AtomicBoolean error = new AtomicBoolean(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // wait for fifo to be created
        try {
            while (!mFifo.exists()) {
                TimeUnit.MILLISECONDS.sleep(300);
            }
        } catch (InterruptedException e) {
            throw e;
        }

        boolean done = false;
        while (!done) {
            // push file descriptors through the fifo
            // lock with file buf until all files are handled
            synchronized (mFileBuf) {
                while (mFileBuf.isEmpty()) {
                    mFileBuf.wait();
                }
                SparseArray<Triplet<ParcelFileDescriptor, Uri, DocumentFile>> fileMap = new SparseArray<>(mFileBuf.size());
                boolean sentFileDescriptors = false;
                synchronized (mFifo) {
                    try (Writer fw = new FileWriter(mFifo);
                         BufferedWriter bw = new BufferedWriter(fw)) {
                        for (Triplet<ParcelFileDescriptor, Uri, DocumentFile> file : mFileBuf) {
                            // detect done signal
                            if (file == null) {
                                done = true;
                            } else {
                                fileMap.put(file.first.getFd(), file);
                            }
                        }

                        StringJoiner fdsj = new StringJoiner(",");
                        for (int i = 0; i < fileMap.size(); i++) {
                            fdsj.add(String.valueOf(fileMap.keyAt(i)));
                        }
                        String output = fdsj.toString();
                        bw.write(done ? output + mFinished : output);
                        sentFileDescriptors = fileMap.size() > 0;
                    } catch (IOException e) {
                        //TODO handle this
                    }
                }

                if (sentFileDescriptors) {
                    // start reading off the queue asynchronously
                    List<String> buffer = new ArrayList<>();
                    MutableBoolean finishedProducing = new MutableBoolean(false);
                    executor.submit(() -> readFifo(buffer, finishedProducing));

                    // organize tag info
                    while (!finishedProducing.get() || !CollectionUtil.isCollectionEmpty_Synchronized(buffer)) {
                        // wait for producer to supply tag info
                        String[] rawInfoArr;
                        synchronized (buffer) {
                            while (!finishedProducing.get() && buffer.isEmpty()) {
                                buffer.wait();
                            }

                            // take data off the buffer
                            rawInfoArr = new String[buffer.size()];
                            buffer.toArray(rawInfoArr);
                            buffer.clear();
                        }

                        for (String rawInfo : rawInfoArr) {
                            TrackInfo trackInfo = new TrackInfo();
                            String preSplitInfo = rawInfo.replace("|*|", "\n");
                            String[] strings = preSplitInfo.split("\n");
                            for (String s : strings) {
                                if (s.startsWith("FD=")) {
                                    try {
                                        int fd = Integer.parseInt(s.substring(3));
                                        Triplet<ParcelFileDescriptor, Uri, DocumentFile> fileInfo = fileMap.get(fd);
                                        trackInfo.setUri(fileInfo.second);
                                        trackInfo.setDir(fileInfo.third);
                                    } catch (NumberFormatException e) {
                                        break;
                                    }
                                } else if (s.startsWith("ARTIST=")) {
                                    trackInfo.setAuthor(s.substring(7));
                                } else if (s.startsWith("ALBUM=")) {
                                    trackInfo.setTitle(s.substring(6));
                                } else if (s.startsWith("TITLE=")) {
                                    trackInfo.setChapter(s.substring(6));
                                } else if (s.startsWith("TRACK=")) {
                                    trackInfo.setTrackNum(Integer.parseInt(s.substring(6)));
                                } else if (s.startsWith("LENGTH=")) {
                                    trackInfo.setLength(Integer.parseInt(s.substring(7)));
                                }
                            }

                            if (trackInfo.getChapter() == null || trackInfo.getChapter().isEmpty() ||
                                trackInfo.getAuthor() == null || trackInfo.getAuthor().isEmpty() ||
                                trackInfo.getTitle() == null || trackInfo.getTitle().isEmpty() ||
                                trackInfo.getTrackNum() <= 0) {
                                guessTrackInfo(trackInfo);
                            }

                            Audiobook fileData = null;
                            if (trackInfo.validate(mContext)) {
                                Map<String, Audiobook> bookMap = hierarchicalData.get(trackInfo.getAuthor());
                                if (bookMap == null) {
                                    bookMap = new TreeMap<>();
                                    hierarchicalData.put(trackInfo.getAuthor(), bookMap);
                                }
                                fileData = bookMap.get(trackInfo.getTitle());
                                if (fileData == null) {
                                    fileData = new Audiobook(trackInfo.getAuthor(), trackInfo.getTitle());
                                    bookMap.put(fileData.getTitle(), fileData);
                                }
                                fileData.addTrack(trackInfo);
                            }

                            if (fileData == null || !fileData.isValid()) {
                                break;
                            }
                        }
                    }
                }

                // close files
                for (int i = 0; i < fileMap.size(); i++) {
                    try {
                        fileMap.valueAt(i).first.close();
                    } catch (IOException e) {
                        continue;
                    }
                }

                mFileBuf.clear();
                mFileBuf.notify();
            }
        }

        executor.shutdown();

        if (error.get()) {
            throw new IllegalArgumentException();
        }
    }

    private void guessTrackInfo(TrackInfo trackInfo) {
        File file = new File(trackInfo.getUri().getPath());
        if (trackInfo.getTrackNum() < 0) {
            Matcher matcher = mTrackNumPattern.matcher(file.getName());
            if (!matcher.find()) {
                return;
            }

            int trackNum = -1;
            try {
                trackNum = Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                return;
            }
            if (trackNum < 0) {
                return;
            }
            trackInfo.setTrackNum(trackNum);
        }
        if (trackInfo.getChapter() == null || trackInfo.getChapter().isEmpty()) {
            String chapter = file.getName();
            int dotIndex = chapter.indexOf(".");
            if (dotIndex != -1) {
                chapter = chapter.substring(0, dotIndex);
            }
            trackInfo.setChapter(chapter);
        }

        File titleDir = file.getParentFile();
        File authorDir = null;
        if (titleDir != null && titleDir != mDataDir) {
            authorDir = titleDir.getParentFile();
        }
        String author = (trackInfo.getAuthor() == null || trackInfo.getAuthor().isEmpty()) && authorDir != null ? authorDir.getName() : trackInfo.getAuthor();
        String title = (trackInfo.getTitle() == null || trackInfo.getTitle().isEmpty()) && titleDir != null ? titleDir.getName() : trackInfo.getTitle();
        if (author == null || author.isEmpty()) {
            author = mContext.getString(R.string.unknown);
        }
        if (title == null || title.isEmpty()) {
            title = mContext.getString(R.string.unknown);
        }
        trackInfo.setAuthor(author);
        trackInfo.setTitle(title);
    }

    public void parseTagsAsync(List<Triplet<ParcelFileDescriptor, Uri, DocumentFile>> fileList, Map<String, Map<String, Audiobook>> hierarchicalData) throws InterruptedException, IllegalArgumentException {
        // start tag parser interface thread
        if (mGetTagInfoThread == null) {
            mGetTagInfoThread = new Thread(() -> {
                try {
                    getTagInfo(String.format("pipe=%s\n", mFifo.getAbsolutePath()));
                } catch (IllegalArgumentException e) {
                    // TODO handle
                }
            });
            mGetTagInfoThread.start();
        }

        // start fifo thread
        if (mTakeTagInfoThread == null) {
            mFileBuf.clear();
            mTakeTagInfoThread = new Thread(() -> {
                try {
                    takeTagInfo(hierarchicalData);
                } catch (InterruptedException | IllegalArgumentException e) {
                    // TODO handle
                }
            });
            mTakeTagInfoThread.start();
        }

        synchronized (mFileBuf) {
            while (!mFileBuf.isEmpty()) {
                mFileBuf.wait();
            }
            mFileBuf.addAll(fileList);
            mFileBuf.notifyAll();
        }

        /*if (error.get()) {
            throw new IllegalArgumentException();
        }*/
    }

    public void release() throws InterruptedException {
        // queue stop signal
        synchronized (mFileBuf) {
            mFileBuf.add(null);
            mFileBuf.notifyAll();
        }

        if (mGetTagInfoThread != null) {
            mGetTagInfoThread.join();
            mGetTagInfoThread = null;
        }

        if (mTakeTagInfoThread != null) {
            mTakeTagInfoThread.join();
            mTakeTagInfoThread = null;
        }

        mFifo.delete();
    }
}
