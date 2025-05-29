package com.fc.safe.home;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.fc.fc_ajdk.data.fcData.SecretDetail;
import com.fc.fc_ajdk.utils.Base32;
import com.fc.safe.R;
import com.fc.safe.db.SecretManager;
import com.fc.safe.ui.DetailActivity;
import com.fc.safe.utils.QRCodeGenerator;
import com.fc.safe.utils.TOTPUtil;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.fc_ajdk.core.crypto.Decryptor;

public class TotpCard extends LinearLayout {
    private TextView titleView;
    private TextView optView;
    private TextView countdownView;
    private ImageButton eyeIcon;
    private ImageButton makeQrIcon;
    private boolean isOptVisible = false;
    private String optValue;
    private SecretDetail secretDetail;

    public TotpCard(Context context, SecretDetail detail, long currentTime) {
        super(context);
        init(context, detail, currentTime);
    }

    public TotpCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        // For XML inflation (not used here)
    }

    private void init(Context context, SecretDetail detail, long currentTime) {
        this.secretDetail = detail;
        LayoutInflater.from(context).inflate(R.layout.item_totp_card, this, true);
        setOrientation(VERTICAL);
        titleView = findViewById(R.id.totpTitle);
        optView = findViewById(R.id.totpOpt);
        countdownView = findViewById(R.id.totpCountdown);
        eyeIcon = findViewById(R.id.eyeIcon);
        makeQrIcon = findViewById(R.id.makeQrIcon);

        titleView.setText(detail.getTitle());
        optValue = generateOpt(detail, currentTime);
        setOptVisible(false);
        updateCountdown(currentTime);

        optView.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("TOTP", optValue);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show();
        });

        eyeIcon.setOnClickListener(v -> {
            setOptVisible(!isOptVisible);
        });

        makeQrIcon.setOnClickListener(v -> {
            QRCodeGenerator.generateAndShowQRCode(context, optValue);
        });

        setOnLongClickListener(v -> {
            showPopupMenu(context);
            return true;
        });
    }

    private String generateOpt(SecretDetail detail, long currentTime) {
        try {
            String content = detail.getContent();
            if (content == null && detail.getContentCipher() != null) {
                byte[] symkey = ConfigureManager.getInstance().getSymkey();
                if (symkey != null) {
                    // Decrypt contentCipher to get content
                    com.fc.fc_ajdk.core.crypto.CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymkey(detail.getContentCipher(), symkey);
                    if (cryptoDataByte.getCode() == null || cryptoDataByte.getCode() == 0) {
                        byte[] data = cryptoDataByte.getData();
                        if (data != null) {
                            content = new String(data);
                        }
                    }
                }
            }
            if (content != null) {
                byte[] secret = Base32.fromBase32(content);
                return TOTPUtil.generateTOTP(secret, currentTime / 1000, 6);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "------";
    }

    private void setOptVisible(boolean visible) {
        isOptVisible = visible;
        if (visible) {
            optView.setText(optValue);
            optView.setTypeface(optView.getTypeface(), Typeface.BOLD);
            eyeIcon.setImageResource(R.drawable.ic_visibility_on);
        } else {
            optView.setText("••••••");
            optView.setTypeface(optView.getTypeface(), Typeface.NORMAL);
            eyeIcon.setImageResource(R.drawable.ic_visibility_off);
        }
    }

    private void showPopupMenu(Context context) {
        PopupMenu popup = new PopupMenu(context, this);
        popup.getMenu().add(0, 1, 0, context.getString(R.string.delete));
        popup.getMenu().add(0, 2, 1, R.string.show_detail);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                // Delete
                ((LinearLayout) getParent()).removeView(this);
                SecretManager.getInstance(context.getApplicationContext()).removeSecretDetail(secretDetail);
                SecretManager.getInstance(context.getApplicationContext()).commit();
                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show();
                return true;
            } else if (item.getItemId() == 2) {
                // Show Detail: launch DetailActivity with secretDetail
                Intent intent = new Intent(context, DetailActivity.class);
                intent.putExtra(DetailActivity.EXTRA_ENTITY_JSON, secretDetail.toJson());
                intent.putExtra(DetailActivity.EXTRA_ENTITY_CLASS, secretDetail.getClass().getName());
                context.startActivity(intent);
                return true;
            }
            return false;
        });
        popup.show();
    }

    public void refreshTotp(long currentTime) {
        refreshPassword(currentTime);
        refreshCountdown(currentTime);
    }

    public void refreshCountdown(long currentTime) {
        updateCountdown(currentTime);
    }

    public void refreshPassword(long currentTime) {
        optValue = generateOpt(secretDetail, currentTime);
        if (isOptVisible) {
            optView.setText(optValue);
        }
    }

    private void updateCountdown(long currentTime) {
        int seconds = (int) ((currentTime / 1000) % 30);
        int countdown = 30 - seconds;
        countdownView.setText(String.valueOf(countdown));
    }
} 