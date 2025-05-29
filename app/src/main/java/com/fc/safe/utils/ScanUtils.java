package com.fc.safe.utils;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import com.fc.safe.qr.QrCodeActivity;

import java.util.Map;

public class ScanUtils {
    private static final int SCAN_REQUEST_CODE = 100;
    private static View lastClickedScanButton;

    public static void setupScanButton(Activity activity, ImageButton scanButton, String fieldName, 
                                     Map<String, ImageButton> fieldScanButtonMap) {
        fieldScanButtonMap.put(fieldName, scanButton);
        scanButton.setOnClickListener(v -> {
            lastClickedScanButton = v;
            Intent intent = new Intent(activity, QrCodeActivity.class);
            intent.putExtra("is_return_string", true);
            activity.startActivityForResult(intent, SCAN_REQUEST_CODE);
        });
    }

    public static void handleScanResult(Activity activity, int requestCode, int resultCode, Intent data,
                                      Map<String, ImageButton> fieldScanButtonMap, Map<String, EditText> fieldInputMap) {
        if (requestCode == SCAN_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            String scannedContent = data.getStringExtra("qr_content");
            if (scannedContent != null && lastClickedScanButton != null) {
                // Find which scan button was clicked by checking which one is the parent of the clicked view
                for (Map.Entry<String, ImageButton> entry : fieldScanButtonMap.entrySet()) {
                    ImageButton scanButton = entry.getValue();
                    if (scanButton.getParent() == lastClickedScanButton.getParent()) {
                        EditText input = fieldInputMap.get(entry.getKey());
                        if (input != null) {
                            input.setText(scannedContent);
                        }
                        break;
                    }
                }
            }
        }
    }
} 