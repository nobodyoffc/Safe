package com.fc.safe.utils;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager2.widget.ViewPager2;

import com.fc.safe.R;
import com.fc.safe.models.BackupHeader;
import com.fc.safe.qr.QRPagerAdapter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for generating and displaying QR codes.
 * This class can be used across multiple activities to avoid code duplication.
 */
public class QRCodeGenerator {

    public static final int DEFAULT_CAPACITY = 300;

    /**
     * Generates QR codes from the given content and displays them in a dialog.
     * 
     * @param context The context to use for creating the dialog
     * @param content The content to encode in the QR code
     */
    public static void generateAndShowQRCode(Context context, String content) {
        List<Bitmap> qrBitmaps = generateQRBitmaps(content);
        
        if (!qrBitmaps.isEmpty()) {
            showQRDialog(context, qrBitmaps);
        } else {
            Toast.makeText(context, context.getString(R.string.error_creating_qr), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Generates QR code bitmaps from the given content.
     * 
     * @param content The content to encode in the QR code
     * @return A list of QR code bitmaps
     */
    public static List<Bitmap> generateQRBitmaps(String content) {
        List<Bitmap> qrBitmaps = new ArrayList<>();

        try {
            // Create encoding hints for UTF-8
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);

            // Split content into chunks if it exceeds 400 bytes
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            if (contentBytes.length > DEFAULT_CAPACITY) {
                List<String> chunks = splitContent(content);
                for (String chunk : chunks) {
                    BitMatrix bitMatrix = new MultiFormatWriter().encode(
                            chunk,
                            BarcodeFormat.QR_CODE,
                            461,
                            461,
                            hints
                    );
                    qrBitmaps.add(createBitmapFromBitMatrix(bitMatrix));
                }
            } else {
                BitMatrix bitMatrix = new MultiFormatWriter().encode(
                        content,
                        BarcodeFormat.QR_CODE,
                        461,
                        461,
                        hints
                );
                qrBitmaps.add(createBitmapFromBitMatrix(bitMatrix));
            }
        } catch (WriterException e) {
            // Error is handled by the caller
        }
        
        return qrBitmaps;
    }

    /**
     * Splits content into chunks that fit within QR code capacity.
     * 
     * @param content The content to split
     * @return A list of content chunks
     */
    private static List<String> splitContent(String content) {
        List<String> chunks = new ArrayList<>();
        int maxBytes = 400;  // Maximum bytes per QR code
        
        int startIndex = 0;
        while (startIndex < content.length()) {
            int endIndex = startIndex;
            int currentChunkBytes = 0;
            
            // Try to add characters until we hit the byte limit
            while (endIndex < content.length()) {
                String nextChar = content.substring(endIndex, Math.min(endIndex + 1, content.length()));
                int nextCharBytes = nextChar.getBytes(StandardCharsets.UTF_8).length;
                
                // If adding next character would exceed the limit, break
                if (currentChunkBytes + nextCharBytes > maxBytes) {
                    break;
                }
                
                currentChunkBytes += nextCharBytes;
                endIndex++;
            }
            
            // If we couldn't add even one character (shouldn't happen with 400 byte limit)
            if (endIndex == startIndex) {
                endIndex = startIndex + 1;  // Force include at least one character
            }
            
            // Add the chunk
            chunks.add(content.substring(startIndex, endIndex));
            startIndex = endIndex;
        }
        
        return chunks;
    }

    /**
     * Converts a BitMatrix to a Bitmap.
     * 
     * @param bitMatrix The BitMatrix to convert
     * @return A Bitmap representation of the BitMatrix
     */
    private static Bitmap createBitmapFromBitMatrix(BitMatrix bitMatrix) {
        int width = bitMatrix.getWidth();
        int height = bitMatrix.getHeight();
        int[] pixels = new int[width * height];
        
        // Convert bit matrix to pixel array
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        
        // Create the bitmap
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        
        return bitmap;
    }

    /**
     * Shows a dialog with the QR code bitmaps.
     * 
     * @param context The context to use for creating the dialog
     * @param qrBitmaps The QR code bitmaps to display
     */
    public static void showQRDialog(Context context, List<Bitmap> qrBitmaps) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = View.inflate(context, R.layout.dialog_qr_display, null);
        
        ViewPager2 viewPager = dialogView.findViewById(R.id.qrViewPager);
        TextView pageIndicator = dialogView.findViewById(R.id.pageIndicator);
        
        viewPager.setOffscreenPageLimit(1);
        viewPager.setUserInputEnabled(true);
        
        QRPagerAdapter adapter = new QRPagerAdapter(qrBitmaps);
        viewPager.setAdapter(adapter);
        
        // Get the text color based on the current theme
        int textColor;
        int nightModeFlags = context.getResources().getConfiguration().uiMode & 
                             Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            // Night mode is active, use white text
            textColor = 0xFFFFFFFF;
        } else {
            // Day mode is active, use black text
            textColor = 0xFF000000;
        }
        
        if (qrBitmaps.size() > 1) {
            pageIndicator.setVisibility(View.VISIBLE);
            pageIndicator.setText(String.format("1/%d", qrBitmaps.size()));
            pageIndicator.setTextColor(textColor);
            
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    pageIndicator.setText(String.format("%d/%d", position + 1, qrBitmaps.size()));
                }
            });
        } else {
            pageIndicator.setVisibility(View.GONE);
        }
        
        builder.setView(dialogView)
               .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
               .setNeutralButton(R.string.save, (dialog, which) -> {
                   saveQRCodes(context, qrBitmaps);
               });
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Set the button text color
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(textColor);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(textColor);
    }

    /**
     * Saves the QR code bitmaps to the device's gallery.
     * 
     * @param context The context to use for saving the QR codes
     * @param qrBitmaps The QR code bitmaps to save
     */
    private static void saveQRCodes(Context context, List<Bitmap> qrBitmaps) {
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

            Uri imageUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri != null) {
                try (OutputStream out = context.getContentResolver().openOutputStream(imageUri)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    savedCount++;
                } catch (IOException e) {
                    // Continue with next image if one fails
                }
            }
            index++;
        }

        if (savedCount > 0) {
            Toast.makeText(context, context.getString(R.string.qr_saved_count, savedCount), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, context.getString(R.string.error_saving_qr), Toast.LENGTH_SHORT).show();
        }
    }

    public static List<List<Bitmap>> makeQRBitmapsList(List<String> jsonList, BackupHeader backupHeader) {
        int count = 0;
        List<List<Bitmap>> qrBitmapsList = new ArrayList<>();
        if (jsonList == null || jsonList.isEmpty()) return qrBitmapsList; // Return empty list if no data

        for(String json : jsonList) {
            if (json != null && !json.isEmpty()) { // Add null/empty check for each json string
                List<Bitmap> bitmaps = generateQRBitmaps(json);
                qrBitmapsList.add(bitmaps);
                count += bitmaps.size();  // Count the actual number of QR codes generated
            }
        }
        if(backupHeader!=null) {
            backupHeader.setQrCodes(count);
            List<Bitmap> headerBitmaps = generateQRBitmaps(backupHeader.toNiceJson());
            qrBitmapsList.get(1).clear();
            qrBitmapsList.get(1).addAll(headerBitmaps);
        }

        return qrBitmapsList;
    }
}