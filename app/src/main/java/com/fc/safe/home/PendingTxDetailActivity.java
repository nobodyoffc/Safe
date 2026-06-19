package com.fc.safe.home;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.PendingTx;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.safe.R;
import com.fc.safe.db.PendingTxManager;
import com.fc.safe.utils.ToastUtils;

import java.util.Date;

public class PendingTxDetailActivity extends BaseCryptoActivity {

    private LinearLayout container;
    private Button copyButton;
    private Button qrButton;
    private Button confirmButton;
    private Button cancelButton;
    private LinearLayout actionRow;

    private PendingTxManager pendingMgr;
    private PendingTx pendingTx;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_pending_tx_detail;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.pending_tx);
    }

    @Override
    protected void initializeViews() {
        container = findViewById(R.id.pending_tx_detail_container);
        copyButton = findViewById(R.id.pending_tx_copy_button);
        qrButton = findViewById(R.id.pending_tx_qr_button);
        confirmButton = findViewById(R.id.pending_tx_confirm_button);
        cancelButton = findViewById(R.id.pending_tx_cancel_button);
        actionRow = findViewById(R.id.pending_tx_action_row);

        pendingMgr = PendingTxManager.getInstance(this);
        String pendingId = getIntent().getStringExtra(PendingTxListActivity.EXTRA_PENDING_ID);
        pendingTx = pendingId != null ? pendingMgr.getById(pendingId) : null;
        if (pendingTx == null) {
            ToastUtils.showError(this, "PendingTx not found");
            finish();
            return;
        }

        renderDetail();
    }

    @Override
    protected void setupButtons() {
        copyButton.setOnClickListener(v -> {
            String payload = exportPayload();
            if (payload == null) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("pending_tx", payload));
            ToastUtils.showInfo(this, getString(R.string.copied_to_clipboard));
        });

        qrButton.setOnClickListener(v -> {
            String payload = exportPayload();
            if (payload == null) return;
            handleQrGeneration(payload);
        });

        confirmButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.pending_tx_confirm_broadcast_prompt)
                    .setPositiveButton(R.string.pending_tx_confirm_broadcast, (d, w) -> {
                        pendingMgr.markConfirmed(this, pendingTx);
                        ToastUtils.showInfo(this, getString(R.string.pending_tx_confirmed_done));
                        finish();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });

        cancelButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.pending_tx_cancel_prompt)
                    .setPositiveButton(R.string.pending_tx_mark_canceled, (d, w) -> {
                        pendingMgr.markCanceled(this, pendingTx);
                        ToastUtils.showInfo(this, getString(R.string.pending_tx_canceled_done));
                        finish();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    /** Signed hex if fully built; otherwise the stored RawTxInfo JSON (useful to continue multisig signing). */
    private String exportPayload() {
        if (pendingTx.getSignedTxHex() != null) return pendingTx.getSignedTxHex();
        return pendingTx.getRawTxInfoJson();
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not used.
    }

    private void renderDetail() {
        container.removeAllViews();

        addLabeled("Status", statusLabel());
        addLabeled(getString(R.string.pending_tx_on_chain_id),
                pendingTx.getOnChainTxId() != null ? pendingTx.getOnChainTxId() : getString(R.string.pending_tx_none_yet));

        if (pendingTx.getOwnerFid() != null) {
            addLabeled("Sender", pendingTx.getOwnerFid());
        }
        if (pendingTx.getCreatedAt() != null) {
            addLabeled(getString(R.string.pending_tx_created_at),
                    DateFormat.format("yyyy-MM-dd HH:mm", new Date(pendingTx.getCreatedAt() * 1000L)).toString());
        }
        if (pendingTx.getUpdatedAt() != null) {
            addLabeled(getString(R.string.pending_tx_updated_at),
                    DateFormat.format("yyyy-MM-dd HH:mm", new Date(pendingTx.getUpdatedAt() * 1000L)).toString());
        }

        // Decoded input/output summary (from RawTxInfo)
        RawTxInfo info = null;
        if (pendingTx.getRawTxInfoJson() != null) {
            try {
                info = RawTxInfo.fromJson(pendingTx.getRawTxInfoJson(), RawTxInfo.class);
            } catch (Exception e) {
                // ignore — detail view degrades gracefully
            }
        }
        if (info != null) {
            if (info.getInputs() != null && !info.getInputs().isEmpty()) {
                addSectionLabel("Spending");
                long sum = 0L;
                for (Cash in : info.getInputs()) {
                    String id = in.getId();
                    if (id == null) id = in.makeId();
                    addCashLine(id, in.getValue() == null ? 0L : in.getValue());
                    if (in.getValue() != null) sum += in.getValue();
                }
                addLabeled("Sum", FchUtils.satoshiToCoin(sum) + " " + getString(R.string.currency_fch));
            }
            if (info.getOutputs() != null && !info.getOutputs().isEmpty()) {
                addSectionLabel(getString(R.string.send_to));
                for (Cash out : info.getOutputs()) {
                    String owner = out.getOwner() != null ? StringUtils.omitMiddle(out.getOwner(), 21) : "(unknown)";
                    long value = out.getValue() == null ? 0L : out.getValue();
                    addCashLine(owner, value);
                }
            }
        }

        // Hide action buttons for terminal records.
        if (!pendingTx.isPending()) {
            actionRow.setVisibility(View.GONE);
        } else {
            actionRow.setVisibility(View.VISIBLE);
        }
    }

    private String statusLabel() {
        if (pendingTx.isConfirmed()) return getString(R.string.pending_tx_status_confirmed);
        if (pendingTx.isCanceled()) return getString(R.string.pending_tx_status_canceled);
        return getString(R.string.pending_tx_status_pending);
    }

    private void addLabeled(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 12, 0, 12);

        TextView fieldName = new TextView(this);
        fieldName.setText(label + ": ");
        fieldName.setTextColor(getResources().getColor(R.color.field_name, getTheme()));
        fieldName.setTypeface(null, Typeface.BOLD);

        TextView fieldValue = new TextView(this);
        fieldValue.setText(value);
        fieldValue.setTextColor(getResources().getColor(R.color.text_color, getTheme()));

        row.addView(fieldName);
        row.addView(fieldValue);
        container.addView(row);
    }

    private void addSectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text + ":");
        label.setTextColor(getResources().getColor(R.color.field_name, getTheme()));
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, 24, 0, 8);
        container.addView(label);
    }

    private void addCashLine(String id, long value) {
        TextView tv = new TextView(this);
        tv.setText("\t" + StringUtils.omitMiddle(id, 21) + ": " + FchUtils.satoshiToCoin(value));
        tv.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
        tv.setPadding(0, 6, 0, 6);
        container.addView(tv);
    }
}
