package com.fc.safe.tx.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.data.fchData.SendTo;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class AddOutputFromFidListDialog extends Dialog {
    private static final String TAG = "AddOutputFromFidListDialog";
    private TextInputEditText amountInput;
    private Button cancelButton;
    private Button addButton;
    private final RawTxInfo rawTxInfo;
    private final long rest;
    private OnDoneListener onDoneListener;

    public interface OnDoneListener {
        void onDone(List<SendTo> sendToList);
    }

    public AddOutputFromFidListDialog(@NonNull Context context, RawTxInfo rawTxInfo, long rest) {
        super(context);
        this.rawTxInfo = rawTxInfo;
        this.rest = rest-34;
        if (context instanceof android.app.Activity) {
            setOwnerActivity((android.app.Activity) context);
        } else {
            TimberLogger.e(TAG, "Context is not an Activity");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_add_output_from_fid_list);

        amountInput = findViewById(R.id.amountInput);
        cancelButton = findViewById(R.id.cancelButton);
        addButton = findViewById(R.id.addButton);

        cancelButton.setOnClickListener(v -> dismiss());

        addButton.setOnClickListener(v -> {
            if (amountInput.getText() == null || amountInput.getText().toString().isEmpty()) {
                Toast.makeText(getContext(), R.string.please_input_amount, SafeApplication.TOAST_LASTING).show();
                return;
            }

            double amount = Double.parseDouble(amountInput.getText().toString());
            
            // Validate amount range
            if (amount < Constants.MIN_AMOUNT || amount > Constants.MAX_AMOUNT) {
                Toast.makeText(getContext(), getContext().getString(R.string.amount_must_be_between, Constants.MIN_AMOUNT, Constants.MAX_AMOUNT), SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Get FID list
            List<String> fidList = SafeApplication.getFidList();
            if (fidList.isEmpty()) {
                Toast.makeText(getContext(), R.string.fid_list_is_empty, SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Validate total amount against rest
            double totalAmount = amount * fidList.size();
            if (totalAmount > FchUtils.satoshiToCoin(rest)) {
                Toast.makeText(getContext(), R.string.total_amount_exceeds_available_balance, SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Create SendTo list
            List<SendTo> sendToList = new java.util.ArrayList<>();
            for (String fid : fidList) {
                SendTo sendTo = new SendTo(fid, amount);
                sendToList.add(sendTo);
                rawTxInfo.getOutputs().add(sendTo);
            }

            if (onDoneListener != null) {
                onDoneListener.onDone(sendToList);
            }

            dismiss();
        });
    }

    public void setOnDoneListener(OnDoneListener listener) {
        this.onDoneListener = listener;
    }
} 