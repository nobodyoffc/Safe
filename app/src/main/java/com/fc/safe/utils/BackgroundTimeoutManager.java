package com.fc.safe.utils;

import android.app.Activity;
import android.content.Intent;
import com.fc.safe.initiate.CheckPasswordActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.initiate.CreatePasswordActivity;
import com.fc.safe.MainActivity;

/**
 * Locks the app with CheckPasswordActivity when it returns after BACKGROUND_TIMEOUT,
 * or when an activity comes up while no vault is open (e.g. Android restored the
 * task in a new process after killing it in the background).
 *
 * The app counts as backgrounded only when none of its activities is started.
 * Pause/resume is not used: the lock screen, permission dialogs and other
 * transient windows pause an activity, and on wake-up the system may resume and
 * pause it several times, which used to stack two password screens.
 */
public class BackgroundTimeoutManager {
    private static final String TAG = "BackgroundTimeoutManager";
    private static final long BACKGROUND_TIMEOUT = 15000; // 15 seconds in milliseconds

    private static int startedActivities = 0;
    private static long lastBackgroundTime = 0;
    private static boolean lockDue = false;
    // Live CheckPasswordActivity instances; while one exists no other is launched.
    private static int passwordChecks = 0;
    // Set between launching a CheckPasswordActivity and its creation.
    private static boolean pendingLaunch = false;

    public static void onActivityCreated(Activity activity) {
        if (activity instanceof CheckPasswordActivity) {
            passwordChecks++;
            pendingLaunch = false;
            lockDue = false;
        }
    }

    public static void onActivityDestroyed(Activity activity) {
        if (activity instanceof CheckPasswordActivity && passwordChecks > 0) {
            passwordChecks--;
        }
    }

    public static void onActivityStarted(Activity activity) {
        if (startedActivities++ == 0 && lastBackgroundTime > 0
                && System.currentTimeMillis() - lastBackgroundTime >= BACKGROUND_TIMEOUT) {
            lockDue = true;
        }
    }

    public static void onActivityStopped(Activity activity) {
        if (startedActivities > 0 && --startedActivities == 0) {
            // A rotation stops and restarts the activity; it is not a trip to the background.
            lastBackgroundTime = activity.isChangingConfigurations() ? 0 : System.currentTimeMillis();
        }
    }

    public static void onActivityResumed(Activity activity) {
        if (activity instanceof CheckPasswordActivity
                || activity instanceof MainActivity
                || activity instanceof CreatePasswordActivity
                || passwordChecks > 0 || pendingLaunch) {
            // A password screen is up or about to be: it already covers this lock.
            lockDue = false;
            return;
        }
        boolean vaultLocked = ConfigureManager.getInstance().getConfigure() == null;
        if (lockDue || vaultLocked) {
            lockDue = false;
            launchPasswordCheck(activity);
        }
    }

    private static void launchPasswordCheck(Activity activity) {
        Intent intent = new Intent(activity, CheckPasswordActivity.class);
        intent.putExtra("from_background_timeout", true);
        // Launch CheckPasswordActivity on TOP of the current task (do NOT clear it).
        //
        // Why not FLAG_ACTIVITY_CLEAR_TASK: clearing the task destroys the back stack,
        // so when the user re-enters the SAME password there is nothing left to resume,
        // which forces a re-authentication loop. By keeping the stack, a correct same
        // password simply finish()es CheckPasswordActivity and the user returns to the
        // exact page they were on. If a DIFFERENT password is entered, CheckPasswordActivity
        // itself clears the task and starts HomeActivity fresh (see verifyPassword()).
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        // The next resume may come before the new activity is created.
        pendingLaunch = true;
    }
}
