package com.fc.safe.myKeys;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.ui.DetailFragment;
import com.fc.safe.utils.TextIconsUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.fc.safe.utils.ToolbarUtils;

public class CreateKeyByPubkeyActivity extends BaseCryptoActivity {
    private static final String TAG = "CreateKeyByPubkey";
    private static final int QR_SCAN_PUBKEY_REQUEST_CODE = 1001;
    private static final int QR_SCAN_LABEL_REQUEST_CODE = 1002;
    
    private DetailFragment detailFragment;
    private TextInputEditText pubkeyInput;
    private TextInputEditText labelInput;
    private Button clearButton;
    private Button previewButton;
    private Button saveButton;
    private LinearLayout keyInfoContainer;
    private LinearLayout inputContainer;
    private LinearLayout buttonContainer;
    
    // QR scan launcher
    private ActivityResultLauncher<Intent> qrScanLauncher;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_create_key_by_pub_key;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.create_key_by_pubkey);
    }

    @Override
    protected void initializeViews() {
        keyInfoContainer = findViewById(R.id.keyInfoContainer);
        inputContainer = findViewById(R.id.inputContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        // Initialize input fields from included layouts
        View pubkeyView = findViewById(R.id.pubkeyView);
        View labelView = findViewById(R.id.labelView);
        
        pubkeyInput = pubkeyView.findViewById(R.id.textInput);
        pubkeyInput.setHint(R.string.input_the_pubkey);
        labelInput = labelView.findViewById(R.id.textInput);
        labelInput.setHint(R.string.input_the_label);
        
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
        TextIconsUtils.setupTextIcons(this, R.id.pubkeyView, R.id.scanIcon, QR_SCAN_PUBKEY_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.labelView, R.id.scanIcon, QR_SCAN_LABEL_REQUEST_CODE);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_PUBKEY_REQUEST_CODE) {
            pubkeyInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_LABEL_REQUEST_CODE) {
            labelInput.setText(qrContent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up root layout touch listener to hide keyboard
        View rootLayout = findViewById(android.R.id.content);
        rootLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hideKeyboard();
            }
            return false;
        });

        // Initialize QR scan launcher
        qrScanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String qrContent = result.getData().getStringExtra("qr_content");
                    if (qrContent != null) {
                        // Determine which input to update based on the request code
                        int requestCode = result.getData().getIntExtra("request_code", 0);
                        handleQrScanResult(requestCode, qrContent);
                    }
                }
            }
        );

        // Setup toolbar
        ToolbarUtils.setupToolbar(this, "Create key by pubkey");

        // Replace deprecated onBackPressed() with OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        // Initialize views
        initializeViews();

        // Setup buttons
        setupButtons();
    }

    private void clearInputs() {
        pubkeyInput.setText("");
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
        String pubkey = pubkeyInput.getText() != null ? pubkeyInput.getText().toString() : "";
        String label = labelInput.getText() != null ? labelInput.getText().toString() : "";

        if (pubkey.isEmpty()) {
            TimberLogger.e(TAG, "Public key is empty");
            return;
        }

        if(!KeyTools.isPubkey(pubkey)){
            Toast.makeText(this, "Invalid public key", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a new KeyInfo object
        KeyInfo keyInfo = new KeyInfo(label, pubkey);

        // Generate and save avatar for the new key
        try {
            keyInfoManager.makeAvatarByFid(keyInfo.getId(),this);
            TimberLogger.i(TAG, "Avatar generated and saved for key: %s", keyInfo.getId());
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
            if (keyInfo != null) {
                saveKeyInfoToDatabase(keyInfo);
            }
        }
    }

    private KeyInfo createKeyInfoFromInputs() {
        String pubkey = pubkeyInput.getText() != null ? pubkeyInput.getText().toString() : "";
        String label = labelInput.getText() != null ? labelInput.getText().toString() : "";

        if (pubkey.isEmpty()) {
            Toast.makeText(this, "Public key is empty", Toast.LENGTH_SHORT).show();
            return null;
        }

        if(!KeyTools.isPubkey(pubkey)){
            Toast.makeText(this, "Invalid public key", Toast.LENGTH_SHORT).show();
            return null;
        }

        return new KeyInfo(label, pubkey);
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