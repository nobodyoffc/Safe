package com.fc.safe.multisign;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.core.fch.TxHandler;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.MultisignTxDetail;
import com.fc.fc_ajdk.data.fchData.Multisig;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.tx.view.TxOutputCard;
import com.fc.safe.tx.SignTxActivity;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.fc.safe.utils.KeyCardContainer;
import com.fc.safe.utils.ChooseMode;
import com.fc.safe.db.CashManager;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.db.PendingTxManager;
import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.core.fch.TxFingerprint;
import com.fc.fc_ajdk.data.fchData.CashMark;
import com.fc.fc_ajdk.data.fchData.PendingTx;
import com.fc.fc_ajdk.data.fchData.TxInfo;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.TimberLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.fc.safe.utils.ToastUtils;

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
    private TxInfo txInfo;


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
        
        // Initialize txInfo for cash database updates
        String rawTx = new TxHandler().createTxHex(rawTxInfo);
        List<Cash> inputCashList = rawTxInfo.getInputs();
        if (rawTx != null && inputCashList != null) {
            txInfo = TxInfo.fromRawTx(rawTx, inputCashList, null);
        }

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
                                new TxHandler().signSchnorrMultiSignTx(rawTxInfo, priKeyBytes);
                                recordPartialSign();
                                multisignTxDetail = MultisignTxDetail.fromMultiSigData(rawTxInfo, this);
                                refreshFragmentContainer();
                                if(isFullSigned)Toast.makeText(this, getString(R.string.tx_is_well_signed_build_it), SafeApplication.TOAST_LASTING).show();
                                else Toast.makeText(this, getString(R.string.signed), SafeApplication.TOAST_LASTING).show();
                            } else {
                                Toast.makeText(this, getString(R.string.failed_to_get_prikey_of, chosenKeyInfo.getId()), SafeApplication.TOAST_LASTING).show();
                            }
                        } else {
                            Toast.makeText(this, getString(R.string.selected_key_not_part_of_multisign), SafeApplication.TOAST_LASTING).show();
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
                copyToClipboard(rawTxInfo.toNiceJson(), "multisig");
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
                buildResult = new TxHandler().buildSchnorrMultiSignTx(rawTxInfo);
                if(!Hex.isHexString(buildResult)){
                    Toast.makeText(this, getString(R.string.failed_to_build_multisign_tx), SafeApplication.TOAST_LASTING).show();
                } else{
                    isBuilt=true;
                    checkIsBuilt();
                    Toast.makeText(this, getString(R.string.built_you_can_broadcast_it), SafeApplication.TOAST_LASTING).show();
                    
                    // Update cash database after successful building
                    updateCashDB(buildResult, txInfo);
                }
                return;
            }
            
            List<KeyInfo> keyInfoList = keyInfoManager.getAllKeyInfoList();
            if (keyInfoList.isEmpty()) {
                showToast(getString(R.string.no_keys_available));
                return;
            }
            Intent intent = ChooseKeyInfoActivity.newIntent(this, keyInfoList, ChooseMode.CHOOSE_ONE_RETURN);
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
        Multisig multisig = new Multisig();
        multisig.setId(multisignTxDetail.getSender());
        keyCardManager.addSenderKeyCard(multisig);
    }

    private void setupCash() {
        if (multisignTxDetail.getCashIdAmountMap() == null || multisignTxDetail.getCashIdAmountMap().isEmpty()) {
            return;
        }
        addLabel(getString(R.string.spending));
        for (Map.Entry<String, String> entry : multisignTxDetail.getCashIdAmountMap().entrySet()) {
            // Create a text line for cash without bold and in text_color
            addCashLine(entry.getKey(), entry.getValue());
        }
    }

    private void addCashLine(String cashId, String amount) {
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setPadding(0, 8, 0, 8);

        TextView cashText = new TextView(this);
        cashText.setText("\t"+StringUtils.omitMiddle(cashId,21) + ": " + amount);
        cashText.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
        cashText.setTypeface(null, android.graphics.Typeface.NORMAL);
        cashText.setOnClickListener(v -> copyToClipboard(cashId, "Cash ID"));
        cashText.setClickable(true);
        cashText.setFocusable(true);

        rowLayout.addView(cashText);
        fragmentContainer.addView(rowLayout);
    }

    private void setupSendTo() {
        if (multisignTxDetail.getSendToList() == null || multisignTxDetail.getSendToList().isEmpty()) {
            TimberLogger.w(TAG, "setupSendTo: sendToList is null or empty");
            return;
        }
        addLabel(getString(R.string.send_to)+":");
        for (Cash sendTo : multisignTxDetail.getSendToList()) {
            TxOutputCard card = new TxOutputCard(this);
            card.setSendTo(sendTo, this, false, false);
            fragmentContainer.addView(card);
        }
    }

    private void setupText() {
        if (multisignTxDetail.getOpReturn() != null && !multisignTxDetail.getOpReturn().isEmpty()) {
            setupCarving(multisignTxDetail.getOpReturn());
        }
        if (multisignTxDetail.getmOfN() != null && !multisignTxDetail.getmOfN().isEmpty()) {
            addTextLine(getString(R.string.m_n)+": " + multisignTxDetail.getmOfN());
        }
        if (multisignTxDetail.getRestSignNum() != null) {
            addTextLine(getString(R.string.need_signs)+": " + multisignTxDetail.getRestSignNum());
        }
    }

    private void setupCarving(String carveValue) {
        boolean isJson = com.fc.fc_ajdk.utils.JsonUtils.isJson(carveValue);

        // Add label
        addLabel(getString(R.string.carving) + ":");

        // Create outlined container with relative layout for icon positioning
        android.widget.RelativeLayout containerLayout = new android.widget.RelativeLayout(this);
        android.widget.RelativeLayout.LayoutParams containerParams = new android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        containerLayout.setLayoutParams(containerParams);
        containerLayout.setBackgroundResource(R.drawable.card_background_outlined);
        containerLayout.setPadding(24, 24, 24, 24);

        // Create TextView for carve value
        TextView carveText = new TextView(this);
        carveText.setId(View.generateViewId());
        android.widget.RelativeLayout.LayoutParams textParams = new android.widget.RelativeLayout.LayoutParams(
            android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
            android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        carveText.setLayoutParams(textParams);
        carveText.setText(carveValue);
        carveText.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
        carveText.setTypeface(null, android.graphics.Typeface.NORMAL);
        carveText.setTextIsSelectable(true);

        containerLayout.addView(carveText);

        // If JSON, add icon at bottom right corner
        if (isJson) {
            ImageView jsonIcon = new ImageView(this);
            android.widget.RelativeLayout.LayoutParams iconParams = new android.widget.RelativeLayout.LayoutParams(
                64, 64
            );
            iconParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
            iconParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
            jsonIcon.setLayoutParams(iconParams);
            jsonIcon.setImageResource(R.drawable.ic_json);
            jsonIcon.setPadding(8, 8, 8, 8);
            jsonIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);

            // Add click listener to show nice JSON
            final String finalCarveValue = carveValue;
            jsonIcon.setOnClickListener(v -> showNiceJson(finalCarveValue));

            containerLayout.addView(jsonIcon);
        }

        fragmentContainer.addView(containerLayout);
    }

    private void showNiceJson(String jsonString) {
        try {
            String niceJson = com.fc.fc_ajdk.utils.JsonUtils.jsonToNiceJson(jsonString);

            // Create dialog to show nice JSON
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.carving) + " (JSON)");

            // Create ScrollView with TextView
            android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
            android.widget.LinearLayout.LayoutParams scrollParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            );
            scrollView.setLayoutParams(scrollParams);
            scrollView.setFillViewport(true);

            TextView textView = new TextView(this);
            textView.setText(niceJson);
            textView.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
            textView.setTypeface(android.graphics.Typeface.MONOSPACE);
            textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            textView.setPadding(32, 32, 32, 32);
            textView.setTextIsSelectable(true);

            android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            textView.setLayoutParams(textParams);

            scrollView.addView(textView);

            builder.setView(scrollView);
            builder.setPositiveButton(R.string.copy, (dialog, which) -> {
                copyToClipboard(niceJson, getString(R.string.carving));
            });
            builder.setNegativeButton(R.string.close, null);

            android.app.AlertDialog dialog = builder.create();
            dialog.show();

            // Set dialog window to use most of the screen height
            if (dialog.getWindow() != null) {
                android.view.WindowManager.LayoutParams layoutParams = new android.view.WindowManager.LayoutParams();
                layoutParams.copyFrom(dialog.getWindow().getAttributes());
                layoutParams.width = android.view.WindowManager.LayoutParams.MATCH_PARENT;
                layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
                dialog.getWindow().setAttributes(layoutParams);
            }

        } catch (Exception e) {
            ToastUtils.showError(this, getString(R.string.failed_to_parse_json));
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
        KeyCardContainer keyCardManager = new KeyCardContainer(this, fragmentContainer, ChooseMode.WITHOUT_CHOOSE);
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
        KeyCardContainer keyCardManager = new KeyCardContainer(this, fragmentContainer, ChooseMode.WITHOUT_CHOOSE, List.of("Sign"));
        keyCardManager.setOnMenuItemClickListener((menuItem, keyInfo) -> {
            if ("Sign".equals(menuItem)) {
                KeyInfo fullKeyInfo = keyInfoManager.getKeyInfoById(keyInfo.getId());
                if (fullKeyInfo != null) {
                    byte[] priKeyBytes = fullKeyInfo.decryptPrikey(ConfigureManager.getInstance().getSymkey());
                    if (priKeyBytes != null) {
                        new TxHandler().signSchnorrMultiSignTx(rawTxInfo, priKeyBytes);
                        recordPartialSign();
                        multisignTxDetail = MultisignTxDetail.fromMultiSigData(rawTxInfo, this);
                        refreshFragmentContainer();
                        Toast.makeText(this, getString(R.string.signed), SafeApplication.TOAST_LASTING).show();
                    } else {
                        Toast.makeText(this, getString(R.string.failed_to_get_prikey_of, keyInfo.getId()), SafeApplication.TOAST_LASTING).show();
                    }
                } else {
                    Toast.makeText(this, getString(R.string.failed_to_get_key_info_of, keyInfo.getId()), SafeApplication.TOAST_LASTING).show();
                }
            }
        });
        for (String fid : multisignTxDetail.getUnSignedFidList()) {
            KeyInfo keyInfo = new KeyInfo(fid);
            keyCardManager.addKeyCard(keyInfo);
        }
    }

    /** Returns the FIDs among multisig signers whose KeyInfo is held locally. */
    private List<String> localSignerFids() {
        List<String> result = new ArrayList<>();
        if (rawTxInfo == null || rawTxInfo.getMultisign() == null) return result;
        KeyInfoManager mgr = KeyInfoManager.getInstance(this);
        for (String fid : rawTxInfo.getMultisign().getFids()) {
            if (mgr.getKeyInfoById(fid) != null) result.add(fid);
        }
        return result;
    }

    /**
     * Called after a partial multisig signature is contributed on this device. Creates a PendingTx
     * record on first sign (or updates an existing one found by fingerprint), and locks the subset
     * of inputs owned by the multisig address (whose signers we hold locally).
     */
    private void recordPartialSign() {
        if (rawTxInfo == null) return;
        try {
            String fingerprint = TxFingerprint.of(rawTxInfo);
            PendingTxManager pendingMgr = PendingTxManager.getInstance(this);
            PendingTx pendingTx = pendingMgr.findByFingerprint(fingerprint);

            String ownerFid = rawTxInfo.getMultisign() != null ? rawTxInfo.getMultisign().getId() : null;

            if (pendingTx == null) {
                pendingTx = PendingTx.create(rawTxInfo.toJson(), fingerprint, true, ownerFid);

                // Lock any input cash whose owner is the multisig address (only if we hold at least
                // one signer locally — otherwise this device has no authority over these inputs).
                boolean holdAnySigner = !localSignerFids().isEmpty();
                List<String> inputIds = new ArrayList<>();
                if (holdAnySigner && rawTxInfo.getInputs() != null) {
                    for (Cash in : rawTxInfo.getInputs()) {
                        String id = in.getId();
                        if (id == null) id = in.makeId();
                        if (id != null) inputIds.add(id);
                    }
                }
                List<String> locked = PendingTxManager.lockInputCashes(this, inputIds, pendingTx.getPendingId());
                pendingTx.setSpentCashIds(locked);
            } else {
                // Already-tracked TX — refresh the stored RawTxInfo so the updated fidSigMap persists.
                pendingTx.setRawTxInfoJson(rawTxInfo.toJson());
            }

            pendingMgr.put(pendingTx);
            pendingMgr.commit();
        } catch (Exception e) {
            TimberLogger.e(TAG, "recordPartialSign: %s", e.getMessage());
        }
    }

    /**
     * Finalizes the PendingTx when the multisig TX is fully built. Fills onChainTxId + signedTxHex
     * and inserts locally-owned output cashes as pending-incoming.
     */
    private void updateCashDB(String builtTx, TxInfo txInfo) {
        if (builtTx == null || txInfo == null) {
            TimberLogger.e(TAG, "updateCashDB: builtTx or txInfo is null");
            return;
        }

        try {
            String builtTxId = Hex.toHex(Hash.sha256x2(Hex.fromHex(builtTx)));
            String fingerprint = TxFingerprint.of(rawTxInfo);
            PendingTxManager pendingMgr = PendingTxManager.getInstance(this);
            PendingTx pendingTx = pendingMgr.findByFingerprint(fingerprint);

            String ownerFid = rawTxInfo.getMultisign() != null ? rawTxInfo.getMultisign().getId() : null;

            if (pendingTx == null) {
                // Fallback: user imported an already-fully-signed TX and built directly without going
                // through recordPartialSign. Lock inputs we own now.
                pendingTx = PendingTx.create(rawTxInfo.toJson(), fingerprint, true, ownerFid);
                List<String> inputIds = new ArrayList<>();
                if (rawTxInfo.getInputs() != null) {
                    for (Cash in : rawTxInfo.getInputs()) {
                        String id = in.getId();
                        if (id == null) id = in.makeId();
                        if (id != null) inputIds.add(id);
                    }
                }
                List<String> locked = PendingTxManager.lockInputCashes(this, inputIds, pendingTx.getPendingId());
                pendingTx.setSpentCashIds(locked);
            }

            pendingTx.setRawTxInfoJson(rawTxInfo.toJson());
            pendingTx.setSignedTxHex(builtTx);
            pendingTx.setOnChainTxId(builtTxId);

            // Insert locally-owned output cashes as pending-incoming.
            KeyInfoManager keyInfoManager = KeyInfoManager.getInstance(this);
            List<Cash> newOutputs = new ArrayList<>();
            List<String> newIds = new ArrayList<>();
            if (txInfo.getIssuedCashes() != null) {
                for (int i = 0; i < txInfo.getIssuedCashes().size(); i++) {
                    CashMark cashMark = txInfo.getIssuedCashes().get(i);
                    if (cashMark.getOwner() == null) continue;
                    if (keyInfoManager.getKeyInfoById(cashMark.getOwner()) == null) continue;

                    Cash newCash = new Cash();
                    newCash.setBirthTxId(builtTxId);
                    newCash.setBirthIndex(i);
                    newCash.setBirthTime(System.currentTimeMillis() / 1000 + 300);
                    newCash.setValue(cashMark.getValue());
                    newCash.setOwner(cashMark.getOwner());
                    newCash.makeId();
                    newOutputs.add(newCash);
                    newIds.add(newCash.getId());
                }
            }
            PendingTxManager.insertPendingOutputs(this, newOutputs, pendingTx.getPendingId());
            pendingTx.setNewCashIds(newIds);

            pendingMgr.put(pendingTx);
            pendingMgr.commit();
            TimberLogger.i(TAG, "updateCashDB: multisig pending TX %s built (onChainTxId=%s, %d outputs pending)",
                    pendingTx.getPendingId(), builtTxId, newIds.size());

        } catch (Exception e) {
            TimberLogger.e(TAG, "updateCashDB: Error updating cash database: %s", e.getMessage());
        }
    }
}