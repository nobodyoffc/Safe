package com.fc.safe.tx.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.Multisig;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.MultisignManager;
import com.fc.safe.utils.TextIconsUtils;
import com.google.android.material.textfield.TextInputEditText;

import java.text.DecimalFormat;
import java.util.Locale;
import com.fc.safe.utils.ToastUtils;

public class AddTxOutputDialog extends Dialog {
    private static final String TAG = "AddTxOutputDialog";
    private TextInputEditText fidInput;
    private TextInputEditText amountInput;
    private TextInputEditText lockHeightInput;
    private TextView maxText;
    private Button clearButton;
    private Button cancelButton;
    private Button doneButton;
    private final RawTxInfo rawTxInfo;
    private final long rest;
    private OnDoneListener onDoneListener;

    private static final int QR_SCAN_FID_REQUEST_CODE = 1003;
    private static final int QR_SCAN_AMOUNT_REQUEST_CODE = 1004;

    public interface OnDoneListener {
        void onDone(Cash sendTo);
    }

    public AddTxOutputDialog(@NonNull Context context, RawTxInfo rawTxInfo, long rest) {
        super(context);
        this.rawTxInfo = rawTxInfo;
        this.rest = rest - 34;
        if (context instanceof android.app.Activity) {
            setOwnerActivity((android.app.Activity) context);
        } else {
            TimberLogger.e(TAG, "Context is not an Activity");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_add_tx_output);

        fidInput = findViewById(R.id.fidInput).findViewById(R.id.textInput);
        fidInput.setHint(R.string.fid);
        amountInput = findViewById(R.id.amountInput).findViewById(R.id.textInput);
        amountInput.setHint(R.string.amount);
        lockHeightInput = findViewById(R.id.lockHeightInput);
        maxText = findViewById(R.id.maxText);

        // Set up MaxText
        DecimalFormat df = new DecimalFormat("0.########");
        df.setDecimalSeparatorAlwaysShown(false);
        String maxTextStr = String.format(Locale.US, "Max: %s %s", df.format(FchUtils.satoshiToCoin(rest)), getContext().getString(R.string.currency_fch));
        maxText.setText(maxTextStr);
        maxText.setOnClickListener(v -> {
            amountInput.setText(df.format(FchUtils.satoshiToCoin(rest)));
        });

        TextIconsUtils.setupTextIcons(this, R.id.fidInput, R.id.scanIcon, QR_SCAN_FID_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.amountInput, R.id.scanIcon, QR_SCAN_AMOUNT_REQUEST_CODE);

        clearButton = findViewById(R.id.clearButton);
        cancelButton = findViewById(R.id.cancelButton);
        doneButton = findViewById(R.id.doneButton);

        clearButton.setOnClickListener(v -> {
            fidInput.setText("");
            amountInput.setText("");
            lockHeightInput.setText("");
        });

        cancelButton.setOnClickListener(v -> dismiss());

        doneButton.setOnClickListener(v -> {
            if(fidInput.getText() == null || amountInput.getText() == null){
                Toast.makeText(getContext(), R.string.please_input_all_fields , SafeApplication.TOAST_LASTING).show();
                return;
            }
            if(fidInput.getText().toString().isEmpty() || amountInput.getText().toString().isEmpty()){
                Toast.makeText(getContext(), R.string.please_input_all_fields, SafeApplication.TOAST_LASTING).show();
                return;
            }
            String fid = fidInput.getText().toString();
            if (!KeyTools.isGoodFid(fid)) {
                Toast.makeText(getContext(), R.string.invalid_fid, SafeApplication.TOAST_LASTING).show();
                return;
            }

            double amount = Double.parseDouble(amountInput.getText().toString());
            
            // Validate amount range
            if (amount < Constants.MIN_AMOUNT || amount > Constants.MAX_AMOUNT) {
                Toast.makeText(getContext(), getContext().getString(R.string.amount_must_be_between, Constants.MIN_AMOUNT, Constants.MAX_AMOUNT), SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Validate against rest
            if (amount > FchUtils.satoshiToCoin(rest)) {
                Toast.makeText(getContext(), R.string.total_amount_exceeds_available_balance, SafeApplication.TOAST_LASTING).show();
                return;
            }

            Long lockTime = parseLockHeight();
            if (lockTime != null && lockTime < 0) {
                Toast.makeText(getContext(), R.string.invalid_lock_height, SafeApplication.TOAST_LASTING).show();
                return;
            }
            Cash sendTo;
            if (lockTime != null && lockTime > 0) {
                // CLTV output. For multisig targets ("3..." FID) we need the multisig's
                // pubkeys/m/n to build the combined CLTV+multisig redeemScript.
                if (fid.startsWith("3")) {
                    Multisig target = MultisignManager.getInstance(getContext()).getMultisignById(fid);
                    if (target == null) {
                        Toast.makeText(getContext(),
                                getContext().getString(R.string.multisig_not_found_for_cltv_output, fid),
                                SafeApplication.TOAST_LASTING).show();
                        return;
                    }
                    sendTo = new Cash(fid, amount, lockTime, target);
                } else {
                    sendTo = new Cash(fid, amount, lockTime);
                }
            } else {
                // No lockTime: plain P2PKH (or plain P2SH if fid is "3..."; TxHandler handles both
                // via Address.fromBase58 on the output side).
                sendTo = new Cash(fid, amount);
            }
            rawTxInfo.getOutputs().add(sendTo);

            if (onDoneListener != null) {
                onDoneListener.onDone(sendTo);
            }

            dismiss();
        });
    }

    public void handleQrScanResult(int requestCode, String qrContent) {
        if (qrContent == null || qrContent.isEmpty()) {
            showToast(getContext().getString(R.string.invalid_qr_code_content));
            return;
        }

        if (requestCode == QR_SCAN_FID_REQUEST_CODE) {
            if (getOwnerActivity() != null) {
                getOwnerActivity().runOnUiThread(() -> {
                    fidInput.setText(qrContent);
                });
            } else {
                showToast(getContext().getString(R.string.failed_to_scan_qr_code));
            }
        } else if (requestCode == QR_SCAN_AMOUNT_REQUEST_CODE) {
            if (getOwnerActivity() != null) {
                getOwnerActivity().runOnUiThread(() -> {
                    amountInput.setText(qrContent);
                });
            } else {
                showToast(getContext().getString(R.string.failed_to_scan_qr_code));
            }
        } else {
            showToast(getContext().getString(R.string.failed_to_scan_qr_code));
        }
    }

    private void showToast(String message) {
        ToastUtils.showInfo(getContext(), message);
    }

    private Long parseLockHeight() {
        if (lockHeightInput == null || lockHeightInput.getText() == null) return null;
        String text = lockHeightInput.getText().toString().trim();
        if (text.isEmpty()) return null;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public void setOnDoneListener(OnDoneListener listener) {
        this.onDoneListener = listener;
    }
} 