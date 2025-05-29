package com.fc.safe.myKeys;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.utils.KeyCardManager;
import com.fc.safe.utils.KeyLabelManager;
import com.fc.safe.utils.ToolbarUtils;

import java.util.List;

public class RandomNewKeysActivity extends AppCompatActivity {
    private static final String TAG = "CreateNewKeys";
    private static final int BATCH_SIZE = 10;
    
    private KeyCardManager keyCardManager;
    private KeyLabelManager keyLabelManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_random_new_keys);

        LinearLayout keyListContainer = findViewById(R.id.key_list);
        keyCardManager = new KeyCardManager(this, keyListContainer, false, List.of("Delete"));
        keyLabelManager = new KeyLabelManager(this);

        // Set up toolbar
        ToolbarUtils.setupToolbar(this, "Random New Keys");

        generateNewKeys();
        setupButtons();
    }

    private void generateNewKeys() {
        byte[] symKey = ConfigureManager.getInstance().getSymkey();
        for (int i = 0; i < BATCH_SIZE; i++) {
            KeyInfo keyInfo = KeyInfo.createNew(symKey);
            keyCardManager.addKeyCard(keyInfo);
        }
    }

    private void setupButtons() {
        Button clearButton = findViewById(R.id.clear_button);
        Button moreButton = findViewById(R.id.more_button);
        Button saveButton = findViewById(R.id.save_button);

        clearButton.setOnClickListener(v -> keyCardManager.clearAll());

        moreButton.setOnClickListener(v -> generateNewKeys());

        saveButton.setOnClickListener(v -> {
            List<KeyInfo> selectedKeys = keyCardManager.getSelectedKeys();
            if (selectedKeys.isEmpty()) {
                Toast.makeText(this, "No keys selected", SafeApplication.TOAST_LASTING).show();
                return;
            }
            keyLabelManager.saveSelectedKeys(selectedKeys);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
} 