package com.fc.safe.tools;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;

import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.fc.safe.utils.QRCodeGenerator;

import java.util.List;

public class PrikeyConverterActivity extends BaseCryptoActivity {
    private static final String TAG = "PrikeyConverterActivity";
    private static final int QR_SCAN_REQUEST_CODE = 1001;

    protected EditText keyEditText;
    private RadioGroup formatOptions;
    private LinearLayout buttonContainer;
    private KeyInfoManager keyInfoManager;
    private LocalDB<KeyInfo> keyInfoLocalDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize KeyInfoManager
        keyInfoManager = KeyInfoManager.getInstance(this);
        keyInfoLocalDB = keyInfoManager.getKeyInfoDB();

        // Initialize views
        initializeViews();
        setupListeners();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_private_key_converter;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.private_key_converter);
    }

    protected void initializeViews() {
        keyEditText = findViewById(R.id.keyView).findViewById(R.id.keyInput);
        keyEditText.setHint(R.string.input_the_prikey);

        resultTextView = findViewById(R.id.resultView).findViewById(R.id.textBoxWithMakeQrLayout);
        resultTextView.setHint(R.string.result);

        clearButton = findViewById(R.id.clearButton);
        copyButton = findViewById(R.id.copyButton);
        convertButton = findViewById(R.id.convertButton);
        formatOptions = findViewById(R.id.formatOptions);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        // Set default option to Base58 Compressed
        formatOptions.check(R.id.optionBase58Compressed);
    
        setupIoIconsView(R.id.keyView, R.id.peopleAndScanIcons, false, true, true, false,
                null, v -> showChooseKeyInfoDialog(true), () -> startQrScan(QR_SCAN_REQUEST_CODE), null);
            
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
        clearButton.setOnClickListener(v -> clearInputs());
        copyButton.setOnClickListener(v -> copyConvertedDataToClipboard());
        convertButton.setOnClickListener(v -> convert());
    }

    @Override
    protected void setupButtons() {
        setupButton(clearButton, v -> clearInputs());
        setupButton(copyButton, v -> copyConvertedDataToClipboard());
        setupButton(convertButton, v -> convert());
    }
    
    private void clearInputs() {
        keyEditText.setText("");
        keyEditText.setEnabled(true);
        keyEditText.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
        keyEditText.setTag(null);
        updateResultText(null);
    }

    private void showChooseKeyInfoDialog() {
        List<KeyInfo> keyInfoList = keyInfoManager.getAllKeyInfoList();
        if (keyInfoList.isEmpty()) {
            showToast(getString(R.string.no_keys_available));
            return;
        }

        Intent intent = ChooseKeyInfoActivity.newIntent(this, keyInfoList,true);
        chooseKeyLauncher.launch(intent);
    }

    private void convert() {
        // Always clear the result first
        resultTextView.setText("");
        copyButton.setEnabled(false);

        String key = keyEditText.isEnabled() ? 
            keyEditText.getText().toString() : 
            (String) keyEditText.getTag();

        if (TextUtils.isEmpty(key)) {
            showToast(getString(R.string.please_enter_a_private_key));
            return;
        }

        try {
            byte[] prikeyBytes = null;

            if(JsonUtils.isJson(key)){
                prikeyBytes  = Decryptor.decryptPrikey(key, ConfigureManager.getInstance().getSymkey());
            }else prikeyBytes = KeyTools.getPrikey32(key);
            if (prikeyBytes == null) {
                showToast(getString(R.string.invalid_private_key_format));
                return;
            }

            String result;
            int selectedId = formatOptions.getCheckedRadioButtonId();
            
            if (selectedId == R.id.optionHex) {
                result = Hex.toHex(prikeyBytes);
            } else if (selectedId == R.id.optionBase58Compressed) {
                result = KeyTools.prikey32To38WifCompressed(Hex.toHex(prikeyBytes));
            } else if (selectedId == R.id.optionBase58) {
                result = KeyTools.prikey32To37(Hex.toHex(prikeyBytes));
            } else {
                showToast(getString(R.string.please_select_a_conversion_format));
                return;
            }

            updateResultText(result);
            copyButton.setEnabled(true);
            
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error converting: " + e.getMessage());
            showToast(getString(R.string.error_converting, e.getMessage()));
        }
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        keyEditText.setText(qrContent);
    }

    @Override
    protected void handleChooseKeyResult(Intent data) {
        KeyInfo keyInfo = ChooseKeyInfoActivity.getSelectedKeyInfo(data, keyInfoManager).get(0);
        if (keyInfo != null && keyInfo.getPrikeyCipher() != null) {
            // Set the key ID as gray text
            String text = "Prikey of " + StringUtils.omitMiddle(keyInfo.getId(), 13);
            keyEditText.setText(text);
            keyEditText.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
            keyEditText.setEnabled(false);

            // Store the actual private key cipher for decryption
            String prikeyCipher = keyInfo.getPrikeyCipher();
            keyEditText.setTag(prikeyCipher);
        } else {
            showToast(getString(R.string.selected_key_has_no_private_key_cipher));
        }
    }
} 