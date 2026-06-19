package com.fc.safe.myKeys;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.Base32;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
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

public class BackupKeysActivity extends BaseCryptoActivity {
    private static final String TAG = "BackupKeys";
    public static final String CURRENT_PASSWORD = "Current Password";
    public static final String RANDOM_PASSWORD = "Random Password";
    public static final String DON_T_ENCRYPT = "Don't encrypt";

    private List<KeyInfo> keyInfoList;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get all KeyInfo from KeyInfoManager
        keyInfoList = KeyInfoManager.getInstance(this).getAllKeyInfoList();
        if (keyInfoList == null || keyInfoList.isEmpty()) {
            ToastUtils.showWarning(this, getString(R.string.keyinfo_list_is_empty));
            finish();
            return;
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_export_secret;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.export_tx);
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
                ClipData clip = ClipData.newPlainText("Exported Keys", textToCopy);
                clipboard.setPrimaryClip(clip);
                ToastUtils.showInfo(this, getString(R.string.copied_to_clipboard));
            } else {
                ToastUtils.showWarning(this, getString(R.string.nothing_to_copy));
            }
        });
    }

    private void doExport(String password, String inputSymkeyStr) {
        String result = generateExportResult(password);
        if (result != null && !result.isEmpty()) {
            String filePath = saveResultToFile(result);
            if (filePath != null) {
                displayFilePath(filePath);
                ToastUtils.showInfo(this, getString(R.string.exported));
            }
        }
        updateButtonStates();
    }

    private String saveResultToFile(String result) {
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) downloadDir.mkdirs();
            String fileName = "backup_keys_" + System.currentTimeMillis() + ".txt";
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

        backupHeader = new BackupHeader();
        backupHeader.settClass(KeyInfo.class.getSimpleName());
        backupHeader.setTime(System.currentTimeMillis());
        backupHeader.setItems(keyInfoList.size());

        List<String> keyAndHeaderList = new ArrayList<>();
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
            case DON_T_ENCRYPT -> {backupHeader=null;}
        }

        if (!encryptMethod.equals(DON_T_ENCRYPT)) {
            backupHeader.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7.getDisplayName());
            BackupKey backupKey = BackupKey.makeBackupKey(backupHeader, null, randomPassword,this);
            keyAndHeaderList.add(JsonUtils.toNiceJson(backupKey));

            String headerNiceJson = backupHeader.toNiceJson();
            keyAndHeaderList.add(headerNiceJson);

            jsonList.addAll(keyAndHeaderList);
        }

        byte[] dbSymkey = ConfigureManager.getInstance().getSymkey();

        for (int i = 0; i < keyInfoList.size(); i++) {
            KeyInfo keyInfo = keyInfoList.get(i);
            if (keyInfo == null) continue;
            String json = addKeyInfoJson(enteredPassword, encryptMethod, keyInfo, randomPassword, dbSymkey, this);
            if(json!=null)
                jsonList.add(json);
        }

        return JsonUtils.makeJsonListString(jsonList);
    }

    public static String addKeyInfoJson(String enteredPassword, String encryptMethod, KeyInfo passedKeyInfo, String randomPassword, byte[] appSymkey, Context context) {
        if(passedKeyInfo==null) return null;

        if(passedKeyInfo.getPrikey()==null && passedKeyInfo.getPrikeyCipher()==null) return null;
        if(passedKeyInfo.getPrikey()==null && passedKeyInfo.getPrikeyCipher()!=null){
            byte[] priKeyBytes = Decryptor.decryptPrikey(passedKeyInfo.getPrikeyCipher(), appSymkey);
            if(priKeyBytes==null) return null;
            passedKeyInfo.setPrikeyBytes(priKeyBytes);
        }

        KeyInfo keyInfo = new KeyInfo();
        keyInfo.setId(passedKeyInfo.getId());
        keyInfo.setLabel(passedKeyInfo.getLabel());
        keyInfo.setSaveTime(passedKeyInfo.getSaveTime());
        if(passedKeyInfo.getPrikeyBytes()==null && passedKeyInfo.getPrikey()==null && passedKeyInfo.getPrikeyCipher()==null){
            keyInfo.setPubkey(passedKeyInfo.getPubkey());
            return keyInfo.toNiceJson();
        }
        keyInfo.setPrikeyBytes(passedKeyInfo.getPrikeyBytes());
        keyInfo.setPubkey(null);
        keyInfo.setPubkeyBytes(null);

        String finalJson;
        try {
             switch (encryptMethod) {
                 case CURRENT_PASSWORD -> {
                     CryptoDataByte cryptoResult = new Encryptor().encryptByPassword(keyInfo.getPrikeyBytes(), enteredPassword.toCharArray());
                     String cipher = cryptoResult.toBase64();
                     keyInfo.setPrikey(null);
                     keyInfo.setPrikeyCipher(cipher);
                     keyInfo.setId(passedKeyInfo.getId());
                 }
                case RANDOM_PASSWORD -> {
                    CryptoDataByte cryptoResult = new Encryptor().encryptByPassword(keyInfo.getPrikeyBytes(), randomPassword.toCharArray());
                    String cipher = cryptoResult.toBase64();
                    keyInfo.setPrikey(null);
                    keyInfo.setPrikeyCipher(cipher);
                    keyInfo.setId(passedKeyInfo.getId());
                }
                case DON_T_ENCRYPT -> {
                     keyInfo.setPrikey(Hex.toHex(keyInfo.getPrikeyBytes()));
                }
                default -> {}
            }
            finalJson = keyInfo.toNiceJson();
        } catch (Exception e) {
            return null;
        }

        return finalJson;
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