package com.fc.safe.home;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.db.CashManager;
import com.fc.safe.utils.FileUtils;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

public class ImportCashActivity extends BaseCryptoActivity {
    private static final String TAG = "ImportCashActivity";
    private static final int QR_SCAN_JSON_REQUEST_CODE = 1001;
    private static final int PERMISSION_REQUEST_STORAGE = 1001;

    private LinearLayout cashJsonInputContainer;
    private LinearLayout cashButtonContainer;
    private TextInputEditText cashJsonInput;
    private Button cashClearButton;
    private Button cashImportButton;
    private String type;
    private FcCashImporter fcCashImporter;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> inputLauncher;
    private List<Cash> importedCashList;
    private String currentFilePath;
    private boolean isFileMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getIntent().getStringExtra("type");
        initCashImporter();
        checkStoragePermission();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_STORAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                Toast.makeText(this, getString(R.string.storage_permission_is_required_to_read_backup_files), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initCashImporter() {
        fcCashImporter = new FcCashImporter(this, new FcCashImporter.OnImportListener() {
            @Override
            public void onImportSuccess(List<Cash> result) {
                CashManager.saveAndFinish(ImportCashActivity.this, result);
            }

            @Override
            public void onImportError(String error) {
                showToast(getString(R.string.operation_failed_with_message, error));
            }
        });
        fcCashImporter.setType(type);
    }

    private void initViews() {
        cashJsonInputContainer = findViewById(R.id.cashJsonInputContainer);
        cashButtonContainer = findViewById(R.id.cashButtonContainer);

        cashJsonInput = cashJsonInputContainer.findViewById(R.id.cashJsonInput).findViewById(R.id.textInput);
        cashJsonInput.setHint(R.string.input_the_cash_json);

        cashClearButton = findViewById(R.id.cashClearButton);
        cashImportButton = findViewById(R.id.cashImportButton);

        // Initialize launchers
        inputLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        fcCashImporter.handleInputResult(result.getData());
                    }
                });

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            handleFileSelection(uri);
                        }
                    }
                });

        setupIoIconsView(R.id.cashJsonInput, R.id.scanIcon, false, false, true, true,
                null, null, () -> startQrScan(QR_SCAN_JSON_REQUEST_CODE), this::openFilePicker);
    }

    private void handleFileSelection(Uri uri) {
        String filePath = FileUtils.getPathFromUri(this, uri);
        String fileName = FileUtils.getFileNameFromUri(this, uri);

        if (filePath == null || fileName == null) {
            showToast(getString(R.string.failed_to_load_file));
            return;
        }

        currentFilePath = filePath;
        isFileMode = true;
        cashJsonInput.setText(R.string.file_loaded_import_it);
        cashJsonInput.setEnabled(false);
        cashJsonInput.setTextColor(Color.GRAY);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        // Set initial directory to Downloads
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, 
            DocumentsContract.buildRootUri("com.android.providers.downloads.documents", "downloads"));
        filePickerLauncher.launch(intent);
    }

    private void setupListeners() {
        cashClearButton.setOnClickListener(v -> {
            hideKeyboard();
            cashJsonInput.setText("");
            cashJsonInput.setEnabled(true);
            isFileMode = false;
        });

        cashImportButton.setOnClickListener(v -> {
            try {
                if (isFileMode && currentFilePath != null) {
                    File file = new File(currentFilePath);
                    if (!file.exists()) {
                        showToast(getString(R.string.file_not_found));
                        return;
                    }
                    try (FileInputStream fis = new FileInputStream(file)) {
                        fcCashImporter.importEntity(fis);
                    }
                } else {
                    String jsonText = cashJsonInput.getText() != null ? cashJsonInput.getText().toString() : "";
                    fcCashImporter.importEntity(jsonText);
                }
            } catch (Exception e) {
                showToast(getString(R.string.no_cash_found));
            }
        });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_import_cash;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.import_tx);
    }

    @Override
    protected void initializeViews() {
        initViews();
    }

    @Override
    protected void setupButtons() {
        setupListeners();
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_JSON_REQUEST_CODE) {
            cashJsonInput.setText(qrContent);
            cashJsonInput.setEnabled(true);
            isFileMode=false;
        }
    }
} 