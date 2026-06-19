package com.fc.safe.secret;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.feipData.Secret;
import com.fc.fc_ajdk.utils.Base32;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.SecretManager;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.models.BackupHeader;
import com.fc.safe.models.BackupKey;
import com.fc.safe.ui.SingleInputActivity;
import com.fc.safe.utils.QRCodeGenerator;
import com.fc.safe.utils.ToastUtils;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BackupSecretsActivity extends BaseCryptoActivity {
    private static final String TAG = "BackupSecrets";
    public static final String CURRENT_PASSWORD = "Current Password";
    public static final String RANDOM_PASSWORD = "Random Password";
    public static final String DON_T_ENCRYPT = "Don't encrypt";

    private List<Secret> secretList;
    private RadioGroup encryptByGroup;
    private Button exportButton;
    private Button makeQrButton;
    private Button copyButton;
    private View resultView;
    private TextInputEditText resultTextBox;
    private final List<String> jsonList = new ArrayList<>();
    List<List<Bitmap>> bitmapListList = new ArrayList<>();

    private RadioButton currentPasswordButton;
    private RadioButton randomPasswordButton;
    private RadioButton noneButton;
    private static final int REQUEST_CODE_PASSWORD = 3001;
    private BackupHeader backupHeader = null;
    private static final int PERMISSION_REQUEST_STORAGE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get all secrets from SecretManager
        secretList = SecretManager.getInstance(this).getAllSecretDetailList();
        if (secretList == null || secretList.isEmpty()) {
            ToastUtils.showWarning(this, getString(R.string.secret_list_is_empty));
            finish();
            return;
        }
        // Decrypt content and clear contentCipher
        for (Secret secret : secretList) {
            if (secret.getContentCipher() != null) {
                try{
                    CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson(secret.getContentCipher());
                    byte[] symKey = ConfigureManager.getInstance().getSymkey();
                    if(symKey==null){
                        ToastUtils.showError(this, getString(R.string.symkey_is_null));
                        return;
                    }
                    Decryptor.decryptBySymkey(cryptoDataByte, com.fc.fc_ajdk.utils.Hex.toHex(symKey));
                    if(cryptoDataByte.getData() != null){
                        secret.setContent(new String(cryptoDataByte.getData()));
                        secret.setContentCipher(null);
                    }
                }catch (Exception e){
                    TimberLogger.e(e.getMessage());
                }
            }
        }
        checkStoragePermission();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
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
                ToastUtils.showWarning(this, getString(R.string.storage_permission_is_required_to_read_backup_files));
            }
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_export_secret;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.export_secrets); // Using string resource
    }

    @Override
    protected void initializeViews() {
        encryptByGroup = findViewById(R.id.encrypt_by_group);
        exportButton = findViewById(R.id.export_button);
        makeQrButton = findViewById(R.id.make_qr_button);
        copyButton = findViewById(R.id.copy_button);
        resultView = findViewById(R.id.resultView);
        resultTextBox = resultView.findViewById(R.id.textBoxWithMakeQrLayout);

        currentPasswordButton = findViewById(R.id.encrypt_current_password);
        randomPasswordButton = findViewById(R.id.encrypt_random_password);
        noneButton = findViewById(R.id.encrypt_none);

        setupRadioButtonListeners();
        setupIoIconsView(R.id.resultView, R.id.makeQrIcon, true, false, false, false,
                this::makeQr, null, null, null);
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean enabled = jsonList != null && !jsonList.isEmpty();
        makeQrButton.setEnabled(enabled);
        makeQrButton.setAlpha(enabled ? 1f : 0.5f);
        copyButton.setEnabled(enabled);
        copyButton.setAlpha(enabled ? 1f : 0.5f);
    }

    private void makeQr() {
        if (!jsonList.isEmpty()) {
            bitmapListList = QRCodeGenerator.makeQRBitmapsList(jsonList,backupHeader);
            List<Bitmap> flattenedBitmaps = new ArrayList<>();
            for (List<Bitmap> bitmapList : bitmapListList) {
                flattenedBitmaps.addAll(bitmapList);
            }
            QRCodeGenerator.showQRDialog(this, flattenedBitmaps);
        } else {
            ToastUtils.showWarning(this, getString(R.string.no_data_to_make_qr_code));
        }
    }

    private void setupRadioButtonListeners() {
        RadioButton[] allButtons = {currentPasswordButton, randomPasswordButton, noneButton};
        for (RadioButton button : allButtons) {
            button.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    for (RadioButton otherButton : allButtons) {
                        if (otherButton != buttonView) {
                            otherButton.setChecked(false);
                        }
                    }
                }
            });
        }
    }

    @Override
    protected void setupButtons() {
        exportButton.setOnClickListener(v -> {
            jsonList.clear();
            if (currentPasswordButton.isChecked()) {
                getInputtedString("Input current password:");
                return;
            } else {
                doExport(null, null);
            }
        });

        makeQrButton.setOnClickListener(v -> makeQr());

        copyButton.setOnClickListener(v -> {
            String textToCopy = JsonUtils.makeJsonListString(jsonList);
            if (textToCopy != null && !textToCopy.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Exported Secrets", textToCopy);
                clipboard.setPrimaryClip(clip);
                ToastUtils.showInfo(this, getString(R.string.copied_to_clipboard));
            } else {
                ToastUtils.showWarning(this, getString(R.string.nothing_to_copy));
            }
        });
    }

    private void doExport(String password, String inputSymKeyStr) {
        String result = generateExportResult(password);
        if (result != null && !result.isEmpty()) {
            String filePath = saveResultToFile(result);
            if (filePath != null) {
                displayFilePath(filePath);
                ToastUtils.showInfo(this, getString(R.string.exported_to, filePath));
            }
        }
        updateButtonStates();
    }

    private String saveResultToFile(String result) {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) downloadDir.mkdirs();
            String fileName = "backup_secrets_" + System.currentTimeMillis() + ".txt";
            File file = new File(downloadDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(result.getBytes());
                fos.flush();
            }
            return file.getAbsolutePath();
        } catch (IOException e) {
            TimberLogger.e(TAG, "Failed to save file: %s", e.getMessage());
            ToastUtils.showError(this, getString(R.string.failed_to_save_file));
            return null;
        }
    }

    private void displayFilePath(String filePath) {
        if (resultTextBox != null && resultView != null) {
            resultTextBox.setText(filePath);
            resultView.setVisibility(View.VISIBLE);
        }
    }

    private String generateExportResult(String enteredPassword) {
        backupHeader = new BackupHeader();
        backupHeader.setTime(System.currentTimeMillis());
        backupHeader.setItems(secretList.size());

        String encryptMethod;
        if (currentPasswordButton != null && currentPasswordButton.isChecked()) {
            encryptMethod = CURRENT_PASSWORD;
        } else if (randomPasswordButton != null && randomPasswordButton.isChecked()) {
            encryptMethod = RANDOM_PASSWORD;
        } else if (noneButton != null && noneButton.isChecked()) {
            encryptMethod = DON_T_ENCRYPT;
        } else {
            ToastUtils.showWarning(this, getString(R.string.select_encryption_method));
            return null;
        }

        String randomPassword = null;
        switch (encryptMethod) {
            case CURRENT_PASSWORD -> {
                if (enteredPassword == null || enteredPassword.isEmpty()) {
                    ToastUtils.showWarning(this, getString(R.string.please_enter_password));
                    return null;
                }
                byte[] passwordBytes = enteredPassword.getBytes();
                String passwordName = IdNameUtils.makePasswordHashName(passwordBytes);
                Configure configure = ConfigureManager.getInstance().getConfigure(this, passwordName);
                if (configure == null || !passwordName.equals(configure.getPasswordName())) {
                    ToastUtils.showError(this, getString(R.string.incorrect_password));
                    return null;
                }
                backupHeader.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7.getDisplayName());
                backupHeader.setKeyName(IdNameUtils.makeKeyName(passwordBytes));
            }
            case RANDOM_PASSWORD -> {
                randomPassword = Base32.toBase32(BytesUtils.getRandomBytes(8));
                backupHeader.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7.getDisplayName());
                backupHeader.setKeyName(IdNameUtils.makeKeyName(randomPassword.getBytes()));
            }
            case DON_T_ENCRYPT -> {}
        }

        if (!encryptMethod.equals(DON_T_ENCRYPT)) {
            backupHeader.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7.getDisplayName());
            BackupKey backupKey = BackupKey.makeBackupKey(backupHeader, null, randomPassword,this);
            jsonList.add(JsonUtils.toNiceJson(backupKey));
        }
        backupHeader.settClass(Secret.class.getSimpleName());

        String headerNiceJson = backupHeader.toNiceJson();
        jsonList.add(headerNiceJson);

        for (int i = 0; i < secretList.size(); i++) {
            Secret secret = secretList.get(i);
            if (secret == null) continue;
            addItemJson(enteredPassword, encryptMethod, secret, randomPassword);
        }

        return JsonUtils.makeJsonListString(jsonList);
    }

    private void addItemJson(String enteredPassword, String encryptMethod, Secret secret, String randomPassword) {
        String encryptedJson = "";
        try {
            switch (encryptMethod) {
                case CURRENT_PASSWORD:
                    encryptedJson = new Encryptor().encryptByPassword(secret.toBytes(), enteredPassword.toCharArray()).toNiceJson();
                    break;
                case RANDOM_PASSWORD:
                    encryptedJson = new Encryptor().encryptByPassword(secret.toBytes(), randomPassword.toCharArray()).toNiceJson();
                    break;
                case DON_T_ENCRYPT:
                    encryptedJson = JsonUtils.toNiceJson(secret);
                    break;
                default:
                    encryptedJson = JsonUtils.toNiceJson(secret);
                    break;
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error encrypting secret: %s", secret.getTitle());
            ToastUtils.showError(this, getString(R.string.error_encrypting_secret, secret.getTitle()));
            return;
        }
        jsonList.add(encryptedJson);
    }

    private void getInputtedString(String promote) {
        Intent intent = new Intent(this, SingleInputActivity.class);
        intent.putExtra(SingleInputActivity.EXTRA_PROMOTE, promote);
        intent.putExtra(SingleInputActivity.EXTRA_INPUT_TYPE, "password");
        startActivityForResult(intent, REQUEST_CODE_PASSWORD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_CODE_PASSWORD) {
                String password = data.getStringExtra(SingleInputActivity.EXTRA_RESULT);
                doExport(password, null);
            }
        }
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not used in this context
    }
} 