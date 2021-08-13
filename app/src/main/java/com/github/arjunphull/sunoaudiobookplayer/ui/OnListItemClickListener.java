package com.github.arjunphull.sunoaudiobookplayer.ui;

import android.view.View;

import com.github.arjunphull.sunoaudiobookplayer.datamodel.AudiobookDataModel;

public interface OnListItemClickListener {
    void onListItemClick();
    void onListItemLongClick(View v, AudiobookDataModel audiobook);
}
