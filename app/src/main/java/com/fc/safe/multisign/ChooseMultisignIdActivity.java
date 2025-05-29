package com.fc.safe.multisign;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fc.fc_ajdk.data.fchData.P2SH;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.MultisignManager;
import com.fc.safe.home.BaseCryptoActivity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChooseMultisignIdActivity extends BaseCryptoActivity {
    private static final String TAG = "ChooseMultisignIdActivity";
    private static final String EXTRA_SELECTED_P2SH = "selected_p2sh";
    private static final String EXTRA_IS_SINGLE_CHOICE = "is_single_choice";
    private static final int PAGE_SIZE = 20;

    private LinearLayout keyListContainer;
    private MultisignKeyCardManager keyCardManager;
    private Button confirmButton;
    private Button cancelButton;
    private TextView emptyView;
    private Long lastIndex = null;
    private boolean isLoading = false;
    private boolean isSingleChoice = false;

    public static Intent createIntent(Context context, boolean isSingleChoice) {
        Intent intent = new Intent(context, ChooseMultisignIdActivity.class);
        intent.putExtra(EXTRA_IS_SINGLE_CHOICE, isSingleChoice);
        return intent;
    }

    public static Map<String, P2SH> getSelectedP2SHMap(Intent data) {
        Map<String, P2SH> resultMap = new HashMap<>();
        TimberLogger.i(TAG, "resultMap siz = " + resultMap.size());

        if (data != null && data.hasExtra(EXTRA_SELECTED_P2SH)) {
            String p2shJson = data.getStringExtra(EXTRA_SELECTED_P2SH);
            if (p2shJson != null) {
                P2SH p2sh = P2SH.fromJson(p2shJson, P2SH.class);
                if (p2sh != null) {
                    resultMap.put(p2sh.getId(), p2sh);
                }
            }
        }
        return resultMap;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        isSingleChoice = getIntent().getBooleanExtra(EXTRA_IS_SINGLE_CHOICE, false);
        TimberLogger.i(TAG, "onCreate started, isSingleChoice: " + isSingleChoice);
        super.onCreate(savedInstanceState);
        TimberLogger.i(TAG, "onCreate completed");
    }

    @Override
    protected void initializeViews() {
        TimberLogger.i(TAG, "initializeViews started");
        keyListContainer = findViewById(R.id.keyListContainer);
        confirmButton = findViewById(R.id.confirmButton);
        cancelButton = findViewById(R.id.cancelButton);
        emptyView = findViewById(R.id.emptyView);
        TimberLogger.i(TAG, "isSingleChoice value: " + isSingleChoice);
        keyCardManager = new MultisignKeyCardManager(this, keyListContainer, true, isSingleChoice);
        TimberLogger.i(TAG, "initializeViews completed");
    }

    @Override
    protected void setupButtons() {
        TimberLogger.i(TAG, "setupButtons started");
        
        confirmButton.setOnClickListener(v -> {
            if (keyCardManager != null) {
                List<P2SH> selectedP2SHs = keyCardManager.getSelectedKeys();

                if (selectedP2SHs != null)TimberLogger.i(TAG, "selectedP2SH size = " + selectedP2SHs.size());
                else TimberLogger.i(TAG, "selectedP2SH is null");

                if (selectedP2SHs != null && !selectedP2SHs.isEmpty()) {
                    Intent resultIntent = new Intent();
                    String p2shJson = JsonUtils.toJson(selectedP2SHs.get(0));
                    resultIntent.putExtra(EXTRA_SELECTED_P2SH, p2shJson);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                } else {
                    Toast.makeText(this, "Please select at least one multisign", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        cancelButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        
        loadMultisigns();
        TimberLogger.i(TAG, "setupButtons completed");
    }

    private void loadMultisigns() {
        isLoading = true;
        List<P2SH> p2shs = MultisignManager.getInstance(this).getPaginatedMultisigns(PAGE_SIZE, null, true);
        if (p2shs != null && !p2shs.isEmpty()) {
            keyCardManager.addMultisignCards(keyListContainer, p2shs);
            lastIndex = MultisignManager.getInstance(this.getApplicationContext()).getIndexById(p2shs.get(p2shs.size() - 1).getId());
            emptyView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.VISIBLE);
        }
        isLoading = false;
    }

    private void loadMoreMultisigns() {
        if (lastIndex == null) return;
        
        isLoading = true;
        List<P2SH> moreP2shs = MultisignManager.getInstance(this.getApplicationContext()).getPaginatedMultisigns(PAGE_SIZE, lastIndex, true);
        if (moreP2shs != null && !moreP2shs.isEmpty()) {
            keyCardManager.addMultisignCards(keyListContainer, moreP2shs);
            lastIndex = MultisignManager.getInstance(this.getApplicationContext()).getIndexById(moreP2shs.get(moreP2shs.size() - 1).getId());
        }
        isLoading = false;
    }

    private boolean isNearBottom() {
        return keyListContainer.getChildCount() > 0 &&
               keyListContainer.getChildAt(keyListContainer.getChildCount() - 1).getBottom() <= 
               keyListContainer.getHeight() + keyListContainer.getScrollY();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_choose_multisign_id;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.choose_multisign_id);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // This activity doesn't need to handle QR scan results
        TimberLogger.i(TAG, "handleQrScanResult called but not implemented for this activity");
    }
} 