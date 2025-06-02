package com.fc.safe.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.fc.fc_ajdk.data.fchData.Multisign;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.MultisignManager;
import com.fc.safe.multisign.CreateMultisignTxActivity;
import com.fc.safe.ui.DetailFragment;
import com.fc.safe.utils.KeyCardManager;

import java.util.List;

public class MultisignDetailActivity extends BaseCryptoActivity {
    private static final String TAG = "MultisignDetailActivity";
    private Multisign multisign;
    private LinearLayout memberCardsContainer;
    private LinearLayout detailContainer;
    private Button createTxButton;
    private Button copyButton;
    private KeyCardManager keyCardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get Multisign from intent
        multisign = (Multisign) getIntent().getSerializableExtra("multisign");
        if (multisign == null) {
            finish();
            return;
        }

        // Initialize views
        initializeViews();

        // Setup member cards
        setupMemberCards();

        // Setup detail fragment
        setupDetailFragment();

        // Setup create transaction button
        setupCreateTxButton();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_multisign_detail;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.multisign_detail);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Handle QR scan result if needed
    }

    protected void initializeViews() {
        memberCardsContainer = findViewById(R.id.memberCardsContainer);
        detailContainer = findViewById(R.id.detailContainer);
        createTxButton = findViewById(R.id.createTxButton);
        copyButton = findViewById(R.id.copyButton);

        // Setup copy button
        copyButton.setOnClickListener(v -> {
            String jsonString = multisign.toNiceJson();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Multisign JSON", jsonString);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, getString(R.string.copied), SafeApplication.TOAST_LASTING).show();
        });

        // Setup delete button
        Button deleteButton = findViewById(R.id.deleteButton);
        deleteButton.setOnClickListener(v -> {
            MultisignManager.getInstance(this.getApplicationContext()).removeMultisign(multisign);
            MultisignManager.getInstance(this.getApplicationContext()).commit();
            MultisignActivity.setNeedsRefresh(true);
            finish();
        });
    }

    private void setupMemberCards() {
        // Add label for members section
        TextView membersLabel = new TextView(this);
        membersLabel.setText("Members");
        membersLabel.setTextSize(18);
        membersLabel.setTypeface(membersLabel.getTypeface(), android.graphics.Typeface.BOLD);
        memberCardsContainer.addView(membersLabel);

        // Create a container for the key cards
        LinearLayout keyCardList = new LinearLayout(this);
        keyCardList.setOrientation(LinearLayout.VERTICAL);
        memberCardsContainer.addView(keyCardList);

        // Initialize KeyCardManager
        keyCardManager = new KeyCardManager(this, keyCardList, null);

        // Create KeyInfo objects from pubKeys and add them to the card manager
        List<String> pubKeys = multisign.getPubKeys();
        if(pubKeys!=null && !pubKeys.isEmpty())
            for (String pubKey : pubKeys) {
                KeyInfo keyInfo = new KeyInfo(null, pubKey);
                keyCardManager.addKeyCard(keyInfo);
            }
    }

    private void setupDetailFragment() {
        // Create and add detail fragment
        DetailFragment detailFragment = DetailFragment.newInstance(multisign, Multisign.class);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.detailContainer, detailFragment)
                .commit();
    }

    private void setupCreateTxButton() {
        createTxButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateMultisignTxActivity.class);
            intent.putExtra("multisign", multisign);
            startActivity(intent);
        });
    }

    @Override
    protected void setupButtons() {
        // Set button heights
        Button deleteButton = findViewById(R.id.deleteButton);

        // Set click listeners
        deleteButton.setOnClickListener(v -> {
            MultisignManager.getInstance(this.getApplicationContext()).removeMultisign(multisign);
            MultisignManager.getInstance(this.getApplicationContext()).commit();
            MultisignActivity.setNeedsRefresh(true);
            finish();
        });

        createTxButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateMultisignTxActivity.class);
            intent.putExtra("multisign", multisign);
            startActivity(intent);
        });
    }
} 