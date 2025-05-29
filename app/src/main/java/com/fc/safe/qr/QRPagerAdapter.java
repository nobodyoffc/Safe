package com.fc.safe.qr;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fc.safe.R;

import java.util.List;

public class QRPagerAdapter extends RecyclerView.Adapter<QRPagerAdapter.QRViewHolder> {
    private final List<Bitmap> qrBitmaps;

    public QRPagerAdapter(List<Bitmap> qrBitmaps) {
        this.qrBitmaps = qrBitmaps;
    }

    @NonNull
    @Override
    public QRViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_qr_display, parent, false);
        return new QRViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QRViewHolder holder, int position) {
        holder.imageView.setImageBitmap(qrBitmaps.get(position));
    }

    @Override
    public int getItemCount() {
        return qrBitmaps.size();
    }

    static class QRViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        QRViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.qrImageView);
        }
    }
} 