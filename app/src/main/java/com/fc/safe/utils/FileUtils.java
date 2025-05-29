package com.fc.safe.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileUtils {
    private static final String TAG = "FileUtils";

    /**
     * Get the file path from a Uri
     * @param context The context
     * @param uri The Uri of the file
     * @return The file path
     */
    public static String getPathFromUri(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }

        // If the URI is a file URI, return the path directly
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        // For content URIs, we need to copy the file to a temporary location
        try {
            // Create a temporary file
            File tempFile = File.createTempFile("temp", null, context.getCacheDir());
            tempFile.deleteOnExit();

            // Copy the content from the URI to the temporary file
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(tempFile)) {
                
                if (inputStream == null) {
                    return null;
                }

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Error getting path from URI: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get the file name from a Uri
     * @param context The context
     * @param uri The Uri of the file
     * @return The file name
     */
    public static String getFileNameFromUri(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }

        String result = null;
        if (uri.getScheme().equals("content")) {
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting file name from URI: " + e.getMessage(), e);
            }
        }
        
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        
        return result;
    }

    /**
     * Check if a file exists
     * @param filePath The path to the file
     * @return True if the file exists, false otherwise
     */
    public static boolean fileExists(String filePath) {
        if (filePath == null) {
            return false;
        }
        
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }
} 