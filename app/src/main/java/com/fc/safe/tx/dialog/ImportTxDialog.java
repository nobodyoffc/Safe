package com.fc.safe.tx.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;

import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.safe.R;


public class ImportTxDialog extends Dialog {
    private EditText jsonInput;
    private Button clearButton;
    private Button cancelButton;
    private Button doneButton;
    private OnDoneListener onDoneListener;

    public interface OnDoneListener {
        void onDone(RawTxInfo rawTxInfo);
    }

    public ImportTxDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_import_tx);

        jsonInput = findViewById(R.id.jsonInput);
        clearButton = findViewById(R.id.clearButton);
        cancelButton = findViewById(R.id.cancelButton);
        doneButton = findViewById(R.id.doneButton);

        clearButton.setOnClickListener(v -> jsonInput.setText(""));

        cancelButton.setOnClickListener(v -> dismiss());

        doneButton.setOnClickListener(v -> {
            String jsonText = jsonInput.getText().toString();
            if (!JsonUtils.isJson(jsonText)) {
                // TODO: Show error message
                return;
            }

            RawTxInfo rawTxInfo = RawTxInfo.fromString(jsonText);
            if (rawTxInfo == null) {
                // TODO: Show error message
                return;
            }

            if (onDoneListener != null) {
                onDoneListener.onDone(rawTxInfo);
            }

            dismiss();
        });
    }

    public void setOnDoneListener(OnDoneListener listener) {
        this.onDoneListener = listener;
    }
} 