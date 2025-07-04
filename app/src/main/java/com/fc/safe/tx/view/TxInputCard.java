package com.fc.safe.tx.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.NumberUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.safe.R;

public class TxInputCard extends CardView {
    private TextView txIdText;
    private TextView indexText;
    private TextView amountText;
    private ImageButton deleteButton;
    private Cash cash;
    private OnDeleteListener onDeleteListener;

    public interface OnDeleteListener {
        void onDelete(TxInputCard card);
    }

    public TxInputCard(Context context) {
        super(context);
        init(context);
    }

    public TxInputCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TxInputCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.layout_tx_input_card, this, true);
        
        // Set CardView background to transparent to remove the default white background
        setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
        
        // Remove shadow by setting elevation to 0
        setCardElevation(0f);
        
        txIdText = findViewById(R.id.txIdText);
        indexText = findViewById(R.id.indexText);
        amountText = findViewById(R.id.amountText);
        deleteButton = findViewById(R.id.deleteButton);

        // Setup copy functionality for TxId
        txIdText.setOnClickListener(v -> {
            if (cash != null) {
                copyToClipboard(context, getContext().getString(R.string.txid), cash.getBirthTxId());
            }
        });

        // Setup copy functionality for Amount
        amountText.setOnClickListener(v -> {
            if (cash != null) {
                String amount = NumberUtils.formatAmount(FchUtils.satoshiToCash(cash.getValue()));
                copyToClipboard(context, getContext().getString(R.string.amount), amount);
            }
        });

        deleteButton.setOnClickListener(v -> {
            if (onDeleteListener != null) {
                onDeleteListener.onDelete(this);
            }
        });
    }

    private void copyToClipboard(Context context, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    public void setCash(Cash cash) {
        this.cash = cash;
        txIdText.setText(StringUtils.omitMiddle(cash.getBirthTxId(), 13));
        indexText.setText(String.valueOf(cash.getBirthIndex()));
        amountText.setText(NumberUtils.formatAmount(FchUtils.satoshiToCoin(cash.getValue())) + " F");
    }

    public Cash getCash() {
        return cash;
    }

    public void setOnDeleteListener(OnDeleteListener listener) {
        this.onDeleteListener = listener;
    }
} 