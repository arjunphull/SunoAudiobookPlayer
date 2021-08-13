package com.github.arjunphull.sunoaudiobookplayer.file;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.documentfile.provider.DocumentFile;

import com.github.arjunphull.sunoaudiobookplayer.util.Triplet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class DirScanner {
    private final int MAX_OPEN_FILES = 200;
    private final int QUEUE_THRESHOLD = 15;
    private final String FIFO_FILE_NAME = "fifo";

    private final File mDataDir;

    public DirScanner(File dataDir) {
        mDataDir = dataDir;
    }

    private void getFilesFromDocumentTree(Context context, DocumentFile dir, BlockingQueue<Triplet<ParcelFileDescriptor, Uri, DocumentFile>> fileQueue) {
        DocumentFile[] documentFiles = dir.listFiles();
        for (DocumentFile df : documentFiles) {
            if (df.isDirectory()) {
                getFilesFromDocumentTree(context, df, fileQueue);
            } else if (df.isFile()) {
                try {
                    Uri docUri = df.getUri();
                    ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(docUri, "r");
                    fileQueue.put(Triplet.create(pfd, docUri, dir));
                } catch (IOException e) {
                    continue;
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public Map<String, Map<String, Audiobook>> scanDirectory(Context context, Uri dirUri) throws InterruptedException, IllegalArgumentException {
        Map<String, Map<String, Audiobook>> hierarchicalData = new TreeMap<>();
        TagParser tagParser = new TagParser(context, new File(mDataDir.getParentFile(), FIFO_FILE_NAME));
        BlockingQueue<Triplet<ParcelFileDescriptor, Uri, DocumentFile>> fileQueue = new ArrayBlockingQueue<>(MAX_OPEN_FILES / 2);
        List<Triplet<ParcelFileDescriptor, Uri, DocumentFile>> transferFileList = new ArrayList<>(MAX_OPEN_FILES / 2);
        AtomicBoolean iteratingTree = new AtomicBoolean(true);

        // iterate directory tree asynchronously
        Thread t = new Thread(() -> {
            getFilesFromDocumentTree(context, DocumentFile.fromTreeUri(context, dirUri), fileQueue);
            iteratingTree.set(false);
        });
        t.start();

        ArrayDeque<Triplet<ParcelFileDescriptor, Uri, DocumentFile>> buffer = new ArrayDeque<>(QUEUE_THRESHOLD*3);
        while (iteratingTree.get() || !fileQueue.isEmpty() || !transferFileList.isEmpty()) {
            fileQueue.drainTo(buffer);
            while (!buffer.isEmpty()) {
                transferFileList.add(buffer.poll());
            }

            if (transferFileList.size() >= QUEUE_THRESHOLD || !iteratingTree.get()) {
                tagParser.parseTagsAsync(transferFileList, hierarchicalData);
                transferFileList.clear();
            } else {
                try {
                    TimeUnit.MILLISECONDS.sleep(300);
                } catch (InterruptedException e) {
                    throw e;
                }
            }
        }
        t.join();
        tagParser.release();

        return hierarchicalData;
    }
}
