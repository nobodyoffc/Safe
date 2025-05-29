package com.fc.safe.utils;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.widget.Toast;

import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.ui.IoIconsView;
import com.fc.safe.home.BaseCryptoActivity;

public class TextIconsUtils {
    private static final String TAG = "TextIconsUtils";

    public static void setupTextIcons(Dialog dialog, int textView, int textIcons, int QR_SCAN_TEXT_REQUEST_CODE) {
        TimberLogger.i(TAG, "setupTextIcons for Dialog called with requestCode: " + QR_SCAN_TEXT_REQUEST_CODE);
        setupIoIconsView(dialog, textView, textIcons, false, false, true, false, false,
                null, null, () -> {
                    TimberLogger.i(TAG, "Scan icon clicked in Dialog");
                    startQrScan(dialog, QR_SCAN_TEXT_REQUEST_CODE);
                }, null);
    }

    public static void setupTextIcons(Activity activity, int textView, int textIcons, int QR_SCAN_TEXT_REQUEST_CODE) {
        TimberLogger.i(TAG, "setupTextIcons for Activity called with requestCode: " + QR_SCAN_TEXT_REQUEST_CODE);
        if (activity instanceof BaseCryptoActivity) {
            setupIoIconsView(activity, textView, textIcons, false, false, true, false, false,
                    null, null, () -> {
                        TimberLogger.i(TAG, "Scan icon clicked in BaseCryptoActivity");
                        ((BaseCryptoActivity) activity).startQrScan(QR_SCAN_TEXT_REQUEST_CODE);
                    }, null);
        } else {
            setupIoIconsView(activity, textView, textIcons, false, false, true, false, false,
                    null, null, () -> {
                        TimberLogger.i(TAG, "Scan icon clicked in Activity");
                        startQrScan(activity, QR_SCAN_TEXT_REQUEST_CODE);
                    }, null);
        }
    }

    public static void setupTextWithoutIcons(Dialog dialog, int textView, int textIcons) {
        TimberLogger.i(TAG, "setupTextWithoutIcons for Dialog called");
        View container = dialog.findViewById(textView);
        IoIconsView icons = container.findViewById(textIcons);
        if (icons != null) {
            icons.init(dialog.getContext(), false, false, false, false);
        }
    }

    public static void setupTextWithoutIcons(Activity activity, int textView, int textIcons) {
        TimberLogger.i(TAG, "setupTextWithoutIcons for Activity called");
        View container = activity.findViewById(textView);
        IoIconsView icons = container.findViewById(textIcons);
        if (icons != null) {
            icons.init(activity, false, false, false, false);
        }
    }

    private static void setupIoIconsView(Dialog dialog, int containerId, int iconId, boolean showMakeQr, boolean showPeople,
                                       boolean showScan, boolean showFile, boolean showAsHex,
                                       IoIconsView.OnMakeQrClickListener makeQrListener,
                                       IoIconsView.OnPeopleClickListener peopleListener,
                                       IoIconsView.OnScanClickListener scanListener,
                                       IoIconsView.OnFileClickListener fileListener) {
        TimberLogger.i(TAG, "setupIoIconsView for Dialog called with showScan: " + showScan);
        View container = dialog.findViewById(containerId);
        IoIconsView icons = container.findViewById(iconId);
        if (icons != null) {
            TimberLogger.i(TAG, "Found IoIconsView in Dialog, initializing");
            icons.init(dialog.getContext(), showMakeQr, showPeople, showScan, showFile);
            if (makeQrListener != null) icons.setOnMakeQrClickListener(makeQrListener);
            if (peopleListener != null) icons.setOnPeopleClickListener(peopleListener);
            if (scanListener != null) {
                TimberLogger.i(TAG, "Setting scan click listener for Dialog");
                icons.setOnScanClickListener(scanListener);
            }
            if (fileListener != null) icons.setOnFileClickListener(fileListener);
        } else {
            TimberLogger.e(TAG, "IoIconsView not found in Dialog for id: " + iconId);
        }
    }

    private static void setupIoIconsView(Activity activity, int containerId, int iconId, boolean showMakeQr, boolean showPeople,
                                       boolean showScan, boolean showFile, boolean showAsHex,
                                       IoIconsView.OnMakeQrClickListener makeQrListener,
                                       IoIconsView.OnPeopleClickListener peopleListener,
                                       IoIconsView.OnScanClickListener scanListener,
                                       IoIconsView.OnFileClickListener fileListener) {
        TimberLogger.i(TAG, "setupIoIconsView for Activity called with showScan: " + showScan);
        View container = activity.findViewById(containerId);
        IoIconsView icons = container.findViewById(iconId);
        if (icons != null) {
            TimberLogger.i(TAG, "Found IoIconsView in Activity, initializing");
            icons.init(activity, showMakeQr, showPeople, showScan, showFile);
            if (makeQrListener != null) icons.setOnMakeQrClickListener(makeQrListener);
            if (peopleListener != null) icons.setOnPeopleClickListener(peopleListener);
            if (scanListener != null) {
                TimberLogger.i(TAG, "Setting scan click listener for Activity");
                icons.setOnScanClickListener(scanListener);
            }
            if (fileListener != null) icons.setOnFileClickListener(fileListener);
        } else {
            TimberLogger.e(TAG, "IoIconsView not found in Activity for id: " + iconId);
        }
    }

    private static void startQrScan(Dialog dialog, int requestCode) {
        TimberLogger.i(TAG, "startQrScan for Dialog called with requestCode: " + requestCode);
        Activity activity = dialog.getOwnerActivity();
        if (activity != null) {
            if (activity instanceof BaseCryptoActivity) {
                TimberLogger.i(TAG, "Starting QR scan from BaseCryptoActivity");
                ((BaseCryptoActivity) activity).startQrScan(requestCode);
            } else {
                TimberLogger.e(TAG, "Owner activity is not a BaseCryptoActivity");
                Toast.makeText(activity, "QR scanning not supported", Toast.LENGTH_SHORT).show();
            }
        } else {
            TimberLogger.e(TAG, "Dialog has no owner activity");
        }
    }

    private static void startQrScan(Activity activity, int requestCode) {
        TimberLogger.i(TAG, "startQrScan for Activity called with requestCode: " + requestCode);
        if (activity instanceof BaseCryptoActivity) {
            TimberLogger.i(TAG, "Starting QR scan from BaseCryptoActivity");
            ((BaseCryptoActivity) activity).startQrScan(requestCode);
        } else {
            TimberLogger.e(TAG, "Activity is not a BaseCryptoActivity");
            Toast.makeText(activity, "QR scanning not supported", Toast.LENGTH_SHORT).show();
        }
    }
} 