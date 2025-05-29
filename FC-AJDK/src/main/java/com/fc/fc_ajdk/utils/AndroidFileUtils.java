package com.fc.fc_ajdk.utils;

import android.content.Context;
import timber.log.Timber;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for Android file operations
 */
public class AndroidFileUtils {
    private static final String TAG = "AndroidFileUtils";

    /**
     * Read a file from app's internal storage
     * @param context Android context
     * @param fileName Name of the file to read
     * @return File contents as a string
     * @throws IOException if file cannot be read
     */
    public static String readFileFromInternalStorage(Context context, String fileName) throws IOException {
        File file = new File(context.getFilesDir(), fileName);
        return readFile(file);
    }

    /**
     * Read a file from app's external storage
     * @param context Android context
     * @param fileName Name of the file to read
     * @return File contents as a string
     * @throws IOException if file cannot be read
     */
    public static String readFileFromExternalStorage(Context context, String fileName) throws IOException {
        File file = new File(context.getExternalFilesDir(null), fileName);
        return readFile(file);
    }

    /**
     * Read a file from a custom path
     * @param filePath Full path to the file
     * @return File contents as a string
     * @throws IOException if file cannot be read
     */
    public static String readFileFromPath(String filePath) throws IOException {
        File file = new File(filePath);
        return readFile(file);
    }

    /**
     * Read a file and return its contents as a string
     * @param file File to read
     * @return File contents as a string
     * @throws IOException if file cannot be read
     */
    public static String readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            Timber.d("Successfully read file: %s", file.getAbsolutePath());
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Timber.e(e, "Failed to read file: %s", file.getAbsolutePath());
            throw e;
        }
    }

    /**
     * Write a string to a file in app's internal storage
     * @param context Android context
     * @param fileName Name of the file to write
     * @param content Content to write to the file
     * @throws IOException if file cannot be written
     */
    public static void writeFileToInternalStorage(Context context, String fileName, String content) throws IOException {
        File file = new File(context.getFilesDir(), fileName);
        writeFile(file, content);
    }

    /**
     * Write a string to a file in app's external storage
     * @param context Android context
     * @param fileName Name of the file to write
     * @param content Content to write to the file
     * @throws IOException if file cannot be written
     */
    public static void writeFileToExternalStorage(Context context, String fileName, String content) throws IOException {
        File file = new File(context.getExternalFilesDir(null), fileName);
        writeFile(file, content);
    }

    /**
     * Write a string to a file at a custom path
     * @param filePath Full path to the file
     * @param content Content to write to the file
     * @throws IOException if file cannot be written
     */
    public static void writeFileToPath(String filePath, String content) throws IOException {
        File file = new File(filePath);
        writeFile(file, content);
    }

    /**
     * Write a string to a file
     * @param file File to write to
     * @param content Content to write to the file
     * @throws IOException if file cannot be written
     */
    public static void writeFile(File file, String content) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
            Timber.d("Successfully wrote to file: %s", file.getAbsolutePath());
        } catch (IOException e) {
            Timber.e(e, "Failed to write to file: %s", file.getAbsolutePath());
            throw e;
        }
    }

    /**
     * Delete a file from app's internal storage
     * @param context Android context
     * @param fileName Name of the file to delete
     * @return true if the file was deleted, false otherwise
     */
    public static boolean deleteFileFromInternalStorage(Context context, String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        return deleteFile(file);
    }

    /**
     * Delete a file from app's external storage
     * @param context Android context
     * @param fileName Name of the file to delete
     * @return true if the file was deleted, false otherwise
     */
    public static boolean deleteFileFromExternalStorage(Context context, String fileName) {
        File file = new File(context.getExternalFilesDir(null), fileName);
        return deleteFile(file);
    }

    /**
     * Delete a file at a custom path
     * @param filePath Full path to the file
     * @return true if the file was deleted, false otherwise
     */
    public static boolean deleteFileFromPath(String filePath) {
        File file = new File(filePath);
        return deleteFile(file);
    }

    /**
     * Delete a file
     * @param file File to delete
     * @return true if the file was deleted, false otherwise
     */
    public static boolean deleteFile(File file) {
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                Timber.d("Successfully deleted file: %s", file.getAbsolutePath());
            } else {
                Timber.w("Failed to delete file: %s", file.getAbsolutePath());
            }
            return deleted;
        }
        Timber.d("File does not exist: %s", file.getAbsolutePath());
        return false;
    }

    /**
     * Check if a file exists in app's internal storage
     * @param context Android context
     * @param fileName Name of the file to check
     * @return true if the file exists, false otherwise
     */
    public static boolean fileExistsInInternalStorage(Context context, String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        return file.exists();
    }

    /**
     * Check if a file exists in app's external storage
     * @param context Android context
     * @param fileName Name of the file to check
     * @return true if the file exists, false otherwise
     */
    public static boolean fileExistsInExternalStorage(Context context, String fileName) {
        File file = new File(context.getExternalFilesDir(null), fileName);
        return file.exists();
    }

    /**
     * Check if a file exists at a custom path
     * @param filePath Full path to the file
     * @return true if the file exists, false otherwise
     */
    public static boolean fileExists(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

    /**
     * Read a file and return its contents as a byte array
     * @param filePath Full path to the file
     * @return File contents as a byte array
     * @throws IOException if file cannot be read
     */
    public static byte[] readFileAsBytes(String filePath) throws IOException {
        File file = new File(filePath);
        return readFileAsBytes(file);
    }

    /**
     * Read a file and return its contents as a byte array
     * @param file File to read
     * @return File contents as a byte array
     * @throws IOException if file cannot be read
     */
    public static byte[] readFileAsBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return data;
        }
    }
} 