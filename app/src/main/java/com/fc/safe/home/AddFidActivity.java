package com.fc.safe.home;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.SafeApplication;
import com.google.android.material.textfield.TextInputEditText;

public class AddFidActivity extends BaseCryptoActivity {
    private static final String TAG = "AddFidActivity";
    private static final int QR_SCAN_FID_REQUEST_CODE = 1001;

    private LinearLayout fidInputContainer;
    private LinearLayout buttonContainer;
    private TextInputEditText fidInput;
    private Button clearButton;
    private Button addButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimberLogger.i(TAG, "onCreate started");
    }

    private void initViews() {
        fidInputContainer = findViewById(R.id.fidInputContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        fidInput = fidInputContainer.findViewById(R.id.fidInput).findViewById(R.id.textInput);
        fidInput.setHint(R.string.input_fid_or_fid_list_separated_by_comma_or_space);
        
        clearButton = findViewById(R.id.clearButton);
        addButton = findViewById(R.id.addButton);

        setupTextIcons(R.id.fidInput, R.id.scanIcon, QR_SCAN_FID_REQUEST_CODE);
    }

    private void setupListeners() {
        clearButton.setOnClickListener(v -> {
            // Hide keyboard
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
            fidInput.setText("");
        });

        addButton.setOnClickListener(v -> {
            String text = fidInput.getText() != null ? fidInput.getText().toString() : "";
            
            // Clean the input text
            text = text.replace("[", "").replace("]", "").replace("\"", "").trim();
            
            // Split by common separators and clean each FID
            String[] fids = text.split("[, \n\t]+");
            boolean hasValidFid = false;
            
            for (String fid : fids) {
                fid = fid.trim();
                if (!fid.isEmpty() && KeyTools.isGoodFid(fid)) {
                    SafeApplication.addFid(fid);
                    hasValidFid = true;
                }
            }
            
            if (!hasValidFid) {
                Toast.makeText(this, R.string.invalid_fid, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            setResult(RESULT_OK);
            finish();
        });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_add_fid;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.add_fid);
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
        if (requestCode == QR_SCAN_FID_REQUEST_CODE) {
            fidInput.setText(qrContent);
        }
    }

    public static Intent createIntent(Context context) {
        return new Intent(context, AddFidActivity.class);
    }
} 