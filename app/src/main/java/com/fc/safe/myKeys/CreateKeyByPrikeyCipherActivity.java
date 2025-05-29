package com.fc.safe.myKeys;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.ui.DetailFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.fc.safe.utils.TextIconsUtils;

public class CreateKeyByPrikeyCipherActivity extends BaseCryptoActivity {
    private static final String TAG = "CreateKeyByPrikeyCipher";
    private static final int QR_SCAN_PRIKEY_CIPHER_REQUEST_CODE = 1001;
    private static final int QR_SCAN_LABEL_REQUEST_CODE = 1002;
    private static final int QR_SCAN_PASSWORD_REQUEST_CODE = 1003;
    
    private DetailFragment detailFragment;
    private TextInputEditText prikeyCipherInput;
    private TextInputEditText passwordInput;
    private TextInputEditText labelInput;
    private Button clearButton;
    private Button previewButton;
    private Button saveButton;
    private LinearLayout detailContainer;
    private LinearLayout inputContainer;
    private LinearLayout buttonContainer;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_create_key_by_pri_key_cipher;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.create_key_by_prikey_cipher);
    }

    @Override
    protected void initializeViews() {
        detailContainer = findViewById(R.id.detailContainer);
        inputContainer = findViewById(R.id.inputContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        // Initialize input fields from included layouts
        View prikeyCipherView = findViewById(R.id.pubkeyView);
        View passwordView = findViewById(R.id.passwordView);
        View labelView = findViewById(R.id.labelView);
        
        prikeyCipherInput = prikeyCipherView.findViewById(R.id.textInput);
        prikeyCipherInput.setHint(R.string.input_the_prikeycipher);
        passwordInput = passwordView.findViewById(R.id.textInput);
        passwordInput.setHint(R.string.input_the_password);
        labelInput = labelView.findViewById(R.id.textInput);
        labelInput.setHint(R.string.input_the_label);

        // Set hints
        TextInputLayout passwordLayout = passwordView.findViewById(R.id.textInputWithScanLayout);
        
        // Remove password toggle and use TextIconsUtils instead
        passwordLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        
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
        TextIconsUtils.setupTextIcons(this, R.id.pubkeyView, R.id.scanIcon, QR_SCAN_PRIKEY_CIPHER_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.passwordView, R.id.scanIcon, QR_SCAN_PASSWORD_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.labelView, R.id.scanIcon, QR_SCAN_LABEL_REQUEST_CODE);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_PRIKEY_CIPHER_REQUEST_CODE) {
            prikeyCipherInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_LABEL_REQUEST_CODE) {
            labelInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_PASSWORD_REQUEST_CODE) {
            passwordInput.setText(qrContent);
        }
    }

    private void clearInputs() {
        prikeyCipherInput.setText("");
        passwordInput.setText("");
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
        String prikeyCipher = prikeyCipherInput.getText() != null ? prikeyCipherInput.getText().toString() : "";
        String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";
        String label = labelInput.getText() != null ? labelInput.getText().toString() : "";

        if (prikeyCipher.isEmpty()) {
            Toast.makeText(this, "Private key cipher is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Password is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Decrypt the private key
        byte[] prikey = decryptPrikey(prikeyCipher, password);
        if (prikey == null) return;

        byte[] prikey32 = KeyTools.getPrikey32(prikey);
        if(prikey32 == null){
            Toast.makeText(this, "Invalid private key", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create a new KeyInfo object
        KeyInfo keyInfo = new KeyInfo(label, prikey32, ConfigureManager.getInstance().getSymkey());

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
                .replace(R.id.detailContainer, detailFragment)
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
        String prikeyCipher = prikeyCipherInput.getText() != null ? prikeyCipherInput.getText().toString() : "";
        String password = passwordInput.getText() != null ? passwordInput.getText().toString() : "";
        String label = labelInput.getText() != null ? labelInput.getText().toString() : "";

        if (prikeyCipher.isEmpty()) {
            Toast.makeText(this, "Private key cipher is empty", Toast.LENGTH_SHORT).show();
            return null;
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Password is empty", Toast.LENGTH_SHORT).show();
            return null;
        }

        // Decrypt the private key
        byte[] prikey = decryptPrikey(prikeyCipher, password);
        if (prikey == null) return null;

        byte[] prikey32 = KeyTools.getPrikey32(prikey);
        if(prikey32 == null){
            Toast.makeText(this, "Invalid private key", Toast.LENGTH_SHORT).show();
            return null;
        }

        return new KeyInfo(label, prikey32, ConfigureManager.getInstance().getSymkey());
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

    @Nullable
    private byte[] decryptPrikey(String prikeyCipher, String password) {
        CryptoDataByte cryptoDataByte = Decryptor.decryptByPassword(prikeyCipher, password);
        if(cryptoDataByte.getCode() != 0){
            Toast.makeText(this,"Failed to decrypt private key",Toast.LENGTH_SHORT).show();
            return null;
        }
        byte[] prikey = cryptoDataByte.getData();
        if (prikey == null) {
            Toast.makeText(this, "Failed to decrypt private key", Toast.LENGTH_SHORT).show();
            return null;
        }
        return prikey;
    }
} 