package com.fc.safe.myKeys;

import android.content.Context;
import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.ui.FcEntityListFragment;

import java.util.ArrayList;
import java.util.List;

public class ChooseKeyInfoActivity extends BaseCryptoActivity {
    private static final String TAG = "ChooseKeyInfoActivity";
    private static final String EXTRA_KEY_INFO_LIST = "extra_key_info_list";
    public static final String EXTRA_SELECTED_KEY_IDS = "extra_selected_key_ids";
    private static final String EXTRA_IS_SINGLE_CHOICE = "extra_is_single_choice";
    
    private FcEntityListFragment<KeyInfo> entityListFragment;

    private Boolean isSingleChoice;

    private Button cancelButton;
    private Button doneButton;
    private LinearLayout buttonContainer;

    /**
     * Creates an intent to start the ChooseKeyInfoActivity
     * 
     * @param context The context
     * @param keyInfoList The list of KeyInfo objects to display
     * @param isSingleChoice Whether only one item can be selected (null for no selection)
     * @return An intent to start the activity
     */
    public static Intent newIntent(Context context, List<KeyInfo> keyInfoList, Boolean isSingleChoice) {
        Intent intent = new Intent(context, ChooseKeyInfoActivity.class);
        intent.putExtra(EXTRA_KEY_INFO_LIST, new ArrayList<>(keyInfoList));
        intent.putExtra(EXTRA_IS_SINGLE_CHOICE, isSingleChoice != null ? isSingleChoice : false);
        intent.putExtra("has_single_choice", isSingleChoice != null);
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
        
        // Get isSingleChoice from intent
        boolean hasSingleChoice = getIntent().getBooleanExtra("has_single_choice", false);
        if (hasSingleChoice) {
            isSingleChoice = getIntent().getBooleanExtra(EXTRA_IS_SINGLE_CHOICE, true);
        } else {
            isSingleChoice = null;
        }
        
        // Create fragment with KeyInfo objects
        entityListFragment = FcEntityListFragment.newInstance(keyInfoList, KeyInfo.class, isSingleChoice);
        
        // Add fragment to container
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, entityListFragment)
                .commit();
        
        // Initialize buttons
        buttonContainer = findViewById(R.id.button_container);
        cancelButton = findViewById(R.id.cancel_button);
        doneButton = findViewById(R.id.done_button);
    }

    @Override
    protected void setupButtons() {
        // Set click listeners
        cancelButton.setOnClickListener(v -> {
            TimberLogger.d(TAG, "Cancel button clicked");
            setResult(RESULT_CANCELED);
            finish();
        });
        
        doneButton.setOnClickListener(v -> {
            TimberLogger.d(TAG, "Done button clicked");
            List<KeyInfo> selectedObjects = entityListFragment.getSelectedObjects();
            TimberLogger.d(TAG, "Selected objects count: " + (selectedObjects != null ? selectedObjects.size() : 0));
            
            if (selectedObjects.isEmpty()) {
                TimberLogger.d(TAG, "No objects selected");
                Toast.makeText(this, getString(R.string.no_key_selected), SafeApplication.TOAST_LASTING).show();
                return;
            }
            
            // Return the list of selected key IDs
            List<String> selectedIds = new ArrayList<>();
            for (KeyInfo keyInfo : selectedObjects) {
                TimberLogger.d(TAG, "Selected key ID: " + keyInfo.getId());
                TimberLogger.d(TAG, "Selected key public key: " + keyInfo.getPubkey());
                selectedIds.add(keyInfo.getId());
            }
            
            Intent resultIntent = new Intent();
            resultIntent.putStringArrayListExtra(EXTRA_SELECTED_KEY_IDS, new ArrayList<>(selectedIds));
            TimberLogger.d(TAG, "Setting result with " + selectedIds.size() + " selected IDs");
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // This activity doesn't handle QR scan results
    }
} 