package com.fc.safe.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.fcData.Signature;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.google.android.material.textfield.TextInputEditText;

public class SignMsgActivity extends BaseCryptoActivity {
    private static final int QR_SCAN_TEXT_REQUEST_CODE = 1001;
    private static final int QR_SCAN_KEY_REQUEST_CODE = 1002;

    private TextInputEditText resultText;
    private TextInputEditText textInput;
    private TextInputEditText keyInput;
    private RadioGroup optionContainer;
    private RadioButton ecdsaOption;
    private RadioButton schnorrOption;
    private RadioButton symkeyOption;
    private Button clearButton;
    private Button copyButton;
    private Button signButton;
    private KeyInfoManager keyInfoManager;

//    private IoIconsView textIcons;
//    private IoIconsView keyIcons;
//    private LinearLayout buttonContainer;
//    private LocalDB<KeyInfo> keyInfoLocalDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize KeyInfoManager
        keyInfoManager = KeyInfoManager.getInstance(this);
//        keyInfoLocalDB = keyInfoManager.getKeyInfoDB();

        initializeViews();
    }

    @Override
    protected void handleChooseKeyResult(Intent data) {
        KeyInfo keyInfo = ChooseKeyInfoActivity.getSelectedKeyInfo(data, keyInfoManager).get(0);
        if (keyInfo != null && keyInfo.getPrikeyCipher() != null) {
            // Set the key ID as gray text
            String text = "PriKey of " + StringUtils.omitMiddle(keyInfo.getId(), 13);
            keyInput.setText(text);
            keyInput.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
            keyInput.setEnabled(false);
            
            // Store the actual private key cipher for decryption
            String priKeyCipher = keyInfo.getPrikeyCipher();
            keyInput.setTag(priKeyCipher);
        } else {
            showToast("Selected key has no private key cipher");
        }
    }

    @Override
    protected void initializeViews() {
        resultText = findViewById(R.id.resultView).findViewById(R.id.textBoxWithMakeQrLayout);
        resultText.setHint(R.string.signature);
        textInput = findViewById(R.id.textView).findViewById(R.id.textInput);
        textInput.setHint(R.string.input_text_to_be_signed);
        keyInput = findViewById(R.id.keyView).findViewById(R.id.keyInput);
        keyInput.setHint(R.string.input_the_key);

//        textIcons = findViewById(R.id.textView).findViewById(R.id.textIcons);
//        keyIcons = findViewById(R.id.keyView).findViewById(R.id.keyIcons);

        optionContainer = findViewById(R.id.optionContainer);
        ecdsaOption = findViewById(R.id.ecdsaOption);
        schnorrOption = findViewById(R.id.schnorrOption);
        symkeyOption = findViewById(R.id.symKeyOption);

        clearButton = findViewById(R.id.clearButton);
        copyButton = findViewById(R.id.copyButton);
        signButton = findViewById(R.id.signButton);

//        buttonContainer = findViewById(R.id.buttonContainer);

        // Setup radio buttons
        setupRadioButtons();

        // Setup buttons
        setupButtons();

        // Setup result icons
        setupResultIcons(R.id.resultView, R.id.makeQrIcon, () -> handleQrGeneration(resultText.getText()==null? null:resultText.getText().toString()));

        // Setup text icons
        setupTextIcons(R.id.textView, R.id.scanIcon, QR_SCAN_TEXT_REQUEST_CODE);

        // Setup key icons
        setupKeyIcons(R.id.keyView, R.id.peopleAndScanIcons, QR_SCAN_KEY_REQUEST_CODE);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_sign_msg;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.sign_words);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_TEXT_REQUEST_CODE) {
            textInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_KEY_REQUEST_CODE) {
            keyInput.setText(qrContent);
        }
    }

    private void setupRadioButtons() {
        optionContainer.setOnCheckedChangeListener((group, checkedId) -> {
            // Get the parent view that contains the TextInputLayout
            if (checkedId == R.id.symKeyOption) {
                keyInput.setHint(getString(R.string.input_the_symmetric_key));
            } else {
                keyInput.setHint(getString(R.string.input_the_private_key));
            }
        });
    }

    protected void setupButtons() {
        // Initially disable copy button
        copyButton.setEnabled(false);
        
        // Set click listeners
        setupButton(clearButton, v -> clearInputs());
        setupButton(copyButton, v -> copyToClipboard());
        setupButton(signButton, v -> signMessage());
    }

    private void clearInputs() {
        resultText.setText("");
        clearInput(textInput);
        clearInput(keyInput);
        copyButton.setEnabled(false);
        optionContainer.check(R.id.ecdsaOption);
    }

    private void copyToClipboard() {
        String result = resultText.getText().toString();
        if (!result.isEmpty()) {
            copyToClipboard(result, "signature");
        }
    }

    private void signMessage() {
        String message = textInput.getText().toString();
        String key = keyInput.isEnabled() ? 
            keyInput.getText().toString() : 
            (String) keyInput.getTag();

        if (message.isEmpty()) {
            showToast(getString(R.string.please_input_message));
            return;
        }

        if (key.isEmpty()) {
            showToast(getString(R.string.please_input_key));
            return;
        }

        Signature signature = new Signature();
        signature.setMsg(message);

        byte[] keyBytes;

        try {
            if (keyInput.isEnabled()) {
                keyBytes = Hex.fromHex(key);
            } else {
                // Key from selection - decrypt the priKeyCipher
                keyBytes = decryptPriKeyCipher(key);
            }
            
            if(keyBytes == null) {
                showToast("Wrong key.");
                return;
            }
        } catch (Exception e) {
            showToast("Wrong key.");
            return;
        }

        // Set the key once for all algorithm types
        signature.setKey(keyBytes);
        
        // Set the appropriate algorithm based on selection
        if (ecdsaOption.isChecked()) {
            signature.setAlg(AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);
        } else if (schnorrOption.isChecked()) {
            signature.setAlg(AlgorithmId.FC_SchnorrSignMsg_No1_NrC7);
        } else if (symkeyOption.isChecked()){
            signature.setAlg(AlgorithmId.FC_Sha256SymSignMsg_No1_NrC7);
        }

        try {
            signature.sign();
            String result = signature.toNiceJson();
            resultText.setText(result);
            copyButton.setEnabled(true);
        } catch (Exception e) {
            showToast("Failed to sign words.");
        }
    }
    
    private byte[] decryptPriKeyCipher(String priKeyCipher) {
        Configure configure = ConfigureManager.getInstance().getConfigure();
        return Decryptor.decryptPrikey(priKeyCipher, configure.getSymkey());
    }

} 