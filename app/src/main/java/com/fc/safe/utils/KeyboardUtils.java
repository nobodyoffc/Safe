package com.fc.safe.utils;

import android.app.Activity;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * Utility class for handling keyboard operations
 */
public class KeyboardUtils {
    
    /**
     * Hides the keyboard if it's currently showing
     * @param activity The activity from which to hide the keyboard
     */
    public static void hideKeyboard(Activity activity) {
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Sets up keyboard hiding when clicking outside input fields
     * @param activity The activity to set up keyboard hiding for
     */
    public static void setupKeyboardHiding(Activity activity) {
        View rootLayout = activity.findViewById(android.R.id.content);
        if (rootLayout != null) {
            rootLayout.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    hideKeyboard(activity);
                }
                return false;
            });
        }
    }

    /**
     * Sets up keyboard hiding for a specific view
     * @param activity The activity containing the view
     * @param view The view to set up keyboard hiding for
     */
    public static void setupKeyboardHiding(Activity activity, View view) {
        if (view != null) {
            view.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    hideKeyboard(activity);
                }
                return false;
            });
        }
    }
}
