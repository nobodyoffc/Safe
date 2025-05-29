package com.fc.safe.tools;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.fc.safe.utils.QRCodeGenerator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PubkeyConverterActivity extends BaseCryptoActivity {
    private static final String TAG = "PubkeyConverterActivity";
    protected EditText keyEditText;
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
        return R.layout.activity_public_key_converter;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.public_key_converter);
    }

    protected void initializeViews() {
        keyEditText = findViewById(R.id.keyView).findViewById(R.id.keyInput);
        keyEditText.setHint(R.string.input_the_pubkey);
        resultTextView = findViewById(R.id.resultView).findViewById(R.id.textBoxWithMakeQrLayout);
        resultTextView.setHint(R.string.result);

        clearButton = findViewById(R.id.clearButton);
        copyButton = findViewById(R.id.copyButton);
        convertButton = findViewById(R.id.convertButton);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        setupIoIconsView(R.id.keyView, R.id.peopleAndScanIcons, false, true, true, false,
                null, v -> showChooseKeyInfoDialog(true), () -> startQrScan(0), null);
            
        setupIoIconsView(R.id.resultView, R.id.makeQrIcon, true, false, false, false,
                () -> {
                String content = resultTextView.getText().toString();
                if (!TextUtils.isEmpty(content)) {
                    QRCodeGenerator.generateAndShowQRCode(this, content);
                } else {
                    showToast("No content to generate QR code");
                }
            }, null, null, null);
    }

    private void setupListeners() {
        // Set click listeners
        clearButton.setOnClickListener(v -> {
            hideKeyboard();
            clearInputs();
        });
        copyButton.setOnClickListener(v -> {
            hideKeyboard();
            copyConvertedDataToClipboard();
        });
        convertButton.setOnClickListener(v -> {
            hideKeyboard();
            convert();
        });
    }

    @Override
    protected void setupButtons() {
        setupButton(clearButton, v -> {
            hideKeyboard();
            clearInputs();
        });
        setupButton(copyButton, v -> {
            hideKeyboard();
            copyConvertedDataToClipboard();
        });
        setupButton(convertButton, v -> {
            hideKeyboard();
            convert();
        });
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
            showToast("No keys available");
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
            showToast("Please enter a public key");
            return;
        }

        try {
            String pubkey = keyEditText.isEnabled() ?
                KeyTools.getPubkey33(key) :
                key;

            Map<String, String> resultMap = new LinkedHashMap<>();
            resultMap.put("FID", KeyTools.pubkeyToFchAddr(pubkey));
            resultMap.put("Compressed in Hex", pubkey);
            resultMap.put("UnCompressed in Hex", KeyTools.recoverPK33ToPK65(pubkey));
            resultMap.put("WIF uncompressed", KeyTools.getPubkeyWifUncompressed(pubkey));
            resultMap.put("WIF compressed with ver 0", KeyTools.getPubkeyWifCompressedWithVer0(pubkey));
            resultMap.put("WIF compressed without ver", KeyTools.getPubkeyWifCompressedWithoutVer(pubkey));

            StringBuilder resultBuilder = new StringBuilder();
            for (Map.Entry<String, String> entry : resultMap.entrySet()) {
                SpannableString keySpan = new SpannableString(entry.getKey() + ": ");
                keySpan.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, keySpan.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                keySpan.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.field_name, getTheme())), 
                    0, keySpan.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                
                resultBuilder.append(keySpan)
                           .append(entry.getValue())
                           .append("\n\n");
            }

            updateResultText(resultBuilder.toString());
            copyButton.setEnabled(true);
            
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error converting: " + e.getMessage());
            showToast("Error converting: " + e.getMessage());
        }
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        keyEditText.setText(qrContent);
    }

    @Override
    protected void handleChooseKeyResult(Intent data) {
        KeyInfo keyInfo = ChooseKeyInfoActivity.getSelectedKeyInfo(data, keyInfoManager).get(0);
        if (keyInfo != null && keyInfo.getPubkey()!= null) {
            // Set the key ID as gray text
            String text = "Pubkey of " + StringUtils.omitMiddle(keyInfo.getId(), 13);
            keyEditText.setText(text);
            keyEditText.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
            keyEditText.setEnabled(false);

            // Store the actual private key cipher for decryption
            String pubkey= keyInfo.getPubkey();
            keyEditText.setTag(pubkey);
        } else {
            showToast("Selected key has no private key cipher");
        }
    }
} 