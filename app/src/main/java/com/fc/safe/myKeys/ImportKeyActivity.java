package com.fc.safe.myKeys;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.secret.FcEntityImporter;
import com.fc.safe.ui.DetailFragment;
import com.fc.safe.utils.TextIconsUtils;
import com.fc.safe.utils.ToastUtils;
import com.fc.safe.utils.WaitingTask;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * Adds a key from whatever key text the user has: a private key, public key, FID,
 * password cipher or key backup JSON, typed, scanned or picked from a file.
 * {@link KeyInputDetector} decides which, and the verdict is shown while typing so a
 * watch-only key is never mistaken for one that can sign.
 */
public class ImportKeyActivity extends BaseCryptoActivity {
    private static final String TAG = "ImportKey";
    private static final int QR_SCAN_KEY_REQUEST_CODE = 1001;
    private static final int QR_SCAN_PASSWORD_REQUEST_CODE = 1002;
    private static final int QR_SCAN_LABEL_REQUEST_CODE = 1003;

    private DetailFragment detailFragment;
    private TextInputEditText keyInput;
    private TextInputEditText passwordInput;
    private TextInputEditText labelInput;
    private TextView detectedText;
    private View passwordView;
    private View labelView;
    private Button clearButton;
    private Button previewButton;
    private Button saveButton;

    private KeyInputDetector.Kind kind = KeyInputDetector.Kind.EMPTY;
    /** A backup file picked from storage; imported as a stream instead of the input text. */
    private Uri fileUri;
    private FcEntityImporter<KeyInfo> importer;

    private final ActivityResultLauncher<Intent> importerPasswordLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null && importer != null) {
                    importer.handleInputResult(result.getData());
                }
            });

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && result.getData().getData() != null) {
                    loadFile(result.getData().getData());
                }
            });

    @Override
    protected int getLayoutId() {
        return R.layout.activity_import_key;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.import_keys);
    }

    @Override
    protected void initializeViews() {
        View keyView = findViewById(R.id.keyView);
        passwordView = findViewById(R.id.passwordView);
        labelView = findViewById(R.id.labelView);

        keyInput = keyView.findViewById(R.id.textInput);
        keyInput.setHint(R.string.input_key_hint);
        passwordInput = passwordView.findViewById(R.id.textInput);
        passwordInput.setHint(R.string.input_the_password);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextInputLayout passwordLayout = passwordView.findViewById(R.id.textInputWithScanLayout);
        passwordLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        labelInput = labelView.findViewById(R.id.textInput);
        labelInput.setHint(R.string.input_the_label);
        detectedText = findViewById(R.id.detectedText);

        clearButton = findViewById(R.id.clearButton);
        previewButton = findViewById(R.id.previewButton);
        saveButton = findViewById(R.id.saveButton);

        keyInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                onKeyInputChanged();
            }
        });
    }

    @Override
    protected void setupButtons() {
        clearButton.setOnClickListener(v -> clearInputs());
        previewButton.setOnClickListener(v -> preview());
        saveButton.setOnClickListener(v -> save());

        setupIoIconsView(R.id.keyView, R.id.scanIcon, false, false, true, true,
                null, null,
                () -> startQrScan(QR_SCAN_KEY_REQUEST_CODE),
                this::openFilePicker);
        TextIconsUtils.setupTextIcons(this, R.id.passwordView, R.id.scanIcon, QR_SCAN_PASSWORD_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.labelView, R.id.scanIcon, QR_SCAN_LABEL_REQUEST_CODE);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_KEY_REQUEST_CODE) {
            exitFileMode();
            keyInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_PASSWORD_REQUEST_CODE) {
            passwordInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_LABEL_REQUEST_CODE) {
            labelInput.setText(qrContent);
        }
    }

    private void onKeyInputChanged() {
        clearDetailFragment();
        kind = fileUri != null ? KeyInputDetector.Kind.BACKUP : KeyInputDetector.detect(textOf(keyInput));
        showDetection();
    }

    private void showDetection() {
        String text = textOf(keyInput).trim();
        String message;
        int color = R.color.error;
        switch (kind) {
            case EMPTY -> message = null;
            case PRIKEY -> {
                message = getString(R.string.key_detected_prikey, fidOfPrikey(text));
                color = R.color.colorPrimary;
            }
            case PUBKEY -> {
                message = getString(R.string.key_detected_pubkey, fidOfPubkey(text));
                color = R.color.warning_color;
            }
            case FID -> {
                message = getString(R.string.key_detected_fid);
                color = R.color.warning_color;
            }
            case CIPHER -> {
                message = getString(R.string.key_detected_cipher);
                color = R.color.colorPrimary;
            }
            case BACKUP -> {
                message = getString(R.string.key_detected_backup);
                color = R.color.colorPrimary;
            }
            case NON_PASSWORD_CIPHER -> message = getString(R.string.key_non_password_cipher);
            case BAD_PRIKEY -> message = getString(R.string.key_bad_prikey);
            case BAD_PUBKEY -> message = getString(R.string.key_bad_pubkey);
            case BAD_FID -> message = getString(R.string.key_bad_fid);
            case MULTIPLE -> message = getString(R.string.key_multiple);
            default -> message = getString(R.string.key_unrecognized, getString(R.string.input_phrase));
        }
        detectedText.setText(message);
        detectedText.setTextColor(getColor(color));
        detectedText.setVisibility(message == null ? View.GONE : View.VISIBLE);
        passwordView.setVisibility(kind == KeyInputDetector.Kind.CIPHER ? View.VISIBLE : View.GONE);
        labelView.setVisibility(kind == KeyInputDetector.Kind.BACKUP ? View.GONE : View.VISIBLE);
    }

    private static String fidOfPrikey(String text) {
        byte[] prikey32 = KeyInputDetector.toPrikey32(text);
        try {
            return KeyTools.prikeyToFid(prikey32);
        } catch (Exception e) {
            return "?";
        } finally {
            BytesUtils.clearByteArray(prikey32);
        }
    }

    private static String fidOfPubkey(String text) {
        try {
            return KeyTools.pubkeyToFchAddr(text);
        } catch (Exception e) {
            return "?";
        }
    }

    private void preview() {
        hideKeyboard();
        buildKeyInfo(false, keyInfo -> {
            try {
                keyInfoManager.makeAvatarByFid(keyInfo.getId(), this);
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error generating avatar: %s", e.getMessage());
            }
            showDetailFragment(keyInfo);
        });
    }

    private void save() {
        hideKeyboard();
        if (detailFragment != null) {
            KeyInfo keyInfo = (KeyInfo) detailFragment.getCurrentEntity();
            if (keyInfo != null) {
                saveKeyInfo(keyInfo);
                return;
            }
        }
        if (kind == KeyInputDetector.Kind.BACKUP) {
            importBackup(null);
            return;
        }
        buildKeyInfo(true, this::saveKeyInfo);
    }

    private void buildKeyInfo(boolean forSave, Consumer<KeyInfo> onReady) {
        String text = textOf(keyInput).trim();
        String label = textOf(labelInput).trim();
        switch (kind) {
            case EMPTY -> ToastUtils.showWarning(this, getString(R.string.please_input_a_key));
            case PRIKEY -> {
                KeyInfo keyInfo = newPrikeyKeyInfo(label, KeyInputDetector.toPrikey32(text));
                if (keyInfo != null) onReady.accept(keyInfo);
            }
            case PUBKEY -> onReady.accept(new KeyInfo(label, text));
            case FID -> onReady.accept(KeyInfo.newFromFid(text, label));
            case CIPHER -> decryptCipher(text, label, forSave, onReady);
            case BACKUP -> ToastUtils.showWarning(this, getString(R.string.key_backup_no_preview));
            default -> ToastUtils.showWarning(this, detectedText.getText().toString());
        }
    }

    private KeyInfo newPrikeyKeyInfo(String label, byte[] prikey32) {
        if (prikey32 != null) {
            try {
                return new KeyInfo(label, prikey32, ConfigureManager.getInstance().getSymkey());
            } catch (Exception e) {
                TimberLogger.e(TAG, "Failed to make key from prikey: %s", e.getMessage());
            }
        }
        ToastUtils.showError(this, getString(R.string.invalid_private_key));
        return null;
    }

    private void decryptCipher(String text, String label, boolean forSave, Consumer<KeyInfo> onReady) {
        String password = textOf(passwordInput);
        if (password.isEmpty()) {
            ToastUtils.showWarning(this, getString(R.string.password_is_empty));
            return;
        }
        CryptoDataByte cipher = KeyInputDetector.parseCipher(text);
        if (cipher == null) {
            ToastUtils.showError(this, getString(R.string.failed_to_decrypt_private_key));
            return;
        }

        // The KDF may be Argon2id, which is intentionally slow, so decrypt off the UI thread.
        // An empty array stands for a failed decryption, since a null result means the work threw.
        WaitingTask.run(this, getString(R.string.decrypting), () -> {
            Decryptor.decryptByPassword(cipher, password.toCharArray());
            if (cipher.getCode() != null && cipher.getCode() == 0 && cipher.getData() != null) {
                return cipher.getData();
            }
            return new byte[0];
        }, data -> onDecrypted(data == null || data.length == 0 ? null : data, password, label, forSave, onReady));
    }

    private void onDecrypted(byte[] plaintext, String password, String label,
                             boolean forSave, Consumer<KeyInfo> onReady) {
        if (plaintext == null) {
            ToastUtils.showError(this, getString(R.string.failed_to_decrypt_private_key));
            return;
        }
        try {
            byte[] prikey32 = KeyInputDetector.prikeyFromPlaintext(plaintext);
            if (prikey32 != null) {
                KeyInfo keyInfo = newPrikeyKeyInfo(label, prikey32);
                if (keyInfo != null) onReady.accept(keyInfo);
            } else if (KeyInputDetector.isJson(plaintext)) {
                // An encrypted backup line rather than a bare key: the importer opens it
                // again with the same password.
                if (forSave) {
                    importBackup(password);
                } else {
                    ToastUtils.showWarning(this, getString(R.string.key_backup_no_preview));
                }
            } else {
                ToastUtils.showError(this, getString(R.string.invalid_private_key));
            }
        } finally {
            BytesUtils.clearByteArray(plaintext);
        }
    }

    private void saveKeyInfo(KeyInfo keyInfo) {
        if (keyInfoManager.checkIfExisted(keyInfo.getId())) {
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.key_already_exists_title))
                    .setMessage(getString(R.string.key_already_exists_message))
                    .setPositiveButton(getString(R.string.replace), (dialog, which) -> saveAndFinish(keyInfo))
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show();
        } else {
            saveAndFinish(keyInfo);
        }
    }

    private void saveAndFinish(KeyInfo keyInfo) {
        keyInfoManager.addKeyInfo(keyInfo);
        keyInfoManager.commit();
        setResult(RESULT_OK);
        finish();
    }

    private void importBackup(String password) {
        // A fresh importer for every attempt, because it accumulates results across calls.
        importer = new FcEntityImporter<>(this, KeyInfo.class, new FcEntityImporter.OnImportListener<>() {
            @Override
            public void onImportSuccess(List<KeyInfo> result) {
                if (result == null || result.isEmpty()) {
                    ToastUtils.showError(ImportKeyActivity.this, R.string.no_key_info_found);
                    return;
                }
                KeyInfoManager.saveAndFinish(ImportKeyActivity.this, result);
            }

            @Override
            public void onImportError(String error) {
                showToast(getString(R.string.operation_failed_with_message, error));
            }

            @Override
            public void onPasswordRequired(Intent intent) {
                importerPasswordLauncher.launch(intent);
            }
        });
        if (password != null) importer.setPassword(password);

        FcEntityImporter<KeyInfo> current = importer;
        Uri uri = fileUri;
        String text = textOf(keyInput).trim();
        // Every password cipher in a backup costs one Argon2id run, so import off the UI thread.
        WaitingTask.run(this, getString(R.string.please_wait), () -> {
            if (uri == null) return current.importEntity(text);
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                return is == null ? null : current.importEntity(is);
            }
        }, result -> {
            if (result == null) ToastUtils.showError(this, R.string.no_key_info_found);
        });
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildRootUri("com.android.providers.downloads.documents", "downloads"));
        filePickerLauncher.launch(intent);
    }

    private void loadFile(Uri uri) {
        fileUri = uri;
        keyInput.setText(R.string.file_loaded_import_it);
        keyInput.setEnabled(false);
        keyInput.setTextColor(getColor(R.color.hint));
    }

    private void exitFileMode() {
        if (fileUri == null) return;
        fileUri = null;
        keyInput.setEnabled(true);
        keyInput.setTextColor(getColor(R.color.text_color));
        keyInput.setText("");
    }

    private void clearInputs() {
        exitFileMode();
        keyInput.setText("");
        passwordInput.setText("");
        labelInput.setText("");
        clearDetailFragment();
    }

    private void showDetailFragment(KeyInfo keyInfo) {
        clearDetailFragment();
        detailFragment = DetailFragment.newInstance(keyInfo, KeyInfo.class);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.keyInfoContainer, detailFragment)
                .commit();
    }

    private void clearDetailFragment() {
        if (detailFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(detailFragment)
                    .commit();
            detailFragment = null;
        }
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() != null ? input.getText().toString() : "";
    }
}
