package com.fc.safe.tx.view;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.safe.R;

public class CashAmountCard extends CardView {
    private TextView cashIdText;
    private TextView amountText;
    private String cashId;
    private String amount;

    public CashAmountCard(Context context) {
        super(context);
        init(context);
    }

    public CashAmountCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CashAmountCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.layout_cash_amount_card, this, true);
        cashIdText = findViewById(R.id.cashIdText);
        amountText = findViewById(R.id.amountText);

        // Setup copy functionality for CashId
        cashIdText.setOnClickListener(v -> {
            if (cashId != null) {
                copyToClipboard(context, getContext().getString(R.string.cash), cashId);
            }
        });

        // Setup copy functionality for Amount
        amountText.setOnClickListener(v -> {
            if (amount != null) {
                copyToClipboard(context, getContext().getString(R.string.amount), amount);
            }
        });
    }

    private void copyToClipboard(Context context, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    public void setCashId(String cashId) {
        this.cashId = cashId;
        cashIdText.setText(getContext().getString(R.string.cash) + ": " + StringUtils.omitMiddle(cashId, 13));
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }
}