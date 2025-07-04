package com.fc.safe.tx.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.utils.TextIconsUtils;
import com.google.android.material.textfield.TextInputEditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CreateCashDialog extends Dialog {
    private static final String TAG = "CreateCashDialog";
    private TextInputEditText txIdInput;
    private TextInputEditText indexInput;
    private TextInputEditText amountInput;
    private TextInputEditText ownerInput;
    private TextInputEditText birthTimeInput;
    private Button clearButton;
    private Button cancelButton;
    private Button doneButton;
    private OnDoneListener onDoneListener;

    private static final int QR_SCAN_TX_ID_REQUEST_CODE = 1001;
    private static final int QR_SCAN_AMOUNT_REQUEST_CODE = 1002;
    private static final int QR_SCAN_OWNER_REQUEST_CODE = 1003;
    private static final int QR_SCAN_TEXT_REQUEST_CODE = 1004;

    public interface OnDoneListener {
        void onDone(Cash cash);
    }

    public CreateCashDialog(@NonNull Context context) {
        super(context);
        if (context instanceof android.app.Activity) {
            setOwnerActivity((android.app.Activity) context);
        } else {
            TimberLogger.e(TAG, "Context is not an Activity");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_create_cash);

        txIdInput = findViewById(R.id.txIdInput).findViewById(R.id.textInput);
        txIdInput.setHint(R.string.txid);
        indexInput = findViewById(R.id.indexInput).findViewById(R.id.textInput);
        indexInput.setHint(R.string.index);
        amountInput = findViewById(R.id.amountInput).findViewById(R.id.textInput);
        amountInput.setHint(R.string.amount);
        ownerInput = findViewById(R.id.ownerInput).findViewById(R.id.textInput);
        ownerInput.setHint(R.string.owner);
        birthTimeInput = findViewById(R.id.birthTimeInput).findViewById(R.id.textInput);
        birthTimeInput.setHint(R.string.birth_time_hint);

        TextIconsUtils.setupTextIcons(this, R.id.txIdInput, R.id.scanIcon, QR_SCAN_TX_ID_REQUEST_CODE);
        TextIconsUtils.setupTextWithoutIcons(this, R.id.indexInput, R.id.scanIcon);
        TextIconsUtils.setupTextIcons(this, R.id.amountInput, R.id.scanIcon, QR_SCAN_AMOUNT_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.ownerInput, R.id.scanIcon, QR_SCAN_OWNER_REQUEST_CODE);
        TextIconsUtils.setupTextWithoutIcons(this, R.id.birthTimeInput, R.id.scanIcon);

        clearButton = findViewById(R.id.clearButton);
        cancelButton = findViewById(R.id.cancelButton);
        doneButton = findViewById(R.id.doneButton);

        clearButton.setOnClickListener(v -> {
            txIdInput.setText("");
            indexInput.setText("");
            amountInput.setText("");
            ownerInput.setText("");
            birthTimeInput.setText("");
        });

        cancelButton.setOnClickListener(v -> dismiss());

        doneButton.setOnClickListener(v -> {
            if(txIdInput.getText() == null || indexInput.getText() == null || 
               amountInput.getText() == null || ownerInput.getText() == null || 
               birthTimeInput.getText() == null){
                Toast.makeText(getContext(), R.string.please_input_all_fields, SafeApplication.TOAST_LASTING).show();
                return;
            }
            if(txIdInput.getText().toString().isEmpty() || indexInput.getText().toString().isEmpty() || 
               amountInput.getText().toString().isEmpty() || ownerInput.getText().toString().isEmpty() || 
               birthTimeInput.getText().toString().isEmpty()){
                Toast.makeText(getContext(), R.string.please_input_all_fields, SafeApplication.TOAST_LASTING).show();
                return;
            }
            
            String txId = txIdInput.getText().toString();
            int index = Integer.parseInt(indexInput.getText().toString());
            double amount = Double.parseDouble(amountInput.getText().toString());
            String owner = ownerInput.getText().toString();
            String birthTimeStr = birthTimeInput.getText().toString();

            // Validate amount range
            if (amount < Constants.MIN_AMOUNT || amount > Constants.MAX_AMOUNT) {
                Toast.makeText(getContext(), getContext().getString(R.string.amount_must_be_between, Constants.MIN_AMOUNT, Constants.MAX_AMOUNT), SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Convert date string to timestamp
            Long birthTime = convertDateToTimestamp(birthTimeStr);
            if (birthTime == null) {
                Toast.makeText(getContext(), R.string.invalid_date_format_please_use_yyyy_mm_dd, SafeApplication.TOAST_LASTING).show();
                return;
            }

            Cash cash = new Cash(txId, index, amount);
            cash.setOwner(owner);
            cash.setBirthTime(birthTime);
            cash.setValid(true);

            // If birthTime is not null, calculate CD
            if (birthTime != null) {
                cash.makeCd();
            }

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
                } else if (requestCode == QR_SCAN_OWNER_REQUEST_CODE) {
                    ownerInput.setText(qrContent);
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
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Convert date string in YYYY-MM-DD HH:MM:SS format to 10-digit timestamp
     * @param dateStr Date string in YYYY-MM-DD HH:MM:SS format
     * @return 10-digit timestamp or null if parsing fails
     */
    private Long convertDateToTimestamp(String dateStr) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            // Use system default timezone for consistent behavior
            dateFormat.setTimeZone(java.util.TimeZone.getDefault());
            Date date = dateFormat.parse(dateStr);
            if (date != null) {
                // Convert to 10-digit timestamp (seconds since epoch)
                return date.getTime() / 1000;
            }
        } catch (ParseException e) {
            TimberLogger.e(TAG, "Error parsing date: %s", e.getMessage());
        }
        return null;
    }
} 