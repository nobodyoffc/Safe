package com.fc.safe.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.fc.safe.R;

public class WaitingDialog extends Dialog {
    private String hint;
    private TextView hintTextView;
    private ProgressBar progressBar;

    public WaitingDialog(@NonNull Context context, String hint) {
        super(context);
        this.hint = hint;
        setCancelable(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_waiting);
        hintTextView = findViewById(R.id.hintTextView);
        progressBar = findViewById(R.id.progressBar);
        if (hint != null) {
            hintTextView.setText(hint);
        }
    }

    public void setHint(String hint) {
        this.hint = hint;
        if (hintTextView != null) {
            hintTextView.setText(hint);
        }
    }
} 