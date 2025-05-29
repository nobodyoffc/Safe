package com.fc.safe.ui;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ImageView;

import com.fc.safe.R;

public class RemindDialog extends Dialog {
    private OnDismissListener dismissListener;

    public RemindDialog(Context context, String prompt) {
        this(context, prompt, false);
    }

    public RemindDialog(Context context, String prompt, boolean isWarning) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_user_confirm);
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        TextView promptTextView = findViewById(R.id.promptTextView);
        Button okButton = findViewById(R.id.yesButton);
        Button stopButton = findViewById(R.id.stopButton);
        Button noButton = findViewById(R.id.noButton);
        ImageView warningIcon = findViewById(R.id.warningIcon);

        promptTextView.setText(prompt);
        warningIcon.setVisibility(isWarning ? View.VISIBLE : View.GONE);
        okButton.setText(R.string.ok);
        
        // Hide Stop and No buttons
        stopButton.setVisibility(View.GONE);
        noButton.setVisibility(View.GONE);

        okButton.setOnClickListener(v -> {
            if (dismissListener != null) {
                dismissListener.onDismiss(this);
            }
            dismiss();
        });
    }

    @Override
    public void setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        super.setOnDismissListener(listener);
    }
} 