package com.fc.safe.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.fc.fc_ajdk.data.fchData.Multisign;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.MultisignManager;
import com.fc.safe.multisign.BuildMultisignTxActivity;
import com.fc.safe.multisign.CreateMultisignIdActivity;
import com.fc.safe.multisign.CreateMultisignTxActivity;
import com.fc.safe.multisign.ImportMultisignTxActivity;
import com.fc.safe.multisign.SignMultisignTxActivity;
import com.fc.safe.tx.SignTxActivity;
import com.fc.safe.multisign.MultisignKeyCardManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MultisignActivity extends BaseCryptoActivity {
    private static final String TAG = "MultisignActivity";
    private static final int PAGE_SIZE = 10;
    private static final int REQUEST_CODE_IMPORT_MULTISIGN_TX = 1001;
    private static boolean needsRefresh = false;
    private Long lastIndex = null;
    private LinearLayout multisignListContainer;
    private ScrollView idListContainer;
    private boolean isLoading = false;
    private MultisignManager multisignManager;
    private MultisignKeyCardManager multisignCardManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimberLogger.d(TAG, "onCreate: Starting MultisignActivity");
        multisignManager = MultisignManager.getInstance(this.getApplicationContext());
        TimberLogger.d(TAG, "MultisignManager initialized");

        initializeViews();
        multisignCardManager = new MultisignKeyCardManager(this, multisignListContainer, false);
        setupScrollListener();
        
        // Load initial multisign cards
        loadMultisigns();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_multisign;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.multisign);
    }

    protected void initializeViews() {
        multisignListContainer = findViewById(R.id.multisignListContainer);
        idListContainer = findViewById(R.id.idListContainer);
    }

    @Override
    protected void setupButtons() {
        Button createIdButton = findViewById(R.id.createIdButton);
        Button createTxButton = findViewById(R.id.createTxButton);
        Button signTxButton = findViewById(R.id.signTxButton);
        Button buildTxButton = findViewById(R.id.buildTxButton);

        setupButton(createIdButton, v -> handleCreateId());
        setupButton(createTxButton, v -> handleCreateTx());
        setupButton(signTxButton, v -> handleSignTx());
        setupButton(buildTxButton, v -> handleBuildTx());
    }

    private void setupScrollListener() {
        idListContainer.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (!isLoading && idListContainer.getChildAt(0).getBottom() <= 
                (idListContainer.getHeight() + idListContainer.getScrollY())) {
                loadMultisigns();
            }
        });
    }

    private void loadMultisigns() {
        if (isLoading) return;
        isLoading = true;
        TimberLogger.d(TAG, "loadMultisigns: Starting to load multisigns. lastIndex=" + lastIndex);

        List<Multisign> multisigns;
        LocalDB<Multisign> multisignDB = multisignManager.getMultisignDB();
        Map<String, Multisign> map;
        if(multisignDB.getSortType().equals(LocalDB.SortType.NO_SORT)) {
            map = multisignDB.getAll();
            if(map == null){
                showToast(getString(R.string.no_multisigns_found));
                isLoading = false;
                return;
            }
            multisigns = new ArrayList<>(map.values());
        } else {
            multisigns = multisignManager.getPaginatedMultisigns(10, lastIndex, true);
        }
        
        TimberLogger.d(TAG, "loadMultisigns: Retrieved " + (multisigns != null ? multisigns.size() : 0) + " multisigns");
        
        if (multisigns != null && !multisigns.isEmpty()) {
            if(lastIndex == null) {
                lastIndex = (long) multisignDB.getSize();
            }
            lastIndex -= multisigns.size();
            TimberLogger.d(TAG, "loadMultisigns: Adding " + multisigns.size() + " multisigns to container");
            multisignCardManager.addMultisignCards(multisignListContainer, multisigns);
            TimberLogger.d(TAG, "loadMultisigns: Container child count: " + multisignListContainer.getChildCount());
            showToast("Loaded " + multisigns.size() + " multisigns");
        } else {
            TimberLogger.d(TAG, "loadMultisigns: No more multisigns to load");
            showToast("No more multisigns to load");
        }
        
        isLoading = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        TimberLogger.d(TAG, "onResume: Checking if multisign list needs refresh");
        if (needsRefresh) {
            TimberLogger.d(TAG, "onResume: Refreshing multisign list");
            lastIndex = null;
            multisignListContainer.removeAllViews();
            loadMultisigns();
            needsRefresh = false;
        }
    }

    public static void setNeedsRefresh(boolean refresh) {
        needsRefresh = refresh;
    }

    private void handleCreateId() {
        Intent intent = new Intent(this, CreateMultisignIdActivity.class);
        startActivity(intent);
    }

    private void handleCreateTx() {
        TimberLogger.d(TAG, "handleCreateTx: Launching CreateMultisignTxActivity");
        Intent intent = new Intent(this, CreateMultisignTxActivity.class);
        startActivity(intent);
    }

    private void handleSignTx() {
        TimberLogger.d(TAG, "handleSignTx: Launching ImportMultisignTxActivity");
        Intent intent = ImportMultisignTxActivity.createIntent(this);
        startActivityForResult(intent, REQUEST_CODE_IMPORT_MULTISIGN_TX);
    }

    private void handleBuildTx() {
        Intent intent = new Intent(this, BuildMultisignTxActivity.class);
        startActivity(intent);
    }

    private void showKeyDetail(Multisign multisign) {
        Intent intent = new Intent(this, MultisignDetailActivity.class);
        intent.putExtra("multisign", multisign);
        startActivity(intent);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // TODO: Implement QR scan result handling
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORT_MULTISIGN_TX && resultCode == RESULT_OK && data != null) {
            String multiSigDataJson = data.getStringExtra(SignTxActivity.EXTRA_TX_INFO_JSON);

                // Launch SignMultisignTxActivity
                Intent intent = new Intent(this, SignMultisignTxActivity.class);
                intent.putExtra(SignTxActivity.EXTRA_TX_INFO_JSON, multiSigDataJson);
                startActivity(intent);
        }
    }

} 