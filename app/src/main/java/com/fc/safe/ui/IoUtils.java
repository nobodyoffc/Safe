package com.fc.safe.ui;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class IoUtils {
    private static final String TAG = "IoUtils";
    private static final int QR_SCAN_JSON_REQUEST_CODE = 1001;

    // Helper method to hide keyboard
    private static void hideKeyboard(Context context, View viewWithWindowToken) {
        if (context == null || viewWithWindowToken == null) return;
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(viewWithWindowToken.getWindowToken(), 0);
        }
    }

    private static void setupTextIcons(Context context, int inputContainerId, int scanIconId, int requestCode) {
        // This method needs to be implemented based on your existing setupTextIcons implementation
        // You might need to pass additional parameters or modify the implementation
        // If this method calls startActivityForResult, 'context' must be an Activity.
        // Example check:
        if (!(context instanceof Activity)) {
            Log.w(TAG, "setupTextIcons called with non-Activity context. Scan functionality might be impaired.");
            // Optionally hide or disable the scan icon if it requires an Activity
            // View scanIconView = ((Activity)context).findViewById(scanIconId); // This would crash
            // if (scanIconView != null) scanIconView.setVisibility(View.GONE);
            return;
        }
        // Activity activity = (Activity) context;
        // ... rest of the implementation using activity ...
        Log.d(TAG, "setupTextIcons would have been called with inputContainerId: " + inputContainerId + ", scanIconId: " + scanIconId + ", requestCode: " + requestCode);
        Log.d(TAG, "This functionality is currently not used with the new dialog_string_input.xml layout.");
    }
} 