package com.fc.safe.home;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;

import androidx.activity.result.contract.ActivityResultContracts;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class EncryptActivity extends BaseCryptoActivity {
    private static final int QR_SCAN_TEXT_REQUEST_CODE = 1001;
    private static final int QR_SCAN_KEY_REQUEST_CODE = 1002;
    
    private TextInputEditText textInput;
    private TextInputEditText keyInput;
    private TextInputEditText resultText;
    private RadioGroup optionContainer;
    private CryptoDataByte cryptoDataByte;
    private Button copyButton;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_encrypt;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.encrypt);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize KeyInfoManager
        keyInfoManager = KeyInfoManager.getInstance(this);

        // Initialize key selection launcher
        chooseKeyLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    List<KeyInfo> selectedKeys = ChooseKeyInfoActivity.getSelectedKeyInfo(result.getData(), keyInfoManager);
                    if (selectedKeys != null && !selectedKeys.isEmpty()) {
                        KeyInfo keyInfo = selectedKeys.get(0);
                        if (keyInfo.getPubkey() != null) {
                            // Set the key ID as gray text
                            String text = "PubKey of " + StringUtils.omitMiddle(keyInfo.getId(), 13);
                            keyInput.setText(text);
                            keyInput.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
                            keyInput.setEnabled(false);
                            
                            // Store the actual public key for encryption
                            keyInput.setTag(keyInfo.getPubkey());
                            
                            // Automatically set the PubKey option when a public key is selected
                            optionContainer.check(R.id.pubKeyOption);
                        } else {
                            showToast("Selected key has no public key");
                        }
                    }
                }
            }
        );

        // Initialize views
        initializeViews();

        // Set up buttons
        setupButtons();
    }

    @Override
    protected void initializeViews() {
        textInput = findViewById(R.id.textView).findViewById(R.id.textInput);
        textInput.setHint(R.string.input_the_text_to_be_encrypted);
        
        resultText = findViewById(R.id.resultView).findViewById(R.id.textBoxWithMakeQrLayout);
        resultText.setHint(R.string.cipher);
        
        keyInput = findViewById(R.id.keyView).findViewById(R.id.keyInput);
        keyInput.setHint(R.string.input_the_key);
        
        optionContainer = findViewById(R.id.optionContainer);

        // Setup icons using shared methods
        setupTextIcons(R.id.textView, R.id.scanIcon, QR_SCAN_TEXT_REQUEST_CODE);
        setupKeyIcons(R.id.keyView, R.id.peopleAndScanIcons, QR_SCAN_KEY_REQUEST_CODE);
        setupResultIcons(R.id.resultView, R.id.makeQrIcon, () -> handleQrGeneration(resultText.getText()==null? null:resultText.getText().toString()));
    }

    @Override
    protected void setupButtons() {
        Button clearButton = findViewById(R.id.clearButton);
        copyButton = findViewById(R.id.copyButton);
        Button encryptButton = findViewById(R.id.encryptButton);

        // Initially disable copy button
        copyButton.setEnabled(false);
        
        // Set click listeners
        setupButton(clearButton, v -> clearInputs());
        setupButton(copyButton, v -> copyToClipboard());
        setupButton(encryptButton, v -> handleEncryption());
    }

    private void clearInputs() {
        clearInput(textInput);
        clearInput(keyInput);
        resultText.setText("");
        cryptoDataByte = null;
        copyButton.setEnabled(false);
        optionContainer.check(R.id.passwordOption);
    }

    private void copyToClipboard() {
        if (cryptoDataByte != null) {
            copyToClipboard(cryptoDataByte.toNiceJson(), "cipher");
        }
    }

    private void handleEncryption() {
        resultText.setText("");
        copyButton.setEnabled(false);

        String text = textInput.getText() != null ? textInput.getText().toString() : "";
        String key = keyInput.getText() != null ? keyInput.getText().toString() : "";

        if (text.isEmpty() || key.isEmpty()) {
            showToast("Please input both text and key");
            return;
        }
        encrypt(text, key);
    }

    private void encrypt(String text, String key) {
        resultText.setText("");
        copyButton.setEnabled(false);

        try {
            String keyType = getSelectedKeyType();
            String actualKey = getActualKey(key, keyType);
            
            if (!validateKey(actualKey, keyType)) {
                return;
            }

            cryptoDataByte = createEncryptedData(text, actualKey, keyType);
            resultText.setText(cryptoDataByte.toNiceJson());
            copyButton.setEnabled(true);
        } catch (Exception e) {
            showError("Encryption failed: " + e.getMessage());
        }
    }

    private String getSelectedKeyType() {
        int selectedId = optionContainer.getCheckedRadioButtonId();
        if (selectedId == R.id.symKeyOption) {
            return "symKey";
        } else if (selectedId == R.id.pubKeyOption) {
            return "pubKey";
        }
        return "password";
    }

    private String getActualKey(String inputKey, String keyType) {
        if (keyType.equals("pubKey") && !keyInput.isEnabled()) {
            return (String) keyInput.getTag();
        }
        return inputKey;
    }

    private boolean validateKey(String key, String keyType) {
        switch (keyType) {
            case "symKey":
                if (!Hex.isHex32(key)) {
                    showError("The symKey should be 32 bytes in hex");
                    return false;
                }
                break;
            case "pubKey":
                if (!KeyTools.isPubkey(key)) {
                    showError("It is not a public key");
                    return false;
                }
                break;
        }
        return true;
    }

    private CryptoDataByte createEncryptedData(String text, String key, String keyType) {
        return switch (keyType) {
            case "password" ->
                    new Encryptor().encryptByPassword(text.getBytes(), key.toCharArray());
            case "symKey" -> new Encryptor().encryptBySymkey(text.getBytes(), Hex.fromHex(key));
            case "pubKey" -> new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7)
                    .encryptByAsyOneWay(text.getBytes(), Hex.fromHex(key));
            default -> throw new IllegalArgumentException("Unsupported key type: " + keyType);
        };
    }

    private void showError(String message) {
        showToast(message);
        copyButton.setEnabled(false);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (qrContent != null) {
            if (requestCode == QR_SCAN_TEXT_REQUEST_CODE) {
                textInput.setText(qrContent);
            } else if (requestCode == QR_SCAN_KEY_REQUEST_CODE) {
                keyInput.setText(qrContent);
            }
        }
    }
} 