package com.fc.safe.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.safe.db.LocalDB;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.ui.PopupMenuHelper;
import com.fc.safe.ui.WaitingDialog;
import com.fc.safe.utils.ChooseMode;
import com.fc.safe.utils.KeyCardContainer;

import java.util.ArrayList;
import java.util.List;
import com.fc.safe.utils.ToastUtils;

public class MyKeysActivity extends BaseCryptoActivity {
    private static final String TAG = "My Keys";
    private static final int DEFAULT_PAGE_SIZE = 50; // Default page size for loading keys

    private KeyCardContainer keyCardContainer;

    private Button deleteButton;
    private Button createNewButton;
    private Button addToListButton;
    private Button exportButton;
    private PopupMenuHelper popupMenuHelper;

    private CheckBox checkboxAll;
    private ImageView sortFidIcon;
    private ImageView sortTimeIcon;
    private ImageView sortLabelIcon;
    private LinearLayout sortFidContainer;
    private LinearLayout sortTimeContainer;
    private LinearLayout sortLabelContainer;

    // Statistics bar UI elements
    private View statisticsBar;
    private TextView statsLoadedCountTextView;
    private TextView statsTotalCountTextView;
    private TextView statsCheckedCountTextView;

    private enum SortState { NONE, ASC, DESC }
    private SortState fidSortState = SortState.NONE;
    private SortState timeSortState = SortState.DESC;  // Default
    private SortState labelSortState = SortState.NONE;

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
        exportButton = findViewById(R.id.export_button);

        // Initialize sort controls
        checkboxAll = findViewById(R.id.checkbox_all);
        sortFidIcon = findViewById(R.id.sort_fid_icon);
        sortTimeIcon = findViewById(R.id.sort_time_icon);
        sortLabelIcon = findViewById(R.id.sort_label_icon);
        sortFidContainer = findViewById(R.id.sort_fid_container);
        sortTimeContainer = findViewById(R.id.sort_time_container);
        sortLabelContainer = findViewById(R.id.sort_label_container);

        // Initialize statistics bar UI elements
        statisticsBar = findViewById(R.id.statistics_bar);
        statsLoadedCountTextView = findViewById(R.id.stats_loaded_count);
        statsTotalCountTextView = findViewById(R.id.stats_total_count);
        statsCheckedCountTextView = findViewById(R.id.stats_checked_count);

        // Initialize KeyCardContainer
        LinearLayout keyListContainer = findViewById(R.id.key_list_container);
        keyCardContainer = new KeyCardContainer(this, keyListContainer, ChooseMode.CHOOSE_MULTI);

        // Set up sort controls
        setupSortControls();

        // Initialize statistics bar with zeros
        updateStatisticsBar();
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
                keyInfoList.clear();
                loadListFragment();  // This will show the empty state message
                updateButtonStates();
                return;
            }

            TimberLogger.i(TAG, "Initial load: %d keys loaded", newList.size());

            keyInfoList.clear();
            keyInfoList.addAll(newList);

            // If we have data, get the last index for pagination
            if (!keyInfoList.isEmpty()) {
                // Get the last item's index
                lastIndex = (long) keyInfoManager.getIndexById(keyInfoList.get(keyInfoList.size() - 1).getId());

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

        // Update statistics bar
        updateStatisticsBar();
    }

    private void loadListFragment() {
        // Clear existing cards
        keyCardContainer.clearAll();

        // Add all key cards
        for (KeyInfo keyInfo : keyInfoList) {
            keyCardContainer.addKeyCard(keyInfo);
        }

        // Apply default sort (saveTime DESC)
        applySorting();

        // Update button states after list is loaded
        updateButtonStates();
    }

    /**
     * Sets up the scroll listener to detect when the user reaches the end of the list
     */
    private void setupScrollListener() {
        // Get the ScrollView from the layout
        ScrollView scrollView = findViewById(android.R.id.content).findViewById(R.id.key_list_container).getParent() instanceof ScrollView ?
                (ScrollView) findViewById(R.id.key_list_container).getParent() : null;

        if (scrollView != null) {
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

    /**
     * Checks if the scroll view is at the bottom
     */
    private boolean isAtBottom(ScrollView scrollView) {
        int scrollY = scrollView.getScrollY();
        int height = scrollView.getHeight();
        int viewHeight = scrollView.getChildAt(0).getHeight();

        // If we're within 100 pixels of the bottom, consider it at the bottom
        return (viewHeight - scrollY - height) <= 100;
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
                        lastIndex = (long) keyInfoManager.getIndexById(moreKeyInfos.get(moreKeyInfos.size() - 1).getId());

                        // Check if there's more data to load
                        hasMoreData = lastIndex > 1;

                        // Add the new items to the list
                        keyInfoList.addAll(moreKeyInfos);

                        // Add the new cards to the container
                        for (KeyInfo keyInfo : moreKeyInfos) {
                            keyCardContainer.addKeyCard(keyInfo);
                        }

                        // Apply current sorting
                        applySorting();

                        updateButtonStates();

                        ToastUtils.showInfo(this, moreKeyInfos.size()+R.string.keys_loaded);
                        
                        if (!hasMoreData) {
                            ToastUtils.showWarning(this, this.getString(R.string.no_more_keys));
                        }
                    } else {
                        // No more data to load
                        hasMoreData = false;
                        ToastUtils.showWarning(this, getString(R.string.no_more_keys));
                    }
                    
                    isLoadingMore = false;
                    dismissWaitingDialog();
                });
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error loading more data: %s", e.getMessage());
                runOnUiThread(() -> {
                    isLoadingMore = false;
                    dismissWaitingDialog();
                    ToastUtils.showError(this, getString(R.string.error_loading_more_keys) + e.getMessage());
                });
            }
        }).start();
    }

    @Override
    protected void setupButtons() {
        // Set click listeners
        deleteButton.setOnClickListener(v -> {
            List<KeyInfo> chosenObjects = keyCardContainer.getSelectedKeys();
            if (chosenObjects.isEmpty()) {
                Toast.makeText(this, R.string.no_items_selected, SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Show waiting dialog during deletion
            showWaitingDialog(getString(R.string.deleting));

            // Perform deletion on background thread
            new Thread(() -> {
                try {
                    // Implement delete functionality
                    keyInfoManager.removeKeyInfos(chosenObjects);

                    // Commit changes to disk
                    keyInfoManager.commit();

                    runOnUiThread(() -> {
                        // Remove deleted items from the current list
                        keyInfoList.removeAll(chosenObjects);

                        // Remove from card container
                        keyCardContainer.removeSelectedKeys();

                        // Update button states
                        updateButtonStates();

                        // Dismiss waiting dialog
                        dismissWaitingDialog();

                        // Show confirmation message
                        Toast.makeText(this, R.string.deleted , SafeApplication.TOAST_LASTING).show();
                    });
                } catch (Exception e) {
                    TimberLogger.e(TAG, "Error deleting keys: %s", e.getMessage());
                    runOnUiThread(() -> {
                        dismissWaitingDialog();
                        Toast.makeText(this, getString(R.string.error_delete), SafeApplication.TOAST_LASTING).show();
                    });
                }
            }).start();
        });

        createNewButton.setOnClickListener(v -> {
            // Set flag to indicate we're creating a new key
            getIntent().putExtra("fromCreateKey", true);
            popupMenuHelper.showCreateKeyMenu(createNewButton);
        });

        addToListButton.setOnClickListener(v -> {
            List<KeyInfo> chosenObjects = keyCardContainer.getSelectedKeys();
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

        exportButton.setOnClickListener(v -> {
            List<KeyInfo> chosenObjects = keyCardContainer.getSelectedKeys();
            if (chosenObjects.isEmpty()) {
                Toast.makeText(this, R.string.no_items_selected , SafeApplication.TOAST_LASTING).show();
                return;
            }
            String keyInfoListJson = com.fc.fc_ajdk.utils.JsonUtils.toJson(chosenObjects);
            Intent intent = new Intent(MyKeysActivity.this, com.fc.safe.myKeys.ExportKeysActivity.class);
            intent.putExtra("keyInfoList", keyInfoListJson);
            startActivity(intent);
        });

        // Set up selection change listener for the container
        if (keyCardContainer != null) {
            keyCardContainer.setOnKeyListChangedListener(updatedKeyList -> {
                updateButtonStates();
                updateStatisticsBar();
            });
        }
    }

    private void setupSortControls() {
        // Set up "All" checkbox
        checkboxAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            keyCardContainer.selectAll(isChecked);
            updateButtonStates();
            updateStatisticsBar();
        });

        // Set up FID sort
        sortFidContainer.setOnClickListener(v -> {
            fidSortState = getNextSortState(fidSortState);
            timeSortState = SortState.NONE;
            labelSortState = SortState.NONE;
            updateSortIcons();
            applySorting();
        });

        // Set up Time sort
        sortTimeContainer.setOnClickListener(v -> {
            timeSortState = getNextSortState(timeSortState);
            fidSortState = SortState.NONE;
            labelSortState = SortState.NONE;
            updateSortIcons();
            applySorting();
        });

        // Set up Label sort
        sortLabelContainer.setOnClickListener(v -> {
            labelSortState = getNextSortState(labelSortState);
            fidSortState = SortState.NONE;
            timeSortState = SortState.NONE;
            updateSortIcons();
            applySorting();
        });

        // Set initial icons
        updateSortIcons();
    }

    private SortState getNextSortState(SortState current) {
        switch (current) {
            case NONE:
                return SortState.ASC;
            case ASC:
                return SortState.DESC;
            case DESC:
                return SortState.NONE;
            default:
                return SortState.NONE;
        }
    }

    private void updateSortIcons() {
        updateSortIcon(sortFidIcon, fidSortState);
        updateSortIcon(sortTimeIcon, timeSortState);
        updateSortIcon(sortLabelIcon, labelSortState);
    }

    private void updateSortIcon(ImageView icon, SortState state) {
        switch (state) {
            case NONE:
                icon.setImageResource(R.drawable.ic_sort_none);
                break;
            case ASC:
                icon.setImageResource(R.drawable.ic_sort_asc);
                break;
            case DESC:
                icon.setImageResource(R.drawable.ic_sort_desc);
                break;
        }
    }

    private void applySorting() {
        if (fidSortState != SortState.NONE) {
            keyCardContainer.sortById(fidSortState == SortState.ASC, true);
        } else if (timeSortState != SortState.NONE) {
            keyCardContainer.sortBySaveTime(timeSortState == SortState.ASC, true);
        } else if (labelSortState != SortState.NONE) {
            keyCardContainer.sortByLabel(labelSortState == SortState.ASC, true);
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

        // Set up scroll listener
        new android.os.Handler().postDelayed(this::setupScrollListener, 100);
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

    /**
     * Updates the statistics bar with current counts
     */
    private void updateStatisticsBar() {
        // Update loaded count
        int loadedCount = keyInfoList != null ? keyInfoList.size() : 0;
        if (statsLoadedCountTextView != null) {
            statsLoadedCountTextView.setText(String.valueOf(loadedCount));
        }

        // Update total count from database
        int totalCount = keyInfoManager != null ? keyInfoManager.getKeyInfoDB().getSize() : 0;
        if (statsTotalCountTextView != null) {
            statsTotalCountTextView.setText(String.valueOf(totalCount));
        }

        // Update checked count
        int checkedCount = keyCardContainer != null ? keyCardContainer.getSelectedKeys().size() : 0;
        if (statsCheckedCountTextView != null) {
            statsCheckedCountTextView.setText(String.valueOf(checkedCount));
        }

        // Hide statistics bar if list is empty
        if (statisticsBar != null) {
            statisticsBar.setVisibility(loadedCount == 0 ? View.GONE : View.VISIBLE);
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