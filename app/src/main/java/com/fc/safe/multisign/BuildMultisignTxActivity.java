package com.fc.safe.multisign;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Button;

import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.core.fch.TxCreator;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.fcData.ReplyBody;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.tx.SignTxActivity;
import com.fc.safe.utils.KeyCardManager;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class BuildMultisignTxActivity extends BaseCryptoActivity {
    private static final String TAG = "BuildMultisignTxActivity";
    private static final int QR_SCAN_TEXT_REQUEST_CODE = 1001;
    private LinearLayout signaturesContainer;
    private TextInputEditText jsonInput;
    private List<RawTxInfo> rawTxInfoList;
    private int signatureCount = 0;
    private Button buildButton;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_sign_multisign_tx;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.build_multisign_tx);
    }

    @Override
    protected void initializeViews() {
        signaturesContainer = findViewById(R.id.signaturesContainer);
        jsonInput = findViewById(R.id.jsonInputContainer).findViewById(R.id.textInput);
        jsonInput.setHint("Input the signed multisign TX JSONs");
        rawTxInfoList = new ArrayList<>();
        buildButton = findViewById(R.id.buildButton);

        // Setup scan icon for JSON input
        setupTextIcons(R.id.jsonInputContainer, R.id.scanIcon, QR_SCAN_TEXT_REQUEST_CODE);
        
        // Initial button state
        updateBuildButtonState();
    }

    @Override
    protected void setupButtons() {
        findViewById(R.id.addButton).setOnClickListener(v -> handleAddSignature());
        findViewById(R.id.buildButton).setOnClickListener(v -> handleBuildTx());
        findViewById(R.id.clearButton).setOnClickListener(v -> handleClear());
    }

    private void updateBuildButtonState() {
        buildButton.setEnabled(!rawTxInfoList.isEmpty());
        buildButton.setAlpha(rawTxInfoList.isEmpty() ? 0.5f : 1.0f);
    }

    private void handleAddSignature() {
        String jsonStr = jsonInput.getText().toString().trim();
        if (jsonStr.isEmpty()) {
            showToast(getString(R.string.please_input_json));
            return;
        }

        try {
            RawTxInfo rawTxInfo = RawTxInfo.fromJson(jsonStr, RawTxInfo.class);
            if (rawTxInfo == null || rawTxInfo.getFidSigMap() == null || rawTxInfo.getFidSigMap().isEmpty()) {
                showToast(getString(R.string.invalid_multisign_data));
                return;
            }

            addSignatureCard(rawTxInfo);
            rawTxInfoList.add(rawTxInfo);
            jsonInput.setText("");
            updateBuildButtonState();
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to parse JSON: " + e.getMessage());
            showToast(getString(R.string.invalid_json_format));
        }
    }

    private void addSignatureCard(RawTxInfo rawTxInfo) {
        View cardView = LayoutInflater.from(this).inflate(R.layout.item_signature_card, signaturesContainer, false);
        TextView label = cardView.findViewById(R.id.signature_label);
        LinearLayout keyCardContainer = cardView.findViewById(R.id.key_card_container);

        signatureCount++;
        label.setText("Signature #" + signatureCount + " signed by:");

        // Create KeyCardManager for this signature card
        KeyCardManager keyCardManager = new KeyCardManager(this, keyCardContainer, null);
        
        // Add key cards for each signer
        for (String fid : rawTxInfo.getFidSigMap().keySet()) {
            KeyInfo keyInfo = new KeyInfo(fid);
            keyCardManager.addKeyCard(keyInfo);
        }

        // Set up long press menu
        cardView.setOnLongClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add("Delete");
            popup.setOnMenuItemClickListener(item -> {
                if ("Delete".equals(item.getTitle())) {
                    signaturesContainer.removeView(cardView);
                    rawTxInfoList.remove(rawTxInfo);
                    updateBuildButtonState();
                    return true;
                }
                return false;
            });
            popup.show();
            return true;
        });

        signaturesContainer.addView(cardView);
    }

    private void handleBuildTx() {
        if (rawTxInfoList.isEmpty()) {
            showToast(getString(R.string.no_signatures_to_build));
            return;
        }

        String[] jsonStrings = new String[rawTxInfoList.size()];
        for (int i = 0; i < rawTxInfoList.size(); i++) {
            jsonStrings[i] = rawTxInfoList.get(i).toJson();
        }

        ReplyBody replyBody = TxCreator.mergeMultisignTxData(jsonStrings);
        if (replyBody == null || replyBody.getCode() != 0) {
            showToast(getString(R.string.failed_to_merge_signatures, (replyBody != null ? replyBody.getMessage() : "Unknown error")));
            return;
        }

        RawTxInfo finalRawTxInfo = (RawTxInfo) replyBody.getData();
        if (finalRawTxInfo == null) {
            showToast(getString(R.string.failed_to_build_transaction));
            return;
        }

        // Launch SignMultisignTxActivity with the merged data
        Intent intent = new Intent(this, SignMultisignTxActivity.class);
        intent.putExtra(SignTxActivity.EXTRA_TX_INFO_JSON, finalRawTxInfo.toJsonWithSenderInfo());
        startActivity(intent);
    }

    private void handleClear() {
        // Clear the input box
        jsonInput.setText("");
        
        // Clear the signatures list
        signaturesContainer.removeAllViews();
        rawTxInfoList.clear();
        signatureCount = 0;
        updateBuildButtonState();
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_TEXT_REQUEST_CODE && qrContent != null) {
            jsonInput.setText(qrContent);
        }
    }
} 