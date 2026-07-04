package com.fc.safe.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.fc.fc_ajdk.data.fchData.Multisig;
import com.fc.safe.db.LocalDB;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.MultisignManager;
import com.fc.safe.multisig.BuildMultisigTxActivity;
import com.fc.safe.multisig.CreateMultisigIdActivity;
import com.fc.safe.multisig.CreateMultisigTxActivity;
import com.fc.safe.multisig.ImportMultisigTxActivity;
import com.fc.safe.multisig.SignMultisigTxActivity;
import com.fc.safe.tx.SignTxActivity;
import com.fc.safe.multisig.MultisigKeyCardManager;

import java.util.List;

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
    private MultisigKeyCardManager multisignCardManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimberLogger.d(TAG, "onCreate: Starting MultisignActivity");
        multisignManager = MultisignManager.getInstance(this.getApplicationContext());
        TimberLogger.d(TAG, "MultisignManager initialized");

        initializeViews();
        multisignCardManager = new MultisigKeyCardManager(this, multisignListContainer, false);
        setupScrollListener();
        
        // Load initial multisig cards
        loadMultisigns();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_multisign;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.multisig);
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
        TimberLogger.d(TAG, "loadMultisigns: Starting to load multisigs. lastIndex=" + lastIndex);

        List<Multisig> multisigs;
        LocalDB<Multisig> multisignDB = multisignManager.getMultisignDB();

        multisigs = multisignManager.getPaginatedMultisigns(10, lastIndex, true);

        TimberLogger.d(TAG, "loadMultisigns: Retrieved " + (multisigs != null ? multisigs.size() : 0) + " multisigs");
        
        if (multisigs != null && !multisigs.isEmpty()) {
            if(lastIndex == null) {
                lastIndex = (long) multisignDB.getSize();
            }
            lastIndex -= multisigs.size();
            TimberLogger.d(TAG, "loadMultisigns: Adding " + multisigs.size() + " multisigs to container");
            multisignCardManager.addMultisignCards(multisignListContainer, multisigs);
            TimberLogger.d(TAG, "loadMultisigns: Container child count: " + multisignListContainer.getChildCount());
            showToast(getString(R.string.loaded_multisigs, multisigs.size()));
        } else {
            TimberLogger.d(TAG, "loadMultisigns: No more multisigs to load");
            showToast(getString(R.string.no_more_multisigs_to_load));
        }
        
        isLoading = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        TimberLogger.d(TAG, "onResume: Checking if multisig list needs refresh");
        if (needsRefresh) {
            TimberLogger.d(TAG, "onResume: Refreshing multisig list");
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
        Intent intent = new Intent(this, CreateMultisigIdActivity.class);
        startActivity(intent);
    }

    private void handleCreateTx() {
        TimberLogger.d(TAG, "handleCreateTx: Launching CreateMultisigTxActivity");
        Intent intent = new Intent(this, CreateMultisigTxActivity.class);
        startActivity(intent);
    }

    private void handleSignTx() {
        TimberLogger.d(TAG, "handleSignTx: Launching ImportMultisigTxActivity");
        Intent intent = ImportMultisigTxActivity.createIntent(this);
        startActivityForResult(intent, REQUEST_CODE_IMPORT_MULTISIGN_TX);
    }

    private void handleBuildTx() {
        Intent intent = new Intent(this, BuildMultisigTxActivity.class);
        startActivity(intent);
    }

    private void showKeyDetail(Multisig multisig) {
        Intent intent = new Intent(this, MultisignDetailActivity.class);
        intent.putExtra("multisig", multisig);
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

                // Launch SignMultisigTxActivity
                Intent intent = new Intent(this, SignMultisigTxActivity.class);
                intent.putExtra(SignTxActivity.EXTRA_TX_INFO_JSON, multiSigDataJson);
                startActivity(intent);
        }
    }

} 