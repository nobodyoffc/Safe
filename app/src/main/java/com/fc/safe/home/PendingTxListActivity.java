package com.fc.safe.home;

import android.content.Intent;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fc.fc_ajdk.data.fchData.PendingTx;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.safe.R;
import com.fc.safe.db.PendingTxManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PendingTxListActivity extends BaseCryptoActivity {
    public static final String EXTRA_PENDING_ID = "extra_pending_id";

    private LinearLayout container;
    private TextView emptyText;
    private PendingTxManager pendingMgr;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_pending_tx_list;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.pending_tx);
    }

    @Override
    protected void initializeViews() {
        container = findViewById(R.id.pending_tx_list_container);
        emptyText = findViewById(R.id.pending_tx_empty_text);
        pendingMgr = PendingTxManager.getInstance(this);
        renderList();
    }

    @Override
    protected void setupButtons() {
        // No action bar buttons; list rows are self-clickable.
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not used.
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderList();
    }

    private void renderList() {
        container.removeAllViews();

        List<PendingTx> all = new ArrayList<>();
        all.addAll(pendingMgr.getPendingList());
        all.addAll(pendingMgr.getArchivedList());

        if (all.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            return;
        }
        emptyText.setVisibility(View.GONE);

        for (PendingTx pendingTx : all) {
            container.addView(buildRow(pendingTx));
        }
    }

    private View buildRow(PendingTx pendingTx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(16, 16, 16, 16);
        row.setBackgroundResource(R.drawable.card_background_outlined);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, 16);
        row.setLayoutParams(rowParams);

        // Top line: status + type
        TextView topLine = new TextView(this);
        topLine.setTypeface(null, Typeface.BOLD);
        topLine.setTextColor(getResources().getColor(statusColor(pendingTx), getTheme()));
        topLine.setText(statusLabel(pendingTx) + (Boolean.TRUE.equals(pendingTx.getMultisig()) ? "  •  multisig" : ""));
        row.addView(topLine);

        // TX identifier: onChainTxId if available, else "(partial multisig — not yet built)"
        TextView idLine = new TextView(this);
        idLine.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
        if (pendingTx.getOnChainTxId() != null) {
            idLine.setText(StringUtils.omitMiddle(pendingTx.getOnChainTxId(), 21));
        } else {
            idLine.setText(getString(R.string.pending_tx_partial_multisig));
            idLine.setTextColor(getResources().getColor(R.color.field_name, getTheme()));
        }
        row.addView(idLine);

        // Sender / updatedAt line
        TextView metaLine = new TextView(this);
        metaLine.setTextColor(getResources().getColor(R.color.field_name, getTheme()));
        StringBuilder meta = new StringBuilder();
        if (pendingTx.getOwnerFid() != null) {
            meta.append(StringUtils.omitMiddle(pendingTx.getOwnerFid(), 17));
        }
        if (pendingTx.getUpdatedAt() != null) {
            if (meta.length() > 0) meta.append("  •  ");
            meta.append(DateFormat.format("yyyy-MM-dd HH:mm", new Date(pendingTx.getUpdatedAt() * 1000L)));
        }
        metaLine.setText(meta.toString());
        row.addView(metaLine);

        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, PendingTxDetailActivity.class);
            intent.putExtra(EXTRA_PENDING_ID, pendingTx.getPendingId());
            startActivity(intent);
        });

        return row;
    }

    private String statusLabel(PendingTx pendingTx) {
        if (pendingTx.isConfirmed()) return getString(R.string.pending_tx_status_confirmed);
        if (pendingTx.isCanceled()) return getString(R.string.pending_tx_status_canceled);
        return getString(R.string.pending_tx_status_pending);
    }

    private int statusColor(PendingTx pendingTx) {
        if (pendingTx.isConfirmed()) return R.color.text_color;
        if (pendingTx.isCanceled()) return R.color.field_name;
        return R.color.field_name; // pending — will highlight via bold only
    }
}
