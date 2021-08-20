package com.github.arjunphull.sunoaudiobookplayer.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.arjunphull.sunoaudiobookplayer.BuildConfig;
import com.github.arjunphull.sunoaudiobookplayer.R;
import com.github.arjunphull.sunoaudiobookplayer.datamodel.AudiobookDataModel;
import com.github.arjunphull.sunoaudiobookplayer.file.Database;
import com.github.arjunphull.sunoaudiobookplayer.file.DirScanner;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.StampedLock;

public class LibraryActivity extends AppCompatActivity implements OnListItemClickListener {
    private final int STORAGE_REQUEST = 100;
    private final String[] STORAGE_PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    private LibraryActivity activity = this;
    private FloatingActionButton mAddBooksBtn;
    private ProgressBar mBusyIndicator;
    private RecyclerView mRecyclerView;
    private List<AudiobookDataModel> mAudiobooks;
    private StampedLock mUiLock;

    private void requestExternalStorageAccess() {
        if (!canAccessExternalSd()) {
            requestPermissions(STORAGE_PERMISSIONS, STORAGE_REQUEST);
        }
    }

    private boolean canAccessExternalSd() {
        return (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE));
    }

    private void setAddBooksBtnVisible(boolean visible) {
        if (visible) {
            mAddBooksBtn.setVisibility(View.VISIBLE);
            mBusyIndicator.setVisibility(View.INVISIBLE);
        } else {
            mAddBooksBtn.setVisibility(View.INVISIBLE);
            mBusyIndicator.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_library);

        mUiLock = new StampedLock();
        mAddBooksBtn = findViewById(R.id.btnAddBooks);
        mBusyIndicator = findViewById(R.id.pbBusyCircle);
        mRecyclerView = findViewById(R.id.rvAudiobooks);

        requestExternalStorageAccess();
        // give user some guidance is this is a first start
        File firstStartFile = new File(getFilesDir(), "started");
        if (!firstStartFile.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.welcome)
                    .setMessage(R.string.first_start_msg)
                    .setNegativeButton(android.R.string.yes, null)
                    .show();
            try {
                firstStartFile.createNewFile();
            } catch (IOException e) {
                // do nothing
            }
        }

        mAudiobooks = Database.getInstance(activity).loadAudiobooks();
        mRecyclerView.setAdapter(new RecyclerViewAdapter(this, this, mAudiobooks));
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mAddBooksBtn.setOnClickListener(view -> {
            // request read access to external storage.
            requestExternalStorageAccess();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            startActivityForResult(Intent.createChooser(intent, "Choose directory"), 35);
        });

        setSupportActionBar(findViewById(R.id.toolbar));
    }

    @Override
    protected void onResume() {
        super.onResume();
        RecyclerView rvAudiobooks = findViewById(R.id.rvAudiobooks);
        // onResume seems to get called spuriously so make sure a library update is not in progress
        long stamp = mUiLock.tryWriteLock();
        if (stamp == 0) {
            return;
        }
        rvAudiobooks.getAdapter().notifyDataSetChanged();
        mUiLock.unlockWrite(stamp);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.miAbout:
                new AlertDialog.Builder(this)
                        .setTitle(R.string.about)
                        .setMessage("Version " + BuildConfig.VERSION_NAME + "\n\n" + "Copyright © 2021 Arjun Phull")
                        .setNegativeButton(android.R.string.yes, null)
                        .show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onListItemClick() {
        long stamp = mUiLock.tryWriteLock();
        if (stamp == 0) {
            return;
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        startActivity(intent);
        mUiLock.unlock(stamp);
    }

    @Override
    public void onListItemLongClick(View v, int position) {
        long stamp = mUiLock.tryWriteLock();
        if (stamp == 0) {
            return;
        }

        AudiobookDataModel audiobook = mAudiobooks.get(position);

        PopupMenu popup = new PopupMenu(this, v);
        Menu menu = popup.getMenu();
        menu.add(R.string.remove_from_library);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().toString().equals(getString(R.string.remove_from_library))) {
                Database database = Database.getInstance(activity);
                database.deleteAudiobook(audiobook.getAuthor(), audiobook.getTitle());
                RecyclerView rvAudiobooks = findViewById(R.id.rvAudiobooks);

                mAudiobooks.remove(position);
                rvAudiobooks.removeViewAt(position);
                rvAudiobooks.getAdapter().notifyItemRemoved(position);
                rvAudiobooks.getAdapter().notifyItemRangeChanged(position, mAudiobooks.size());
                return true;
            } else {
                return false;
            }
        });
        popup.show();
        mUiLock.unlock(stamp);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case 35:
                // make progress bar visible and speed dial invisible (both for aesthetics and for synchronization purposes)
                setAddBooksBtnVisible(false);

                // scan for audiobook asynchronously
                new Thread(() -> {
                    long stamp = mUiLock.writeLock();
                    Uri dirUri = data.getData();
                    LibraryActivity activity = this;
                    activity.getContentResolver().takePersistableUriPermission(dirUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    Database database = Database.getInstance(activity);
                    DirScanner scanner = new DirScanner(database.getDataDir());
                    try {
                        database.mergeHierarchicalData(activity, scanner.scanDirectory(activity, dirUri));
                    } catch (Exception e) {
                        database.deleteDatabase();
                    }

                    mAudiobooks = Database.getInstance(activity).loadAudiobooks();

                    // populate the recyclerview on the UI thread
                    runOnUiThread(() -> {
                        RecyclerView rvAudiobooks = findViewById(R.id.rvAudiobooks);
                        rvAudiobooks.setAdapter(new RecyclerViewAdapter(activity, activity, mAudiobooks));
                        setAddBooksBtnVisible(true);
                        mUiLock.unlockWrite(stamp);
                    });
                }).start();
                break;
        }
    }
}