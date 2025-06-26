package com.fc.safe.myKeys;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.secret.FcEntityImporter;
import com.fc.safe.utils.FileUtils;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class ImportKeyInfoActivity extends BaseCryptoActivity {
    private static final String TAG = "ImportKeyInfoActivity";
    private static final int QR_SCAN_JSON_REQUEST_CODE = 1001;
    private static final int PERMISSION_REQUEST_STORAGE = 1001;

    private LinearLayout keyInfoJsonInputContainer;
    private LinearLayout keyInfoButtonContainer;
    private TextInputEditText keyInfoJsonInput;
    private Button keyInfoClearButton;
    private Button keyInfoImportButton;
    private String type;

    private FcEntityImporter<KeyInfo> fcEntityImporter;

    private final ActivityResultLauncher<Intent> passwordInputLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    fcEntityImporter.handleInputResult(result.getData());
                }
            });

    private final ActivityResultLauncher<Intent> symKeyInputLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    fcEntityImporter.handleInputResult(result.getData());
                }
            });
    private List<KeyInfo> importedKeyInfoList;

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private String currentFilePath;
    private boolean isFileMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getIntent().getStringExtra("type");
        initKeyInfoImporter();
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

    private void initKeyInfoImporter() {
        fcEntityImporter = new FcEntityImporter<>(this, KeyInfo.class, new FcEntityImporter.OnImportListener<>() {
            @Override
            public void onImportSuccess(List<KeyInfo> result) {
                KeyInfoManager.saveAndFinish(ImportKeyInfoActivity.this, result);
            }

            @Override
            public void onImportError(String error) {
                showToast(getString(R.string.operation_failed_with_message, error));
            }

            @Override
            public void onPasswordRequired(Intent intent) {
                passwordInputLauncher.launch(intent);
            }

            @Override
            public void onSymkeyRequired(Intent intent) {
                symKeyInputLauncher.launch(intent);
            }
        });
        fcEntityImporter.setType(type);
    }

    private void initViews() {
        keyInfoJsonInputContainer = findViewById(R.id.keyInfoJsonInputContainer);
        keyInfoButtonContainer = findViewById(R.id.keyInfoButtonContainer);

        keyInfoJsonInput = findViewById(R.id.keyInfoJsonInput).findViewById(R.id.textInput);
        keyInfoJsonInput.setHint(R.string.input_the_key_info_json);

        keyInfoClearButton = findViewById(R.id.keyInfoClearButton);
        keyInfoImportButton = findViewById(R.id.keyInfoImportButton);

        // Initialize file picker launcher
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

//        // Use setupIoIconsView if available, else fallback to setupTextIcons
//        try {
//            setupIoIconsView(R.id.keyInfoJsonInput, R.id.scanIcon, false, false, true, true,
//                    null, null, () -> startQrScan(QR_SCAN_JSON_REQUEST_CODE), this::openFilePicker);
//        } catch (Exception e) {
//            setupTextIcons(R.id.keyInfoJsonInput, R.id.scanIcon, QR_SCAN_JSON_REQUEST_CODE);
//        }

        setupIoIconsView(R.id.keyInfoJsonInput, R.id.scanIcon, false, false, true, true,
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
        keyInfoJsonInput.setText(R.string.file_loaded_import_it);
        keyInfoJsonInput.setEnabled(false);
        keyInfoJsonInput.setTextColor(getColor(R.color.hint));
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
        keyInfoClearButton.setOnClickListener(v -> {
            FcEntityImporter.hideKeyboard(getCurrentFocus());
            keyInfoJsonInput.setText("");
            keyInfoJsonInput.setEnabled(true);
            keyInfoJsonInput.setTextColor(getColor(R.color.text_color));
            isFileMode = false;
        });

        keyInfoImportButton.setOnClickListener(v -> {
            try {
                if (isFileMode && currentFilePath != null) {
                    java.io.File file = new java.io.File(currentFilePath);
                    if (!file.exists()) {
                        showToast(getString(R.string.file_not_found));
                        return;
                    }
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                        fcEntityImporter.importEntity(fis);
                    }
                } else {
                    String jsonText = keyInfoJsonInput.getText() != null ? keyInfoJsonInput.getText().toString() : "";
                    fcEntityImporter.importEntity(jsonText);
                }
            } catch (Exception e) {
                Toast.makeText(this, R.string.no_key_info_found, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_import_key_info;
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
            keyInfoJsonInput.setText(qrContent);
            keyInfoJsonInput.setEnabled(true);
            keyInfoJsonInput.setTextColor(getColor(R.color.text_color));
            isFileMode = false;
        }
    }
} 