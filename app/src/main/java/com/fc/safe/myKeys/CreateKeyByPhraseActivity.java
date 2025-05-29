package com.fc.safe.myKeys;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import android.widget.Toast;

import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.ui.DetailFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.fc.safe.utils.TextIconsUtils;

public class CreateKeyByPhraseActivity extends BaseCryptoActivity {
    private static final String TAG = "CreateKeyByPhrase";
    private static final int QR_SCAN_PHRASE_REQUEST_CODE = 1001;
    private static final int QR_SCAN_LABEL_REQUEST_CODE = 1002;
    
    private KeyInfoManager keyInfoManager;
    private DetailFragment detailFragment;
    private TextInputEditText phraseInput;
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
        return getString(R.string.create_key_by_phrase);
    }

    @Override
    protected void initializeViews() {
        keyInfoManager = KeyInfoManager.getInstance(this);
        keyInfoContainer = findViewById(R.id.keyInfoContainer);
        inputContainer = findViewById(R.id.inputContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        // Initialize input fields from included layouts
        View phraseView = findViewById(R.id.pubkeyView);
        View labelView = findViewById(R.id.labelView);
        
        phraseInput = phraseView.findViewById(R.id.textInput);
        phraseInput.setHint(R.string.input_the_phrase);
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
        TextIconsUtils.setupTextIcons(this, R.id.pubkeyView, R.id.scanIcon, QR_SCAN_PHRASE_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.labelView, R.id.scanIcon, QR_SCAN_LABEL_REQUEST_CODE);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_PHRASE_REQUEST_CODE) {
            phraseInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_LABEL_REQUEST_CODE) {
            labelInput.setText(qrContent);
        }
    }

    private void clearInputs() {
        phraseInput.setText("");
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
        String phrase = phraseInput.getText() != null ? phraseInput.getText().toString() : "";
        String label = labelInput.getText() != null ? labelInput.getText().toString() : "";

        if (phrase.isEmpty()) {
            return;
        }

        // Generate private key from phrase using SHA-256
        byte[] priKey32 = Hash.sha256(phrase.getBytes());

        // Create a new KeyInfo object
        KeyInfo keyInfo = new KeyInfo(label, priKey32, ConfigureManager.getInstance().getSymkey());

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
            // Generate and save avatar for the new key
            try {
                keyInfoManager.makeAvatarByFid(keyInfo.getId(), this);
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error generating avatar: %s", e.getMessage());
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
        String phrase = phraseInput.getText() != null ? phraseInput.getText().toString() : "";
        String label = labelInput.getText() != null ? labelInput.getText().toString() : "";

        if (phrase.isEmpty()) {
            Toast.makeText(this, "Phrase is empty", Toast.LENGTH_SHORT).show();
            return null;
        }

        // Generate private key from phrase using SHA-256
        byte[] priKey32 = Hash.sha256(phrase.getBytes());

        return new KeyInfo(label, priKey32, ConfigureManager.getInstance().getSymkey());
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