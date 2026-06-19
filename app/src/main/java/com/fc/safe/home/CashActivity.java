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
import android.app.Dialog;

import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.safe.db.LocalDB;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.ui.PopupMenuHelper;
import com.fc.safe.ui.WaitingDialog;
import com.fc.safe.db.CashManager;
import com.fc.safe.tx.dialog.CreateCashDialog;
import com.fc.safe.multisign.CreateMultisignTxActivity;
import com.fc.safe.utils.CashCardContainer;
import com.fc.safe.utils.ToastUtils;
import com.fc.safe.utils.ChooseMode;

import java.util.ArrayList;
import java.util.List;

public class CashActivity extends BaseCryptoActivity {
    private static final String TAG = "Cash";
    private static final int DEFAULT_PAGE_SIZE = 50; // Default page size for loading cash
    private static final int REQUEST_CREATE_TX = 1001; // Request code for CreateTxActivity
    
    public static final String EXTRA_SELECT_MODE = "select_mode";
    public static final String EXTRA_SELECTED_CASH = "selected_cash";

    private CashCardContainer cashCardContainer;
    protected CashManager cashManager;

    private Button deleteButton;
    private Button createNewButton;
    private Button sendButton;
    private Button importButton;
    private PopupMenuHelper popupMenuHelper;

    // Summary card UI elements
    private TextView selectedCashCountTextView;
    private TextView selectedCashAmountTextView;
    private TextView selectedCashCdTextView;

    // Statistics bar UI elements
    private View statisticsBar;
    private TextView statsLoadedCountTextView;
    private TextView statsTotalCountTextView;
    private TextView statsCheckedCountTextView;

    // Sort controls
    private CheckBox checkboxAll;
    private ImageView sortOwnerIcon;
    private ImageView sortValueIcon;
    private ImageView sortCdIcon;
    private LinearLayout sortOwnerContainer;
    private LinearLayout sortValueContainer;
    private LinearLayout sortCdContainer;

    private enum SortState { NONE, ASC, DESC }
    private SortState ownerSortState = SortState.NONE;
    private SortState valueSortState = SortState.NONE;
    private SortState cdSortState = SortState.NONE;
    
    private final List<Cash> cashList = new ArrayList<>();
    private Long lastIndex = null;
    private boolean isLoadingMore = false;
    private boolean hasMoreData = true;
    private LocalDB<Cash> cashDB;

    private WaitingDialog waitingDialog;
    private Dialog currentDialog;
    private TextView emptyStateTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Initialize CashManager
            cashManager = CashManager.getInstance(this);

            // Initialize PopupMenuHelper
            popupMenuHelper = new PopupMenuHelper(this);

            // Check if we're in selection mode
            boolean isSelectMode = getIntent().getBooleanExtra(EXTRA_SELECT_MODE, false);
            if (isSelectMode) {
                // Change the send button to return button
                sendButton = findViewById(R.id.send_button);
                if (sendButton != null) {
                    sendButton.setText(R.string.return_text);
                }
            }

            // Load initial page of Cash objects
            loadInitialData();
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error initializing CashActivity: %s", e.getMessage());
            showToast(getString(R.string.error_initializing_cash_activity)+":"+e.getMessage());
            
            // Initialize empty state to prevent crash
            cashList.clear();
            hasMoreData = false;
            lastIndex = null;
            
            // Initialize empty fragment
            loadListFragment();
            updateButtonStates();
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_cash;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.cash);
    }

    @Override
    protected void initializeViews() {
        // Initialize buttons
        deleteButton = findViewById(R.id.delete_button);
        createNewButton = findViewById(R.id.create_button);
        sendButton = findViewById(R.id.send_button);
        importButton = findViewById(R.id.import_button);

        // Initialize summary card UI elements
        selectedCashCountTextView = findViewById(R.id.selectedCashCount);
        selectedCashAmountTextView = findViewById(R.id.selectedCashAmount);
        selectedCashCdTextView = findViewById(R.id.selectedCashCd);

        // Initialize statistics bar UI elements
        statisticsBar = findViewById(R.id.statistics_bar);
        statsLoadedCountTextView = findViewById(R.id.stats_loaded_count);
        statsTotalCountTextView = findViewById(R.id.stats_total_count);
        statsCheckedCountTextView = findViewById(R.id.stats_checked_count);

        // Initialize sort controls
        checkboxAll = findViewById(R.id.checkbox_all);
        sortOwnerIcon = findViewById(R.id.sort_owner_icon);
        sortValueIcon = findViewById(R.id.sort_value_icon);
        sortCdIcon = findViewById(R.id.sort_cd_icon);
        sortOwnerContainer = findViewById(R.id.sort_owner_container);
        sortValueContainer = findViewById(R.id.sort_value_container);
        sortCdContainer = findViewById(R.id.sort_cd_container);

        // Initialize empty state text
        emptyStateTextView = findViewById(R.id.empty_state_text);

        // Initialize CashCardContainer
        LinearLayout cashListContainer = findViewById(R.id.cash_list_container);
        cashCardContainer = new CashCardContainer(this, cashListContainer, ChooseMode.CHOOSE_MULTI);

        // Set up sort controls
        setupSortControls();

        // Initialize summary card with zeros
        updateSummaryCard();

        // Initialize statistics bar with zeros
        updateStatisticsBar();
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Check if any dialog is showing and handle its QR scan result
        if (currentDialog != null) {
            if (currentDialog instanceof CreateCashDialog) {
                ((CreateCashDialog) currentDialog).handleQrScanResult(requestCode, qrContent);
            }
        }
        // CashActivity doesn't use QR scanning functionality for main activity
    }

    /**
     * Loads the initial page of Cash objects
     */
    private void loadInitialData() {
        // Show loading indicator
        try {
            // Get the first page of Cash objects from the end
            List<Cash> newList = cashManager.getPaginatedCashes(DEFAULT_PAGE_SIZE, null, true);

            if(newList == null || newList.isEmpty()) {
                dismissWaitingDialog();
                showToast(getString(R.string.no_cash_found));
                updateButtonStates();
                // Initialize empty fragment even when no cash found
                loadListFragment();
                return;
            }

            TimberLogger.i(TAG, "Initial load: %d cash loaded", newList.size());

            cashList.clear();
            cashList.addAll(newList);

            // If we have data, get the last index for pagination
            if (!cashList.isEmpty()) {
                // Get the last item's index
                lastIndex = cashManager.getIndexById(cashList.get(cashList.size() - 1).getId());

                // Check if there's more data to load
                hasMoreData = lastIndex != null && lastIndex > 1;
            } else {
                // No data available
                hasMoreData = false;
                lastIndex = null;
            }

            updateButtonStates();
            loadListFragment();
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error loading initial data: %s", e.getMessage());
            showToast(getString(R.string.error_loading_cash, e.getMessage()));
        }
    }

    private void updateButtonStates() {
        boolean hasCash = !(cashList==null || cashList.isEmpty());

        deleteButton.setEnabled(hasCash);
        deleteButton.setAlpha(hasCash ? 1.0f : 0.5f);
        sendButton.setEnabled(hasCash);
        sendButton.setAlpha(hasCash ? 1.0f : 0.5f);
        importButton.setEnabled(true); // Import button is always enabled
        importButton.setAlpha(1.0f);

        // Update statistics bar
        updateStatisticsBar();
    }

    private void loadListFragment() {
        // Clear existing cards
        cashCardContainer.clearAll();

        // Check if cash list is empty
        if (cashList == null || cashList.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
            // Add all cash cards
            for (Cash cash : cashList) {
                cashCardContainer.addCashCard(cash);
            }
        }

        // Apply default sorting if needed
        applySorting();

        // Update button states after list is loaded
        updateButtonStates();
        // Update summary card after list is loaded
        updateSummaryCard();
    }

    /**
     * Sets up the scroll listener to detect when the user reaches the end of the list
     */
    private void setupScrollListener() {
        // Get the ScrollView from the layout
        ScrollView scrollView = findViewById(android.R.id.content).findViewById(R.id.cash_list_container).getParent() instanceof ScrollView ?
                (ScrollView) findViewById(R.id.cash_list_container).getParent() : null;

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
        showWaitingDialog("Loading more cash...");
        
        // Perform database operations on background thread
        new Thread(() -> {
            try {
                // Get the next page of Cash objects
                List<Cash> moreCash = cashManager.getPaginatedCashes(DEFAULT_PAGE_SIZE, lastIndex, true);
                
                runOnUiThread(() -> {
                    if (moreCash != null && !moreCash.isEmpty()) {
                        // Update the lastIndex for the next page
                        lastIndex = cashManager.getIndexById(moreCash.get(moreCash.size() - 1).getId());
                        
                        // Check if there's more data to load
                        hasMoreData = lastIndex != null && lastIndex > 1;
                        
                        // Add the new items to the list
                        cashList.addAll(moreCash);

                        // Add the new cards to the card manager
                        for (Cash cash : moreCash) {
                            cashCardContainer.addCashCard(cash);
                        }

                        // Apply current sorting
                        applySorting();

                        updateButtonStates();
                        updateSummaryCard();

                        ToastUtils.showInfo(this, getString(R.string.cash_loaded, moreCash.size()));
                        
                        if (!hasMoreData) {
                            ToastUtils.showWarning(this, this.getString(R.string.no_more_cash));
                        }
                    } else {
                        // No more data to load
                        hasMoreData = false;
                        ToastUtils.showWarning(this, getString(R.string.no_more_cash));
                    }
                    
                    isLoadingMore = false;
                    dismissWaitingDialog();
                });
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error loading more data: %s", e.getMessage());
                runOnUiThread(() -> {
                    isLoadingMore = false;
                    dismissWaitingDialog();
                    ToastUtils.showError(this, getString(R.string.error_loading_more_cash) + e.getMessage());
                });
            }
        }).start();
    }

    @Override
    protected void setupButtons() {
        // Set click listeners
        deleteButton.setOnClickListener(v -> {
            List<Cash> chosenObjects = cashCardContainer.getSelectedCashes();
            if (chosenObjects.isEmpty()) {
                Toast.makeText(this, R.string.no_items_selected, SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Don't delete cashes that are referenced by a pending TX — it would orphan the record.
            for (Cash cash : chosenObjects) {
                if (cash.isLockedByPending()) {
                    Toast.makeText(this, R.string.cash_locked_by_pending_tx, SafeApplication.TOAST_LASTING).show();
                    return;
                }
            }

            // Show waiting dialog
            showWaitingDialog(getString(R.string.deleting));

            // Perform delete operation on background thread
            new Thread(() -> {
                try {
                    // Implement delete functionality
                    cashManager.removeCashes(chosenObjects);

                    // Commit changes to disk
                    cashManager.commit();

                    // Update UI on main thread
                    runOnUiThread(() -> {
                        // Remove deleted items from the current list
                        cashList.removeAll(chosenObjects);

                        // Remove from card manager
                        cashCardContainer.removeSelectedCashes();

                        // Update button states
                        updateButtonStates();
                        updateSummaryCard();

                        // Dismiss waiting dialog
                        dismissWaitingDialog();

                        // Show confirmation message
                        Toast.makeText(this, R.string.deleted, SafeApplication.TOAST_LASTING).show();
                    });
                } catch (Exception e) {
                    TimberLogger.e(TAG, "Error deleting cash: %s", e.getMessage());
                    runOnUiThread(() -> {
                        dismissWaitingDialog();
                        ToastUtils.showError(this, getString(R.string.error_deleting_cash) + ": " + e.getMessage());
                    });
                }
            }).start();
        });

        createNewButton.setOnClickListener(v -> {
            // Show CreateCashDialog
            CreateCashDialog createCashDialog = new CreateCashDialog(this);
            currentDialog = createCashDialog;
            createCashDialog.setOnDoneListener(cash -> {
                // Add the created cash to the database
                cashManager.addCash(cash);
                cashManager.commit();
                
                // Add to the current list
                cashList.add(0, cash); // Add to the beginning of the list

                // Reload the list to show the new cash
                loadListFragment();

                // Update button states
                updateButtonStates();
                updateSummaryCard();
                
                // Show success message
                Toast.makeText(this, R.string.cash_created_successfully, SafeApplication.TOAST_LASTING).show();
                
                // Clear current dialog reference
                currentDialog = null;
            });
            createCashDialog.setOnDismissListener(dialog -> {
                currentDialog = null;
            });
            createCashDialog.show();
        });

        sendButton.setOnClickListener(v -> {
            List<Cash> chosenObjects = cashCardContainer.getSelectedCashes();
            if (chosenObjects.isEmpty()) {
                Toast.makeText(this, R.string.no_items_selected , SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Reject any cash that's locked by a pending TX (or still pending-incoming).
            for (Cash cash : chosenObjects) {
                if (cash.isLockedByPending()) {
                    Toast.makeText(this, R.string.cash_locked_by_pending_tx, SafeApplication.TOAST_LASTING).show();
                    return;
                }
                // Pending-incoming cashes are valid=false — also skip.
                if (cash.isValid() == null || !cash.isValid()) {
                    Toast.makeText(this, R.string.cash_locked_by_pending_tx, SafeApplication.TOAST_LASTING).show();
                    return;
                }
            }

            // Check if we're in selection mode
            boolean isSelectMode = getIntent().getBooleanExtra(EXTRA_SELECT_MODE, false);
            if (isSelectMode) {
                // Return selected cash to the calling activity
                Intent resultIntent = new Intent();
                // Convert Cash objects to JSON strings
                List<String> cashJsonList = new ArrayList<>();
                for (Cash cash : chosenObjects) {
                    cashJsonList.add(cash.toJson());
                }
                resultIntent.putStringArrayListExtra(EXTRA_SELECTED_CASH, new ArrayList<>(cashJsonList));
                setResult(RESULT_OK, resultIntent);
                finish();
                return;
            }

            // Normal send mode - validate that all cash have the same owner FID or null
            String ownerFid = null;
            for (Cash cash : chosenObjects) {
                String cashOwner = cash.getOwner();
                if (cashOwner != null && !cashOwner.isEmpty()) {
                    if (ownerFid == null) {
                        ownerFid = cashOwner;
                    } else if (!ownerFid.equals(cashOwner)) {
                        Toast.makeText(this, getString(R.string.all_cash_must_have_same_owner), SafeApplication.TOAST_LASTING).show();
                        return;
                    }
                }
            }

            // Create RawTxInfo with selected cash
            com.fc.fc_ajdk.core.fch.RawTxInfo rawTxInfo = new com.fc.fc_ajdk.core.fch.RawTxInfo();
            rawTxInfo.setInputs(chosenObjects);
            
            // Set sender if we have a valid owner FID
            if (ownerFid != null) {
                rawTxInfo.setSender(ownerFid);
            }
            
            // Convert to JSON string
            String rawTxInfoJson = rawTxInfo.toJsonWithSenderInfo();
            
            // Check if sender FID starts with '3' (multisig address)
            if (ownerFid != null && ownerFid.startsWith("3")) {
                // Start CreateMultisignTxActivity for multisig transaction
                Intent intent = new Intent(CashActivity.this, CreateMultisignTxActivity.class);
                intent.putExtra(CreateMultisignTxActivity.EXTRA_TX_INFO_JSON, rawTxInfoJson);
                startActivityForResult(intent, REQUEST_CREATE_TX);
            } else {
                // Start CreateTxActivity for normal transaction
                Intent intent = new Intent(CashActivity.this, CreateTxActivity.class);
                intent.putExtra(CreateTxActivity.EXTRA_TX_INFO_JSON, rawTxInfoJson);
                startActivityForResult(intent, REQUEST_CREATE_TX);
            }
        });

        importButton = findViewById(R.id.import_button);
        importButton.setOnClickListener(v -> {
            Intent intent = new Intent(CashActivity.this, ImportCashActivity.class);
            startActivityForResult(intent, 2001);
        });

        // Set up selection change listener for the card manager
        if (cashCardContainer != null) {
            cashCardContainer.setOnCashListChangedListener(updatedCashList -> {
                updateButtonStates();
                updateSummaryCard();
                updateStatisticsBar();
            });
        }
    }
    
    private void refreshList() {
        // Clear existing list
        cashList.clear();
        
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
        
        // Only refresh if we're returning from creating a new cash
        if (getIntent().getBooleanExtra("fromCreateCash", false)) {
            refreshList();
            // Clear the flag
            getIntent().removeExtra("fromCreateCash");
        }
        
        // Set up scroll listener
        new android.os.Handler().postDelayed(this::setupScrollListener, 100);
    }

    /**
     * Updates the summary card with totals from selected cash list
     */
    private void updateSummaryCard() {
        List<Cash> selectedCashList = cashCardContainer != null ? cashCardContainer.getSelectedCashes() : new ArrayList<>();

        // Calculate totals
        int cashCount = selectedCashList.size();
        long totalValue = 0;  // in satoshi
        long totalCd = 0;

        for (Cash cash : selectedCashList) {
            // Sum values
            if (cash.getValue() != null) {
                totalValue += cash.getValue();
            }

            // Sum CD (Coin Days)
            if (cash.getCd() != null) {
                totalCd += cash.getCd();
            }
        }

        // Update UI elements
        if (selectedCashCountTextView != null) {
            selectedCashCountTextView.setText(String.valueOf(cashCount));
        }

        if (selectedCashAmountTextView != null) {
            // Convert satoshi to formatted string using FchUtils.formatSatoshiValue
            String formattedAmount = FchUtils.formatSatoshiValue(totalValue);
            selectedCashAmountTextView.setText(formattedAmount);
        }

        if (selectedCashCdTextView != null) {
            // Format large number for CD (using the same format from HomeActivity)
            String formattedCd = formatLargeNumber(totalCd);
            selectedCashCdTextView.setText(formattedCd);
        }
    }
    
    /**
     * Helper method to format large numbers (borrowed from HomeActivity pattern)
     */
    private String formatLargeNumber(long number) {
        if (number >= 1000000000) {
            return String.format("%.1fb", number / 1000000000.0);
        } else if (number >= 1000000) {
            return String.format("%.1fm", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fk", number / 1000.0);
        } else {
            return String.valueOf(number);
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

    private void showEmptyState() {
        if (emptyStateTextView != null) {
            emptyStateTextView.setVisibility(View.VISIBLE);
        }
    }

    private void hideEmptyState() {
        if (emptyStateTextView != null) {
            emptyStateTextView.setVisibility(View.GONE);
        }
    }

    private void setupSortControls() {
        // Set up "All" checkbox
        checkboxAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cashCardContainer.selectAll(isChecked);
            updateButtonStates();
            updateSummaryCard();
            updateStatisticsBar();
        });

        // Set up Owner sort
        sortOwnerContainer.setOnClickListener(v -> {
            ownerSortState = getNextSortState(ownerSortState);
            valueSortState = SortState.NONE;
            cdSortState = SortState.NONE;
            updateSortIcons();
            applySorting();
        });

        // Set up Value sort
        sortValueContainer.setOnClickListener(v -> {
            valueSortState = getNextSortState(valueSortState);
            ownerSortState = SortState.NONE;
            cdSortState = SortState.NONE;
            updateSortIcons();
            applySorting();
        });

        // Set up CD sort
        sortCdContainer.setOnClickListener(v -> {
            cdSortState = getNextSortState(cdSortState);
            ownerSortState = SortState.NONE;
            valueSortState = SortState.NONE;
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
        updateSortIcon(sortOwnerIcon, ownerSortState);
        updateSortIcon(sortValueIcon, valueSortState);
        updateSortIcon(sortCdIcon, cdSortState);
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
        if (ownerSortState != SortState.NONE) {
            cashCardContainer.sortByOwner(ownerSortState == SortState.ASC, true);
        } else if (valueSortState != SortState.NONE) {
            cashCardContainer.sortByValue(valueSortState == SortState.ASC, true);
        } else if (cdSortState != SortState.NONE) {
            cashCardContainer.sortByCd(cdSortState == SortState.ASC, true);
        }
    }

    /**
     * Updates the statistics bar with current counts
     */
    private void updateStatisticsBar() {
        // Update loaded count
        int loadedCount = cashList != null ? cashList.size() : 0;
        if (statsLoadedCountTextView != null) {
            statsLoadedCountTextView.setText(String.valueOf(loadedCount));
        }

        // Update total count from database
        int totalCount = cashManager != null ? cashManager.getCashDB().getSize() : 0;
        if (statsTotalCountTextView != null) {
            statsTotalCountTextView.setText(String.valueOf(totalCount));
        }

        // Update checked count
        int checkedCount = cashCardContainer != null ? cashCardContainer.getSelectedCashes().size() : 0;
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CREATE_TX) {
            // Refresh cash list when returning from CreateTxActivity
            // This is needed because cash may have been added or removed during transaction creation/signing
            TimberLogger.i(TAG, "Returned from CreateTxActivity, refreshing cash list");
            refreshList();
        } else if (requestCode == 2001 && resultCode == RESULT_OK) {
            // Handle import result
            refreshList();
        }
    }
} 