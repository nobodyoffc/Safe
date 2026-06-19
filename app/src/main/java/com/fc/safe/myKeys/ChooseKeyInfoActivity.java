package com.fc.safe.myKeys;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.utils.KeyCardContainer;
import com.fc.safe.utils.ChooseMode;

import java.util.ArrayList;
import java.util.List;
import com.fc.safe.utils.ToastUtils;

public class ChooseKeyInfoActivity extends BaseCryptoActivity {
    private static final String TAG = "ChooseKeyInfoActivity";
    private static final String EXTRA_KEY_INFO_LIST = "extra_key_info_list";
    public static final String EXTRA_SELECTED_KEY_IDS = "extra_selected_key_ids";
    private static final String EXTRA_CHOOSE_MODE = "extra_choose_mode";

    private KeyCardContainer keyCardContainer;
    private LinearLayout keyListContainer;

    private ChooseMode chooseMode;

    private Button cancelButton;
    private Button doneButton;
    private LinearLayout buttonContainer;

    /**
     * Creates an intent to start the ChooseKeyInfoActivity
     *
     * @param context The context
     * @param keyInfoList The list of KeyInfo objects to display
     * @param chooseMode The mode to use for choosing keys
     * @return An intent to start the activity
     */
    public static Intent newIntent(Context context, List<KeyInfo> keyInfoList, ChooseMode chooseMode) {
        Intent intent = new Intent(context, ChooseKeyInfoActivity.class);
        intent.putExtra(EXTRA_KEY_INFO_LIST, new ArrayList<>(keyInfoList));
        intent.putExtra(EXTRA_CHOOSE_MODE, chooseMode != null ? chooseMode : ChooseMode.CHOOSE_ONE_RETURN);
        return intent;
    }
    
    /**
     * Gets the selected KeyInfos from the activity result
     *
     * @param data           The intent data from onActivityResult
     * @param keyInfoManager The KeyInfoManager instance
     * @return The list of selected KeyInfos, or null if none was selected
     */
    public static List<KeyInfo> getSelectedKeyInfo(Intent data, KeyInfoManager keyInfoManager) {
        if (data == null || keyInfoManager == null) return null;
        List<String> selectedIds = data.getStringArrayListExtra(EXTRA_SELECTED_KEY_IDS);
        if (selectedIds == null || selectedIds.isEmpty()) return null;
        
        // Get KeyInfos by their IDs
        List<KeyInfo> selectedKeyInfos = new ArrayList<>();
        for (String id : selectedIds) {
            KeyInfo keyInfo = keyInfoManager.getKeyInfoById(id);
            if (keyInfo != null) {
                selectedKeyInfos.add(keyInfo);
            }
        }
        return selectedKeyInfos;
    }
    
    @Override
    protected int getLayoutId() {
        return R.layout.activity_choose_key_info;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.choose_key);
    }

    @Override
    protected void initializeViews() {
        // Initialize KeyInfoManager and DB

        // Get the list of KeyInfo objects from the intent
        List<KeyInfo> keyInfoList = (List<KeyInfo>) getIntent().getSerializableExtra(EXTRA_KEY_INFO_LIST);
        if (keyInfoList == null) {
            keyInfoList = keyInfoManager.getAllKeyInfoList();
        }

        // Get chooseMode from intent
        chooseMode = (ChooseMode) getIntent().getSerializableExtra(EXTRA_CHOOSE_MODE);
        if (chooseMode == null) {
            chooseMode = ChooseMode.CHOOSE_ONE_RETURN;
        }

        // Initialize the key list container
        keyListContainer = findViewById(R.id.key_list_container);

        // Create KeyCardContainer with the specified mode
        keyCardContainer = new KeyCardContainer(this, keyListContainer, chooseMode);

        // Set up card click callback for CHOOSE_ONE_RETURN mode
        if (chooseMode == ChooseMode.CHOOSE_ONE_RETURN) {
            keyCardContainer.setOnCardClickListener(this::returnSelectedKey);
        }

        // Add all keys to the container
        for (KeyInfo keyInfo : keyInfoList) {
            keyCardContainer.addKeyCard(keyInfo);
        }

        // Initialize buttons
        buttonContainer = findViewById(R.id.button_container);
        cancelButton = findViewById(R.id.cancel_button);
        doneButton = findViewById(R.id.done_button);
    }

    /**
     * Return the selected key and finish the activity
     */
    private void returnSelectedKey(KeyInfo keyInfo) {
        List<String> selectedIds = new ArrayList<>();
        selectedIds.add(keyInfo.getId());

        Intent resultIntent = new Intent();
        resultIntent.putStringArrayListExtra(EXTRA_SELECTED_KEY_IDS, new ArrayList<>(selectedIds));
        TimberLogger.d(TAG, "Returning selected key ID: " + keyInfo.getId());
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    /**
     * Return the selected keys and finish the activity
     */
    private void returnSelectedKeys() {
        List<KeyInfo> selectedKeys = keyCardContainer.getSelectedKeys();

        if (selectedKeys.isEmpty()) {
            ToastUtils.makeText(this, R.string.no_keys_selected);
            return;
        }

        List<String> selectedIds = new ArrayList<>();
        for (KeyInfo keyInfo : selectedKeys) {
            selectedIds.add(keyInfo.getId());
        }

        Intent resultIntent = new Intent();
        resultIntent.putStringArrayListExtra(EXTRA_SELECTED_KEY_IDS, new ArrayList<>(selectedIds));
        TimberLogger.d(TAG, "Returning " + selectedIds.size() + " selected key(s)");
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void setupButtons() {
        switch (chooseMode) {
            case CHOOSE_ONE_RETURN:
                // Hide buttons for CHOOSE_ONE_RETURN mode as clicking on a card will return immediately
                buttonContainer.setVisibility(View.GONE);
                break;

            case CHOOSE_ONE:
            case CHOOSE_MULTI:
            case CHOOSE_MULTI_WITHOUT_EDIT:
                // Show buttons and set up click listeners
                buttonContainer.setVisibility(View.VISIBLE);

                cancelButton.setOnClickListener(v -> {
                    setResult(RESULT_CANCELED);
                    finish();
                });

                doneButton.setOnClickListener(v -> returnSelectedKeys());
                break;

            case WITHOUT_CHOOSE:
                // Hide buttons and disable selection
                buttonContainer.setVisibility(View.GONE);
                break;

            default:
                buttonContainer.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // This activity doesn't handle QR scan results
    }
} 