/*
 * Copyright (c) 2025 Android project OpenVision API
 * All rights reserved.
 * Project: My Application
 * File: PhotoHistoryAdapter.java
 * Last Modified: 5/10/2025 3:34
 */

package vn.edu.usth.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class PhotoHistoryAdapter extends RecyclerView.Adapter<PhotoHistoryAdapter.VH> {

    private final List<PhotoEntry> items;
    private final SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
    private OnPhotoClickListener onPhotoClickListener;

    public void setOnPhotoClickListener(OnPhotoClickListener listener) {
        this.onPhotoClickListener = listener;
    }

    public PhotoHistoryAdapter(List<PhotoEntry> items) {
        this.items = items;
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        PhotoEntry e = items.get(position);
        try {
            Glide.with(h.itemView.getContext())
                    .load(e.getUri())
                    .transform(new RoundedCorners(16))
                    .centerCrop()
                    .into(h.image);
        } catch (Exception ex) {
            h.image.setImageURI(e.getUri());
        }
        h.date.setText(fmt.format(e.getDateTaken()));

        // Set click listener
        h.itemView.setOnClickListener(v -> {
            if (onPhotoClickListener != null) {
                onPhotoClickListener.onPhotoClick(e);
            }
        });
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
        return new VH(v);
    }

    public interface OnPhotoClickListener {
        void onPhotoClick(PhotoEntry photoEntry);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView date;

        VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image_photo);
            date = itemView.findViewById(R.id.text_date);
        }
    }
}
