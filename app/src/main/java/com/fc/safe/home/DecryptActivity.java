package com.fc.safe.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.google.android.material.textfield.TextInputEditText;

public class DecryptActivity extends BaseCryptoActivity {
    private static final String TAG = "DecryptActivity";
    private static final int QR_SCAN_TEXT_REQUEST_CODE = 1001;
    private static final int QR_SCAN_KEY_REQUEST_CODE = 1002;

    private TextInputEditText cipherEditText;
    private TextInputEditText keyEditText;
    private Button clearButton;
    private Button copyDataButton;
    private Button decryptButton;
    private LinearLayout buttonContainer;
    private LocalDB<KeyInfo> keyInfoLocalDB;
    
    // Activity result launchers
    private ActivityResultLauncher<Intent> qrScanKeyLauncher;
    private ActivityResultLauncher<Intent> chooseKeyLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize KeyInfoManager
        keyInfoManager = KeyInfoManager.getInstance(this);
        keyInfoLocalDB = keyInfoManager.getKeyInfoDB();
        
        // Initialize views
        initializeViews();

        // Set up buttons
        setupButtons();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_decrypt;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.decrypt);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_TEXT_REQUEST_CODE) {
            cipherEditText.setText(qrContent);
        } else if (requestCode == QR_SCAN_KEY_REQUEST_CODE) {
            keyEditText.setText(qrContent);
        }
    }

    @Override
    protected void initializeViews() {
        TimberLogger.i(TAG, "Initializing views in DecryptActivity");
        cipherEditText = findViewById(R.id.cipherView).findViewById(R.id.textInput);
        cipherEditText.setHint(R.string.input_the_cipher);
        keyEditText = findViewById(R.id.keyView).findViewById(R.id.keyInput);
        keyEditText.setHint(R.string.input_the_key);
        clearButton = findViewById(R.id.clearButton);
        copyDataButton = findViewById(R.id.copyDataButton);
        copyDataButton.setEnabled(false);
        decryptButton = findViewById(R.id.decryptButton);
        resultTextView = findViewById(R.id.resultView).findViewById(R.id.textBoxWithMakeQrLayout);
        resultTextView.setHint(R.string.result);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        // Setup icons using shared methods
        setupTextIcons(R.id.cipherView, R.id.scanIcon, QR_SCAN_TEXT_REQUEST_CODE);
        setupKeyIcons(R.id.keyView, R.id.peopleAndScanIcons, QR_SCAN_KEY_REQUEST_CODE);
        setupResultIcons(R.id.resultView, R.id.makeQrIcon, () -> handleQrGeneration(resultTextView.getText().toString()));
    }

    @Override
    protected void setupButtons() {
        // Set click listeners using shared method
        setupButton(clearButton, v -> clearInputs());
        setupButton(copyDataButton, v -> copyConvertedDataToClipboard());
        setupButton(decryptButton, v -> decrypt());
    }

    @Override
    protected void handleChooseKeyResult(Intent data) {
        KeyInfo keyInfo = ChooseKeyInfoActivity.getSelectedKeyInfo(data, keyInfoManager).get(0);
        if (keyInfo != null && keyInfo.getPrikeyCipher() != null) {
            // Set the key ID as gray text
            String text = "PriKey of " + StringUtils.omitMiddle(keyInfo.getId(), 13);
            keyEditText.setText(text);
            keyEditText.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
            keyEditText.setEnabled(false);
            
            // Store the actual private key cipher for decryption
            String priKeyCipher = keyInfo.getPrikeyCipher();
            keyEditText.setTag(priKeyCipher);
        } else {
            showToast(getString(R.string.selected_key_has_no_private_key_cipher));
        }
    }

    private void clearInputs() {
        cipherEditText.setText("");
        keyEditText.setText("");
        keyEditText.setEnabled(true);
        resultTextView.setText("");

    }

    private void decrypt() {
        // Always clear the result first
        resultTextView.setText("");
        copyDataButton.setEnabled(false);
        if( cipherEditText.getText()==null){
            Toast.makeText(this, getString(R.string.please_enter_cipher), SafeApplication.TOAST_LASTING).show();
            return;
        }
        String cipher = cipherEditText.getText().toString();
        String key = keyEditText.isEnabled() ?
                keyEditText.getText()!=null?
                        keyEditText.getText().toString()
                        : null
                : (String) keyEditText.getTag();
        if(key==null){
            Toast.makeText(this, getString(R.string.please_enter_key), SafeApplication.TOAST_LASTING).show();
            return;
        }
        // Validate inputs
        if (TextUtils.isEmpty(cipher)) {
            showToast(getString(R.string.please_enter_cipher));
            return;
        }

        if (TextUtils.isEmpty(key)) {
            showToast(getString(R.string.please_enter_key));
            return;
        }

        try {
            // Parse and validate cipher data
            CryptoDataByte cryptoDataByteCipher = parseCipherData(cipher);
            if (cryptoDataByteCipher == null) {
                showToast(getString(R.string.not_a_valid_cipher));
                return;
            }
            
            // Get cipher type and parse key data
            EncryptType cipherType = cryptoDataByteCipher.getType();
            byte[] keyBytes = parseKeyData(key, cipherType);
            
            // Validate key for asymmetric encryption
            if (keyBytes == null && 
                (cipherType == EncryptType.AsyOneWay || cipherType == EncryptType.AsyTwoWay)) {
                showToast(getString(R.string.not_a_valid_key));
                return;
            }

            // Perform decryption
            decryptData(cryptoDataByteCipher, key, keyBytes, cipherType);

            // Check decryption result
            if (cryptoDataByteCipher.getCode() != 0) {
                showToast(getString(R.string.failed_to_decrypt_with_message, cryptoDataByteCipher.getMessage()));
                return;
            }

            // Show decrypted data and enable copy button
            byte[] data = cryptoDataByteCipher.getData();
            if(data==null){
                showToast(getString(R.string.got_nothing_from_cipher, cryptoDataByteCipher.getMessage()));
                return;
            }
            updateResultText(new String(data));
            copyDataButton.setEnabled(true);
            
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error decrypting: " + e.getMessage());
            showToast(getString(R.string.error_decrypting, e.getMessage()));
        }
    }
    
    private CryptoDataByte parseCipherData(String cipher) {
        if (JsonUtils.isJson(cipher)) {
            return CryptoDataByte.fromJson(cipher);
        } else if (StringUtils.isBase64(cipher)) {
            byte[] bundle = Base64.decode(cipher, Base64.DEFAULT);
            return CryptoDataByte.fromBundle(bundle);
        }
        return null;
    }
    
    private byte[] parseKeyData(String key, EncryptType cipherType) {
        if (JsonUtils.isJson(key)) {
            Configure configure = ConfigureManager.getInstance().getConfigure();
            return Decryptor.decryptPrikey(key, configure.getSymkey());
        } 
        
        // For asymmetric encryption, extract private key
        if (cipherType == EncryptType.AsyOneWay || cipherType == EncryptType.AsyTwoWay) {
            return KeyTools.getPrikey32(key);
        }
        
        // For other encryption types, return null
        return null;
    }
    
    private void decryptData(CryptoDataByte cryptoDataByteCipher, String key, byte[] keyBytes, EncryptType cipherType) {
        switch (cipherType) {
            case Password -> Decryptor.decryptByPassword(cryptoDataByteCipher, key.toCharArray());
            case Symkey -> Decryptor.decryptBySymkey(cryptoDataByteCipher, key);
            case AsyOneWay, AsyTwoWay -> Decryptor.decryptByAsyKey(cryptoDataByteCipher, keyBytes);
        }
    }

} 