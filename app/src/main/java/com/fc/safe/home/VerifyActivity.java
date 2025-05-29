package com.fc.safe.home;

import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.result.contract.ActivityResultContracts;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.fcData.Signature;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.fc.safe.ui.IoIconsView;
import com.google.android.material.textfield.TextInputEditText;

public class VerifyActivity extends BaseCryptoActivity {
    private static final int QR_SCAN_TEXT_REQUEST_CODE = 1001;
    private static final int QR_SCAN_KEY_REQUEST_CODE = 1002;

    private ImageView resultIcon;
    private TextInputEditText textInput;
    private TextInputEditText keyInput;
    private Button clearButton;
    private Button verifyButton;
    private IoIconsView textIcons;
    private IoIconsView keyIcons;
    private LinearLayout buttonContainer;
    private LocalDB<KeyInfo> keyInfoLocalDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize KeyInfoManager
        keyInfoManager = KeyInfoManager.getInstance(this);
        keyInfoLocalDB = keyInfoManager.getKeyInfoDB();
        
        // Initialize key selection launcher
        chooseKeyLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    KeyInfo keyInfo = ChooseKeyInfoActivity.getSelectedKeyInfo(result.getData(),keyInfoManager).get(0);
                    if (keyInfo != null) {
                        // Set the key ID as gray text
                        String text = "PubKey of " + keyInfo.getId();
                        keyInput.setText(text);
                        keyInput.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
                        keyInput.setEnabled(false);
                        
                        // Store the actual key ID for verification
                        keyInput.setTag(keyInfo.getId());
                    } else {
                        showToast("Selected key has no ID");
                    }
                }
            }
        );

        // Setup buttons
        setupButtons();

        // Setup text icons
        setupTextIcons(R.id.textView, R.id.scanIcon, QR_SCAN_TEXT_REQUEST_CODE);
        setupTextIcons(R.id.keyView, R.id.scanIcon, QR_SCAN_KEY_REQUEST_CODE);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_verify;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.verify_signature);
    }

    @Override
    protected void initializeViews() {
        resultIcon = findViewById(R.id.resultIcon);
        
        // Get parent views first
        View textView = findViewById(R.id.textView);
        View keyView = findViewById(R.id.keyView);
        
        // Find child views using parent views
        textInput = textView.findViewById(R.id.textInput);
        textInput.setHint(R.string.input_the_signature);
        keyInput = keyView.findViewById(R.id.textInput);
        keyInput.setHint(R.string.only_for_sym_key_sign);

        textIcons = textView.findViewById(R.id.scanIcon);
        keyIcons = keyView.findViewById(R.id.scanIcon);
        
        clearButton = findViewById(R.id.clearButton);
        verifyButton = findViewById(R.id.verifyButton);
        buttonContainer = findViewById(R.id.buttonContainer);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_TEXT_REQUEST_CODE) {
            textInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_KEY_REQUEST_CODE) {
            keyInput.setText(qrContent);
        }
    }

    @Override
    protected void setupButtons() {
        // Set click listeners
        setupButton(clearButton, v -> clearInputs());
        setupButton(verifyButton, v -> verifyMessage());
    }

    private void clearInputs() {
        clearInput(textInput);
        clearInput(keyInput);
        resultIcon.setImageResource(R.drawable.ic_verify_default);
    }

    private void verifyMessage() {
        Editable text = textInput.getText();
        if(text==null){
            showToast("Please input signature");
            return;
        }
        String signatureJson = text.toString();
        String key = keyInput.isEnabled() ?
                keyInput.getText()==null?null:keyInput.getText().toString() :
            (String) keyInput.getTag();

        if (signatureJson.isEmpty()) {
            showToast(getString(R.string.please_input_message));
            return;
        }

        try {
            // Parse the signature JSON
            Signature signature = Signature.fromJson(signatureJson);

            if(signature.getAlg().equals(AlgorithmId.FC_Sha256SymSignMsg_No1_NrC7)){
                if(key==null || key.isEmpty()){
                    showToast("The symKey is required for SHA256 signature");
                    return;
                }else{
                    signature.setKey(Hex.fromHex(key));
                }
            }else if(key != null && !key.isEmpty()){
                if(signature.getFid()==null && KeyTools.isGoodFid(key))signature.setFid(key);
                else showToast("The key is not required.");
            }
            
            // Verify the signature
            boolean isValid = signature.verify();
            
            // Update the result icon
            if (isValid) {
                resultIcon.setImageResource(R.drawable.ic_verify_success);
            } else {
                resultIcon.setImageResource(R.drawable.ic_verify_fail);
            }
            
        } catch (Exception e) {
            resultIcon.setImageResource(R.drawable.ic_verify_fail);
        }
    }

} 