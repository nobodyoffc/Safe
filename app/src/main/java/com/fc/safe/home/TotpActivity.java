package com.fc.safe.home;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fc.fc_ajdk.data.feipData.Secret;
import com.fc.safe.R;
import com.fc.safe.db.SecretManager;
import com.fc.safe.secret.ImportTotpActivity;
import com.fc.safe.utils.ToastUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

public class TotpActivity extends BaseCryptoActivity {
    private TextView currentTimeText;
    private LinearLayout totpCardList;
    private Handler timeHandler;
    private Runnable timeRunnable;
    private List<TotpCard> totpCards = new ArrayList<>();
    private int lastCountdown = -1;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_totp;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.menu_totp);
    }

    @Override
    protected void initializeViews() {
        currentTimeText = findViewById(R.id.currentTimeText);
        totpCardList = findViewById(R.id.totpCardList);
        setupTimeUpdater();
        loadTotpCards();
    }

    @Override
    protected void setupButtons() {
        Button importButton = findViewById(R.id.importButton);
        Button createButton = findViewById(R.id.createButton);
        Button doneButton = findViewById(R.id.doneButton);
        importButton.setOnClickListener(v -> {
            Intent intent = new Intent(TotpActivity.this, ImportTotpActivity.class);
            intent.putExtra("type", "TOTP");
            startActivity(intent);
        });
        createButton.setOnClickListener(v -> {
            Intent intent = new Intent(TotpActivity.this, com.fc.safe.myKeys.CreateSecretActivity.class);
            intent.putExtra("type", "TOTP");
            startActivity(intent);
        });
        doneButton.setOnClickListener(v -> finish());
    }

    private void setupTimeUpdater() {
        timeHandler = new Handler(Looper.getMainLooper());
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(now));
                currentTimeText.setText(time);
                int seconds = (int) ((now / 1000) % 30);
                int countdown = 30 - seconds;
                for (TotpCard card : totpCards) {
                    card.refreshCountdown(now);
                }
                if (countdown == 30 || lastCountdown == 1) { // countdown resets to 30, or just passed 0
                    for (TotpCard card : totpCards) {
                        card.refreshPassword(now);
                    }
                }
                lastCountdown = countdown;
                timeHandler.postDelayed(this, 1000);
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void loadTotpCards() {
        totpCardList.removeAllViews();
        totpCards.clear();
        List<Secret> allSecrets = SecretManager.getInstance(this).getAllSecretDetailList();
        if(allSecrets == null) {
            ToastUtils.showWarning(this, getString(R.string.no_totp_found));
            return;
        }
        for (Secret detail : allSecrets) {
            if ("TOTP".equalsIgnoreCase(detail.getType())) {
                addTotpCard(detail);
            }
        }
    }

    private void addTotpCard(Secret detail) {
        TotpCard card = new TotpCard(this, detail, System.currentTimeMillis());
        totpCardList.addView(card);
        totpCards.add(card);
    }

    @Override
    protected void onDestroy() {
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
        super.onDestroy();
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not used in this activity
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTotpCards();
    }
} 