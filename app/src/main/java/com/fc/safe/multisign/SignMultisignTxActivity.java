package com.fc.safe.multisign;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.core.fch.TxCreator;
import com.fc.fc_ajdk.data.fchData.MultisignTxDetail;
import com.fc.fc_ajdk.data.fchData.Multisign;
import com.fc.fc_ajdk.data.fchData.SendTo;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.tx.view.CashAmountCard;
import com.fc.safe.tx.view.TxOutputCard;
import com.fc.safe.tx.SignTxActivity;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.fc.safe.utils.KeyCardManager;

import java.util.List;
import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class SignMultisignTxActivity extends BaseCryptoActivity {
    private static final String TAG = "SignMultisignTxActivity";
    public static final int RESULT_BUILT = 1002;  // Add result code constant
    private RawTxInfo rawTxInfo;
    private MultisignTxDetail multisignTxDetail;
    private LinearLayout fragmentContainer;
    private Button makeQrButton;
    private Button copyButton;
    private Button signButton;
    private ActivityResultLauncher<Intent> chooseKeyLauncher;
    private boolean isBuilt = false;
    private boolean isFullSigned = false;
    private String buildResult = null;


    @Override
    protected int getLayoutId() {
        return R.layout.activity_confirm_multisign_tx;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.sign_multisign_tx);
    }

    @Override
    protected void initializeViews() {
        // Get data from intent
        String txJson = (String)getIntent().getSerializableExtra(SignTxActivity.EXTRA_TX_INFO_JSON);
        rawTxInfo = RawTxInfo.fromJson(txJson,RawTxInfo.class);
        if (rawTxInfo == null) {
            finish();
            return;
        }
        multisignTxDetail = MultisignTxDetail.fromMultiSigData(rawTxInfo, this);

        fragmentContainer = findViewById(R.id.fragmentContainer);
        LinearLayout buttonContainer = findViewById(R.id.buttonContainer);
        makeQrButton = findViewById(R.id.makeQrButton);
        copyButton = findViewById(R.id.copyButton);
        signButton = findViewById(R.id.signButton);

        checkIsBuilt();

        setupSender();
        setupCash();
        setupSendTo();
        setupText();
        setupSignedFids();
        setupUnsignedFids();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize key selection launcher
        chooseKeyLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    List<KeyInfo> selectedKeys = ChooseKeyInfoActivity.getSelectedKeyInfo(result.getData(), keyInfoManager);
                    if (selectedKeys != null && !selectedKeys.isEmpty()) {
                        KeyInfo chosenKeyInfo = selectedKeys.get(0);
                        if (rawTxInfo.getMultisign().getFids().contains(chosenKeyInfo.getId())) {
                            byte[] priKeyBytes = chosenKeyInfo.decryptPrikey(ConfigureManager.getInstance().getSymkey());
                            if (priKeyBytes != null) {
                                TxCreator.signSchnorrMultiSignTx(rawTxInfo, priKeyBytes);
                                multisignTxDetail = MultisignTxDetail.fromMultiSigData(rawTxInfo, this);
                                refreshFragmentContainer();
                                if(isFullSigned)Toast.makeText(this,"The TX is well signed! Build it.", SafeApplication.TOAST_LASTING).show();
                                else Toast.makeText(this,"Signed!", SafeApplication.TOAST_LASTING).show();
                            } else {
                                Toast.makeText(this, "Failed to get the priKey of " + chosenKeyInfo.getId(), SafeApplication.TOAST_LASTING).show();
                            }
                        } else {
                            Toast.makeText(this, "Selected key is not part of this multisign transaction", SafeApplication.TOAST_LASTING).show();
                        }
                    }
                }
            }
        );
    }

    private void refreshFragmentContainer() {
        // Clear existing views
        fragmentContainer.removeAllViews();
        
        // Check if transaction is fully signed
        checkIsBuilt();

        // Re-setup all views with updated data
        setupSender();
        setupCash();
        setupSendTo();
        setupText();
        setupSignedFids();
        setupUnsignedFids();
    }

    private void checkIsBuilt() {
        isFullSigned = isFullSigned();
        if (isBuilt) {
            signButton.setText(getString(R.string.done));
            copyButton.setText(getString(R.string.copy_result));
        } else if(isFullSigned){
            signButton.setText(getString(R.string.build));
        }
    }

    private boolean isFullSigned() {
        return multisignTxDetail.getRestSignNum() != null && multisignTxDetail.getRestSignNum() == 0;
    }

    @Override
    protected void setupButtons() {
        makeQrButton.setOnClickListener(v -> {
            if (isBuilt){
                if(buildResult != null)
                    handleQrGeneration(buildResult);
            } else {
                handleQrGeneration(rawTxInfo.toNiceJson());
            }
        });

        copyButton.setOnClickListener(v -> {
            if (isBuilt) {
                if( buildResult != null)
                    copyToClipboard(buildResult, "multisign_result");
            } else {
                copyToClipboard(rawTxInfo.toNiceJson(), "multisign");
            }
        });

        signButton.setOnClickListener(v -> {
            if(isBuilt) {
                copyToClipboard(buildResult, "multisign_result");
                setResult(RESULT_BUILT);
                finish();
                return;
            }
            if (isFullSigned) {
                if(isBuilt){
                    Toast.makeText(this, getString(R.string.built_you_can_broadcast_it), SafeApplication.TOAST_LASTING).show();
                    return;
                }
                buildResult = TxCreator.buildSchnorrMultiSignTx(rawTxInfo);
                if(!Hex.isHexString(buildResult)){
                    Toast.makeText(this, getString(R.string.failed_to_build_multisign_tx), SafeApplication.TOAST_LASTING).show();
                } else{
                    isBuilt=true;
                    checkIsBuilt();
                    Toast.makeText(this, getString(R.string.built_you_can_broadcast_it), SafeApplication.TOAST_LASTING).show();
                }
                return;
            }
            
            List<KeyInfo> keyInfoList = keyInfoManager.getAllKeyInfoList();
            if (keyInfoList.isEmpty()) {
                showToast(getString(R.string.no_keys_available));
                return;
            }
            Intent intent = ChooseKeyInfoActivity.newIntent(this, keyInfoList, true);
            chooseKeyLauncher.launch(intent);
        });
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
            card.setSendTo(sendTo, this, false, false);
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
        TextView fieldName = new TextView(this);
        if (parts.length == 2) {
            fieldName.setText(getString(R.string.field_name_format, parts[0]));
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
        KeyCardManager keyCardManager = new KeyCardManager(this, fragmentContainer, null, List.of("Sign"));
        keyCardManager.setOnMenuItemClickListener((menuItem, keyInfo) -> {
            if ("Sign".equals(menuItem)) {
                KeyInfo fullKeyInfo = keyInfoManager.getKeyInfoById(keyInfo.getId());
                if (fullKeyInfo != null) {
                    byte[] priKeyBytes = fullKeyInfo.decryptPrikey(ConfigureManager.getInstance().getSymkey());
                    if (priKeyBytes != null) {
                        TxCreator.signSchnorrMultiSignTx(rawTxInfo, priKeyBytes);
                        multisignTxDetail = MultisignTxDetail.fromMultiSigData(rawTxInfo, this);
                        refreshFragmentContainer();
                        Toast.makeText(this, "Signed!", SafeApplication.TOAST_LASTING).show();
                    } else {
                        Toast.makeText(this, "Failed to get the priKey of " + keyInfo.getId(), SafeApplication.TOAST_LASTING).show();
                    }
                } else {
                    Toast.makeText(this, "Failed to get the key info of " + keyInfo.getId(), SafeApplication.TOAST_LASTING).show();
                }
            }
        });
        for (String fid : multisignTxDetail.getUnSignedFidList()) {
            KeyInfo keyInfo = new KeyInfo(fid);
            keyCardManager.addKeyCard(keyInfo);
        }
    }
} 