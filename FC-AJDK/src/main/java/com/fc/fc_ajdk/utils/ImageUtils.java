package com.fc.fc_ajdk.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class ImageUtils {
    private static final String TAG = "ImageUtils";

    public static boolean savePng(byte[] imageData, String outputPath) {
        if (imageData == null || outputPath == null) {
            Log.e(TAG, "Invalid input: imageData is null or outputPath is null.");
            return false;
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            if (bitmap == null) {
                Log.e(TAG, "Conversion to Bitmap failed, possibly due to incorrect data format.");
                return false;
            }

            File outputFile = new File(outputPath);
            FileOutputStream fos = new FileOutputStream(outputFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            bitmap.recycle();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error saving PNG: " + e.getMessage());
            return false;
        }
    }

    public static Bitmap convertToBitmap(byte[] imageData) {
        if (imageData == null) {
            Log.e(TAG, "Invalid input: imageData is null.");
            return null;
        }

        try {
            return BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
        } catch (Exception e) {
            Log.e(TAG, "Error converting to Bitmap: " + e.getMessage());
            return null;
        }
    }

    public static byte[] convertToByteArray(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Invalid input: bitmap is null.");
            return null;
        }

        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            return stream.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error converting to byte array: " + e.getMessage());
            return null;
        }
    }
}
