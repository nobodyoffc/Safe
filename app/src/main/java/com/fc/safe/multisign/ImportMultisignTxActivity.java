package com.fc.safe.multisign;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;

import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.tx.SignTxActivity;
import com.google.android.material.textfield.TextInputEditText;

public class ImportMultisignTxActivity extends BaseCryptoActivity {
    private static final String TAG = "ImportMultisignTxActivity";
    private static final int QR_SCAN_JSON_REQUEST_CODE = 1001;

    private LinearLayout jsonInputContainer;
    private LinearLayout buttonContainer;
    private TextInputEditText jsonInput;
    private Button clearButton;
    private Button importButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimberLogger.i(TAG, "onCreate started");
    }

    private void initViews() {
        jsonInputContainer = findViewById(R.id.jsonInputContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        jsonInput = jsonInputContainer.findViewById(R.id.jsonInput).findViewById(R.id.textInput);
        jsonInput.setHint(R.string.import_the_tx_json);
        
        clearButton = findViewById(R.id.clearButton);
        importButton = findViewById(R.id.importButton);

        setupTextIcons(R.id.jsonInput, R.id.scanIcon, QR_SCAN_JSON_REQUEST_CODE);
    }

    private void setupListeners() {
        clearButton.setOnClickListener(v -> {
            // Hide keyboard
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
            jsonInput.setText("");
        });

        importButton.setOnClickListener(v -> {
            TimberLogger.i(TAG, "importButton clicked");
            String jsonText = jsonInput.getText() != null ? jsonInput.getText().toString() : "";
            
            if (jsonText.isEmpty()) {
                TimberLogger.e(TAG, "JSON text is empty");
                showToast("Please input JSON text");
                return;
            }

            try {
                Intent resultIntent = new Intent();
                resultIntent.putExtra(SignTxActivity.EXTRA_TX_INFO_JSON, jsonText);
                setResult(RESULT_OK, resultIntent);
                finish();
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error parsing JSON: " + e.getMessage());
                showToast("Invalid JSON format");
            }
        });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_import_tx_info;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.import_multisign_tx);
    }

    @Override
    protected void initializeViews() {
        initViews();
    }

    @Override
    protected void setupButtons() {
        setupListeners();
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_JSON_REQUEST_CODE) {
            jsonInput.setText(qrContent);
        }
    }

    public static Intent createIntent(Context context) {
        return new Intent(context, ImportMultisignTxActivity.class);
    }
}