package com.fc.safe.utils;

import android.app.Activity;
import android.content.Intent;
import com.fc.safe.initiate.CheckPasswordActivity;
import com.fc.safe.MainActivity;

public class BackgroundTimeoutManager {
    private static final String TAG = "BackgroundTimeoutManager";
    private static final long BACKGROUND_TIMEOUT = 15000; // 15 seconds in milliseconds

    private static long lastBackgroundTime = 0;
    private static boolean isInBackground = false;

    public static void onAppBackground() {
        if (!isInBackground) {
            lastBackgroundTime = System.currentTimeMillis();
            isInBackground = true;
        }
    }

    public static void onAppForeground(Activity activity) {
        if (isInBackground) {
            long currentTime = System.currentTimeMillis();
            long timeInBackground = currentTime - lastBackgroundTime;

            if (timeInBackground >= BACKGROUND_TIMEOUT) {
                launchPasswordCheck(activity);
            }

            isInBackground = false;
        }
    }

    private static void launchPasswordCheck(Activity activity) {
        // Don't launch CheckPasswordActivity if we're already on it or MainActivity
        if (activity instanceof CheckPasswordActivity || activity instanceof MainActivity) {
            return;
        }

        Intent intent = new Intent(activity, CheckPasswordActivity.class);
        intent.putExtra("from_background_timeout", true);
        // Launch CheckPasswordActivity on TOP of the current task (do NOT clear it).
        // SINGLE_TOP prevents stacking a second copy if one is somehow already present.
        //
        // Why not FLAG_ACTIVITY_CLEAR_TASK: clearing the task destroys the back stack,
        // so when the user re-enters the SAME password there is nothing left to resume,
        // which forces a re-authentication loop. By keeping the stack, a correct same
        // password simply finish()es CheckPasswordActivity and the user returns to the
        // exact page they were on. If a DIFFERENT password is entered, CheckPasswordActivity
        // itself clears the task and starts HomeActivity fresh (see verifyPassword()).
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }
} 