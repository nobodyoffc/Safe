package com.fc.safe.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.qr.QrCodeActivity;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.fc.safe.ui.IoIconsView;
import com.fc.safe.utils.KeyboardUtils;
import com.fc.safe.utils.ToolbarUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.fc.safe.utils.TextIconsUtils;

import java.util.List;

public abstract class BaseCryptoActivity extends AppCompatActivity {
    protected ActivityResultLauncher<Intent> qrScanLauncher;
    protected ActivityResultLauncher<Intent> chooseKeyLauncher;
    protected TextView resultTextView;
    protected Button clearButton;
    protected Button copyButton;
    protected Button convertButton;
    protected String convertedData;
    protected KeyInfoManager keyInfoManager;
    private static final String TAG = "BaseCryptoActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Configure logging to filter GMS logs
//        TimberLogger.configureLogging(true);

        setContentView(getLayoutId());
        
        // Initialize KeyInfoManager
        keyInfoManager = KeyInfoManager.getInstance(this);
        
        // Initialize activity result launchers
        setupActivityResultLaunchers();
        
        // Set up toolbar
        ToolbarUtils.setupToolbar(this, getActivityTitle());
        
        // Set up keyboard hiding
        setupKeyboardHiding();
        
        // Set up back button handling
        setupBackButton();
        
        // Initialize views
        initializeViews();
        
        // Setup buttons
        setupButtons();
    }

    protected abstract int getLayoutId();
    protected abstract String getActivityTitle();
    protected abstract void initializeViews();

    protected void handleQrGeneration(String resultText) {
        if (resultText == null) {
            showToast(getString(R.string.please_input_text));
            return;
        }

        if (!TextUtils.isEmpty(resultText)) {
            IoIconsView.launchQrGenerator(this, resultText);
        } else {
            showToast(getString(R.string.no_content_to_generate_qr_code));
        }
    }

    protected abstract void setupButtons();

    private void setupBackButton() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void setupKeyboardHiding() {
        // Use the existing KeyboardUtils method for the root layout
        KeyboardUtils.setupKeyboardHiding(this);

        // Set up keyboard hiding for all container views
        ViewGroup rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            setupKeyboardHidingForViewGroup(rootView);
        }
    }

    private void setupKeyboardHidingForViewGroup(ViewGroup viewGroup) {
        // Set up touch listener for the current view group
        viewGroup.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Only hide keyboard if the view is not an EditText or TextInputEditText
                if (!(v instanceof EditText) && !(v instanceof TextInputEditText)) {
                    hideKeyboard();
                }
            }
            return false;
        });

        // Recursively set up touch listeners for all child views
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {
                setupKeyboardHidingForViewGroup((ViewGroup) child);
            } else if (!(child instanceof EditText) && !(child instanceof TextInputEditText)) {
                child.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        hideKeyboard();
                    }
                    return false;
                });
            }
        }
    }

    protected void setupActivityResultLaunchers() {
        // QR scan launcher
        qrScanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String qrContent = result.getData().getStringExtra("qr_content");
                    if (qrContent != null) {
                        handleQrScanResult(result.getData().getIntExtra("request_code", 0), qrContent);
                    }
                }
            }
        );

        // Choose key launcher
        chooseKeyLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    handleChooseKeyResult(result.getData());
                }
            }
        );
    }

    protected abstract void handleQrScanResult(int requestCode, String qrContent);

    protected void handleChooseKeyResult(Intent data) {
        // This method can be overridden by subclasses to handle the key selection result
        TimberLogger.i(TAG, "Key selection result received");
    }

    public void setupIoIconsView(int containerId, int iconId, boolean showMakeQr, boolean showPeople,
                                  boolean showScan, boolean showFile,
                                 IoIconsView.OnMakeQrClickListener makeQrListener,
                                  IoIconsView.OnPeopleClickListener peopleListener,
                                  IoIconsView.OnScanClickListener scanListener,
                                  IoIconsView.OnFileClickListener fileListener) {
        View container = findViewById(containerId);
        IoIconsView icons = container.findViewById(iconId);
        if (icons != null) {
            icons.init(this, showMakeQr, showPeople, showScan, showFile);
            if (makeQrListener != null) icons.setOnMakeQrClickListener(makeQrListener);
            if (peopleListener != null) icons.setOnPeopleClickListener(peopleListener);
            if (scanListener != null) icons.setOnScanClickListener(scanListener);
            if (fileListener != null) icons.setOnFileClickListener(fileListener);
        }
    }

    public void startQrScan(int requestCode) {
        Intent intent = new Intent(this, QrCodeActivity.class);
        intent.putExtra("is_return_string", true);
        intent.putExtra("request_code", requestCode);
        qrScanLauncher.launch(intent);
    }

    protected void copyToClipboard(String text, String label) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        showToast(getString(R.string.copied));
    }

    protected void showToast(String message) {
        Toast.makeText(this, message, SafeApplication.TOAST_LASTING).show();
    }

    protected void clearInput(TextInputEditText input) {
        input.setText("");
        input.setEnabled(true);
        input.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
        input.setTag(null);
    }

    protected void setupButton(Button button, View.OnClickListener listener) {
        button.setOnClickListener(listener);
    }

    protected void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    protected void updateResultText(String result) {
        resultTextView.setVisibility(View.VISIBLE);

        if (result == null) {
            resultTextView.setText("");
            copyButton.setEnabled(false);
            convertedData = null;
            return;
        }

        convertedData = result;
        resultTextView.setText(convertedData);
    }

    protected void copyConvertedDataToClipboard() {
        if (convertedData != null) {
            copyToClipboard(convertedData, "Converted Data");
        }
    }

    protected void showChooseKeyInfoDialog(Boolean isSingleChoice) {
        TimberLogger.i(TAG, "Showing choose key info dialog, isSingleChoice: " + isSingleChoice);
        List<KeyInfo> keyInfoList = keyInfoManager.getAllKeyInfoList();
        TimberLogger.i(TAG, "Retrieved " + keyInfoList.size() + " key info items");
        
        if (keyInfoList.isEmpty()) {
            TimberLogger.w(TAG, "No keys available to show in dialog");
            showToast(getString(R.string.no_key_available));
            return;
        }

        Intent intent = ChooseKeyInfoActivity.newIntent(this, keyInfoList, isSingleChoice);
        TimberLogger.i(TAG, "Launching ChooseKeyInfoActivity");
        chooseKeyLauncher.launch(intent);
    }

    protected void setupTextIcons(int textView, int textIcons, int QR_SCAN_TEXT_REQUEST_CODE) {
        TextIconsUtils.setupTextIcons(this, textView, textIcons, QR_SCAN_TEXT_REQUEST_CODE);
    }

    protected void setupKeyIcons(int keyView, int keyIcons, int QR_SCAN_KEY_REQUEST_CODE) {
        setupIoIconsView(keyView, keyIcons, false, true, true, false,
                null, v -> showChooseKeyInfoDialog(true), () -> startQrScan(QR_SCAN_KEY_REQUEST_CODE), null);
    }

    protected void setupResultIcons(int resultView, int resultIcons, IoIconsView.OnMakeQrClickListener makeQrListener) {
        setupIoIconsView(resultView, resultIcons, true, false, false, false,
                makeQrListener, null, null, null);
    }
} 