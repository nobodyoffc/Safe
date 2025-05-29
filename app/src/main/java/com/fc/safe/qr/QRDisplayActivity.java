package com.fc.safe.qr;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.fc.safe.R;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class QRDisplayActivity extends AppCompatActivity {

    private List<Bitmap> qrBitmaps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_display);

        ViewPager2 viewPager = findViewById(R.id.viewPager);
        qrBitmaps = getIntent().getParcelableArrayListExtra("qr_bitmaps");
        
        QRPagerAdapter adapter = new QRPagerAdapter(qrBitmaps);
        viewPager.setAdapter(adapter);

        Button saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveAllQRCodes());
    }

    private void saveAllQRCodes() {
        int savedCount = 0;
        int index = 0;
        for (Bitmap bitmap : qrBitmaps) {
            String fileName = "QR_" + System.currentTimeMillis() + "_" + index + ".png";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            }

            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(imageUri)) {
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        savedCount++;
                    }
                } catch (IOException e) {
                    // Continue with next image if one fails
                }
            }
            index++;
        }

        if (savedCount > 0) {
            Toast.makeText(this, getString(R.string.qr_saved_count, savedCount), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.error_saving_qr), Toast.LENGTH_SHORT).show();
        }
    }
    
    private static class QRPagerAdapter extends RecyclerView.Adapter<QRPagerAdapter.QRViewHolder> {
        private final List<Bitmap> qrBitmaps;
        
        QRPagerAdapter(List<Bitmap> qrBitmaps) {
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
            
            QRViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.qrImageView);
            }
        }
    }
} 