package com.fc.safe.tx.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.P2SH;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.utils.TextIconsUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.fc.safe.utils.ToastUtils;

public class AddTxInputDialog extends Dialog {
    private static final String TAG = "AddTxInputDialog";
    private TextInputEditText txIdInput;
    private TextInputEditText indexInput;
    private TextInputEditText amountInput;
    private TextInputEditText redeemScriptInput;
    private Button clearButton;
    private Button cancelButton;
    private Button doneButton;
    private final RawTxInfo rawTxInfo;
    private OnDoneListener onDoneListener;

    private static final int QR_SCAN_TX_ID_REQUEST_CODE = 1001;
    private static final int QR_SCAN_AMOUNT_REQUEST_CODE = 1002;
    private static final int QR_SCAN_TEXT_REQUEST_CODE = 1003;

    public interface OnDoneListener {
        void onDone(Cash cash);
    }

    public AddTxInputDialog(@NonNull Context context, RawTxInfo rawTxInfo) {
        super(context);
        this.rawTxInfo = rawTxInfo;
        if (context instanceof android.app.Activity) {
            setOwnerActivity((android.app.Activity) context);
        } else {
            TimberLogger.e(TAG, "Context is not an Activity");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_add_tx_input);

        txIdInput = findViewById(R.id.txIdInput).findViewById(R.id.textInput);
        txIdInput.setHint(R.string.txid);
        indexInput = findViewById(R.id.indexInput).findViewById(R.id.textInput);
        indexInput.setHint(R.string.index);
        amountInput = findViewById(R.id.amountInput).findViewById(R.id.textInput);
        amountInput.setHint(R.string.amount);
        redeemScriptInput = findViewById(R.id.redeemScriptInput);

        TextIconsUtils.setupTextIcons(this, R.id.txIdInput, R.id.scanIcon, QR_SCAN_TX_ID_REQUEST_CODE);
        TextIconsUtils.setupTextWithoutIcons(this, R.id.indexInput, R.id.scanIcon);
        TextIconsUtils.setupTextIcons(this, R.id.amountInput, R.id.scanIcon, QR_SCAN_AMOUNT_REQUEST_CODE);

        clearButton = findViewById(R.id.clearButton);
        cancelButton = findViewById(R.id.cancelButton);
        doneButton = findViewById(R.id.doneButton);

        clearButton.setOnClickListener(v -> {
            txIdInput.setText("");
            indexInput.setText("");
            amountInput.setText("");
            redeemScriptInput.setText("");
        });

        cancelButton.setOnClickListener(v -> dismiss());

        doneButton.setOnClickListener(v -> {
            if(txIdInput.getText() == null || indexInput.getText() == null || amountInput.getText() == null){
                Toast.makeText(getContext(), R.string.please_input_all_fields, SafeApplication.TOAST_LASTING).show();
                return;
            }
            if(txIdInput.getText().toString().isEmpty() || indexInput.getText().toString().isEmpty() || amountInput.getText().toString().isEmpty()){

                Toast.makeText(getContext(), R.string.please_input_all_fields , SafeApplication.TOAST_LASTING).show();
                return;
            }
            String txId = txIdInput.getText().toString();
            int index = Integer.parseInt(indexInput.getText().toString());
            double amount = Double.parseDouble(amountInput.getText().toString());

            // Validate amount range
            if (amount < Constants.MIN_AMOUNT || amount > Constants.MAX_AMOUNT) {
                Toast.makeText(getContext(), getContext().getString(R.string.amount_must_be_between, Constants.MIN_AMOUNT, Constants.MAX_AMOUNT), SafeApplication.TOAST_LASTING).show();

                return;
            }

            Cash cash = new Cash(txId, index, amount);

            // Optional redeem script: if the user enters hex, parse it as P2SH and
            // populate redeemScript, lockTime, and owner (FID derived from the script).
            String redeemScriptHex = redeemScriptInput.getText() == null
                    ? "" : redeemScriptInput.getText().toString().trim();
            if (!redeemScriptHex.isEmpty()) {
                if (!Hex.isHexString(redeemScriptHex)) {
                    Toast.makeText(getContext(), R.string.invalid_redeem_script, SafeApplication.TOAST_LASTING).show();
                    return;
                }
                try {
                    P2SH p2sh = new P2SH(redeemScriptHex);
                    cash.setRedeemScript(redeemScriptHex);
                    if (p2sh.getLockTime() != null) {
                        cash.setLockTime(p2sh.getLockTime());
                    }
                    if (p2sh.getFid() != null) {
                        cash.setOwner(p2sh.getFid());
                    }
                    if (p2sh.getType() != null) {
                        cash.setType(p2sh.getType().name());
                    }
                } catch (Exception e) {
                    TimberLogger.e(TAG, "Invalid redeem script: " + e.getMessage());
                    Toast.makeText(getContext(), R.string.invalid_redeem_script, SafeApplication.TOAST_LASTING).show();
                    return;
                }
            }

            rawTxInfo.getInputs().add(cash);

            if (onDoneListener != null) {
                onDoneListener.onDone(cash);
            }
            dismiss();
        });
    }

    public void handleQrScanResult(int requestCode, String qrContent) {
        if (qrContent == null || qrContent.isEmpty()) {
            showToast(getContext().getString(R.string.invalid_qr_code_content));
            return;
        }

        if (getOwnerActivity() != null) {
            getOwnerActivity().runOnUiThread(() -> {
                if (requestCode == QR_SCAN_TX_ID_REQUEST_CODE) {
                    txIdInput.setText(qrContent);
                } else if (requestCode == QR_SCAN_AMOUNT_REQUEST_CODE) {
                    amountInput.setText(qrContent);
                } else if (requestCode == QR_SCAN_TEXT_REQUEST_CODE) {
                    txIdInput.setText(qrContent);
                } else {
                    showToast(getContext().getString(R.string.failed_to_scan_qr_code));
                }
            });
        } else {
            showToast(getContext().getString(R.string.failed_to_scan_qr_code));
        }
    }

    public void setOnDoneListener(OnDoneListener listener) {
        this.onDoneListener = listener;
    }

    private void showToast(String message) {
        ToastUtils.showInfo(getContext(), message);
    }
} 