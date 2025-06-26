package com.fc.safe.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Toast;

import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.ui.FcEntityListFragment;
import com.fc.safe.ui.PopupMenuHelper;
import com.fc.safe.ui.WaitingDialog;

import java.util.ArrayList;
import java.util.List;

public class MyKeysActivity extends BaseCryptoActivity {
    private static final String TAG = "My Keys";
    private static final int DEFAULT_PAGE_SIZE = 50; // Default page size for loading keys
    
    private FcEntityListFragment<KeyInfo> entityListFragment;

    private Button deleteButton;
    private Button createNewButton;
    private Button addToListButton;
    private Button exportButton;
    private PopupMenuHelper popupMenuHelper;
    
    private final List<KeyInfo> keyInfoList = new ArrayList<>();
    private Long lastIndex = null;
    private boolean isLoadingMore = false;
    private boolean hasMoreData = true;
    private LocalDB<KeyInfo> keyInfoDB;

    private WaitingDialog waitingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize PopupMenuHelper
        popupMenuHelper = new PopupMenuHelper(this);

        // Load initial page of KeyInfo objects
        loadInitialData();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_my_keys;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.my_keys);
    }

    @Override
    protected void initializeViews() {
        // Initialize buttons
        deleteButton = findViewById(R.id.delete_button);
        createNewButton = findViewById(R.id.create_button);
        addToListButton = findViewById(R.id.add_to_list_button);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // MyKeysActivity doesn't use QR scanning functionality
    }

    /**
     * Loads the initial page of KeyInfo objects
     */
    private void loadInitialData() {
        // Show loading indicator
        try {
            // Get the first page of KeyInfo objects from the end
            List<KeyInfo> newList = keyInfoManager.getPaginatedKeyInfos(DEFAULT_PAGE_SIZE, null, true);

            if(newList == null || newList.isEmpty()) {
                dismissWaitingDialog();
                showToast(getString(R.string.no_key_info_found));
                updateButtonStates();
                return;
            }

            TimberLogger.i(TAG, "Initial load: %d keys loaded", newList.size());

            keyInfoList.clear();
            keyInfoList.addAll(newList);

            // If we have data, get the last index for pagination
            if (!keyInfoList.isEmpty()) {
                // Get the last item's index
                lastIndex = keyInfoManager.getIndexById(keyInfoList.get(keyInfoList.size() - 1).getId());

                // Check if there's more data to load
                hasMoreData = lastIndex > 1;
            } else {
                // No data available
                hasMoreData = false;
                lastIndex = null;
            }

            updateButtonStates();
            loadListFragment();
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error loading initial data: %s", e.getMessage());
            showToast(getString(R.string.error_loading_keys, e.getMessage()));
        }
    }

    private void updateButtonStates() {
        boolean hasKeyInfos = !(keyInfoList==null || keyInfoList.isEmpty());
        
        deleteButton.setEnabled(hasKeyInfos);
        deleteButton.setAlpha(hasKeyInfos ? 1.0f : 0.5f);
        addToListButton.setEnabled(hasKeyInfos);
        addToListButton.setAlpha(hasKeyInfos ? 1.0f : 0.5f);
        exportButton.setEnabled(hasKeyInfos);
        exportButton.setAlpha(hasKeyInfos ? 1.0f : 0.5f);
    }

    private void loadListFragment() {
        // Create fragment with the initial KeyInfo objects
        entityListFragment = FcEntityListFragment.newInstance(keyInfoList, KeyInfo.class, false);
        
        // Add fragment to container
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, entityListFragment)
                .commit();

        // Update button states after fragment is loaded
        updateButtonStates();
    }

    /**
     * Sets up the scroll listener to detect when the user reaches the end of the list
     */
    private void setupScrollListener() {
        // Get the ScrollView from the fragment
        View fragmentView = entityListFragment.getView();
        if (fragmentView != null) {
            // Find the ScrollView in the fragment's view hierarchy
            View scrollView = findScrollView(fragmentView);
            if (scrollView != null) {
                // Add scroll listener
                scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    boolean atBottom = isAtBottom(scrollView);
                    boolean scrollingUp = scrollY < oldScrollY;
                    
                    // Check if we've reached the bottom of the scroll view and user is scrolling up
                    if (atBottom && scrollingUp && !isLoadingMore && hasMoreData) {
                        TimberLogger.i(TAG, "Loading more data - reached bottom while scrolling up");
                        loadMoreData();
                    }
                });
            }
        }
    }
    
    /**
     * Recursively finds a ScrollView in the view hierarchy
     */
    private View findScrollView(View view) {
        if (view instanceof ScrollView) {
            return view;
        }
        
        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                View result = findScrollView(child);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks if the scroll view is at the bottom
     */
    private boolean isAtBottom(View scrollView) {
        if (scrollView instanceof ScrollView sv) {
            int scrollY = sv.getScrollY();
            int height = sv.getHeight();
            int viewHeight = sv.getChildAt(0).getHeight();
            
            // If we're within 100 pixels of the bottom, consider it at the bottom
            return (viewHeight - scrollY - height) <= 100;
        }
        return false;
    }
    
    /**
     * Loads more data when the user scrolls to the end of the list
     */
    private void loadMoreData() {
        if (isLoadingMore || !hasMoreData) {
            return;
        }
        
        isLoadingMore = true;
        TimberLogger.i(TAG, "Loading more data, lastIndex: %d", lastIndex);
        
        // Show loading indicator
        showWaitingDialog("Loading more keys...");
        
        // Perform database operations on background thread
        new Thread(() -> {
            try {
                // Get the next page of KeyInfo objects
                List<KeyInfo> moreKeyInfos = keyInfoManager.getPaginatedKeyInfos(DEFAULT_PAGE_SIZE, lastIndex, true);
                
                runOnUiThread(() -> {
                    if (moreKeyInfos != null && !moreKeyInfos.isEmpty()) {
                        // Update the lastIndex for the next page
                        lastIndex = keyInfoManager.getIndexById(moreKeyInfos.get(moreKeyInfos.size() - 1).getId());
                        
                        // Check if there's more data to load
                        hasMoreData = lastIndex > 1;
                        
                        // Add the new items to the list
                        keyInfoList.addAll(moreKeyInfos);
                        
                        // Update the fragment's list
                        entityListFragment.updateList(keyInfoList);
                        updateButtonStates();

                        Toast.makeText(this, moreKeyInfos.size()+R.string.keys_loaded, Toast.LENGTH_SHORT).show();
                        
                        if (!hasMoreData) {
                            Toast.makeText(this, R.string.no_more_keys, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        // No more data to load
                        hasMoreData = false;
                        Toast.makeText(this, getString(R.string.no_more_keys), Toast.LENGTH_LONG).show();
                    }
                    
                    isLoadingMore = false;
                    dismissWaitingDialog();
                });
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error loading more data: %s", e.getMessage());
                runOnUiThread(() -> {
                    isLoadingMore = false;
                    dismissWaitingDialog();
                    Toast.makeText(this, getString(R.string.error_loading_more_keys) + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    @Override
    protected void setupButtons() {
        // Set click listeners
        deleteButton.setOnClickListener(v -> {
            List<KeyInfo> chosenObjects = entityListFragment.getSelectedObjects();
            if (chosenObjects.isEmpty()) {
                Toast.makeText(this, R.string.no_items_selected, SafeApplication.TOAST_LASTING).show();
                return;
            }
            // Implement delete functionality
            keyInfoManager.removeKeyInfos(chosenObjects);
            
            // Commit changes to disk
            keyInfoManager.commit();
            
            // Remove deleted items from the current list
            keyInfoList.removeAll(chosenObjects);
            
            // Update the fragment's list directly
            entityListFragment.updateList(keyInfoList);
            
            // Update button states
            updateButtonStates();
            
            // Show confirmation message
            Toast.makeText(this, R.string.deleted , SafeApplication.TOAST_LASTING).show();
        });

        createNewButton.setOnClickListener(v -> {
            // Set flag to indicate we're creating a new key
            getIntent().putExtra("fromCreateKey", true);
            popupMenuHelper.showCreateKeyMenu(createNewButton);
        });

        addToListButton.setOnClickListener(v -> {
            List<KeyInfo> chosenObjects = entityListFragment.getSelectedObjects();
            if (chosenObjects.isEmpty()) {
                Toast.makeText(this, R.string.no_items_selected , SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Add selected key IDs to SafeApplication.fidList
            for (KeyInfo keyInfo : chosenObjects) {
                SafeApplication.addFid(keyInfo.getId());
            }

            // Show confirmation message
            Toast.makeText(this, this.getString(R.string.some_fids_added_to_list, chosenObjects.size() ), SafeApplication.TOAST_LASTING).show();
        });

        exportButton = findViewById(R.id.export_button);
        exportButton.setOnClickListener(v -> {
            List<KeyInfo> chosenObjects = entityListFragment.getSelectedObjects();
            if (chosenObjects.isEmpty()) {
                Toast.makeText(this, R.string.no_items_selected , SafeApplication.TOAST_LASTING).show();
                return;
            }
            String keyInfoListJson = com.fc.fc_ajdk.utils.JsonUtils.toJson(chosenObjects);
            Intent intent = new Intent(MyKeysActivity.this, com.fc.safe.myKeys.ExportKeysActivity.class);
            intent.putExtra("keyInfoList", keyInfoListJson);
            startActivity(intent);
        });

        // Set up selection change listener for the fragment
        if (entityListFragment != null) {
            entityListFragment.setOnSelectionChangeListener(() -> {
                updateButtonStates();
            });
        }
    }
    
    private void refreshList() {
        // Clear existing list
        keyInfoList.clear();
        
        // Reset pagination variables
        lastIndex = null;
        hasMoreData = true;
        
        // Update button states
        updateButtonStates();
        
        // Load initial data again
        loadInitialData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Only refresh if we're returning from creating a new key
        if (getIntent().getBooleanExtra("fromCreateKey", false)) {
            refreshList();
            // Clear the flag
            getIntent().removeExtra("fromCreateKey");
        }
        
        // Wait for fragment view to be ready before setting up scroll listener
        if (entityListFragment != null && entityListFragment.getView() != null) {
            setupScrollListener();
        } else {
            // Try again after a short delay to ensure fragment view is ready
            new android.os.Handler().postDelayed(() -> {
                if (entityListFragment != null && entityListFragment.getView() != null) {
                    setupScrollListener();
                }
            }, 100);
        }
    }

    private void showWaitingDialog(String message) {
        if (waitingDialog == null) {
            waitingDialog = new WaitingDialog(this, message);
        } else {
            waitingDialog.setHint(message);
        }
        if (!isFinishing() && !waitingDialog.isShowing()) {
            waitingDialog.show();
        }
    }

    private void dismissWaitingDialog() {
        if (waitingDialog != null && waitingDialog.isShowing() && !isFinishing()) {
            waitingDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.dismiss();
        }
        waitingDialog = null;
    }
} 