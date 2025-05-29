package com.fc.safe.home;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.core.fch.TxCreator;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.SendTo;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.tx.ImportTxInfoActivity;
import com.fc.safe.tx.SignTxActivity;
import com.fc.safe.tx.dialog.AddTxInputDialog;
import com.fc.safe.tx.dialog.AddTxOutputDialog;
import com.fc.safe.tx.dialog.AddOutputFromFidListDialog;
import com.fc.safe.tx.view.TxInputCard;
import com.fc.safe.tx.view.TxOutputCard;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.DecimalFormat;

public class CreateTxActivity extends BaseCryptoActivity {
    private static final String TAG = "CreateTxActivity";
    private static final int QR_SCAN_TEXT_REQUEST_CODE = 1001;
    private static final int QR_SCAN_KEY_REQUEST_CODE = 1002;

    private RawTxInfo rawTxInfo;
    private KeyInfoManager keyInfoManager;

    private LinearLayout txContainer;
    private LinearLayout inputCardsContainer;
    private LinearLayout outputCardsContainer;
    private LinearLayout opreturnContainer;
    private LinearLayout keyContainer;
    private LinearLayout buttonContainer;

    private List<TxInputCard> inputCards = new ArrayList<>();
    private List<TxOutputCard> outputCards = new ArrayList<>();

    private TextInputEditText opreturnInput;
    private TextInputEditText keyInput;
    private Button clearButton;
    private Button importTxButton;
    private Button signButton;
    private Button copyButton;
    private ImageButton plusInputButton;
    private ImageButton plusOutputButton;
    private TextView outputHint;
    private TextView inputHint;
    private TextView totalAndFeeText;
    private TextView totalInputText;

    private KeyInfo keyInfo;
    private String opreturn;
    private long fee;
    private long totalInput;
    private long totalOutput;
    private long rest;

    private ActivityResultLauncher<Intent> chooseKeyLauncher;
    private ActivityResultLauncher<Intent> importTxLauncher;
    private ActivityResultLauncher<Intent> signTxLauncher;

    private Dialog currentDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimberLogger.i(TAG, "onCreate started");

        // Set window flags to prevent transition issues
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        // Initialize KeyInfoManager
        keyInfoManager = KeyInfoManager.getInstance(this);

        // Initialize rawTxInfo
        rawTxInfo = new RawTxInfo();
        TimberLogger.i(TAG, "RawTxInfo initialized");

        // Initialize chooseKeyLauncher
        chooseKeyLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                    handleChooseKeyResult(result.getData());
                }
            }
        );

        // Initialize importTxLauncher
        importTxLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                    String offLineTxInfoJson = result.getData().getStringExtra(SignTxActivity.EXTRA_TX_INFO_JSON);
                    if (offLineTxInfoJson != null) {
                        try {
                            RawTxInfo importedRawTxInfo = RawTxInfo.fromJson(offLineTxInfoJson, RawTxInfo.class);
                            if (importedRawTxInfo != null) {
                                handleImportedTxInfo(importedRawTxInfo);
                            }
                        } catch (Exception e) {
                            TimberLogger.e(TAG, "Error parsing RawTxInfo JSON: " + e.getMessage());
                        }
                    }
                }
            }
        );

        // Initialize signTxLauncher
        signTxLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == SignTxActivity.RESULT_SIGNED) {
                    finish();  // Finish this activity when transaction is signed
                }
            }
        );
    }

    private void initViews() {
        txContainer = findViewById(R.id.txContainer);
        inputCardsContainer = findViewById(R.id.inputCardsContainer);
        outputCardsContainer = findViewById(R.id.outputCardsContainer);
        opreturnContainer = findViewById(R.id.opreturnContainer);
        keyContainer = findViewById(R.id.keyContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        outputHint = findViewById(R.id.outputHint);
        inputHint = findViewById(R.id.inputHint);
        totalAndFeeText = findViewById(R.id.totalAndFeeText);
        totalInputText = findViewById(R.id.totalInputText);

        opreturnInput = opreturnContainer.findViewById(R.id.opreturnInput).findViewById(R.id.textInput);
        opreturnInput.setHint(R.string.input_the_text_carved_on_chain);
        keyInput = keyContainer.findViewById(R.id.multisignIdInput).findViewById(R.id.keyInput);
        keyInput.setHint(R.string.sender);

        clearButton = findViewById(R.id.clearButton);
        importTxButton = findViewById(R.id.importTxButton);
        signButton = findViewById(R.id.createButton);
        copyButton = findViewById(R.id.copyButton);
        plusInputButton = findViewById(R.id.plusInputButton);
        plusOutputButton = findViewById(R.id.plusOutputButton);

        // Setup icons
        setupTextIcons(R.id.opreturnInput, R.id.scanIcon, QR_SCAN_TEXT_REQUEST_CODE);
        setupKeyIcons(R.id.multisignIdInput, R.id.peopleAndScanIcons, QR_SCAN_KEY_REQUEST_CODE);
    }

    private void setupListeners() {
        // Add click listener to root layout to hide keyboard
        View rootView = findViewById(android.R.id.content);
        rootView.setOnClickListener(v -> {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        });

        plusInputButton.setOnClickListener(v -> {
            TimberLogger.i(TAG, "plusInputButton clicked");
            // Hide keyboard before showing dialog
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }

            AddTxInputDialog dialog = new AddTxInputDialog(this, rawTxInfo);
            currentDialog = dialog;
            dialog.setOnDoneListener(cash -> {
                if(cash==null)return;
                TimberLogger.i(TAG, "AddTxInputDialog done, cash: " + cash);
                TxInputCard card = new TxInputCard(this);
                card.setCash(cash);
                card.setOnDeleteListener(this::removeInputCard);
                inputCards.add(card);
                inputCardsContainer.addView(card, inputCardsContainer.getChildCount() - 1);
                inputHint.setVisibility(View.GONE);
                currentDialog = null;
                updateTotalAndFeeText();
                if(cash.getOwner()!=null)keyInput.setText(cash.getOwner());
            });
            dialog.setOnDismissListener(d -> currentDialog = null);
            dialog.show();
        });

        plusOutputButton.setOnClickListener(v -> {
            TimberLogger.i(TAG, "plusOutputButton clicked");
            AddTxOutputDialog dialog = new AddTxOutputDialog(this, rawTxInfo, rest);
            currentDialog = dialog;  // Set the current dialog
            dialog.setOnDoneListener(sendTo -> {
                TxOutputCard card = new TxOutputCard(this);
                card.setSendTo(sendTo, this, true);
                card.setOnDeleteListener(this::removeOutputCard);
                outputCards.add(card);
                outputCardsContainer.addView(card);
                outputHint.setVisibility(View.GONE);
                currentDialog = null;  // Clear the current dialog
                updateTotalAndFeeText();
            });
            dialog.setOnDismissListener(d -> currentDialog = null);  // Clear the current dialog when dismissed
            dialog.show();
        });

        // Add long press listener for plus output button
        plusOutputButton.setOnLongClickListener(v -> {
            TimberLogger.i(TAG, "plusOutputButton long pressed");
            // Hide keyboard
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }

            AddOutputFromFidListDialog dialog = new AddOutputFromFidListDialog(this, rawTxInfo, rest);
            currentDialog = dialog;
            dialog.setOnDoneListener(sendToList -> {
                for (SendTo sendTo : sendToList) {
                    TxOutputCard card = new TxOutputCard(this);
                    card.setSendTo(sendTo, this, true);
                    card.setOnDeleteListener(this::removeOutputCard);
                    outputCards.add(card);
                    outputCardsContainer.addView(card);
                }
                outputHint.setVisibility(View.GONE);
                currentDialog = null;
                updateTotalAndFeeText();
            });
            dialog.setOnDismissListener(d -> currentDialog = null);
            dialog.show();
            return true;
        });

        clearButton.setOnClickListener(v -> {
            TimberLogger.i(TAG, "clearButton clicked");
            // Hide keyboard
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }

            rawTxInfo = new RawTxInfo();

            // Remove all views except the plus buttons
            for (int i = inputCardsContainer.getChildCount() - 1; i >= 0; i--) {
                View child = inputCardsContainer.getChildAt(i);
                if (child != plusInputButton) {
                    inputCardsContainer.removeView(child);
                }
            }

            for (int i = outputCardsContainer.getChildCount() - 1; i >= 0; i--) {
                View child = outputCardsContainer.getChildAt(i);
                if (child != plusOutputButton) {
                    outputCardsContainer.removeView(child);
                }
            }

            opreturnInput.setText("");
            keyInput.setText("");
            inputCards.clear();
            outputCards.clear();
            inputHint.setVisibility(View.VISIBLE);
            outputHint.setVisibility(View.VISIBLE);
        });

        importTxButton.setOnClickListener(v -> {
            TimberLogger.i(TAG, "importTxButton clicked");
            // Hide keyboard
//            View currentFocus = getCurrentFocus();
//            if (currentFocus != null) {
//                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
//            }
            importTxLauncher.launch(ImportTxInfoActivity.createIntent(this));
        });

        signButton.setOnClickListener(v -> {
            TimberLogger.i(TAG, "signButton clicked");

            updateOffLineTxInfo();

            if(rawTxInfo ==null){
                Toast.makeText(this, R.string.no_tx_to_sign, SafeApplication.TOAST_LASTING).show();
                return;
            }

            if(!goodTxInfo(rawTxInfo)) return;

            // Convert RawTxInfo to JSON
            String offLineTxInfoJson = rawTxInfo.toJsonWithSenderInfo();

            // Start SignTxActivity
            Intent intent = new Intent(this, SignTxActivity.class);
            intent.putExtra(SignTxActivity.EXTRA_TX_INFO_JSON, offLineTxInfoJson);

            signTxLauncher.launch(intent);
        });

        copyButton.setOnClickListener(v -> {
            TimberLogger.i(TAG, "copyButton clicked");
            // Hide keyboard
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }


            updateOffLineTxInfo();


            if(rawTxInfo ==null){
                Toast.makeText(this, R.string.no_tx_to_copy, SafeApplication.TOAST_LASTING).show();
                return;
            }
            RawTxInfo copied = new RawTxInfo().copy(rawTxInfo);
            copied.setSenderInfo(null);
            String json = copied.toNiceJson();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Transaction JSON", json);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, R.string.tx_json_copied_to_clipboard, SafeApplication.TOAST_LASTING).show();

        });
    }

    private void updateOffLineTxInfo() {
        opreturn = opreturnInput.getText()==null? null:opreturnInput.getText().toString();

        String fid = keyInput.getText()==null? null:keyInput.getText().toString();

        if(keyInfo==null ||!keyInfo.getId().equals(fid)) {
            if(fid!=null && KeyTools.isGoodFid(fid)){
                keyInfo = keyInfoManager.getKeyInfoById(fid);
                if(keyInfo==null){
                    keyInfo = new KeyInfo(fid);
                }
            }
        }

        if(opreturn !=null || keyInput!=null){
            if(rawTxInfo ==null)
                rawTxInfo = new RawTxInfo();
            if(opreturn !=null) rawTxInfo.setOpReturn(opreturn);
            if(keyInfo!=null){
                rawTxInfo.setSenderInfo(keyInfo);
                rawTxInfo.setSender(keyInfo.getId());
            }
        }
    }

    private void updateTotalAndFeeText() {
        updateTotalsAndFee(rawTxInfo);
        DecimalFormat df = new DecimalFormat("0.########");
        df.setDecimalSeparatorAlwaysShown(false);

        // Create SpannableString for output text
        String outputText = String.format(Locale.US, "%s %s F    %s %s c",
            getString(R.string.paying),
            df.format(FchUtils.satoshiToCoin(totalOutput)),
            getString(R.string.fee),
            df.format(FchUtils.satoshiToCash(fee)));
        SpannableString spannableOutput = new SpannableString(outputText);
        
        // Make "Paying" and "Fee" bold and colored
        int payingIndex = outputText.indexOf(getString(R.string.paying));
        int feeIndex = outputText.indexOf(getString(R.string.fee));
        int fieldNameColor = getResources().getColor(R.color.field_name);
        
        spannableOutput.setSpan(new StyleSpan(Typeface.BOLD), payingIndex, payingIndex + getString(R.string.paying).length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableOutput.setSpan(new ForegroundColorSpan(fieldNameColor), payingIndex, payingIndex + getString(R.string.paying).length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        spannableOutput.setSpan(new StyleSpan(Typeface.BOLD), feeIndex, feeIndex + getString(R.string.fee).length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableOutput.setSpan(new ForegroundColorSpan(fieldNameColor), feeIndex, feeIndex + getString(R.string.fee).length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        totalAndFeeText.setText(spannableOutput);

        // Create SpannableString for input text
        String inputText = String.format(Locale.US, "%s %s F",
                getString(R.string.spending),
                df.format(FchUtils.satoshiToCoin(totalInput)));
        SpannableString spannableInput = new SpannableString(inputText);
        
        // Make "Spending" bold and colored
        int spendingIndex = inputText.indexOf(getString(R.string.spending));
        spannableInput.setSpan(new StyleSpan(Typeface.BOLD), spendingIndex, spendingIndex + getString(R.string.spending).length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableInput.setSpan(new ForegroundColorSpan(fieldNameColor), spendingIndex, spendingIndex + getString(R.string.spending).length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        totalInputText.setText(spannableInput);
    }

    private boolean goodTxInfo(RawTxInfo rawTxInfo) {
        List<Cash> inputs = rawTxInfo.getInputs();
        for(Cash cash : inputs){
            if(cash.getValue()<=0){
                Toast.makeText(this, R.string.input_value_must_be_greater_than_0 , SafeApplication.TOAST_LASTING).show();
                return false;
            }
            if(!Hex.isHex32(cash.getBirthTxId())){
                Toast.makeText(this, R.string.invalid_txid , SafeApplication.TOAST_LASTING).show();
                return false;
            }
            if(cash.getBirthIndex()<0){
                Toast.makeText(this, R.string.invalid_index , SafeApplication.TOAST_LASTING).show();
                return false;
            }
        }

        if(rawTxInfo.getInputs()==null || rawTxInfo.getInputs().isEmpty()){
            Toast.makeText(this, R.string.inputs_cannot_be_empty , SafeApplication.TOAST_LASTING).show();
            return false;
        }

        updateTotalsAndFee(rawTxInfo);

        if(totalInput<fee+totalOutput){
            Toast.makeText(this, R.string.inputs_are_not_enough_to_cover_the_fee , SafeApplication.TOAST_LASTING).show();
            return false;
        }

        updateTotalAndFeeText();
        return true;
    }

    private void updateTotalsAndFee(RawTxInfo rawTxInfo) {
        totalInput = 0;
        totalOutput = 0;
        fee = 0;
        rest = 0;
        
        if(rawTxInfo ==null){
            rawTxInfo = new RawTxInfo();
        }
        if(rawTxInfo.getInputs()==null) rawTxInfo.setInputs(new ArrayList<>());
        if(rawTxInfo.getOutputs()==null) rawTxInfo.setOutputs(new ArrayList<>());

        // Only calculate fee if there are inputs or outputs
        if (!rawTxInfo.getInputs().isEmpty() || !rawTxInfo.getOutputs().isEmpty()) {
            for(Cash cash : rawTxInfo.getInputs()){
                totalInput += cash.getValue();
            }

            for(SendTo sendTo : rawTxInfo.getOutputs()){
                totalOutput += FchUtils.coinToSatoshi(sendTo.getAmount());
            }

            int opReturnBytesLen = rawTxInfo.getOpReturn() == null ? 0 : rawTxInfo.getOpReturn().getBytes().length;
            int inputNum = rawTxInfo.getInputs().size();
            int outputNum = rawTxInfo.getOutputs().size();

            fee = TxCreator.calcFee(inputNum, outputNum, opReturnBytesLen, TxCreator.DEFAULT_FEE_RATE, false, null);
            rest = totalInput - totalOutput - fee;
        }
    }

    private void removeInputCard(TxInputCard card) {
        inputCards.remove(card);
        inputCardsContainer.removeView(card);
        rawTxInfo.getInputs().remove(card.getCash());
        if (inputCards.isEmpty()) {
            inputHint.setVisibility(View.VISIBLE);
        }
        updateTotalAndFeeText();
    }

    private void removeOutputCard(TxOutputCard card) {
        outputCards.remove(card);
        outputCardsContainer.removeView(card);
        rawTxInfo.getOutputs().remove(card.getSendTo());
        if (outputCards.isEmpty()) {
            outputHint.setVisibility(View.VISIBLE);
        }
        updateTotalAndFeeText();
    }

    private void handleImportedTxInfo(RawTxInfo importedRawTxInfo) {

        if (importedRawTxInfo == null) {
            TimberLogger.e(TAG, "Imported RawTxInfo is null");
            return;
        }

        keyInfo=null;
        opreturn=null;

        // Copy all fields from importedRawTxInfo to rawTxInfo
        rawTxInfo.copy(importedRawTxInfo);

        String sender = rawTxInfo.getSender();
        if(rawTxInfo.getSenderInfo()==null){
            if(sender !=null && KeyTools.isGoodFid(sender)){
               keyInfo = KeyInfoManager.getInstance(this).getKeyInfoById(sender);
               if(keyInfo==null){
                   keyInfo= new KeyInfo(sender);
                   rawTxInfo.setSenderInfo(keyInfo);
               }
            }
        }
        // Clear existing views and lists
        // Clear input cards
        for (int i = inputCardsContainer.getChildCount() - 1; i >= 0; i--) {
            View child = inputCardsContainer.getChildAt(i);
            if (child != plusInputButton) {
                inputCardsContainer.removeView(child);
            }
        }
        inputCards.clear();

        // Clear output cards
        for (int i = outputCardsContainer.getChildCount() - 1; i >= 0; i--) {
            View child = outputCardsContainer.getChildAt(i);
            if (child != plusOutputButton) {
                outputCardsContainer.removeView(child);
            }
        }
        outputCards.clear();

        // Clear text inputs
        opreturnInput.setText("");
        keyInput.setText("");

        // Add input cards
        if (rawTxInfo.getInputs() != null) {
            for (Cash cash : rawTxInfo.getInputs()) {
                TxInputCard card = new TxInputCard(this);
                card.setCash(cash);
                card.setOnDeleteListener(this::removeInputCard);
                inputCards.add(card);
                inputCardsContainer.addView(card, inputCardsContainer.getChildCount() - 1);
                if(cash.getOwner()!=null)keyInput.setText(cash.getOwner());
            }
        }

        // Add output cards
        if (rawTxInfo.getOutputs() != null) {
            for (SendTo sendTo : rawTxInfo.getOutputs()) {
                TxOutputCard card = new TxOutputCard(this);
                card.setSendTo(sendTo, this, true);
                card.setOnDeleteListener(this::removeOutputCard);
                outputCards.add(card);
                outputCardsContainer.addView(card);
            }
        }

        // Set opreturn text
        if (rawTxInfo.getOpReturn() != null) {
            opreturnInput.setText(rawTxInfo.getOpReturn());
        }

        // Set key input with sender's FID
        if (sender != null) {
            keyInput.setText(sender);
            // Load keyInfo from KeyInfoManager
            KeyInfo keyInfo = keyInfoManager.getKeyInfoById(sender);
            if (keyInfo != null) {
                // Set the key ID as gray text
                String text = keyInfo.getId();
                keyInput.setText(text);
                keyInput.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
                keyInput.setEnabled(false);
                keyInput.setTag(keyInfo.getId());
            }
        }

        // Update hints visibility
        inputHint.setVisibility(inputCards.isEmpty() ? View.VISIBLE : View.GONE);
        outputHint.setVisibility(outputCards.isEmpty() ? View.VISIBLE : View.GONE);

        updateTotalAndFeeText();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_create_multisign_tx;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.create_tx);
    }

    @Override
    protected void initializeViews() {
        initViews();
        updateTotalAndFeeText();
    }

    @Override
    protected void setupButtons() {
        setupListeners();
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (qrContent == null || qrContent.isEmpty()) {
            showToast(getString(R.string.invalid_qr_code_content));
            return;
        }

        // Check if any dialog is showing and handle its QR scan result
        if (currentDialog != null) {
            if (currentDialog instanceof AddTxInputDialog) {
                ((AddTxInputDialog) currentDialog).handleQrScanResult(requestCode, qrContent);
            } else if (currentDialog instanceof AddTxOutputDialog) {
                ((AddTxOutputDialog) currentDialog).handleQrScanResult(requestCode, qrContent);
            }
        } else {
            // Handle QR scan results for the main activity
            if (requestCode == QR_SCAN_TEXT_REQUEST_CODE) {
                opreturnInput.setText(qrContent);
            } else if (requestCode == QR_SCAN_KEY_REQUEST_CODE) {
                keyInput.setText(qrContent);
                // Load keyInfo from KeyInfoManager
                KeyInfo keyInfo = keyInfoManager.getKeyInfoById(qrContent);
                if (keyInfo != null) {
                    // Set the key ID as gray text
                    String text = keyInfo.getId();
                    keyInput.setText(text);
                    keyInput.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
                    keyInput.setEnabled(false);
                    keyInput.setTag(keyInfo.getId());
                }
            }
        }
    }

    @Override
    protected void handleChooseKeyResult(Intent data) {
        List<KeyInfo> selectedKeys = ChooseKeyInfoActivity.getSelectedKeyInfo(data, keyInfoManager);
        if (selectedKeys != null && !selectedKeys.isEmpty()) {
            KeyInfo keyInfo = selectedKeys.get(0);
            // Set the key ID as gray text
            String text = keyInfo.getId();
            keyInput.setText(text);
            keyInput.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
            keyInput.setEnabled(false);
            keyInput.setTag(keyInfo.getId());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clear any pending operations
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
            currentDialog = null;
        }

        // Clear references
        inputCards.clear();
        outputCards.clear();
        rawTxInfo = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Hide keyboard when activity is paused
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }
} 