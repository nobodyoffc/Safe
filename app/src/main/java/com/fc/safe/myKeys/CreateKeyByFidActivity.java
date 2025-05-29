package com.fc.safe.myKeys;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import android.widget.Toast;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.ui.DetailFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.fc.safe.utils.TextIconsUtils;

public class CreateKeyByFidActivity extends BaseCryptoActivity {
    private static final String TAG = "CreateKeyByFid";
    private static final int QR_SCAN_FID_REQUEST_CODE = 1001;
    private static final int QR_SCAN_LABEL_REQUEST_CODE = 1002;
    
    private DetailFragment detailFragment;
    private TextInputEditText fidInput;
    private TextInputEditText labelInput;
    private Button clearButton;
    private Button previewButton;
    private Button saveButton;
    private LinearLayout keyInfoContainer;
    private LinearLayout inputContainer;
    private LinearLayout buttonContainer;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_create_key_by_pub_key;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.create_key_by_fid);
    }

    @Override
    protected void initializeViews() {
        keyInfoContainer = findViewById(R.id.keyInfoContainer);
        inputContainer = findViewById(R.id.inputContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        // Initialize input fields from included layouts
        View fidView = findViewById(R.id.pubkeyView);
        View labelView = findViewById(R.id.labelView);

        fidInput = fidView.findViewById(R.id.textInput);
        fidInput.setHint(R.string.input_the_pubkey);
        labelInput = labelView.findViewById(R.id.textInput);
        labelInput.setHint(R.string.input_the_label);
        
//        TextInputLayout fidLayout = fidView.findViewById(R.id.textInputWithScanLayout);
//        fidInput = fidLayout.findViewById(R.id.textInputLayout);
//        fidInput.setHint(R.string.input_the_fid);
//
//        TextInputLayout labelLayout = labelView.findViewById(R.id.textInputLayout);
//        labelInput = labelLayout.findViewById(R.id.textInputLayout);
//        labelInput.setHint(R.string.input_the_label);
        
        clearButton = findViewById(R.id.clearButton);
        previewButton = findViewById(R.id.previewButton);
        saveButton = findViewById(R.id.saveButton);
    }

    @Override
    protected void setupButtons() {
        clearButton.setOnClickListener(v -> clearInputs());
        previewButton.setOnClickListener(v -> previewKeyInfo());
        saveButton.setOnClickListener(v -> saveKeyInfo());

        // Setup scan icons using TextIconsUtils
        TextIconsUtils.setupTextIcons(this, R.id.pubkeyView, R.id.scanIcon, QR_SCAN_FID_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.labelView, R.id.scanIcon, QR_SCAN_LABEL_REQUEST_CODE);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_FID_REQUEST_CODE) {
            fidInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_LABEL_REQUEST_CODE) {
            labelInput.setText(qrContent);
        }
    }

    private void clearInputs() {
        fidInput.setText("");
        labelInput.setText("");
        clearDetailFragment();
    }

    private void clearDetailFragment() {
        if (detailFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(detailFragment)
                    .commit();
            detailFragment = null;
        }
    }

    private void previewKeyInfo() {
        String fid = fidInput.getText() != null ? fidInput.getText().toString() : "";
        if(!KeyTools.isGoodFid(fid)){
            Toast.makeText(this, "Invalid FID", Toast.LENGTH_SHORT).show();
            return;
        }
        String label = labelInput.getText() != null ? labelInput.getText().toString() : "";

        if (fid.isEmpty()) {
            return;
        }

        // Create a new KeyInfo object using FID
        KeyInfo keyInfo = KeyInfo.newFromFid(fid, label);

        // Generate and save avatar for the new key
        try {
            keyInfoManager.makeAvatarByFid(keyInfo.getId(), this);
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error generating avatar: %s", e.getMessage());
        }

        // Show the KeyInfo in the detail fragment
        showDetailFragment(keyInfo);
    }

    private void showDetailFragment(KeyInfo keyInfo) {
        // Remove existing fragment if any
        clearDetailFragment();

        // Create and show new fragment
        detailFragment = DetailFragment.newInstance(keyInfo, KeyInfo.class);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.keyInfoContainer, detailFragment)
                .commit();
    }

    private void saveKeyInfo() {
        if (detailFragment == null) {
            // If no preview was done, try to create KeyInfo from inputs
            KeyInfo keyInfo = createKeyInfoFromInputs();
            if (keyInfo == null) {
                return;
            }
            saveKeyInfoToDatabase(keyInfo);
        } else {
            // Use the previewed KeyInfo
            KeyInfo keyInfo = (KeyInfo) detailFragment.getCurrentEntity();
            if (keyInfo == null) {
                Toast.makeText(this, "Failed to save key info", Toast.LENGTH_SHORT).show();
                return;
            }
            saveKeyInfoToDatabase(keyInfo);
        }
    }

    private KeyInfo createKeyInfoFromInputs() {
        String fid = fidInput.getText() != null ? fidInput.getText().toString() : "";
        if(!KeyTools.isGoodFid(fid)){
            Toast.makeText(this, "Invalid FID", Toast.LENGTH_SHORT).show();
            return null;
        }
        String label = labelInput.getText() != null ? labelInput.getText().toString() : "";

        if (fid.isEmpty()) {
            Toast.makeText(this, "FID is empty", Toast.LENGTH_SHORT).show();
            return null;
        }

        return KeyInfo.newFromFid(fid, label);
    }

    private void saveKeyInfoToDatabase(KeyInfo keyInfo) {
        // Check if the key already exists
        if (keyInfoManager.checkIfExisted(keyInfo.getId())) {
            new AlertDialog.Builder(this)
                .setTitle("Key Already Exists")
                .setMessage("A key with this ID already exists. Do you want to replace it?")
                .setPositiveButton("Replace", (dialog, which) -> {
                    saveAndFinish(keyInfo);
                })
                .setNegativeButton("Cancel", null)
                .show();
        } else {
            saveAndFinish(keyInfo);
        }
    }

    private void saveAndFinish(KeyInfo keyInfo) {
        keyInfoManager.addKeyInfo(keyInfo);
        keyInfoManager.commit();
        setResult(RESULT_OK);
        finish();
    }
} 