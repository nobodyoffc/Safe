package com.fc.safe.secret;

import static com.fc.safe.myKeys.ExportKeysActivity.SYMKEY;

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
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.SecretDetail;
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

    private List<SecretDetail> secretList;
    private RadioGroup encryptByGroup;
    private Button exportButton;
    private Button makeQrButton;
    private Button copyButton;
    private View resultView;
    private TextInputEditText resultTextBox;
    private final List<String> jsonList = new ArrayList<>();
    List<List<Bitmap>> bitmapListList = new ArrayList<>();

    private RadioButton currentPasswordButton;
    private RadioButton symKeyButton;
    private RadioButton randomPasswordButton;
    private RadioButton noneButton;
    private static final int REQUEST_CODE_PASSWORD = 3001;
    private static final int REQUEST_CODE_SYMKEY = 3002;
    private BackupHeader backupHeader = null;
    private static final int PERMISSION_REQUEST_STORAGE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get all secrets from SecretManager
        secretList = SecretManager.getInstance(this).getAllSecretDetailList();
        if (secretList == null || secretList.isEmpty()) {
            Toast.makeText(this, "Secret list is empty", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Decrypt content and clear contentCipher
        for (SecretDetail secret : secretList) {
            if (secret.getContentCipher() != null) {
                try{
                    CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson(secret.getContentCipher());
                    byte[] symKey = ConfigureManager.getInstance().getSymkey();
                    if(symKey==null){
                        Toast.makeText(this,"SymKey is null", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(this, "Storage permission is required to save backup files", Toast.LENGTH_LONG).show();
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
        symKeyButton = findViewById(R.id.encrypt_symkey);
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
            Toast.makeText(this, "No data to make QR code.", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupRadioButtonListeners() {
        RadioButton[] allButtons = {currentPasswordButton, symKeyButton, randomPasswordButton, noneButton};
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
            } else if (symKeyButton.isChecked()) {
                getSymKeyString("Input the symmetric key:");
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
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doExport(String password, String inputSymKeyStr) {
        String result = generateExportResult(password, inputSymKeyStr);
        if (result != null && !result.isEmpty()) {
            String filePath = saveResultToFile(result);
            if (filePath != null) {
                displayFilePath(filePath);
                Toast.makeText(this, "Exported to: " + filePath, Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private void displayFilePath(String filePath) {
        if (resultTextBox != null && resultView != null) {
            resultTextBox.setText(filePath);
            resultView.setVisibility(View.VISIBLE);
        }
    }

    private String generateExportResult(String enteredPassword, String inputSymKeyStr) {
        backupHeader = new BackupHeader();
        backupHeader.setTime(System.currentTimeMillis());
        backupHeader.setItems(secretList.size());

        RadioButton encryptByRadio = null;
        if (currentPasswordButton != null && currentPasswordButton.isChecked()) {
            encryptByRadio = currentPasswordButton;
        } else if (symKeyButton != null && symKeyButton.isChecked()) {
            encryptByRadio = symKeyButton;
        } else if (randomPasswordButton != null && randomPasswordButton.isChecked()) {
            encryptByRadio = randomPasswordButton;
        } else if (noneButton != null && noneButton.isChecked()) {
            encryptByRadio = noneButton;
        }

        if (encryptByRadio == null) {
            Toast.makeText(this, getString(R.string.select_encryption_method), Toast.LENGTH_SHORT).show();
            return null;
        }
        String encryptMethod = encryptByRadio.getText().toString();

        String randomPassword = null;
        byte[] symKey = null;
        switch (encryptMethod) {
            case CURRENT_PASSWORD -> {
                if (enteredPassword == null || enteredPassword.isEmpty()) {
                    Toast.makeText(this, getString(R.string.please_enter_password), Toast.LENGTH_SHORT).show();
                    return null;
                }
                byte[] passwordBytes = enteredPassword.getBytes();
                String passwordName = IdNameUtils.makePasswordHashName(passwordBytes);
                Configure configure = ConfigureManager.getInstance().getConfigure(this, passwordName);
                if (configure == null || !passwordName.equals(configure.getPasswordName())) {
                    Toast.makeText(this, getString(R.string.incorrect_password), Toast.LENGTH_SHORT).show();
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
            case SYMKEY -> {
                if (inputSymKeyStr == null || inputSymKeyStr.isEmpty() || !Hex.isHex32(inputSymKeyStr)) {
                    Toast.makeText(this, getString(R.string.sym_key_has_to_be_a_hex_of_32_bytes), Toast.LENGTH_SHORT).show();
                    return null;
                }
                symKey = Hex.fromHex(inputSymKeyStr);
                backupHeader.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7.getDisplayName());
                backupHeader.setKeyName(IdNameUtils.makeKeyName(symKey));
            }
            case DON_T_ENCRYPT -> {}
        }

        if (!encryptMethod.equals(DON_T_ENCRYPT)) {
            backupHeader.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7.getDisplayName());
            BackupKey backupKey = BackupKey.makeBackupKey(backupHeader,inputSymKeyStr, randomPassword);
            jsonList.add(JsonUtils.toNiceJson(backupKey));
        }
        backupHeader.settClass(SecretDetail.class.getSimpleName());

        String headerNiceJson = backupHeader.toNiceJson();
        jsonList.add(headerNiceJson);

        for (int i = 0; i < secretList.size(); i++) {
            SecretDetail secret = secretList.get(i);
            if (secret == null) continue;
            addItemJson(enteredPassword, encryptMethod, secret, randomPassword, symKey);
        }

        return JsonUtils.makeJsonListString(jsonList);
    }

    private void addItemJson(String enteredPassword, String encryptMethod, SecretDetail secret, String randomPassword, byte[] symKey) {
        String encryptedJson = "";
        try {
            switch (encryptMethod) {
                case CURRENT_PASSWORD:
                    encryptedJson = new Encryptor().encryptByPassword(secret.toBytes(), enteredPassword.toCharArray()).toNiceJson();
                    break;
                case RANDOM_PASSWORD:
                    encryptedJson = new Encryptor().encryptByPassword(secret.toBytes(), randomPassword.toCharArray()).toNiceJson();
                    break;
                case SYMKEY:
                    encryptedJson = new Encryptor().encryptBySymkey(secret.toBytes(), symKey).toNiceJson();
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
            Toast.makeText(this, "Error encrypting secret: " + secret.getTitle(), Toast.LENGTH_SHORT).show();
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

    private void getSymKeyString(String promote) {
        Intent intent = new Intent(this, SingleInputActivity.class);
        intent.putExtra(SingleInputActivity.EXTRA_PROMOTE, promote);
        intent.putExtra(SingleInputActivity.EXTRA_INPUT_TYPE, "text");
        startActivityForResult(intent, REQUEST_CODE_SYMKEY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_CODE_PASSWORD) {
                String password = data.getStringExtra(SingleInputActivity.EXTRA_RESULT);
                doExport(password, null);
            } else if (requestCode == REQUEST_CODE_SYMKEY) {
                String symKeyStr = data.getStringExtra(SingleInputActivity.EXTRA_RESULT);
                doExport(null, symKeyStr);
            }
        }
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not used in this context
    }
} 