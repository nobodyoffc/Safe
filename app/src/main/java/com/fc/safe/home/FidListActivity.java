package com.fc.safe.home;

import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.safe.R;
import com.fc.safe.SafeApplication;
import com.fc.safe.utils.KeyCardManager;

import java.util.List;

public class FidListActivity extends BaseCryptoActivity {
    private KeyCardManager keyCardManager;
    private LinearLayout keyCardContainer;
    private Button clearButton;
    private Button deleteButton;
    private Button addButton;
    private static final int REQUEST_ADD_FID = 1001;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_list;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.fid_list);
    }

    @Override
    protected void initializeViews() {
        keyCardContainer = findViewById(R.id.keyCardContainer);
        clearButton = findViewById(R.id.clearButton);
        deleteButton = findViewById(R.id.deleteButton);
        addButton = findViewById(R.id.addButton);

        // Initialize KeyCardManager
        keyCardManager = new KeyCardManager(this, keyCardContainer, false);
        keyCardManager.setOnKeyListChangedListener(this::onKeyListChanged);

        // Load initial FIDs
        loadFidList();
    }

    @Override
    protected void setupButtons() {
        clearButton.setOnClickListener(v -> {
            SafeApplication.clearFidList();
            keyCardManager.clearAll();
            updateButtonStates();
        });

        deleteButton.setOnClickListener(v -> {
            List<KeyInfo> selectedKeys = keyCardManager.getSelectedKeys();
            for (KeyInfo keyInfo : selectedKeys) {
                SafeApplication.removeFid(keyInfo.getId());
            }
            loadFidList();
        });

        addButton.setOnClickListener(v -> {
            startActivityForResult(AddFidActivity.createIntent(this), REQUEST_ADD_FID);
        });
    }

    private void loadFidList() {
        keyCardManager.clearAll();
        List<String> fidList = SafeApplication.getFidList();
        for (String fid : fidList) {
            KeyInfo keyInfo = KeyInfo.newFromFid(fid, null);
            if (keyInfo != null) {
                keyCardManager.addKeyCard(keyInfo);
            }
        }
        updateButtonStates();
    }

    private void onKeyListChanged(List<KeyInfo> updatedKeyInfoList) {
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean hasItems = !keyCardManager.getKeyInfoList().isEmpty();
        boolean hasSelection = !keyCardManager.getSelectedKeys().isEmpty();
        
        clearButton.setEnabled(hasItems);
        deleteButton.setEnabled(hasSelection);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not needed for this activity
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADD_FID) {
            loadFidList();
        }
    }
} 