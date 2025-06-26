package com.fc.safe.tools;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.utils.QRCodeGenerator;

public class JsonConvertActivity extends BaseCryptoActivity {
    private static final String TAG = "JsonConvertActivity";

    protected EditText keyEditText;
    private LinearLayout buttonContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize views
        initializeViews();
        setupListeners();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_json_converter;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.json_converter);
    }

    protected void initializeViews() {
        keyEditText = findViewById(R.id.keyView).findViewById(R.id.textInput);
        keyEditText.setHint(R.string.input_json);
        resultTextView = findViewById(R.id.resultView).findViewById(R.id.textBoxWithMakeQrLayout);
        resultTextView.setHint(R.string.result);

        clearButton = findViewById(R.id.clearButton);
        copyButton = findViewById(R.id.copyButton);
        convertButton = findViewById(R.id.convertButton);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        setupIoIconsView(R.id.keyView, R.id.scanIcon, false, false, true, false,
                null, null, () -> startQrScan(0), null);
            
        setupIoIconsView(R.id.resultView, R.id.makeQrIcon, true, false, false, false,
                () -> {
                String content = resultTextView.getText().toString();
                if (!TextUtils.isEmpty(content)) {
                    QRCodeGenerator.generateAndShowQRCode(this, content);
                } else {
                    showToast(getString(R.string.no_content_to_generate_qr_code));
                }
            }, null, null, null);
    }

    private void setupListeners() {
        // Set click listeners
        clearButton.setOnClickListener(v -> {
            hideKeyboard();
            clearInputs();
        });
        copyButton.setOnClickListener(v -> {
            hideKeyboard();
            copyConvertedDataToClipboard();
        });
        convertButton.setOnClickListener(v -> {
            hideKeyboard();
            convert();
        });
    }

    @Override
    protected void setupButtons() {
        setupButton(clearButton, v -> {
            hideKeyboard();
            clearInputs();
        });
        setupButton(copyButton, v -> {
            hideKeyboard();
            copyConvertedDataToClipboard();
        });
        setupButton(convertButton, v -> {
            hideKeyboard();
            convert();
        });
    }
    
    private void clearInputs() {
        keyEditText.setText("");
        keyEditText.setEnabled(true);
        keyEditText.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
        keyEditText.setTag(null);
        updateResultText(null);
    }

    private void convert() {
        // Always clear the result first
        resultTextView.setText("");
        copyButton.setEnabled(false);

        String inputedText = keyEditText.getText().toString();

        if (TextUtils.isEmpty(inputedText)) {
            showToast(getString(R.string.please_input_json_text));
            return;
        }

        try {
            String result;
            // Check if input is already a nice JSON
            if (inputedText.contains("\n") || inputedText.contains("  ")) {
                // Convert nice JSON to compressed JSON
                result = JsonUtils.niceJsonToJson(inputedText);
            } else {
                // Convert compressed JSON to nice JSON
                result = JsonUtils.jsonToNiceJson(inputedText);
            }

            if (result != null) {
                updateResultText(result);
                copyButton.setEnabled(true);
            } else {
                showToast(getString(R.string.invalid_json_format));
            }
            
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error converting: " + e.getMessage());
            showToast(getString(R.string.error_converting, e.getMessage()));
        }
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        keyEditText.setText(qrContent);
    }
} 