package com.fc.safe.myKeys;

import static com.fc.safe.myKeys.BackupKeysActivity.addKeyInfoJson;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.Base32;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.models.BackupHeader;
import com.fc.safe.models.BackupKey;
import com.fc.safe.ui.SingleInputActivity;
import com.fc.safe.utils.QRCodeGenerator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class ExportKeysActivity extends BaseCryptoActivity {
    private static final String TAG = "ExportKeys";
    public static final String CURRENT_PASSWORD = "Current Password";
    public static final String RANDOM_PASSWORD = "Random Password";
    public static final String SYMKEY = "Symkey";
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
    private RadioButton symkeyButton;
    private RadioButton randomPasswordButton;
    private RadioButton noneButton;
    private static final int REQUEST_CODE_PASSWORD = 3001;
    private static final int REQUEST_CODE_SYMKEY = 3002;
    private BackupHeader backupHeader = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get and parse the key info list from intent
        String keyInfoListJson = getIntent().getStringExtra("keyInfoList");
        if(keyInfoListJson == null){
            Toast.makeText(this,"KeyInfo list is null", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        keyInfoList = JsonUtils.listFromJson(keyInfoListJson, KeyInfo.class);
        if (keyInfoList == null || keyInfoList.isEmpty()) {
            Toast.makeText(this, "KeyInfo list is empty", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // No decryption needed for KeyInfo like SecretDetail
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
        symkeyButton = findViewById(R.id.encrypt_symkey);
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
        RadioButton[] allButtons = {currentPasswordButton, symkeyButton, randomPasswordButton, noneButton};
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
            } else if (symkeyButton.isChecked()) {
                getSymkeyString("Input the symmetric key:");
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
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doExport(String password, String inputSymkeyStr) {
        String result = generateExportResult(password, inputSymkeyStr);
        if (result != null && !result.isEmpty()) {
            displayResult(result);
        }
        updateButtonStates();
    }

    private String generateExportResult(String enteredPassword, String inputtedSymkeyStr) {

        RadioButton encryptByRadio = null;
        if (currentPasswordButton != null && currentPasswordButton.isChecked()) {
            encryptByRadio = currentPasswordButton;
        } else if (symkeyButton != null && symkeyButton.isChecked()) {
            encryptByRadio = symkeyButton;
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

        backupHeader = new BackupHeader();
        backupHeader.settClass(KeyInfo.class.getSimpleName());
        backupHeader.setTime(System.currentTimeMillis());
        backupHeader.setItems(keyInfoList.size());

        List<String> keyAndHeaderList = new ArrayList<>();
        String randomPassword = null;
        byte[] inputedSymkey = null;
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
                if (inputtedSymkeyStr == null || inputtedSymkeyStr.isEmpty() || !Hex.isHex32(inputtedSymkeyStr)) {
                    Toast.makeText(this, getString(R.string.sym_key_has_to_be_a_hex_of_32_bytes), Toast.LENGTH_SHORT).show();
                    return null;
                }
                inputedSymkey = Hex.fromHex(inputtedSymkeyStr);
                backupHeader.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7.getDisplayName());
                backupHeader.setKeyName(IdNameUtils.makeKeyName(inputedSymkey));
            }
            case DON_T_ENCRYPT -> {backupHeader=null;}
        }

        if (!encryptMethod.equals(DON_T_ENCRYPT)) {
            backupHeader.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7.getDisplayName());
            BackupKey backupKey = BackupKey.makeBackupKey(backupHeader,inputtedSymkeyStr, randomPassword);
            keyAndHeaderList.add(JsonUtils.toNiceJson(backupKey));

            String headerNiceJson = backupHeader.toNiceJson();
            keyAndHeaderList.add(headerNiceJson);

            jsonList.addAll(keyAndHeaderList);
        }

        byte[] dbSymkey = ConfigureManager.getInstance().getSymkey();

        for (int i = 0; i < keyInfoList.size(); i++) {
            KeyInfo keyInfo = keyInfoList.get(i);
            if (keyInfo == null) continue;
            String json = addKeyInfoJson(enteredPassword, encryptMethod, keyInfo, randomPassword, inputedSymkey,dbSymkey,this);
            if(json!=null)jsonList.add(json);
        }

        return JsonUtils.makeJsonListString(jsonList);
    }
//
//    private void addItemJson(String enteredPassword, String encryptMethod, KeyInfo passedKeyInfo, String randomPassword, byte[] symKey, byte[] appSymkey) {
//        if(passedKeyInfo==null)return;
//        if(passedKeyInfo.getPriKey()==null && passedKeyInfo.getPriKeyCipher()==null)return;
//        if(passedKeyInfo.getPriKey()==null && passedKeyInfo.getPriKeyCipher()!=null){
//            byte[] priKeyBytes = Decryptor.decryptPriKey(passedKeyInfo.getPriKeyCipher(), appSymkey);
//            if(priKeyBytes==null)return;
//            passedKeyInfo.setPriKey(Hex.toHex(priKeyBytes));
//        }
//
//        KeyInfo keyInfo = new KeyInfo();
//        keyInfo.setPriKey(passedKeyInfo.getPriKey());
//        keyInfo.setLabel(passedKeyInfo.getLabel());
//        keyInfo.setSaveTime(passedKeyInfo.getSaveTime());
//        if(passedKeyInfo.getPriKey()==null){
//            keyInfo.setId(passedKeyInfo.getId());
//            keyInfo.setPubKey(passedKeyInfo.getPubKey());
//        }
//
//        String keyInfoJson;
//        try {
//             switch (encryptMethod) {
//                 case CURRENT_PASSWORD:
//                     keyInfoJson = new Encryptor().encryptByPassword(keyInfo.toBytes(), enteredPassword.toCharArray()).toNiceJson();
//                     break;
//                 case RANDOM_PASSWORD:
//                     keyInfoJson = new Encryptor().encryptByPassword(keyInfo.toBytes(), randomPassword.toCharArray()).toNiceJson();
//                     break;
//                 case SYM_KEY:
//                     keyInfoJson = new Encryptor().encryptBySymkey(keyInfo.toBytes(), symKey).toNiceJson();
//                     break;
//                 default:
//                     keyInfoJson = JsonUtils.toNiceJson(keyInfo);
//                     break;
//            }
//        } catch (Exception e) {
//            TimberLogger.e(TAG, "Error encrypting key: %s", keyInfo.getId());
//            Toast.makeText(this, "Error encrypting key: " + keyInfo.getId(), Toast.LENGTH_SHORT).show();
//            return;
//        }
//        jsonList.add(keyInfoJson);
//    }

    private void getInputtedString(String promote) {
        Intent intent = new Intent(this, SingleInputActivity.class);
        intent.putExtra(SingleInputActivity.EXTRA_PROMOTE, promote);
        intent.putExtra(SingleInputActivity.EXTRA_INPUT_TYPE, "password");
        startActivityForResult(intent, REQUEST_CODE_PASSWORD);
    }

    private void getSymkeyString(String promote) {
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

    private void displayResult(String result) {
        if (resultTextBox != null && resultView != null) {
            resultTextBox.setText(result);
            resultView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not used in this context
    }
} 