package com.fc.safe.tx;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.core.fch.TxHandler;
import com.fc.fc_ajdk.data.fchData.TxInfo;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.CashMark;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.tx.view.TxOutputCard;
import com.fc.safe.utils.IdUtils;
import com.fc.safe.db.CashManager;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.db.PendingTxManager;
import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.core.fch.TxFingerprint;
import com.fc.fc_ajdk.data.fchData.PendingTx;

import java.util.ArrayList;

import java.util.List;

import com.fc.safe.utils.ToastUtils;

public class SignTxActivity extends BaseCryptoActivity {
    private static final String TAG = "SignTxActivity";
    public static final int RESULT_SIGNED = 1001;
    private KeyInfo senderKeyInfo;
    private String rawTx;
    private Long cdd;
    private List<Cash> inputCashList;
    private TxInfo txInfo;
    private LinearLayout fragmentContainer;
    private LinearLayout buttonContainer;
    private Button makeQrButton;
    private Button copyButton;
    private Button signButton;
    private String signedTx = null;
    private RawTxInfo rawTxInfo;
    private boolean isSigned;

    public static final String EXTRA_TX_INFO_JSON = "extra_tx_info_json";

    @Override
    protected int getLayoutId() {
        return R.layout.activity_confirm_multisign_tx;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.sign_tx);
    }

    @Override
    protected void initializeViews() {
        // Get data from intent
        String offLineTxInfoJson = getIntent().getStringExtra(EXTRA_TX_INFO_JSON);
        if(offLineTxInfoJson == null){
            Toast.makeText(this, getString(R.string.the_tx_is_empty), SafeApplication.TOAST_LASTING).show();
            finish();
            return;
        }

        try {
            rawTxInfo = RawTxInfo.fromJson(offLineTxInfoJson, RawTxInfo.class);
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error parsing RawTxInfo JSON: " + e.getMessage());
            Toast.makeText(this, getString(R.string.invalid_transaction_data), SafeApplication.TOAST_LASTING).show();
            finish();
            return;
        }

        if(rawTxInfo == null){
            Toast.makeText(this, getString(R.string.the_tx_is_empty), SafeApplication.TOAST_LASTING).show();
            finish();
            return;
        }

        senderKeyInfo = rawTxInfo.getSenderInfo();
        rawTx = new TxHandler().createTxHex(rawTxInfo);
        inputCashList = rawTxInfo.getInputs();
        
        if (senderKeyInfo == null || rawTx == null || inputCashList == null) {
            finish();
            return;
        }
        
        txInfo = TxInfo.fromRawTx(rawTx, inputCashList, cdd);

        fragmentContainer = findViewById(R.id.fragmentContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        makeQrButton = findViewById(R.id.makeQrButton);
        copyButton = findViewById(R.id.copyButton);
        signButton = findViewById(R.id.signButton);

        setupSender();
        setupSendTo();
        setupSummary();
        setupCarve();

        updateButtonTexts();
    }

    @Override
    protected void setupButtons() {
        makeQrButton.setOnClickListener(v -> {
            if (signedTx != null) {
                handleQrGeneration(signedTx);
            }
        });

        copyButton.setOnClickListener(v -> {
            if (signedTx != null) {
                copyToClipboard(signedTx, getString(R.string.signed));
            }else{
                copyToClipboard(rawTxInfo.toNiceJson(), getString(R.string.unsigned));
            }
        });

        signButton.setOnClickListener(v -> {
            if(isSigned) {
                copyToClipboard(signedTx, getString(R.string.signed));
                setResult(RESULT_SIGNED);
                finish();
                return;
            }
            byte[] priKey = senderKeyInfo.decryptPrikey(ConfigureManager.getInstance().getSymkey());
            if (priKey != null) {
                signedTx = new TxHandler().signTx(rawTxInfo, priKey);
                if (Hex.isHexString(signedTx)) {
                    Toast.makeText(this, getString(R.string.signed_you_can_broadcast_it), SafeApplication.TOAST_LASTING).show();
                    isSigned = true;
                    updateButtonTexts();
                    
                    // Update cash database after successful signing
                    updateCashDB(signedTx, txInfo);
                    
                } else {
                    Toast.makeText(this, getString(R.string.failed_to_sign_transaction), SafeApplication.TOAST_LASTING).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.failed_to_get_private_key), SafeApplication.TOAST_LASTING).show();
            }
        });
    }

    public void updateButtonTexts() {
        if(isSigned){
            copyButton.setText(R.string.copy_result);
            signButton.setText(R.string.done);
        } else{
            copyButton.setText(R.string.copy_tx);
            signButton.setText(R.string.sign);
        }
    }

    @Override
    protected void copyToClipboard(String text, String label) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        showToast(getString(R.string.copied_to_clipboard));
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not used in this activity
    }

    private void setupSender() {
        if (senderKeyInfo == null || senderKeyInfo.getId() == null || senderKeyInfo.getId().isEmpty()) {
            return;
        }
        View cardView = LayoutInflater.from(this).inflate(R.layout.item_sender_key_card, fragmentContainer, false);
        
        ImageView avatar = cardView.findViewById(R.id.key_avatar);
        TextView keyLabel = cardView.findViewById(R.id.key_label);
        TextView keyId = cardView.findViewById(R.id.key_id);

        try {
            byte[] avatarBytes = AvatarMaker.createAvatar(senderKeyInfo.getId(), this);
            if (avatarBytes != null) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                avatar.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            ToastUtils.showError(this, getString(R.string.failed_to_create_avatar));
        }
        
        keyLabel.setText(senderKeyInfo.getLabel());
        keyLabel.setTextColor(getResources().getColor(R.color.field_name, getTheme()));
        keyLabel.setTypeface(keyLabel.getTypeface(), android.graphics.Typeface.BOLD);
        keyId.setText(senderKeyInfo.getId());
        
        avatar.setOnClickListener(v -> IdUtils.showAvatarDialog(this, senderKeyInfo.getId()));
        keyId.setOnClickListener(v -> copyToClipboard(senderKeyInfo.getId(), getString(R.string.sender)));
        
        fragmentContainer.addView(cardView);
    }

    private void setupSendTo() {
        List<CashMark> issued = txInfo.getIssuedCashes();
        boolean hasIssued = issued != null && !issued.isEmpty();
        List<Cash> rawOutputs = rawTxInfo != null ? rawTxInfo.getOutputs() : null;
        boolean hasRaw = rawOutputs != null && !rawOutputs.isEmpty();
        if (!hasIssued && !hasRaw) return;

        addLabel(getString(R.string.send_to) + ":");

        // User-specified outputs come from rawTxInfo — display with original owner FID and lockTime.
        // The issued cashes parsed from the tx bytes would show the P2SH "3..." address for CLTV outputs,
        // which hides the original payee. So prefer the raw outputs for the first N entries.
        int userOutputCount = hasRaw ? rawOutputs.size() : 0;
        for (int i = 0; i < userOutputCount; i++) {
            Cash raw = rawOutputs.get(i);
            Double amount = raw.getAmount();
            Cash sendTo = new Cash(raw.getOwner(), amount != null ? amount : 0.0);
            if (raw.getLockTime() != null && raw.getLockTime() > 0) {
                sendTo.setLockTime(raw.getLockTime());
            }
            TxOutputCard card = new TxOutputCard(this);
            card.setSendTo(sendTo, this, false, false);
            fragmentContainer.addView(card);
        }

        // Anything extra in the parsed tx outputs (typically the change output) — render from issued cashes.
        if (hasIssued) {
            for (int i = userOutputCount; i < issued.size(); i++) {
                CashMark cashMark = issued.get(i);
                Cash sendTo = new Cash(cashMark.getOwner(), FchUtils.satoshiToCoin(cashMark.getValue()));
                TxOutputCard card = new TxOutputCard(this);
                card.setSendTo(sendTo, this, false, false);
                fragmentContainer.addView(card);
            }
        }
    }

    private void setupCarve() {
        if (txInfo.getOpReBrief() == null || txInfo.getOpReBrief().isEmpty()) {
            return;
        }

        String carveValue = txInfo.getOpReBrief();
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

            // Create dialog with custom theme
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.JsonDialogTheme);

            // Inflate custom layout
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_json_viewer, null);

            // Setup views
            TextView dialogTitle = dialogView.findViewById(R.id.dialogTitle);
            TextView jsonTextView = dialogView.findViewById(R.id.jsonTextView);
            Button copyButton = dialogView.findViewById(R.id.copyButton);
            Button closeButton = dialogView.findViewById(R.id.closeButton);

            dialogTitle.setText(getString(R.string.carving) + " (JSON)");
            jsonTextView.setText(niceJson);

            builder.setView(dialogView);
            AlertDialog dialog = builder.create();

            // Setup button listeners
            copyButton.setOnClickListener(v -> {
                copyToClipboard(niceJson, getString(R.string.carving));
                dialog.dismiss();
            });

            closeButton.setOnClickListener(v -> dialog.dismiss());

            dialog.show();

            // Set dialog window to use most of the screen
            if (dialog.getWindow() != null) {
                android.view.WindowManager.LayoutParams layoutParams = new android.view.WindowManager.LayoutParams();
                layoutParams.copyFrom(dialog.getWindow().getAttributes());
                layoutParams.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.95);
                layoutParams.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
                dialog.getWindow().setAttributes(layoutParams);
            }

        } catch (Exception e) {
            ToastUtils.showError(this, getString(R.string.failed_to_parse_json));
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

    private void setupSummary() {
        // Spending cash IDs
        if (inputCashList != null && !inputCashList.isEmpty()) {
            addLabel(getString(R.string.spending_cash_ids));
            for (Cash cash : inputCashList) {
                String cashId = cash.getId();
                if (cashId == null) {
                    cashId = cash.makeId();
                }
                addCashLine(cashId,String.valueOf(FchUtils.satoshiToCoin(cash.getValue())));
            }
        }

        // Spending sum
        long spendingSum = 0;
        for (Cash cash : inputCashList) {
            spendingSum += cash.getValue();
        }
        addTextLine(getString(R.string.sum) + ": " + FchUtils.satoshiToCoin(spendingSum) + " " + getString(R.string.currency_fch));

        // Paying sum
        long payingSum = 0;
        if (txInfo.getIssuedCashes() != null) {
            for (CashMark cashMark : txInfo.getIssuedCashes()) {
                payingSum += cashMark.getValue();
            }
        }
//        addTextLine(getString(R.string.paying_sum) + ": " + FchUtils.satoshiToCoin(payingSum) + " F");

        // Fee
        long fee = spendingSum - payingSum;
        addTextLineWithHelp(getString(R.string.fee) + ": " + FchUtils.satoshiToCash(fee) + " " + getString(R.string.currency_cash), "1c = 100satoshi = 0.000001F");

        // CDD
        if (cdd != null) {
            addTextLine(getString(R.string.cdd) + ": " + cdd);
        }
    }

    private void addTextLine(String text) {
        if(text==null || text.isEmpty()) return;
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setPadding(0, 16, 0, 16);

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
            fieldValue.setOnClickListener(v -> copyToClipboard(parts[1], getString(R.string.result)));
            fieldValue.setClickable(true);
            fieldValue.setFocusable(true);

            rowLayout.addView(fieldName);
            rowLayout.addView(fieldValue);
        } else {
            TextView fieldValue = new TextView(this);
            fieldValue.setText("\t"+text);
            fieldValue.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
            fieldValue.setTypeface(null, android.graphics.Typeface.NORMAL);
            fieldValue.setOnClickListener(v -> copyToClipboard(text, getString(R.string.result)));
            fieldValue.setClickable(true);
            fieldValue.setFocusable(true);
            rowLayout.addView(fieldValue);
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

    private void addTextLineWithHelp(String text, String helpMessage) {
        if(text==null || text.isEmpty()) return;
        LinearLayout rowLayout = new LinearLayout(this);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setPadding(0, 16, 0, 16);
        rowLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

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
            fieldValue.setOnClickListener(v -> copyToClipboard(parts[1], getString(R.string.result)));
            fieldValue.setClickable(true);
            fieldValue.setFocusable(true);

            // Add help icon
            ImageView helpIcon = new ImageView(this);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(48, 48);
            iconParams.setMargins(16, 0, 0, 0);
            helpIcon.setLayoutParams(iconParams);
            helpIcon.setImageResource(R.drawable.ic_help);
            helpIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            helpIcon.setPadding(8, 8, 8, 8);
            helpIcon.setOnClickListener(v -> {
                ToastUtils.makeText(this, helpMessage, Toast.LENGTH_LONG,"INFO");
            });

            rowLayout.addView(fieldName);
            rowLayout.addView(fieldValue);
            rowLayout.addView(helpIcon);
        } else {
            TextView fieldValue = new TextView(this);
            fieldValue.setText("\t"+text);
            fieldValue.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
            fieldValue.setTypeface(null, android.graphics.Typeface.NORMAL);
            fieldValue.setOnClickListener(v -> copyToClipboard(text, getString(R.string.result)));
            fieldValue.setClickable(true);
            fieldValue.setFocusable(true);
            rowLayout.addView(fieldValue);
        }

        fragmentContainer.addView(rowLayout);
    }

    /**
     * Creates a PendingTx record and locks the referenced cashes. Input cashes stay valid=true but
     * get pendingId set; locally-owned output cashes are inserted as valid=false with pendingId set.
     * The user later confirms or cancels via the PendingTx list.
     */
    private void updateCashDB(String signedTx, TxInfo txInfo) {
        if (signedTx == null || txInfo == null) {
            TimberLogger.e(TAG, "updateCashDB: signedTx or txInfo is null");
            return;
        }

        try {
            String signedTxId = Hex.toHex(Hash.sha256x2(Hex.fromHex(signedTx)));
            TimberLogger.i(TAG, "updateCashDB: signedTxId = %s", signedTxId);

            String fingerprint = TxFingerprint.of(rawTxInfo);
            String ownerFid = senderKeyInfo != null ? senderKeyInfo.getId() : null;

            PendingTx pendingTx = PendingTx.create(rawTxInfo.toJson(), fingerprint, false, ownerFid);
            pendingTx.setSignedTxHex(signedTx);
            pendingTx.setOnChainTxId(signedTxId);

            PendingTxManager pendingMgr = PendingTxManager.getInstance(this);

            // Lock input cashes.
            List<String> inputIds = new ArrayList<>();
            if (txInfo.getSpentCashes() != null) {
                for (CashMark spent : txInfo.getSpentCashes()) {
                    if (spent.getId() != null) inputIds.add(spent.getId());
                }
            }
            List<String> locked = PendingTxManager.lockInputCashes(this, inputIds, pendingTx.getPendingId());
            pendingTx.setSpentCashIds(locked);

            // Insert locally-owned outputs as pending-incoming.
            KeyInfoManager keyInfoManager = KeyInfoManager.getInstance(this);
            List<Cash> newOutputs = new ArrayList<>();
            List<String> newIds = new ArrayList<>();
            if (txInfo.getIssuedCashes() != null) {
                for (int i = 0; i < txInfo.getIssuedCashes().size(); i++) {
                    CashMark cashMark = txInfo.getIssuedCashes().get(i);
                    if (cashMark.getOwner() == null) continue;
                    if (keyInfoManager.getKeyInfoById(cashMark.getOwner()) == null) continue;

                    Cash newCash = new Cash();
                    newCash.setBirthTxId(signedTxId);
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
            TimberLogger.i(TAG, "updateCashDB: pending TX %s registered (%d inputs locked, %d outputs pending)",
                    pendingTx.getPendingId(), locked.size(), newIds.size());

        } catch (Exception e) {
            TimberLogger.e(TAG, "updateCashDB: Error updating cash database: %s", e.getMessage());
        }
    }


} 