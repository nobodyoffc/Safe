package com.fc.safe.myKeys;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.app.AlertDialog;

import androidx.core.util.Consumer;

import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.core.crypto.Kdf;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.ui.DetailFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.fc.safe.utils.TextIconsUtils;
import com.fc.safe.utils.ToastUtils;

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
            ToastUtils.showInfo(this, getString(R.string.please_input_phrase));
            return;
        }

        chooseKdfMethod(useArgon2id -> {
            byte[] priKey32 = derivePrivateKey(phrase, useArgon2id);
            KeyInfo keyInfo = new KeyInfo(label, priKey32, ConfigureManager.getInstance().getSymkey());
            generateAndSaveAvatar(keyInfo);
            showDetailFragment(keyInfo);
        });
    }

    private byte[] derivePrivateKey(String phrase, boolean useArgon2id) {
        if (useArgon2id) {
            // Empty salt keeps derivation deterministic for the same phrase
            return Kdf.Argon2id_No1_NrC7.deriveSymkey(phrase.toCharArray(), new byte[0]);
        }
        return Hash.sha256(phrase.getBytes());
    }

    private void chooseKdfMethod(Consumer<Boolean> onChosen) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_key_derivation)
                .setMessage(R.string.choose_key_derivation_message)
                .setPositiveButton(R.string.kdf_argon2id_recommended, (d, w) -> onChosen.accept(true))
                .setNegativeButton(R.string.kdf_sha256, (d, w) -> confirmSha256(onChosen))
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    private void confirmSha256(Consumer<Boolean> onChosen) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sha256_warning_title)
                .setMessage(R.string.sha256_warning_message)
                .setPositiveButton(R.string.continue_anyway, (d, w) -> onChosen.accept(false))
                .setNegativeButton(R.string.cancel, null)
                .show();
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
            // If no preview was done, ask for the derivation method then build KeyInfo
            createKeyInfoFromInputs(keyInfo -> {
                if (keyInfo == null) return;
                generateAndSaveAvatar(keyInfo);
                saveKeyInfoToDatabase(keyInfo);
            });
            return;
        }

        // Use the previewed KeyInfo
        KeyInfo keyInfo = (KeyInfo) detailFragment.getCurrentEntity();
        if (keyInfo == null) {
            ToastUtils.showError(this, getString(R.string.failed_to_get_keyinfo_from_preview));
            return;
        }
        generateAndSaveAvatar(keyInfo);
        saveKeyInfoToDatabase(keyInfo);
    }

    private void generateAndSaveAvatar(KeyInfo keyInfo) {
        if (keyInfo == null || keyInfo.getId() == null) {
            TimberLogger.e(TAG, "Cannot generate avatar: KeyInfo or ID is null");
            return;
        }

        try {
            // Check if avatar already exists
            byte[] existingAvatar = keyInfoManager.getAvatarById(keyInfo.getId());
            if (existingAvatar != null) {
                TimberLogger.i(TAG, "Avatar already exists for key: %s", keyInfo.getId());
                return;
            }

            // Generate and save avatar for the new key
            keyInfoManager.makeAvatarByFid(keyInfo.getId(), this);
            TimberLogger.i(TAG, "Avatar generated and saved for key: %s", keyInfo.getId());
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error generating avatar for key %s: %s", keyInfo.getId(), e.getMessage());
            // Show user-friendly error message
            ToastUtils.showError(this, getString(R.string.warning_failed_to_generate_avatar));
        }
    }

    private void createKeyInfoFromInputs(Consumer<KeyInfo> onReady) {
        String phrase = phraseInput.getText() != null ? phraseInput.getText().toString() : "";
        String label = labelInput.getText() != null ? labelInput.getText().toString() : "";

        if (phrase.isEmpty()) {
            ToastUtils.showInfo(this, getString(R.string.please_input_phrase));
            onReady.accept(null);
            return;
        }

        chooseKdfMethod(useArgon2id -> {
            byte[] priKey32 = derivePrivateKey(phrase, useArgon2id);
            onReady.accept(new KeyInfo(label, priKey32, ConfigureManager.getInstance().getSymkey()));
        });
    }

    private void saveKeyInfoToDatabase(KeyInfo keyInfo) {
        if (keyInfo == null) {
            ToastUtils.showError(this, getString(R.string.error_keyinfo_is_null));
            return;
        }

        // Check if the key already exists
        if (keyInfoManager.checkIfExisted(keyInfo.getId())) {
            new AlertDialog.Builder(this)
                .setTitle(getString(R.string.key_already_exists_title))
                .setMessage(getString(R.string.key_already_exists_message))
                .setPositiveButton(getString(R.string.replace), (dialog, which) -> {
                    saveAndFinish(keyInfo);
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        } else {
            saveAndFinish(keyInfo);
        }
    }

    private void saveAndFinish(KeyInfo keyInfo) {
        try {
            keyInfoManager.addKeyInfo(keyInfo);
            keyInfoManager.commit();
            ToastUtils.showInfo(this, getString(R.string.key_saved_successfully));
            setResult(RESULT_OK);
            finish();
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error saving KeyInfo: %s", e.getMessage());
            ToastUtils.showError(this, getString(R.string.error_saving_key_with_message, e.getMessage()));
        }
    }
} 