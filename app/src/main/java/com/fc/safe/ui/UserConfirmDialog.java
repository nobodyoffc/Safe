package com.fc.safe.ui;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;

import com.fc.safe.R;

public class UserConfirmDialog extends Dialog {
    public enum Choice {
        STOP, NO, YES
    }

    public interface OnChoiceListener {
        void onChoice(Choice choice);
    }

    public UserConfirmDialog(Context context, String prompt, OnChoiceListener listener) {
        this(context, prompt, listener, false);
    }

    public UserConfirmDialog(Context context, String prompt, OnChoiceListener listener, boolean isWarning) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_user_confirm);
        setCancelable(false);

        TextView promptTextView = findViewById(R.id.promptTextView);
        Button stopButton = findViewById(R.id.stopButton);
        Button noButton = findViewById(R.id.noButton);
        Button yesButton = findViewById(R.id.yesButton);
        ImageView warningIcon = findViewById(R.id.warningIcon);

        promptTextView.setText(prompt);
        warningIcon.setVisibility(isWarning ? View.VISIBLE : View.GONE);

        stopButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onChoice(Choice.STOP);
        });
        noButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onChoice(Choice.NO);
        });
        yesButton.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onChoice(Choice.YES);
        });
    }
} 