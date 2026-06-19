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

import com.fc.fc_ajdk.data.feipData.Secret;
import com.fc.safe.db.LocalDB;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.SecretManager;
import com.fc.safe.myKeys.CreateSecretActivity;
import com.fc.safe.secret.ExportSecretActivity;
import com.fc.safe.secret.ImportSecretActivity;
import com.fc.safe.secret.SecretCardContainer;
import com.fc.safe.ui.PopupMenuHelper;
import com.fc.safe.utils.ChooseMode;
import com.fc.safe.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;

public class SecretActivity extends BaseCryptoActivity {
    private static final String TAG = "Secrets";
    private static final int DEFAULT_PAGE_SIZE = 20;

    private SecretCardContainer secretCardContainer;
    private LinearLayout secretListContainer;
    private ScrollView scrollView;
    private SecretManager secretManager;
    private Button deleteButton;
    private Button createNewButton;
    private Button importButton;
    private Button exportButton;

    private CheckBox checkboxAll;
    private ImageView sortTimeIcon;
    private ImageView sortTypeIcon;
    private ImageView sortTitleIcon;
    private LinearLayout sortTimeContainer;
    private LinearLayout sortTypeContainer;
    private LinearLayout sortTitleContainer;

    // Statistics bar UI elements
    private View statisticsBar;
    private TextView statsLoadedCountTextView;
    private TextView statsTotalCountTextView;
    private TextView statsCheckedCountTextView;

    private enum SortState { NONE, ASC, DESC }
    private SortState timeSortState = SortState.DESC;  // Default
    private SortState typeSortState = SortState.NONE;
    private SortState titleSortState = SortState.NONE;

    private final List<Secret> secretList = new ArrayList<>();
    private Long lastIndex = null;
    private boolean isLoadingMore = false;
    private boolean hasMoreData = true;
    private LocalDB<Secret> secretDetailDB;
    private PopupMenuHelper popupMenuHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        secretManager = SecretManager.getInstance(this);
        secretDetailDB = secretManager.getSecretDetailDB();

        popupMenuHelper = new PopupMenuHelper(this);

        loadInitialData();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_secret;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.my_secrets);
    }

    @Override
    protected void initializeViews() {
        deleteButton = findViewById(R.id.delete_button);
        createNewButton = findViewById(R.id.create_button);
        importButton = findViewById(R.id.import_button);
        exportButton = findViewById(R.id.export_button);
        secretListContainer = findViewById(R.id.secret_list_container);
        scrollView = findViewById(R.id.scroll_view);

        // Initialize sort controls
        checkboxAll = findViewById(R.id.checkbox_all);
        sortTimeIcon = findViewById(R.id.sort_time_icon);
        sortTypeIcon = findViewById(R.id.sort_type_icon);
        sortTitleIcon = findViewById(R.id.sort_title_icon);
        sortTimeContainer = findViewById(R.id.sort_time_container);
        sortTypeContainer = findViewById(R.id.sort_type_container);
        sortTitleContainer = findViewById(R.id.sort_title_container);

        // Initialize statistics bar UI elements
        statisticsBar = findViewById(R.id.statistics_bar);
        statsLoadedCountTextView = findViewById(R.id.stats_loaded_count);
        statsTotalCountTextView = findViewById(R.id.stats_total_count);
        statsCheckedCountTextView = findViewById(R.id.stats_checked_count);

        // Set up sort controls
        setupSortControls();

        // Initialize statistics bar with zeros
        updateStatisticsBar();
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // SecretActivity doesn't use QR scanning functionality (similar to MyKeysActivity)
    }

    private void loadInitialData() {
        List<Secret> newList = secretManager.getPaginatedSecretDetails(DEFAULT_PAGE_SIZE, null, true);
        if(newList == null) {
            newList = new ArrayList<>();
        }
        TimberLogger.i(TAG, "Initial load: %d secrets loaded", newList.size());

        secretList.clear();
        secretList.addAll(newList);

        if (!secretList.isEmpty()) {
            lastIndex = secretManager.getIndexById(secretList.get(secretList.size() - 1).getId());
            
            hasMoreData = lastIndex != null && lastIndex > 1;
        } else {
            hasMoreData = false;
            lastIndex = null;
        }

        updateButtonStates();
        initializeSecretCardContainer();
        loadSecretCards();
    }

    private void updateButtonStates() {
        boolean hasItems = !secretList.isEmpty();
        deleteButton.setEnabled(hasItems);
        deleteButton.setAlpha(hasItems ? 1.0f : 0.5f);
        exportButton.setEnabled(hasItems);
        exportButton.setAlpha(hasItems ? 1.0f : 0.5f);

        // Update statistics bar
        updateStatisticsBar();
    }

    private void initializeSecretCardContainer() {
        secretCardContainer = new SecretCardContainer(this, secretListContainer, ChooseMode.CHOOSE_MULTI);

        // Set listener to update button states when selection changes
        secretCardContainer.setOnSecretListChangedListener(updatedSecretList -> {
            // Handle list changes if needed
            updateStatisticsBar();
        });
    }

    private void loadSecretCards() {
        if (secretCardContainer != null) {
            secretCardContainer.clearAll();
            for (Secret secret : secretList) {
                secretCardContainer.addSecretCard(secret);
            }

            // Apply default sort (saveTime DESC)
            applySorting();
        }
    }

    private void setupScrollListener() {
        if (scrollView != null) {
            scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (!v.canScrollVertically(1) && scrollY > oldScrollY) {
                    if (!isLoadingMore && hasMoreData) {
                        TimberLogger.i(TAG, "Loading more data - reached end of list");
                        loadMoreData();
                    }
                }
            });
        } else {
            TimberLogger.w(TAG, "ScrollView is null, cannot set up scroll listener.");
        }
    }

    private void loadMoreData() {
        if (isLoadingMore || !hasMoreData) {
            return;
        }
        
        isLoadingMore = true;
        TimberLogger.i(TAG, "Loading more data, lastIndex: %d", lastIndex);
        
        List<Secret> moreSecrets = secretManager.getPaginatedSecretDetails(DEFAULT_PAGE_SIZE, lastIndex, true);

        if (moreSecrets != null && !moreSecrets.isEmpty()) {
            lastIndex = secretManager.getIndexById(moreSecrets.get(moreSecrets.size() - 1).getId());
            hasMoreData = lastIndex != null && lastIndex > 1;

            secretList.addAll(moreSecrets);
            if (secretCardContainer != null) {
                for (Secret secret : moreSecrets) {
                    secretCardContainer.addSecretCard(secret);
                }

                // Apply current sorting
                applySorting();
            }
            updateButtonStates();

            runOnUiThread(() -> ToastUtils.showInfo(this, getString(R.string.secrets_loaded, moreSecrets.size())));

            if (!hasMoreData) {
                runOnUiThread(() -> ToastUtils.showInfo(this, getString(R.string.no_more_secrets)));
            }
        } else {
            hasMoreData = false;
            runOnUiThread(() -> ToastUtils.showInfo(this, getString(R.string.no_more_secrets)));
        }
        isLoadingMore = false;
    }

    @Override
    protected void setupButtons() {
        deleteButton.setOnClickListener(v -> {
            if (secretCardContainer == null) return;
            List<Secret> chosenObjects = secretCardContainer.getSelectedSecrets();
            if (chosenObjects.isEmpty()) {
                ToastUtils.showWarning(this, getString(R.string.no_items_selected));
                return;
            }
            secretManager.removeSecretDetails(chosenObjects);
            secretManager.commit();
            refreshList();
            ToastUtils.showInfo(this, getString(R.string.deleted));
        });

        createNewButton.setOnClickListener(v -> {
            getIntent().putExtra("fromCreateSecret", true);
            Intent intent = new Intent(SecretActivity.this, CreateSecretActivity.class);
            startActivity(intent);
        });

        importButton.setOnClickListener(v -> {
            Intent intent = new Intent(SecretActivity.this, ImportSecretActivity.class);
            startActivityForResult(intent, 2001);
        });

        exportButton.setOnClickListener(v -> {
            if (secretCardContainer == null) return;
            List<Secret> chosenObjects = secretCardContainer.getSelectedSecrets();
            if (chosenObjects.isEmpty()) {
                ToastUtils.showWarning(this, getString(R.string.no_items_selected));
                return;
            }
            String secretListJson = JsonUtils.toJson(chosenObjects);
            Intent intent = new Intent(SecretActivity.this, ExportSecretActivity.class);
            intent.putExtra("secretList", secretListJson);
            startActivity(intent);
        });
    }
    
    private void refreshList() {
        secretList.clear();
        lastIndex = null;
        hasMoreData = true;
        isLoadingMore = false;
        
        updateButtonStates();
        loadInitialData();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (getIntent().getBooleanExtra("fromCreateSecret", false)) {
            refreshList();
            getIntent().removeExtra("fromCreateSecret");
        }

        // Setup scroll listener after a short delay to ensure views are ready
        new android.os.Handler().postDelayed(() -> {
            if (scrollView != null) {
                setupScrollListener();
            } else {
                TimberLogger.w(TAG, "onResume: scrollView is still null after delay. Scroll listener not set.");
            }
        }, 200);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2001 && resultCode == RESULT_OK) {
            // Import secret activity result
            refreshList();
        } else if (requestCode == 2002 && resultCode == RESULT_OK) {
            // Update secret activity result
            refreshList();
        }
    }

    private void setupSortControls() {
        // Set up "All" checkbox
        checkboxAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            secretCardContainer.selectAll(isChecked);
            updateButtonStates();
            updateStatisticsBar();
        });

        // Set up Time sort
        sortTimeContainer.setOnClickListener(v -> {
            timeSortState = getNextSortState(timeSortState);
            typeSortState = SortState.NONE;
            titleSortState = SortState.NONE;
            updateSortIcons();
            applySorting();
        });

        // Set up Type sort
        sortTypeContainer.setOnClickListener(v -> {
            typeSortState = getNextSortState(typeSortState);
            timeSortState = SortState.NONE;
            titleSortState = SortState.NONE;
            updateSortIcons();
            applySorting();
        });

        // Set up Title sort
        sortTitleContainer.setOnClickListener(v -> {
            titleSortState = getNextSortState(titleSortState);
            timeSortState = SortState.NONE;
            typeSortState = SortState.NONE;
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
        updateSortIcon(sortTimeIcon, timeSortState);
        updateSortIcon(sortTypeIcon, typeSortState);
        updateSortIcon(sortTitleIcon, titleSortState);
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
        if (timeSortState != SortState.NONE) {
            secretCardContainer.sortBySaveTime(timeSortState == SortState.ASC, true);
        } else if (typeSortState != SortState.NONE) {
            secretCardContainer.sortByType(typeSortState == SortState.ASC, true);
        } else if (titleSortState != SortState.NONE) {
            secretCardContainer.sortByTitle(titleSortState == SortState.ASC, true);
        }
    }

    /**
     * Updates the statistics bar with current counts
     */
    private void updateStatisticsBar() {
        // Update loaded count
        int loadedCount = secretList != null ? secretList.size() : 0;
        if (statsLoadedCountTextView != null) {
            statsLoadedCountTextView.setText(String.valueOf(loadedCount));
        }

        // Update total count from database
        int totalCount = secretManager != null ? secretManager.getSecretDetailDB().getSize() : 0;
        if (statsTotalCountTextView != null) {
            statsTotalCountTextView.setText(String.valueOf(totalCount));
        }

        // Update checked count
        int checkedCount = secretCardContainer != null ? secretCardContainer.getSelectedSecrets().size() : 0;
        if (statsCheckedCountTextView != null) {
            statsCheckedCountTextView.setText(String.valueOf(checkedCount));
        }

        // Hide statistics bar if list is empty
        if (statisticsBar != null) {
            statisticsBar.setVisibility(loadedCount == 0 ? View.GONE : View.VISIBLE);
        }
    }
} 