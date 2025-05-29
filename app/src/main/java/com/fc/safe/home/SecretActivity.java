package com.fc.safe.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Toast;

import com.fc.fc_ajdk.data.fcData.SecretDetail;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.SecretManager;
import com.fc.safe.myKeys.CreateSecretActivity;
import com.fc.safe.secret.ExportSecretActivity;
import com.fc.safe.secret.ImportSecretActivity;
import com.fc.safe.ui.FcEntityListFragment;
import com.fc.safe.ui.PopupMenuHelper;

import java.util.ArrayList;
import java.util.List;

public class SecretActivity extends BaseCryptoActivity {
    private static final String TAG = "Secrets";
    private static final int DEFAULT_PAGE_SIZE = 20;

    private FcEntityListFragment<SecretDetail> entityListFragment;
    private SecretManager secretManager;
    private Button deleteButton;
    private Button createNewButton;
    private Button importButton;
    private Button exportButton;

    private final List<SecretDetail> secretDetailList = new ArrayList<>();
    private Long lastIndex = null;
    private boolean isLoadingMore = false;
    private boolean hasMoreData = true;
    private LocalDB<SecretDetail> secretDetailDB;
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
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // SecretActivity doesn't use QR scanning functionality (similar to MyKeysActivity)
    }

    private void loadInitialData() {
        List<SecretDetail> newList = secretManager.getPaginatedSecretDetails(DEFAULT_PAGE_SIZE, null, true);
        if(newList == null) {
            newList = new ArrayList<>();
        }
        TimberLogger.i(TAG, "Initial load: %d secrets loaded", newList.size());

        secretDetailList.clear();
        secretDetailList.addAll(newList);

        if (!secretDetailList.isEmpty()) {
            lastIndex = secretManager.getIndexById(secretDetailList.get(secretDetailList.size() - 1).getId());
            
            hasMoreData = lastIndex != null && lastIndex > 1;
        } else {
            hasMoreData = false;
            lastIndex = null;
        }

        updateButtonStates();
        loadListFragment();
    }

    private void updateButtonStates() {
        boolean hasItems = !secretDetailList.isEmpty();
        deleteButton.setEnabled(hasItems);
        deleteButton.setAlpha(hasItems ? 1.0f : 0.5f);
        exportButton.setEnabled(hasItems);
        exportButton.setAlpha(hasItems ? 1.0f : 0.5f);
    }

    private void loadListFragment() {
        entityListFragment = FcEntityListFragment.newInstance(secretDetailList, SecretDetail.class, false);
        
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, entityListFragment)
                .commit();
    }

    private void setupScrollListener() {
        View fragmentView = entityListFragment.getView();
        if (fragmentView != null) {
            View scrollView = findScrollView(fragmentView);
            if (scrollView instanceof ScrollView) {
                ((ScrollView) scrollView).setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (!v.canScrollVertically(1) && scrollY > oldScrollY) {
                        if (!isLoadingMore && hasMoreData) {
                             TimberLogger.i(TAG, "Loading more data - reached end of list");
                             loadMoreData();
                        }
                    }
                });
            } else {
                 TimberLogger.w(TAG, "ScrollView not found in fragment view hierarchy for scroll listener setup.");
            }
        } else {
             TimberLogger.w(TAG, "Fragment view is null, cannot set up scroll listener.");
        }
    }
    
    private View findScrollView(View view) {
        if (view instanceof ScrollView) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                View result = findScrollView(child);
                if (result instanceof ScrollView) {
                    return result;
                }
            }
        }
        return null;
    }

    private void loadMoreData() {
        if (isLoadingMore || !hasMoreData) {
            return;
        }
        
        isLoadingMore = true;
        TimberLogger.i(TAG, "Loading more data, lastIndex: %d", lastIndex);
        
        List<SecretDetail> moreSecrets = secretManager.getPaginatedSecretDetails(DEFAULT_PAGE_SIZE, lastIndex, true);
        
        if (moreSecrets != null && !moreSecrets.isEmpty()) {
            lastIndex = secretManager.getIndexById(moreSecrets.get(moreSecrets.size() - 1).getId());
            hasMoreData = lastIndex != null && lastIndex > 1;
            
            secretDetailList.addAll(moreSecrets);
            if (entityListFragment != null) {
                entityListFragment.updateList(new ArrayList<>(secretDetailList));
            }
            updateButtonStates();

            runOnUiThread(() -> Toast.makeText(this, getString(R.string.secrets_loaded, moreSecrets.size()), Toast.LENGTH_SHORT).show());
            
            if (!hasMoreData) {
                runOnUiThread(() -> Toast.makeText(this, R.string.no_more_secrets , Toast.LENGTH_LONG).show());
            }
        } else {
            hasMoreData = false;
            runOnUiThread(() -> Toast.makeText(this, R.string.no_more_secrets , Toast.LENGTH_LONG).show());
        }
        isLoadingMore = false;
    }

    @Override
    protected void setupButtons() {
        deleteButton.setOnClickListener(v -> {
            if (entityListFragment == null) return;
            List<SecretDetail> chosenObjects = entityListFragment.getSelectedObjects();
            if (chosenObjects.isEmpty()) {
                Toast.makeText(this, R.string.no_items_selected, SafeApplication.TOAST_LASTING).show();
                return;
            }
            secretManager.removeSecretDetails(chosenObjects);
            secretManager.commit();
            refreshList();
            Toast.makeText(this, R.string.deleted, SafeApplication.TOAST_LASTING).show();
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
            if (entityListFragment == null) return;
            List<SecretDetail> chosenObjects = entityListFragment.getSelectedObjects();
            if (chosenObjects.isEmpty()) {
                Toast.makeText(this, R.string.no_items_selected, SafeApplication.TOAST_LASTING).show();
                return;
            }
            String secretListJson = JsonUtils.toJson(chosenObjects);
            Intent intent = new Intent(SecretActivity.this, ExportSecretActivity.class);
            intent.putExtra("secretList", secretListJson);
            startActivity(intent);
        });
    }
    
    private void refreshList() {
        secretDetailList.clear();
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
        
        if (entityListFragment != null && entityListFragment.getView() != null) {
            setupScrollListener();
        } else {
            new android.os.Handler().postDelayed(() -> {
                if (entityListFragment != null && entityListFragment.getView() != null) {
                    setupScrollListener();
                } else {
                    TimberLogger.w(TAG, "onResume: entityListFragment or its view is still null after delay. Scroll listener not set.");
                }
            }, 200);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2001 && resultCode == RESULT_OK) {
            refreshList();
        }
    }
} 