package com.fc.safe.myKeys;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.ui.WaitingDialog;
import com.fc.safe.utils.KeyCardContainer;
import com.fc.safe.utils.ChooseMode;
import com.fc.safe.utils.KeyLabelManager;
import com.fc.safe.utils.ToolbarUtils;

import java.util.List;

public class RandomNewKeysActivity extends AppCompatActivity {
    private static final String TAG = "CreateNewKeys";
    private static final int BATCH_SIZE = 10;

    private KeyCardContainer keyCardManager;
    private KeyLabelManager keyLabelManager;
    private CheckBox checkboxAll;
    private WaitingDialog waitingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_random_new_keys);

        LinearLayout keyListContainer = findViewById(R.id.key_list);
        keyCardManager = new KeyCardContainer(this, keyListContainer, ChooseMode.CHOOSE_MULTI_WITHOUT_EDIT, List.of("Delete"));
        keyLabelManager = new KeyLabelManager(this);

        // Initialize checkbox
        checkboxAll = findViewById(R.id.checkbox_all);

        // Set up toolbar
        ToolbarUtils.setupToolbar(this, "Random New Keys");

        // Show waiting dialog and generate keys
        showWaitingDialog();
        generateNewKeysAsync();
        setupButtons();
        setupCheckboxAll();
    }

    private void showWaitingDialog() {
        waitingDialog = new WaitingDialog(this, getString(R.string.generating_keys));
        waitingDialog.show();
    }

    private void dismissWaitingDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.dismiss();
            waitingDialog = null;
        }
    }

    private void generateNewKeysAsync() {
        new Thread(() -> {
            byte[] symKey = ConfigureManager.getInstance().getSymkey();
            for (int i = 0; i < BATCH_SIZE; i++) {
                KeyInfo keyInfo = KeyInfo.createNew(symKey);
                runOnUiThread(() -> keyCardManager.addKeyCard(keyInfo));
            }
            runOnUiThread(this::dismissWaitingDialog);
        }).start();
    }

    private void generateNewKeys() {
        showWaitingDialog();
        new Thread(() -> {
            byte[] symKey = ConfigureManager.getInstance().getSymkey();
            for (int i = 0; i < BATCH_SIZE; i++) {
                KeyInfo keyInfo = KeyInfo.createNew(symKey);
                runOnUiThread(() -> keyCardManager.addKeyCard(keyInfo));
            }
            runOnUiThread(this::dismissWaitingDialog);
        }).start();
    }

    private void setupButtons() {
        Button clearButton = findViewById(R.id.clear_button);
        Button moreButton = findViewById(R.id.more_button);
        Button saveButton = findViewById(R.id.save_button);

        clearButton.setOnClickListener(v -> {
            keyCardManager.clearAll();
            checkboxAll.setChecked(false);
        });

        moreButton.setOnClickListener(v -> generateNewKeys());

        saveButton.setOnClickListener(v -> {
            List<KeyInfo> selectedKeys = keyCardManager.getSelectedKeys();
            if (selectedKeys.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_keys_selected), SafeApplication.TOAST_LASTING).show();
                return;
            }
            keyLabelManager.saveSelectedKeys(selectedKeys);
        });
    }

    private void setupCheckboxAll() {
        // Set up "All" checkbox to select/deselect all keys
        checkboxAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            keyCardManager.selectAll(isChecked);
        });

        // Set up listener to update checkbox state when individual cards are checked/unchecked
        keyCardManager.setOnKeyListChangedListener(updatedKeyList -> {
            // Update checkbox state based on selection
            if (keyCardManager.areAllSelected()) {
                checkboxAll.setChecked(true);
            } else if (keyCardManager.areNoneSelected()) {
                checkboxAll.setChecked(false);
            } else {
                // Some but not all selected - set to intermediate/unchecked state
                checkboxAll.setChecked(false);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissWaitingDialog();
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