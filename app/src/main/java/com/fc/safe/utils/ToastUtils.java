package com.fc.safe.utils;

import android.content.Context;
import android.widget.Toast;

import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.db.ToastManager;

public class ToastUtils {
    private static final String TAG = "ToastUtils";
    
    public static void makeText(Context context, CharSequence text) {
        makeText(context, text, Toast.LENGTH_SHORT, "INFO");
    }

    public enum type{
        INFO,
        WARNING,
        ERROR
    }
    
    public static void makeText(Context context, CharSequence text, int duration, String level) {
        // Guard against null context
        if (context == null) {
            TimberLogger.e(TAG, "Cannot show toast: context is null. Message: %s", text);
            return;
        }

        try {
            // Show the toast
            Toast.makeText(context, text, duration).show();

            // Save to ToastManager - try to get instance with context first, fallback to singleton
            try {
                ToastManager toastManager = ToastManager.getInstance();
                if (toastManager != null) {
                    toastManager.saveToastMessage(text.toString(), level);
                }


            } catch (Exception managerError) {
                TimberLogger.e(TAG, "Error saving to ToastManager: " + managerError.getMessage(), managerError);
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error showing and saving toast: " + e.getMessage(), e);
            // Fallback to regular toast if there's any issue
            try {
                Toast.makeText(context, text, duration).show();
            } catch (Exception fallbackError) {
                TimberLogger.e(TAG, "Fallback toast also failed: " + fallbackError.getMessage(), fallbackError);
            }
        }
    }
    
    public static void makeText(Context context, int resId) {
        makeText(context, resId, Toast.LENGTH_SHORT, "INFO");
    }
    
    public static void makeText(Context context, int resId, int duration, String level) {
        try {
            String text = context.getString(resId);
            makeText(context, text, duration, level);
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error getting string resource: " + e.getMessage(), e);
            // Fallback to regular toast
            try {
                Toast.makeText(context, resId, duration).show();
            } catch (Exception fallbackError) {
                TimberLogger.e(TAG, "Fallback toast also failed: " + fallbackError.getMessage(), fallbackError);
            }
        }
    }
    
    // Convenience methods for different levels
    public static void showInfo(Context context, CharSequence text) {
        makeText(context, text, Toast.LENGTH_SHORT, "INFO");
    }
    
    public static void showInfo(Context context, int resId) {
        makeText(context, resId, Toast.LENGTH_SHORT, "INFO");
    }
    
    public static void showWarning(Context context, CharSequence text) {
        makeText(context, text, Toast.LENGTH_LONG, "WARNING");
    }
    
    public static void showWarning(Context context, int resId) {
        makeText(context, resId, Toast.LENGTH_LONG, "WARNING");
    }
    
    public static void showError(Context context, CharSequence text) {
        makeText(context, text, Toast.LENGTH_LONG, "ERROR");
    }
    
    public static void showError(Context context, int resId) {
        makeText(context, resId, Toast.LENGTH_LONG, "ERROR");
    }
}