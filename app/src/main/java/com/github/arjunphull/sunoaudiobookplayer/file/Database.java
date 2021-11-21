package com.github.arjunphull.sunoaudiobookplayer.file;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import androidx.documentfile.provider.DocumentFile;

import com.github.arjunphull.sunoaudiobookplayer.datamodel.AudiobookDataModel;
import com.github.arjunphull.sunoaudiobookplayer.util.UriUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Database {
    //constants
    private final String DATA_DIR_NAME = "data";
    private final String TRACKS_FILE_NAME = "trackinfo.json";
    private final String COVER_FILE_NAME = "cover.jpg";
    private final String POSITION_FILE_NAME = "position";
    private final int LOAD_THREAD_POOL_SIZE = 6;

    private static Database sInstance = null;

    private final File mDataDir;

    private Map<String, Map<String, Audiobook>> mHierarchicalData;

    private Database(Context context) {
        mDataDir = new File(context.getFilesDir(), DATA_DIR_NAME);
        mHierarchicalData = new TreeMap<>();

        Set<File> deleteSet = new HashSet<>();
        ExecutorService executor = Executors.newFixedThreadPool(LOAD_THREAD_POOL_SIZE);
        List<Future<?>> executorJobs = new LinkedList<>();
        loadHierarchicalData(context, mDataDir.listFiles(), deleteSet, executor, executorJobs);

        // wait for all jobs to finish
        while (true) {
            synchronized (executorJobs) {
                List<Future<?>> finishedList = new ArrayList<>(executorJobs.size());
                for (Future<?> job : executorJobs) {
                    if (job.isDone()) {
                        finishedList.add(job);
                    }
                }
                for (Future<?> job : finishedList) {
                    executorJobs.remove(job);
                }
                if (executorJobs.isEmpty()) {
                    break;
                }
            }
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
        executor.shutdown();

        for (File titleDir : deleteSet) {
            File authorDir = titleDir.getParentFile();
            deleteAudiobook(authorDir.getName(), titleDir.getName());
        }
    }

    public static synchronized Database getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new Database(context);
        }

        return sInstance;
    }

    private boolean deleteFiles(File[] files) {
        boolean success = true;
        for (File file : files) {
            if (file.isDirectory()) {
                success &= deleteFiles(file.listFiles());
            } else {
                success &= file.delete();
            }
        }
        return success;
    }

    public boolean deleteAudiobook(String author, String title) {
        boolean success = true;

        Map<String, Audiobook> bookMap = mHierarchicalData.get(author);
        if (bookMap != null) {
            bookMap.remove(title);
            if (bookMap.isEmpty()) {
                mHierarchicalData.remove(author);
            }
        }

        File bookDir;
        try {
            bookDir = new File(mDataDir, author + System.getProperty("file.separator") + title);
            if (bookDir.exists()) {
                success &= deleteFiles(bookDir.listFiles());
                success &= bookDir.delete();
                File authorDir = bookDir.getParentFile();
                if (authorDir.listFiles().length == 0) {
                    success &= authorDir.delete();
                }
            }
        } catch (SecurityException e) {
            return false;
        }

        return success;
    }

    public boolean deleteDatabase() {
        boolean result = false;
        try {
            if (deleteFiles(Objects.requireNonNull(mDataDir.listFiles()))) {
                result = mDataDir.delete();
                mHierarchicalData = new TreeMap<>();
            }
        } catch (SecurityException e) {
            result = false;
        }

        return result;
    }

    private void loadHierarchicalData(Context context, File[] files, Set<File> deleteSet, ExecutorService executor, List<Future<?>> executorJobs) {
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                Future<?> future = executor.submit(() -> loadHierarchicalData(context, file.listFiles(), deleteSet, executor, executorJobs));
                synchronized (executorJobs) {
                    executorJobs.add(future);
                }
            } else {
                File titleDir = file.getParentFile();
                synchronized (deleteSet) {
                    if (deleteSet.contains(titleDir)) {
                        continue;
                    }
                }
                File authorDir = titleDir.getParentFile();
                String author = authorDir.getName();
                String title = titleDir.getName();

                Map<String, Audiobook> bookMap;
                Audiobook fileData;
                synchronized (mHierarchicalData) {
                    bookMap = mHierarchicalData.get(author);
                    if (bookMap == null) {
                        bookMap = new TreeMap<>();
                        mHierarchicalData.put(author, bookMap);
                    }
                }
                synchronized (bookMap) {
                    fileData = bookMap.get(titleDir.getName());
                    if (fileData == null) {
                        fileData = new Audiobook(author, title);
                        bookMap.put(title, fileData);
                    }
                }
                // no need to synchronize fileData since threads are allocated at a directory level, not file level
                if (file.getName().equals(POSITION_FILE_NAME)) {
                    try (Scanner scanner = new Scanner(file)) {
                        while (scanner.hasNextLine()) {
                            String s = scanner.nextLine();
                            if (s.startsWith("track=")) {
                                fileData.setCurrentTrack(Integer.parseInt(s.substring(6)));
                            } else if (s.startsWith("position=")) {
                                fileData.setCurrentPosition(Integer.parseInt(s.substring(9)));
                            }
                        }
                    } catch (FileNotFoundException e) {
                        continue;
                    }
                } else if (file.getName().equals(COVER_FILE_NAME)) {
                    fileData.setCoverArtFile(file);
                } else if (file.getName().equals(TRACKS_FILE_NAME)) {
                    try {
                        final JSONObject obj = new JSONObject(new String(Files.readAllBytes(file.toPath())));
                        final JSONArray trackData = obj.getJSONArray("tracks");
                        final int trackDataLength = trackData.length();
                        for (int i = 0; i < trackDataLength; i++) {
                            final JSONObject trackObj = trackData.getJSONObject(i);
                            final TrackInfo trackInfo = new TrackInfo();
                            trackInfo.setUri(Uri.parse(trackObj.getString("uri")));
                            int checkUriExists = UriUtil.checkUriExists(context, trackInfo.getUri());
                            if (checkUriExists == 1) {
                                trackInfo.setTrackNum(trackObj.getInt("num"));
                                trackInfo.setLength(trackObj.getInt("length"));
                                trackInfo.setChapter(trackObj.getString("chapter"));
                                fileData.addTrack(trackInfo);
                            } else {
                                synchronized (deleteSet) {
                                    deleteSet.add(titleDir);
                                }
                                break;
                            }
                        }
                    } catch (IOException | JSONException e) {
                        synchronized (deleteSet) {
                            deleteSet.add(titleDir);
                        }
                    }
                }
            }
        }
    }

    private void findAndSaveCoverArt(Context context, Map<String, Map<String, Audiobook>> hierarchicalData) {
        try {
            if (!mDataDir.exists() && !mDataDir.mkdir()) {
                return; //TODO: handle
            }

            for (Map.Entry<String, Map<String, Audiobook>> authorEnt : hierarchicalData.entrySet()) {
                File authorDir = new File(mDataDir, authorEnt.getKey());
                for (Map.Entry<String, Audiobook> bookEnt : authorEnt.getValue().entrySet()) {
                    File bookDir = new File(authorDir, bookEnt.getKey());
                    if (!bookDir.exists() && !bookDir.mkdirs()) {
                        return; // TODO: handle
                    }

                    // get and save cover art
                    // first see if it is embedded in the first track
                    // if it isn't, scan the directory for pictures and choose the first one found
                    File destCoverFile = new File(bookDir, COVER_FILE_NAME);
                    TrackInfo firstTrackInfo = bookEnt.getValue().getFirstTrack();
                    ParcelFileDescriptor firstTrackPfd = context.getContentResolver().openFileDescriptor(firstTrackInfo.getUri(), "r");
                    Bitmap cover = TagParser.parseCoverArt(firstTrackPfd.getFd());
                    firstTrackPfd.close();
                    if (cover == null) {
                        DocumentFile[] files;
                        try {
                            files = firstTrackInfo.getDir().listFiles();
                        } catch (NullPointerException e) {
                            continue;
                        }
                        for (DocumentFile file : files) {
                            // check if this is a file
                            if (!file.isFile()) {
                                continue;
                            }

                            // check if this is an image
                            if (!context.getContentResolver().getType(file.getUri()).startsWith("image")) {
                                continue;
                            }

                            // load bitmap
                            try {
                                cover = MediaStore.Images.Media.getBitmap(context.getContentResolver(), file.getUri());
                                break;
                            } catch (Exception e) {
                                continue;
                            }
                        }
                    }
                    // save cover art
                    if (cover != null) {
                        FileOutputStream fOut = new FileOutputStream(destCoverFile);
                        cover.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
                        fOut.flush();
                        fOut.close();
                        bookEnt.getValue().setCoverArtFile(destCoverFile);
                    }
                }
            }
        } catch (SecurityException | IOException e) {
            deleteDatabase();
        }
    }

    private void saveHierarchicalData(Map<String, Map<String, Audiobook>> hierarchicalData) {
        try {
            if (!mDataDir.exists() && !mDataDir.mkdir()) {
                return; //TODO: handle
            }

            for (Map.Entry<String, Map<String, Audiobook>> authorEnt : hierarchicalData.entrySet()) {
                File authorDir = new File(mDataDir, authorEnt.getKey());
                for (Map.Entry<String, Audiobook> bookEnt : authorEnt.getValue().entrySet()) {
                    File bookDir = new File(authorDir, bookEnt.getKey());
                    if (!bookDir.exists() && !bookDir.mkdirs()) {
                        return; // TODO: handle
                    }

                    FileWriter fileWriter = new FileWriter(new File(bookDir, TRACKS_FILE_NAME));
                    fileWriter.write("{");
                    fileWriter.write(bookEnt.getValue().TracksToJsonArray());
                    fileWriter.write("}");
                    fileWriter.close();

                    // save meta data
                    PrintWriter printWriter = new PrintWriter(new File(bookDir, POSITION_FILE_NAME));
                    printWriter.println("track=" + bookEnt.getValue().getCurrentTrack());
                    printWriter.println("position=" + bookEnt.getValue().getCurrentPosition());
                    printWriter.close();
                }
            }
        } catch (SecurityException | IOException e) {
            deleteDatabase();
        }
    }


    public File getDataDir() {
        return mDataDir;
    }

    public void mergeHierarchicalData(Context context, Map<String, Map<String, Audiobook>> hierarchicalData) {
        // first merge
        for (Map.Entry<String, Map<String, Audiobook>> authorEnt : hierarchicalData.entrySet()) {
            String author = authorEnt.getKey();
            Map<String, Audiobook> bookMap = mHierarchicalData.get(author);
            if (bookMap == null) {
                mHierarchicalData.put(author, authorEnt.getValue());
                continue;
            }
            for (Map.Entry<String, Audiobook> bookEnt : authorEnt.getValue().entrySet()) {
                String title = bookEnt.getKey();
                Audiobook fileData = bookMap.get(title);
                if (fileData != null) {
                    // if we already have this audiobook, delete it from the database
                    deleteAudiobook(author, title);
                }
                bookMap.put(title, bookEnt.getValue());
            }
        }

        // now validate
        List<Audiobook> invalidAudiobooks = new ArrayList<>();
        for (Map.Entry<String, Map<String, Audiobook>> authorEnt : mHierarchicalData.entrySet()) {
            for (Map.Entry<String, Audiobook> bookEnt : authorEnt.getValue().entrySet()) {
                Audiobook audiobook = bookEnt.getValue();
                audiobook.sanitize();
                if (!audiobook.isValid()) {
                    invalidAudiobooks.add(audiobook);
                }
            }
        }
        for (Audiobook invalidAudiobook : invalidAudiobooks) {
            deleteAudiobook(invalidAudiobook.getAuthor(), invalidAudiobook.getTitle());
        }

        saveHierarchicalData(mHierarchicalData);
        findAndSaveCoverArt(context, mHierarchicalData);
    }

    public List<AudiobookDataModel> loadAudiobooks() {
        List<AudiobookDataModel> audiobooks = new ArrayList<>();
        for (Map.Entry<String, Map<String, Audiobook>> authorEnt : mHierarchicalData.entrySet()) {
            for (Map.Entry<String, Audiobook> bookEnt : authorEnt.getValue().entrySet()) {
                AudiobookDataModel audiobook = AudiobookDataModel.createAudiobook(authorEnt.getKey(), bookEnt.getKey(), bookEnt.getValue());
                audiobooks.add(audiobook);
            }
        }

        return audiobooks;
    }

    public void updateLocation(AudiobookDataModel audiobook) throws IOException {
        Map<String, Audiobook> bookMap = mHierarchicalData.get(audiobook.getAuthor());
        if (bookMap == null) {
            return;
        }

        Audiobook book = bookMap.get(audiobook.getTitle());
        if (book == null) {
            return;
        }

        boolean different = book.getCurrentTrack() != audiobook.getCurrentTrack() || book.getCurrentPosition() != audiobook.getCurrentPosition();
        if (!different) {
            return;
        }

        book.setCurrentTrack(audiobook.getCurrentTrack());
        book.setCurrentPosition(audiobook.getCurrentPosition());

        File authorDir = new File(mDataDir, audiobook.getAuthor());
        File bookDir = new File(authorDir, audiobook.getTitle());
        File positionFile = new File(bookDir, POSITION_FILE_NAME);

        PrintWriter writer = new PrintWriter(positionFile);
        writer.println("track=" + audiobook.getCurrentTrack());
        writer.println("position=" + audiobook.getCurrentPosition());
        writer.close();
    }
}