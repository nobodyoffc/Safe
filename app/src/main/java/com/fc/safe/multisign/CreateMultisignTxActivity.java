package com.fc.safe.multisign;

import static com.fc.fc_ajdk.constants.Constants.COIN_TO_SATOSHI;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.WindowManager;
import android.content.ClipboardManager;
import android.content.ClipData;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.core.fch.TxCreator;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.Multisign;
import com.fc.fc_ajdk.data.fchData.SendTo;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.tx.dialog.AddTxInputDialog;
import com.fc.safe.tx.dialog.AddTxOutputDialog;
import com.fc.safe.tx.dialog.AddOutputFromFidListDialog;

import com.fc.safe.tx.view.TxInputCard;
import com.fc.safe.tx.view.TxOutputCard;
import com.fc.safe.tx.ImportTxInfoActivity;
import com.fc.safe.tx.SignTxActivity;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.text.DecimalFormat;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class CreateMultisignTxActivity extends BaseCryptoActivity {
    private static final String TAG = "CreateMultisignTxActivity";

    private static final int QR_SCAN_TEXT_REQUEST_CODE = 1001;
    private static final int QR_SCAN_KEY_REQUEST_CODE = 1002;
    private static final int REQUEST_CODE_CHOOSE_KEY = 1003;
    private RawTxInfo rawTxInfo;

    private LinearLayout txContainer;
    private LinearLayout inputCardsContainer;
    private LinearLayout outputCardsContainer;
    private LinearLayout opreturnContainer;
    private LinearLayout keyContainer;
    private LinearLayout buttonContainer;

    private List<TxInputCard> inputCards = new ArrayList<>();
    private List<TxOutputCard> outputCards = new ArrayList<>();

    private TextInputEditText opreturnInput;
    private TextInputEditText multisignInput;
    private Button clearButton;
    private Button importTxButton;
    private Button createButton;
    private Button copyButton;
    private ImageButton plusInputButton;
    private ImageButton plusOutputButton;
    private TextView outputHint;
    private TextView inputHint;
    private TextView totalAndFeeText;
    private TextView totalInputText;

    private long feeRateLong;
    private long fee;
    private long totalInput;
    private long totalOutput;
    private long rest;

    // Track the current dialog
    private Dialog currentDialog;

    private ActivityResultLauncher<Intent> chooseMultisignLauncher;
    private ActivityResultLauncher<Intent> importTxLauncher;
    private ActivityResultLauncher<Intent> signMultisignTxLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimberLogger.i(TAG, "onCreate started");

        // Set window flags to prevent transition issues
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        // Initialize rawTxInfo from intent or create new one
        String txJson = (String)getIntent().getSerializableExtra(SignTxActivity.EXTRA_TX_INFO_JSON);
        rawTxInfo = RawTxInfo.fromJson(txJson,RawTxInfo.class);
        if (rawTxInfo == null) {
            rawTxInfo = new RawTxInfo();
            // If we have a Multisign in the intent, set it
            Multisign multisign = (Multisign) getIntent().getSerializableExtra("multisign");
            if (multisign != null) {
                rawTxInfo.setMultisign(multisign);
            }
        }
        TimberLogger.i(TAG, "RawTxInfo initialized");
        feeRateLong = (long) (TxCreator.DEFAULT_FEE_RATE / 1000 * COIN_TO_SATOSHI);

        // Initialize chooseMultisignLauncher
        chooseMultisignLauncher = registerForActivityResult(
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

        // Initialize signMultisignTxLauncher
        signMultisignTxLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == SignMultisignTxActivity.RESULT_BUILT) {
                    finish();  // Finish this activity when transaction is built
                }
            }
        );

        TimberLogger.i(TAG, "onCreate completed");
    }

    private void initViews() {
        TimberLogger.i(TAG, "initViews started");
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
        multisignInput = keyContainer.findViewById(R.id.multisignIdInput).findViewById(R.id.keyInput);
        multisignInput.setHint(R.string.sender_from_script_or_multisign);

        clearButton = findViewById(R.id.clearButton);
        importTxButton = findViewById(R.id.importTxButton);
        createButton = findViewById(R.id.createButton);
        copyButton = findViewById(R.id.copyButton);
        plusInputButton = findViewById(R.id.plusInputButton);
        plusOutputButton = findViewById(R.id.plusOutputButton);

        setupTextIcons(R.id.opreturnInput, R.id.scanIcon, QR_SCAN_TEXT_REQUEST_CODE);
        setupKeyIcons(R.id.multisignIdInput, R.id.peopleAndScanIcons, QR_SCAN_KEY_REQUEST_CODE);
    }

    private void setupListeners() {
        TimberLogger.i(TAG, "setupListeners started");

        // Add click listener to root layout to hide keyboard
        View rootView = findViewById(android.R.id.content);
        rootView.setOnClickListener(v -> {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
        });

        copyButton.setOnClickListener(v -> {
            TimberLogger.i(TAG, "copyButton clicked");
            // Hide keyboard
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }

            if (!makeRawTxInfo()) {
                Toast.makeText(this, "Failed to create transaction info", SafeApplication.TOAST_LASTING).show();
                return;
            }

            if (rawTxInfo == null) {
                Toast.makeText(this, "No transaction to copy", SafeApplication.TOAST_LASTING).show();
                return;
            }

            String json = rawTxInfo.toNiceJson();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Transaction JSON", json);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, getString(R.string.copied), SafeApplication.TOAST_LASTING).show();
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
            multisignInput.setText("");
            inputCards.clear();
            outputCards.clear();
            inputHint.setVisibility(View.VISIBLE);
            outputHint.setVisibility(View.VISIBLE);
        });

        importTxButton.setOnClickListener(v -> {
            TimberLogger.i(TAG, "importTxButton clicked");
            // Hide keyboard
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
            importTxLauncher.launch(ImportTxInfoActivity.createIntent(this));
        });

        createButton.setOnClickListener(v -> {
            TimberLogger.i(TAG, "createButton clicked");

            if (!makeRawTxInfo()) return;

            if (rawTxInfo == null) {
                TimberLogger.e(TAG, "Failed to create multisign TX");
                Toast.makeText(this,"Failed to create multisign TX.", SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Start SignMultisignTxActivity
            Intent intent = new Intent(this, SignMultisignTxActivity.class);
            intent.putExtra(SignTxActivity.EXTRA_TX_INFO_JSON, rawTxInfo.toJsonWithSenderInfo());
            signMultisignTxLauncher.launch(intent);  // Use launcher instead of startActivity
        });
        TimberLogger.i(TAG, "setupListeners completed");
    }

    private boolean makeRawTxInfo() {
        if(opreturnInput.getText()!=null && !opreturnInput.getText().toString().isEmpty())
            rawTxInfo.setOpReturn(opreturnInput.getText().toString());

        if (multisignInput.getText() == null || multisignInput.getText().toString().isEmpty()) {
            Toast.makeText(this,"Input the script or Multisign. Or select a multisign FID.", SafeApplication.TOAST_LASTING).show();
            return false;
        }

        String multisignText = multisignInput.getText().toString();

        Multisign multisign;
        if(KeyTools.isGoodFid(multisignText)&& multisignText.startsWith("3")){
            multisign = rawTxInfo.getMultisign();
        } else if (Hex.isHexString(multisignText)) {
            multisign = Multisign.parseMultisignRedeemScript(multisignText);
            rawTxInfo.setMultisign(multisign);
        } else if (JsonUtils.isJson(multisignText)) {
            multisign = Multisign.fromJson(multisignText, Multisign.class);
            rawTxInfo.setMultisign(multisign);
        }else{
            Toast.makeText(this,"Failed to parse Multisign.", SafeApplication.TOAST_LASTING).show();
            return false;
        }

        if(!goodTxInfo(rawTxInfo)) return false;

        rawTxInfo.setSender(multisign.getId());
        return true;
    }

    private void updateTotalAndFeeText() {
        updateTotalsAndFee(rawTxInfo);
        DecimalFormat df = new DecimalFormat("0.########");
        df.setDecimalSeparatorAlwaysShown(false);

        // Create SpannableString for output text
        String outputText = String.format(Locale.US, "Paying %s F    Fee %s c",
            df.format(FchUtils.satoshiToCoin(totalOutput)),
            df.format(FchUtils.satoshiToCash(fee)));
        SpannableString spannableOutput = new SpannableString(outputText);
        
        // Make "Paying" and "Fee" bold and colored
        int payingIndex = outputText.indexOf("Paying");
        int feeIndex = outputText.indexOf("Fee");
        int fieldNameColor = getResources().getColor(R.color.field_name);
        
        spannableOutput.setSpan(new StyleSpan(Typeface.BOLD), payingIndex, payingIndex + 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableOutput.setSpan(new ForegroundColorSpan(fieldNameColor), payingIndex, payingIndex + 6, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        spannableOutput.setSpan(new StyleSpan(Typeface.BOLD), feeIndex, feeIndex + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableOutput.setSpan(new ForegroundColorSpan(fieldNameColor), feeIndex, feeIndex + 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        totalAndFeeText.setText(spannableOutput);

        // Create SpannableString for input text
        String inputText = String.format(Locale.US, "Spending %s F",
                df.format(FchUtils.satoshiToCoin(totalInput)));
        SpannableString spannableInput = new SpannableString(inputText);
        
        // Make "Spending" bold and colored
        int spendingIndex = inputText.indexOf("Spending");
        spannableInput.setSpan(new StyleSpan(Typeface.BOLD), spendingIndex, spendingIndex + 8, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableInput.setSpan(new ForegroundColorSpan(fieldNameColor), spendingIndex, spendingIndex + 8, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        totalInputText.setText(spannableInput);
    }

    private boolean goodTxInfo(RawTxInfo rawTxInfo) {

        List<Cash> inputs = rawTxInfo.getInputs();
        for(Cash cash : inputs){
            if(cash.getValue()<=0){
                Toast.makeText(this,"Input value must be greater than 0.", SafeApplication.TOAST_LASTING).show();
                return false;
            }
            if(!Hex.isHex32(cash.getBirthTxId())){
                Toast.makeText(this,"Invalid txid.", SafeApplication.TOAST_LASTING).show();
                return false;
            }
            if(cash.getBirthIndex()<0){
                Toast.makeText(this,"Invalid index.", SafeApplication.TOAST_LASTING).show();
                return false;
            }
        }

        if(rawTxInfo.getMultisign()==null){
            Toast.makeText(this,"Failed to parse Multisign.", SafeApplication.TOAST_LASTING).show();
            return false;
        }

        if(rawTxInfo.getInputs()==null || rawTxInfo.getInputs().isEmpty()){
            Toast.makeText(this,"Inputs cannot be empty.", SafeApplication.TOAST_LASTING).show();
            return false;
        }


        updateTotalsAndFee(rawTxInfo);


        if(totalInput<fee+totalOutput){
            Toast.makeText(this,"Inputs are not enough to cover the fee.", SafeApplication.TOAST_LASTING).show();
            return false;
        }

        updateTotalAndFeeText();
        return true;
    }

    private void updateTotalsAndFee(RawTxInfo rawTxInfo) {
        totalInput = 0;
        totalOutput = 0;
        if(rawTxInfo ==null){
            rawTxInfo = new RawTxInfo();
        }
        if(rawTxInfo.getInputs()==null) rawTxInfo.setInputs(new ArrayList<>());

        if(rawTxInfo.getOutputs()==null) rawTxInfo.setOutputs(new ArrayList<>());

        for(Cash cash : rawTxInfo.getInputs()){
            totalInput += cash.getValue();
        }

        totalOutput = 0;
        for(SendTo sendTo : rawTxInfo.getOutputs()){
            totalOutput += FchUtils.coinToSatoshi(sendTo.getAmount());
        }

        int opReturnBytesLen = rawTxInfo.getOpReturn() == null ?0: rawTxInfo.getOpReturn().getBytes().length;
        int inputNum = rawTxInfo.getInputs().size();
        int outputNum = rawTxInfo.getOutputs().size();


        int m = rawTxInfo.getMultisign()==null? 2: rawTxInfo.getMultisign().getM();
        int n = rawTxInfo.getMultisign()==null? 3: rawTxInfo.getMultisign().getN();

        fee = feeRateLong * TxCreator.calcSizeMultiSign(inputNum, outputNum, opReturnBytesLen, m, n);
        rest = totalInput-totalOutput-fee;
    }

    private void removeInputCard(TxInputCard card) {
        TimberLogger.i(TAG, "removeInputCard called");
        inputCards.remove(card);
        inputCardsContainer.removeView(card);
        rawTxInfo.getInputs().remove(card.getCash());
        if (inputCards.isEmpty()) {
            inputHint.setVisibility(View.VISIBLE);
        }
        updateTotalAndFeeText();
    }

    private void removeOutputCard(TxOutputCard card) {
        TimberLogger.i(TAG, "removeOutputCard called");
        outputCards.remove(card);
        outputCardsContainer.removeView(card);
        rawTxInfo.getOutputs().remove(card.getSendTo());
        if (outputCards.isEmpty()) {
            outputHint.setVisibility(View.VISIBLE);
        }
        updateTotalAndFeeText();
    }

    private void handleImportedTxInfo(RawTxInfo importedRawTxInfo) {
        TimberLogger.i(TAG, "handleImportedTxInfo started");
        TimberLogger.i(TAG, "Imported RawTxInfo: " + (importedRawTxInfo != null ? "not null" : "null"));

        if (importedRawTxInfo == null) {
            TimberLogger.e(TAG, "Imported RawTxInfo is null");
            return;
        }

        // Copy all fields from importedRawTxInfo to rawTxInfo
        rawTxInfo.copy(importedRawTxInfo);

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
        multisignInput.setText("");

        // Add input cards
        if (rawTxInfo.getInputs() != null) {
            TimberLogger.i(TAG, "Adding input cards, count: " + rawTxInfo.getInputs().size());
            for (Cash cash : rawTxInfo.getInputs()) {
                TimberLogger.i(TAG, "Creating input card for cash: " + cash);
                TxInputCard card = new TxInputCard(this);
                card.setCash(cash);
                card.setOnDeleteListener(this::removeInputCard);
                inputCards.add(card);
                inputCardsContainer.addView(card, inputCardsContainer.getChildCount() - 1);
                TimberLogger.i(TAG, "Added input card, new container child count: " + inputCardsContainer.getChildCount());
            }
        } else {
            TimberLogger.w(TAG, "No inputs to add");
        }

        // Add output cards
        if (rawTxInfo.getOutputs() != null) {
            TimberLogger.i(TAG, "Adding output cards, count: " + rawTxInfo.getOutputs().size());
            for (SendTo sendTo : rawTxInfo.getOutputs()) {
                TimberLogger.i(TAG, "Creating output card for sendTo: " + sendTo);
                TxOutputCard card = new TxOutputCard(this);
                card.setSendTo(sendTo, this, true);
                card.setOnDeleteListener(this::removeOutputCard);
                outputCards.add(card);
                outputCardsContainer.addView(card, outputCardsContainer.getChildCount() - 1);

                updateTotalAndFeeText();

                TimberLogger.i(TAG, "Added output card, new container child count: " + outputCardsContainer.getChildCount());
            }
        } else {
            TimberLogger.w(TAG, "No outputs to add");
        }

        // Set opreturn text
        if (rawTxInfo.getOpReturn() != null) {
            TimberLogger.i(TAG, "Setting opreturn text: " + rawTxInfo.getOpReturn());
            opreturnInput.setText(rawTxInfo.getOpReturn());
        } else {
            TimberLogger.w(TAG, "No message to set in opreturn");
        }

        // Set p2sh input
        if (rawTxInfo.getSender() != null) {
            TimberLogger.i(TAG, "Setting p2sh input: " + rawTxInfo.getSender());
            multisignInput.setText(rawTxInfo.getSender());
        } else {
            TimberLogger.w(TAG, "No sender to set in p2sh input");
        }

        // Update hints visibility
        boolean inputHintVisible = inputCards.isEmpty();
        boolean outputHintVisible = outputCards.isEmpty();
        TimberLogger.i(TAG, "Setting hints visibility - Input hint: " + inputHintVisible + ", Output hint: " + outputHintVisible);
        inputHint.setVisibility(inputHintVisible ? View.VISIBLE : View.GONE);
        outputHint.setVisibility(outputHintVisible ? View.VISIBLE : View.GONE);

        updateTotalAndFeeText();

        TimberLogger.i(TAG, "Final container states - Input cards: " + inputCards.size() +
            ", Output cards: " + outputCards.size() +
            ", Input container children: " + inputCardsContainer.getChildCount() +
            ", Output container children: " + outputCardsContainer.getChildCount());

        TimberLogger.i(TAG, "handleImportedTxInfo completed");
    }

    @Override
    protected int getLayoutId() {
        TimberLogger.i(TAG, "getLayoutId called, returning: " + R.layout.activity_create_multisign_tx);
        return R.layout.activity_create_multisign_tx;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.create_multisign_tx);
    }

    @Override
    protected void initializeViews() {
        TimberLogger.i(TAG, "initializeViews called");
        initViews();
        updateTotalAndFeeText();  // Initialize the text with initial values
    }

    @Override
    protected void setupButtons() {
        TimberLogger.i(TAG, "setupButtons called");
        setupListeners();
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        TimberLogger.i(TAG, "handleQrScanResult called with requestCode: " + requestCode + ", qrContent: " + qrContent);
        if (qrContent == null || qrContent.isEmpty()) {
            TimberLogger.e(TAG, "Invalid QR code content");
            showToast("Invalid QR code content");
            return;
        }

        // Check if any dialog is showing and handle its QR scan result
        if (currentDialog != null) {
            TimberLogger.i(TAG, "Current dialog is: " + currentDialog.getClass().getSimpleName());
            if (currentDialog instanceof AddTxInputDialog) {
                TimberLogger.i(TAG, "Handling QR scan result for AddTxInputDialog");
                ((AddTxInputDialog) currentDialog).handleQrScanResult(requestCode, qrContent);
            } else if (currentDialog instanceof AddTxOutputDialog) {
                TimberLogger.i(TAG, "Handling QR scan result for AddTxOutputDialog with requestCode: " + requestCode);
                ((AddTxOutputDialog) currentDialog).handleQrScanResult(requestCode, qrContent);
            } else {
                TimberLogger.e(TAG, "Unknown dialog type: " + currentDialog.getClass().getSimpleName());
            }
        } else {
            // Handle QR scan results for the main activity
            TimberLogger.i(TAG, "No dialog showing, handling QR scan result for main activity");
            if (requestCode == QR_SCAN_TEXT_REQUEST_CODE) {
                TimberLogger.i(TAG, "Setting QR content to opreturnInput: " + qrContent);
                opreturnInput.setText(qrContent);
            } else if (requestCode == QR_SCAN_KEY_REQUEST_CODE) {
                TimberLogger.i(TAG, "Setting QR content to p2shInput: " + qrContent);
                multisignInput.setText(qrContent);
            } else {
                TimberLogger.e(TAG, "Unknown request code: " + requestCode);
            }
        }
    }

    @Override
    protected void handleChooseKeyResult(Intent data) {
        Map<String, Multisign> resultMap = ChooseMultisignIdActivity.getSelectedMultisignMap(data);
        if (!resultMap.isEmpty()) {
            // Hide keyboard
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }

            // Get the first (and only) Multisign from the map
            Multisign selectedMultisign = resultMap.values().iterator().next();
            // Set the Multisign input with the selected Multisign's ID
            multisignInput.setText(selectedMultisign.getId());
            rawTxInfo.setMultisign(selectedMultisign);
        }
    }

    @Override
    protected void setupKeyIcons(int inputId, int iconsId, int qrScanRequestCode) {
        // For CreateMultisignTxActivity, use ChooseMultisignIdActivity
        setupIoIconsView(inputId, iconsId, false, true, true, false,
                null,
            v -> {
                // Hide keyboard
                View currentFocus = getCurrentFocus();
                if (currentFocus != null) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                }
                chooseMultisignLauncher.launch(ChooseMultisignIdActivity.createIntent(this, true));
            },
            () -> startQrScan(qrScanRequestCode),
            null);
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