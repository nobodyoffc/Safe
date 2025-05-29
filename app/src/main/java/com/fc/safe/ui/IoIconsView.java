package com.fc.safe.ui;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.qr.QrCodeActivity;
import com.fc.safe.utils.QRCodeGenerator;

/**
 * A custom view that displays input/output icons (people, scan, make QR, and file) with built-in click handling.
 * This can be reused across different activities.
 */
public class IoIconsView extends LinearLayout {
    
    private static final String TAG = "IoIconsView";
    
    private ImageButton peopleButton;
    private ImageButton scanButton;
    private ImageButton makeQrButton;
    private ImageButton fileButton;
    
    // Callback interfaces for handling icon clicks
    public interface OnPeopleClickListener {
        void onPeopleClick(boolean isSingleChoice);
    }
    
    public interface OnScanClickListener {
        void onScanClick();
    }
    
    public interface OnMakeQrClickListener {
        void onMakeQrClick();
    }
    
    public interface OnFileClickListener {
        void onFileClick();
    }
    
    private OnPeopleClickListener peopleClickListener;
    private OnScanClickListener scanClickListener;
    private OnMakeQrClickListener makeQrClickListener;
    private OnFileClickListener fileClickListener;
    private boolean isSingleChoice = false;
    
    public IoIconsView(Context context) {
        super(context);
        init(context, false);
    }
    
    public IoIconsView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, false);
    }
    
    public IoIconsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, false);
    }
    
    /**
     * Create a new IoIconsView with the specified configuration
     * @param context The context
     * @param showMakeQr Whether to show the make QR icon
     * @param showPeople Whether to show the people icon
     * @param showScan Whether to show the scan icon
     * @param showFile Whether to show the file icon
     * @param isSingleChoice Whether to show the single choice mode
     * @return A new IoIconsView instance
     */
    public static IoIconsView create(Context context, boolean showMakeQr, boolean showPeople, boolean showScan, boolean showFile, boolean isSingleChoice) {
        IoIconsView view = new IoIconsView(context);
        view.init(context, showMakeQr, showPeople, showScan, showFile);
        view.setSingleChoice(isSingleChoice);
        return view;
    }
    
    private void init(Context context, boolean showMakeQr) {
        init(context, showMakeQr, true, true, true);
    }
    
    /**
     * Initialize the view with the specified configuration
     *
     * @param context    The context
     * @param showMakeQr Whether to show the make QR icon
     * @param showPeople Whether to show the people icon
     * @param showScan   Whether to show the scan icon
     * @param showFile   Whether to show the file icon
     */
    public void init(Context context, boolean showMakeQr, boolean showPeople, boolean showScan, boolean showFile) {
        TimberLogger.d(TAG, "Initializing IoIconsView");
        
        // Only inflate the layout if it hasn't been inflated yet
        if (getChildCount() == 0) {
            // Inflate the layout
            LayoutInflater.from(context).inflate(R.layout.layout_io_icons, this, true);
            
            // Find the buttons
            peopleButton = findViewById(R.id.peopleButton);
            scanButton = findViewById(R.id.scanButton);
            makeQrButton = findViewById(R.id.makeQrButton);
            fileButton = findViewById(R.id.fileButton);
            
            // Set up click listeners
            peopleButton.setOnClickListener(v -> {
                if (peopleClickListener != null) {
                    peopleClickListener.onPeopleClick(isSingleChoice);
                }
            });
            
            scanButton.setOnClickListener(v -> {
                if (scanClickListener != null) {
                    scanClickListener.onScanClick();
                }
            });
            
            makeQrButton.setOnClickListener(v -> {
                if (makeQrClickListener != null) {
                    makeQrClickListener.onMakeQrClick();
                }
            });
            
            fileButton.setOnClickListener(v -> {
                if (fileClickListener != null) {
                    fileClickListener.onFileClick();
                }
            });
        }
        
        // Show or hide the buttons based on the parameters
        makeQrButton.setVisibility(showMakeQr ? View.VISIBLE : View.GONE);
        peopleButton.setVisibility(showPeople ? View.VISIBLE : View.GONE);
        scanButton.setVisibility(showScan ? View.VISIBLE : View.GONE);
        fileButton.setVisibility(showFile ? View.VISIBLE : View.GONE);
    }
    
    /**
     * Set the listener for the people icon click
     */
    public void setOnPeopleClickListener(OnPeopleClickListener listener) {
        this.peopleClickListener = listener;
    }
    
    /**
     * Set the listener for the scan icon click
     */
    public void setOnScanClickListener(OnScanClickListener listener) {
        this.scanClickListener = listener;
    }
    
    /**
     * Set the listener for the make QR icon click
     */
    public void setOnMakeQrClickListener(OnMakeQrClickListener listener) {
        this.makeQrClickListener = listener;
    }
    
    /**
     * Set the listener for the file icon click
     */
    public void setOnFileClickListener(OnFileClickListener listener) {
        this.fileClickListener = listener;
    }
    
    /**
     * Helper method to launch the QR scanner activity
     * @param activity The current activity
     * @param requestCode The request code to use for onActivityResult
     */
    public static void launchQrScanner(android.app.Activity activity, int requestCode) {
        Intent intent = new Intent(activity, QrCodeActivity.class);
        intent.putExtra("is_return_string", true);
        activity.startActivityForResult(intent, requestCode);
    }
    
    /**
     * Helper method to launch the QR code generator activity
     * @param activity The current activity
     * @param content The content to encode in the QR code
     */
    public static void launchQrGenerator(android.app.Activity activity, String content) {
        QRCodeGenerator.generateAndShowQRCode(activity, content);
    }
    
    public void setSingleChoice(boolean isSingleChoice) {
        this.isSingleChoice = isSingleChoice;
    }
} 