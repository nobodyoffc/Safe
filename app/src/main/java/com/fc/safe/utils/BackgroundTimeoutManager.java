package com.fc.safe.utils;

import android.app.Activity;
import android.content.Intent;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.initiate.CheckPasswordActivity;

public class BackgroundTimeoutManager {
    private static final String TAG = "BackgroundTimeoutManager";
    private static final long BACKGROUND_TIMEOUT = 15000; // 15 seconds in milliseconds
    
    private static long lastBackgroundTime = 0;
    private static boolean isInBackground = false;
    
    public static void onAppBackground() {
        if (!isInBackground) {
            lastBackgroundTime = System.currentTimeMillis();
            isInBackground = true;
            TimberLogger.d(TAG, "App entered background at: " + lastBackgroundTime);
        }
    }
    
    public static void onAppForeground(Activity activity) {
        if (isInBackground) {
            long currentTime = System.currentTimeMillis();
            long timeInBackground = currentTime - lastBackgroundTime;
            
            TimberLogger.d(TAG, "App returned to foreground. Time in background: " + timeInBackground + "ms");
            
            if (timeInBackground >= BACKGROUND_TIMEOUT) {
                TimberLogger.d(TAG, "Background timeout exceeded, launching password check");
                launchPasswordCheck(activity);
            }
            
            isInBackground = false;
        }
    }
    
    private static void launchPasswordCheck(Activity activity) {
        Intent intent = new Intent(activity, CheckPasswordActivity.class);
        intent.putExtra("from_background_timeout", true);
        activity.startActivity(intent);
    }
} 