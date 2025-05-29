package com.fc.safe.secret;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.fc.fc_ajdk.data.fcData.SecretDetail;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.db.SecretManager;
import com.fc.safe.utils.FileUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class ImportTotpActivity extends BaseCryptoActivity {
    private static final String TAG = "ImportTotpActivity";
    private static final int QR_SCAN_JSON_REQUEST_CODE = 1001;

    private LinearLayout secretJsonInputContainer;
    private LinearLayout secretButtonContainer;
    private TextInputEditText secretJsonInput;
    private Button secretClearButton;
    private Button secretImportButton;
    private String type;
    private FcEntityImporter<SecretDetail> fcEntityImporter;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> inputLauncher;
    private List<SecretDetail> importedSecretList;
    private String currentFilePath;
    private boolean isFileMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getIntent().getStringExtra("type");
        initSecretImporter();
    }

    private void initSecretImporter() {
        fcEntityImporter = new FcEntityImporter<>(this, SecretDetail.class, new FcEntityImporter.OnImportListener<>() {
            @Override
            public void onImportSuccess(List<SecretDetail> result) {
                SecretManager.saveAndFinish(ImportTotpActivity.this, result);
            }

            @Override
            public void onImportError(String error) {
                showToast(error);
            }

            @Override
            public void onPasswordRequired(Intent intent) {
                inputLauncher.launch(intent);
            }

            @Override
            public void onSymkeyRequired(Intent intent) {
                inputLauncher.launch(intent);
            }
        });
        fcEntityImporter.setType(type);
    }

    private void initViews() {
        secretJsonInputContainer = findViewById(R.id.secretJsonInputContainer);
        secretButtonContainer = findViewById(R.id.secretButtonContainer);

        secretJsonInput = secretJsonInputContainer.findViewById(R.id.secretJsonInput).findViewById(R.id.textInput);
        secretJsonInput.setHint(R.string.input_the_secret_json);

        secretClearButton = findViewById(R.id.secretClearButton);
        secretImportButton = findViewById(R.id.secretImportButton);

        // Initialize launchers
        inputLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        fcEntityImporter.handleInputResult(result.getData());
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

        setupIoIconsView(R.id.secretJsonInput, R.id.scanIcon, false, false, true, true,
                null, null, () -> startQrScan(QR_SCAN_JSON_REQUEST_CODE), this::openFilePicker);
    }

    private void handleFileSelection(Uri uri) {
        String filePath = FileUtils.getPathFromUri(this, uri);
        String fileName = FileUtils.getFileNameFromUri(this, uri);

        if (filePath == null || fileName == null) {
            showToast("Failed to load file");
            return;
        }

        currentFilePath = filePath;
        isFileMode = true;
        secretJsonInput.setText(filePath);
        secretJsonInput.setEnabled(false);
        secretJsonInput.setTextColor(Color.GRAY);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void setupListeners() {
        secretClearButton.setOnClickListener(v -> {
            FcEntityImporter.hideKeyboard(getCurrentFocus());
            secretJsonInput.setText("");
            secretJsonInput.setEnabled(true);
            isFileMode = false;
        });

        secretImportButton.setOnClickListener(v -> {
            try {
                String inputText = "";
                if (isFileMode && currentFilePath != null) {
                    File file = new File(currentFilePath);
                    if (!file.exists()) {
                        showToast("File not found");
                        return;
                    }
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[(int) file.length()];
                        fis.read(buffer);
                        inputText = new String(buffer);
                    }
                } else {
                    inputText = secretJsonInput.getText() != null ? secretJsonInput.getText().toString() : "";
                }

                List<SecretDetail> secretDetails = parseTotpInput(inputText);
                if (secretDetails != null && !secretDetails.isEmpty()) {
                    SecretManager.saveAndFinish(this, secretDetails);
                } else if (fcEntityImporter.getFinalTList() != null && !fcEntityImporter.getFinalTList().isEmpty()) {
                    // If we have items in finalTList, it means we're waiting for password input
                    // The FcEntityImporter will handle the password input and decryption
                    return;
                } else {
                    showToast(getString(R.string.no_secret_found));
                }
            } catch (Exception e) {
                showToast(getString(R.string.no_secret_found));
            }
        });
    }

    private List<SecretDetail> parseTotpInput(String input) {
        List<SecretDetail> secretDetails = new ArrayList<>();
        
        // Try parsing as JSON first
        try {
            JsonObject jsonObject = JsonParser.parseString(input).getAsJsonObject();
            if (jsonObject.has("secret") && jsonObject.has("label")) {
                SecretDetail secretDetail = new SecretDetail();
                String label =jsonObject.get("label").getAsString();
                if(label.contains(" - ")){
                    label = label.split(" - ")[1];
                }

                if(label.contains(":") && !label.contains(": ")){
                    label = label.replaceAll(":", ": ");
                }
                secretDetail.setTitle(label);
                String secret = jsonObject.get("secret").getAsString();
                if(secret==null || secret.isEmpty())return null;
                secretDetail.setContent(secret);
                secretDetail.setType("TOTP");
                secretDetails.add(secretDetail);
                return secretDetails;
            }
        } catch (Exception e) {
            // Not a valid JSON, continue to try other formats
        }

        // Try parsing as URI
        try {
            if (input.startsWith("otpauth://totp/")) {
                Uri uri = Uri.parse(input);
                String path = uri.getPath();
                if (path != null) {
                    String account = path.substring(1); // Remove "/totp/"
                    String secret = uri.getQueryParameter("secret");
                    String issuer = uri.getQueryParameter("issuer");
                    String title = issuer+": "+account;
                    if (secret != null) {
                        SecretDetail secretDetail = new SecretDetail();
                        secretDetail.setTitle(title);
                        secretDetail.setContent(secret);
                        secretDetail.setType("TOTP");
                        secretDetails.add(secretDetail);
                        return secretDetails;
                    }
                }
            }
        } catch (Exception e) {
            // Not a valid URI, continue to try FcEntityImporter
        }

        // Try FcEntityImporter as last resort
        try {
            List<SecretDetail> imported = fcEntityImporter.importEntity(input);
            if (imported != null) {
                imported.removeIf(secretDetail -> secretDetail.getContent() == null || secretDetail.getContent().isEmpty());
                if (!imported.isEmpty()) {
                    return imported;
                }
            }
            // If we get here, it means we need to wait for password input
            return null;
        } catch (Exception e) {
            // FcEntityImporter failed
            return null;
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_import_secret;
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
            secretJsonInput.setText(qrContent);
            secretJsonInput.setEnabled(true);
            isFileMode = false;
        }
    }
} 