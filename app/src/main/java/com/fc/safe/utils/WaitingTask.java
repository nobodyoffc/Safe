package com.fc.safe.utils;

import android.app.Activity;

import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.ui.WaitingDialog;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Runs slow work, such as the Argon2id run behind every password cipher, off the UI thread while a
 * waiting dialog shows, so the screen neither freezes nor triggers "app isn't responding".
 */
public final class WaitingTask {
    private static final String TAG = "WaitingTask";

    private WaitingTask() {
    }

    /**
     * Shows a waiting dialog, runs {@code work} on a background thread, then dismisses the dialog and
     * passes the result to {@code onDone} on the UI thread. Work that throws passes null. {@code onDone}
     * is skipped if the activity finished in the meantime.
     */
    public static <R> void run(Activity activity, String hint, Callable<R> work, Consumer<R> onDone) {
        WaitingDialog dialog = new WaitingDialog(activity, hint);
        dialog.show();
        new Thread(() -> {
            R result = null;
            try {
                result = work.call();
            } catch (Exception e) {
                TimberLogger.e(TAG, "Background work failed: " + e.getMessage(), e);
            }
            R finalResult = result;
            activity.runOnUiThread(() -> {
                try {
                    if (dialog.isShowing()) dialog.dismiss();
                } catch (IllegalArgumentException ignore) {
                    // The activity's window is already gone.
                }
                if (onDone != null && !activity.isFinishing() && !activity.isDestroyed()) onDone.accept(finalResult);
            });
        }, TAG).start();
    }
}
