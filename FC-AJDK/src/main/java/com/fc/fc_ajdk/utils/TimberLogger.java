package com.fc.fc_ajdk.utils;

import timber.log.Timber;

/**
 * Utility class for centralized Timber logging
 * Provides a unified interface for logging in both Android and command-line environments
 */
public class TimberLogger {
    private static final String DEFAULT_TAG = "FC-AJDK";
    private static String appTag = DEFAULT_TAG;
    private static boolean filterGmsLogs = true;

    /**
     * Initialize the logger
     * @param tag The tag to use for logs
     */
    public static void init(String tag) {
        appTag = tag != null ? tag : DEFAULT_TAG;
        
        // If not already planted, plant a debug tree
        if (Timber.forest().isEmpty()) {
            // Create a custom tree implementation
            Timber.plant(new CustomDebugTree());
            // Log initialization to confirm it's working
            android.util.Log.d(appTag, "TimberLogger initialized with tag: " + appTag);
        }
    }

    /**
     * Configure log filtering options
     * @param filterGms Whether to filter out Google Play Services logs
     */
    public static void configureLogging(boolean filterGms) {
        filterGmsLogs = filterGms;
        // Replant the tree with new configuration
        Timber.uprootAll();
        Timber.plant(new CustomDebugTree());
    }
    
    /**
     * Custom debug tree implementation
     */
    private static class CustomDebugTree extends Timber.Tree {
        @Override
        protected void log(int priority, String tag, String message, Throwable t) {
            // Skip GMS logs if filtering is enabled
            if (filterGmsLogs && tag != null && tag.contains("gms")) {
                return;
            }

            // Use the provided tag if available, otherwise use appTag
            String finalTag = tag != null ? tag : appTag;
            
            // For formatted messages, we need to handle them differently
            // Since we can't access the format arguments directly from Timber,
            // we'll just log the message as is
            
            switch (priority) {
                case android.util.Log.VERBOSE:
                    android.util.Log.v(finalTag, message, t);
                    break;
                case android.util.Log.DEBUG:
                    android.util.Log.d(finalTag, message, t);
                    break;
                case android.util.Log.INFO:
                    android.util.Log.i(finalTag, message, t);
                    break;
                case android.util.Log.WARN:
                    android.util.Log.w(finalTag, message, t);
                    break;
                case android.util.Log.ERROR:
                    android.util.Log.e(finalTag, message, t);
                    break;
                default:
                    android.util.Log.d(finalTag, message, t);
                    break;
            }
        }
    }

    /**
     * Log a verbose message
     * @param message The message to log
     */
    public static void v(String message) {
        Timber.tag(appTag).v(message);
    }

    /**
     * Log a debug message
     * @param message The message to log
     */
    public static void d(String message) {
        Timber.tag(appTag).d(message);
    }

    /**
     * Log an info message
     * @param message The message to log
     */
    public static void i(String message) {
        Timber.tag(appTag).i(message);
    }

    /**
     * Log a warning message
     * @param message The message to log
     */
    public static void w(String message) {
        Timber.tag(appTag).w(message);
    }

    /**
     * Log an error message
     * @param message The message to log
     */
    public static void e(String message) {
        Timber.tag(appTag).e(message);
    }

    /**
     * Log an error message with an exception
     * @param message The message to log
     * @param throwable The exception to log
     */
    public static void e(String message, Throwable throwable) {
        Timber.tag(appTag).e(throwable, message);
    }

    /**
     * Log a message with format and arguments
     * @param format The format string
     * @param args The arguments to format
     */
    public static void d(String tag,String format, Object... args) {
        String formattedMessage = String.format(format, args);
        Timber.tag(tag).d(formattedMessage);
    }

    /**
     * Log an error message with format and arguments
     * @param format The format string
     * @param args The arguments to format
     */
    public static void e(String tag,String format, Object... args) {
        String formattedMessage = String.format(format, args);
        Timber.tag(tag).e(formattedMessage);
    }

    /**
     * Log a warning message with format and arguments
     * @param format The format string
     * @param args The arguments to format
     */
    public static void w(String tag,String format, Object... args) {
        String formattedMessage = String.format(format, args);
        Timber.tag(tag).w(formattedMessage);
    }

    /**
     * Log an info message with format and arguments
     * @param format The format string
     * @param args The arguments to format
     */
    public static void i(String tag,String format, Object... args) {
        String formattedMessage = String.format(format, args);
        Timber.tag(tag).i(formattedMessage);
    }
    
    /**
     * Log a debug message with a specific tag
     * @param tag The tag to use for the log
     * @param message The message to log
     */
    public static void d(String tag, String message) {
        Timber.tag(tag).d(message);
    }
    
    /**
     * Log an error message with a specific tag
     * @param tag The tag to use for the log
     * @param message The message to log
     */
    public static void e(String tag, String message) {
        Timber.tag(tag).e(message);
    }
    
    /**
     * Log an error message with a specific tag and exception
     * @param tag The tag to use for the log
     * @param message The message to log
     * @param throwable The exception to log
     */
    public static void e(String tag, String message, Throwable throwable) {
        Timber.tag(tag).e(throwable, message);
    }
}