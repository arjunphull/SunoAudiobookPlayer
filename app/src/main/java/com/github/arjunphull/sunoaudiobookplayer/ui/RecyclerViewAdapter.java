package com.github.arjunphull.sunoaudiobookplayer.ui;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.github.arjunphull.sunoaudiobookplayer.R;
import com.github.arjunphull.sunoaudiobookplayer.datamodel.AudiobookDataModel;

import java.util.List;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {
    private static AudiobookDataModel sSelectedAudiobook;

    private final Context mContext;
    private final OnListItemClickListener mOnClickListener;
    private final List<AudiobookDataModel> mAudiobooks;

    public RecyclerViewAdapter(Context context, OnListItemClickListener onClickListener, List<AudiobookDataModel> audiobooks) {
        mContext = context;
        mOnClickListener = onClickListener;
        mAudiobooks = audiobooks;
    }

    public static AudiobookDataModel getSelectedAudiobook() {
        return sSelectedAudiobook;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.recyclerviewitem_audiobook, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerViewAdapter.ViewHolder viewHolder, int position) {
        AudiobookDataModel audiobook = mAudiobooks.get(position);
        viewHolder.mTitleTextView.setText(audiobook.getTitle());
        viewHolder.mAuthorTextView.setText(audiobook.getAuthor());
        updateRemainingTime(viewHolder, audiobook);
        if (audiobook.getCoverArtPath() != null) {
            viewHolder.mCoverImageView.setImageBitmap(BitmapFactory.decodeFile(audiobook.getCoverArtPath()));
        } else {
            viewHolder.mCoverImageView.setImageBitmap(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_default_cover));
        }
    }

    @Override
    public int getItemCount() {
        return mAudiobooks.size();
    }

    public void updateRemainingTime(RecyclerViewAdapter.ViewHolder viewHolder, AudiobookDataModel audiobook) {
        viewHolder.mRemainingTimeTextView.setText(audiobook.getRemainingTimeFormatted());
        viewHolder.mBookProgressBar.setProgress((int)(viewHolder.mBookProgressBar.getMax() * (audiobook.getElapsedSeconds() / (float)(audiobook.getLength() / 1000))));
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private final TextView mTitleTextView;
        private final TextView mAuthorTextView;
        private final TextView mRemainingTimeTextView;
        private final ImageView mCoverImageView;
        private final ProgressBar mBookProgressBar;

        public ViewHolder(View itemView) {
            super(itemView);
            itemView.setLongClickable(true);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
            mTitleTextView = itemView.findViewById(R.id.tvTitle);
            mAuthorTextView = itemView.findViewById(R.id.tvAuthor);
            mRemainingTimeTextView = itemView.findViewById(R.id.tvRemainingTime);
            mCoverImageView = itemView.findViewById(R.id.ivCoverArt);
            mBookProgressBar = itemView.findViewById(R.id.pbBook);
        }

        @Override
        public void onClick(View v) {
            sSelectedAudiobook = mAudiobooks.get(getAdapterPosition());
            mOnClickListener.onListItemClick();
        }

        public boolean onLongClick(View v) {
            mOnClickListener.onListItemLongClick(v, getAdapterPosition());
            return true;
        }
    }
}
