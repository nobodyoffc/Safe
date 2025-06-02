package com.fc.safe;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.data.fchData.Multisign;
import com.fc.fc_ajdk.data.fchData.MultisignTxDetail;
import com.fc.fc_ajdk.data.fchData.SendTo;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.tx.SignTxActivity;
import com.fc.safe.tx.view.CashAmountCard;
import com.fc.safe.tx.view.TxOutputCard;
import com.fc.safe.utils.KeyCardManager;
import com.fc.safe.multisign.MultisignKeyCardManager;

import java.util.Map;

public class ConfirmMultisignTxActivity extends BaseCryptoActivity {
    private RawTxInfo rawTxInfo;
    private MultisignTxDetail multisignTxDetail;
    private LinearLayout fragmentContainer;
    private LinearLayout buttonContainer;
    private Button makeQrButton;
    private Button copyButton;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_confirm_multisign_tx;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.confirm_multisign_tx);
    }

    @Override
    protected void initializeViews() {
        // Get data from intent
        String txInfoJson = getIntent().getStringExtra(SignTxActivity.EXTRA_TX_INFO_JSON);
        rawTxInfo = RawTxInfo.fromJson(txInfoJson,RawTxInfo.class);
        if (rawTxInfo == null) {
            finish();
            return;
        }
        multisignTxDetail = MultisignTxDetail.fromMultiSigData(rawTxInfo, this);

        fragmentContainer = findViewById(R.id.fragmentContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        makeQrButton = findViewById(R.id.makeQrButton);
        copyButton = findViewById(R.id.copyButton);

        setupSender();
        setupCash();
        setupSendTo();
        setupText();
        setupSignedFids();
        setupUnsignedFids();
    }

    @Override
    protected void setupButtons() {
        makeQrButton.setOnClickListener(v -> {
            handleQrGeneration(rawTxInfo.toNiceJson());
        });

        copyButton.setOnClickListener(v -> {
            copyToClipboard(rawTxInfo.toNiceJson(), "multisign");
        });
    }

    @Override
    protected void copyToClipboard(String text, String label) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        showToast(getString(R.string.copied));
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not used in this activity
    }

    private void setupSender() {
        if (multisignTxDetail.getSender() == null || multisignTxDetail.getSender().isEmpty()) {
            return;
        }
        MultisignKeyCardManager keyCardManager = new MultisignKeyCardManager(this, fragmentContainer, false);
        Multisign multisign = new Multisign();
        multisign.setId(multisignTxDetail.getSender());
        keyCardManager.addSenderKeyCard(multisign);
    }

    private void setupCash() {
        if (multisignTxDetail.getCashIdAmountMap() == null || multisignTxDetail.getCashIdAmountMap().isEmpty()) {
            return;
        }
        addLabel(getString(R.string.spending));
        for (Map.Entry<String, String> entry : multisignTxDetail.getCashIdAmountMap().entrySet()) {
            CashAmountCard card = new CashAmountCard(this);
            card.setCashId(entry.getKey());
            card.setAmount(entry.getValue());
            fragmentContainer.addView(card);
        }
    }

    private void setupSendTo() {
        if (multisignTxDetail.getSendToList() == null || multisignTxDetail.getSendToList().isEmpty()) {
            return;
        }
        addLabel(getString(R.string.send_to)+":");
        for (SendTo sendTo : multisignTxDetail.getSendToList()) {
            TxOutputCard card = new TxOutputCard(this);
            card.setSendTo(sendTo, this);
            fragmentContainer.addView(card);
        }
    }

    private void setupText() {
        if (multisignTxDetail.getOpReturn() != null && !multisignTxDetail.getOpReturn().isEmpty()) {
            addTextLine(getString(R.string.carving)+": " + multisignTxDetail.getOpReturn());
        }
        if (multisignTxDetail.getmOfN() != null && !multisignTxDetail.getmOfN().isEmpty()) {
            addTextLine(getString(R.string.m_n)+": " + multisignTxDetail.getmOfN());
        }
        if (multisignTxDetail.getRestSignNum() != null) {
            addTextLine(getString(R.string.need_signs)+": " + multisignTxDetail.getRestSignNum());
        }
    }

    private void addTextLine(String text) {
        if(text==null || text.isEmpty())return;
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setPadding(0, 16, 0, 16); // Add vertical padding for spacing

        // Split the text into field name and value
        String[] parts = text.split(": ", 2);
        if (parts.length == 2) {
            TextView fieldName = new TextView(this);
            fieldName.setText(parts[0] + ": ");
            fieldName.setTextColor(getResources().getColor(R.color.field_name, getTheme()));
            fieldName.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView fieldValue = new TextView(this);
            fieldValue.setText(parts[1]);
            fieldValue.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
            fieldValue.setTypeface(null, android.graphics.Typeface.NORMAL);

            rowLayout.addView(fieldName);
            rowLayout.addView(fieldValue);
        } else {
            // If no colon found, treat the whole text as a field name
            TextView fieldName = new TextView(this);
            fieldName.setText(text);
            fieldName.setTextColor(getResources().getColor(R.color.field_name, getTheme()));
            fieldName.setTypeface(null, android.graphics.Typeface.BOLD);
            rowLayout.addView(fieldName);
        }

        fragmentContainer.addView(rowLayout);
    }

    private void addLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(getResources().getColor(R.color.field_name, getTheme()));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setPadding(0, 16, 0, 8);
        fragmentContainer.addView(label);
    }

    private void setupSignedFids() {
        if (multisignTxDetail.getSignedFidList() == null || multisignTxDetail.getSignedFidList().isEmpty()) {
            return;
        }
        addLabel("Signed members:");
        KeyCardManager keyCardManager = new KeyCardManager(this, fragmentContainer, null);
        for (String fid : multisignTxDetail.getSignedFidList()) {
            KeyInfo keyInfo = new KeyInfo(fid);
            keyCardManager.addKeyCard(keyInfo);
        }

    }

    private void setupUnsignedFids() {
        if (multisignTxDetail.getUnSignedFidList() == null || multisignTxDetail.getUnSignedFidList().isEmpty()) {
            return;
        }
        addLabel("Unsigned members:");
        KeyCardManager keyCardManager = new KeyCardManager(this, fragmentContainer, null);
        for (String fid : multisignTxDetail.getUnSignedFidList()) {
            KeyInfo keyInfo = new KeyInfo(fid);
            keyCardManager.addKeyCard(keyInfo);
        }
    }
} 