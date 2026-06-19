package com.fc.safe.secret;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;

import com.fc.fc_ajdk.data.feipData.Secret;
import com.fc.fc_ajdk.utils.Base32;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.SecretManager;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.utils.TextIconsUtils;
import com.fc.safe.utils.ToastUtils;
import com.fc.safe.utils.ToolbarUtils;
import com.google.android.material.textfield.TextInputEditText;

public class UpdateSecretActivity extends com.fc.safe.home.BaseCryptoActivity {
    private static final String TAG = "UpdateSecretActivity";

    private LinearLayout secretInfoContainer;
    private LinearLayout inputContainer;
    private LinearLayout buttonContainer;
    private TextInputEditText titleInput;
    private TextInputEditText contentInput;
    private TextInputEditText memoInput;
    private AutoCompleteTextView typeInput;
    private Button clearButton;
    private Button updateButton;
    private Button newRandomButton;

    // Define request codes for QR scan if not already defined
    private static final int QR_SCAN_TITLE_REQUEST_CODE = 1001;
    private static final int QR_SCAN_CONTENT_REQUEST_CODE = 1002;
    private static final int QR_SCAN_MEMO_REQUEST_CODE = 1003;
    private static final int QR_SCAN_TYPE_REQUEST_CODE = 1004;

    private Secret originalSecret;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_update_secret;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.update_secret);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the secret detail from intent
        String secretJson = getIntent().getStringExtra("secret");
        if (secretJson == null || secretJson.isEmpty()) {
            ToastUtils.makeText(this, "No secret data provided");
            finish();
            return;
        }

        try {
            originalSecret = Secret.fromJson(secretJson, Secret.class);
            if (originalSecret == null) {
                ToastUtils.makeText(this, "Invalid secret data");
                finish();
                return;
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error parsing secret data: %s", e.getMessage());
            ToastUtils.makeText(this, "Error loading secret data");
            finish();
            return;
        }

        // Set up root layout touch listener to hide keyboard
        View rootLayout = findViewById(android.R.id.content);
        rootLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hideKeyboard();
            }
            return false;
        });

        // Setup toolbar
        ToolbarUtils.setupToolbar(this, getActivityTitle());

        // Replace deprecated onBackPressed() with OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        initializeViews();
        setupButtons();
        setupTypeDropdown();
        populateFields();

        // Setup scan icons for the three input boxes
        TextIconsUtils.setupTextIcons(this, R.id.titleView, R.id.scanIcon, QR_SCAN_TITLE_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.contentView, R.id.scanIcon, QR_SCAN_CONTENT_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.memoView, R.id.scanIcon, QR_SCAN_MEMO_REQUEST_CODE);
    }

    protected void initializeViews() {
        secretInfoContainer = findViewById(R.id.secretInfoContainer);
        inputContainer = findViewById(R.id.inputContainer);
        buttonContainer = findViewById(R.id.buttonContainer);

        View titleView = findViewById(R.id.titleView);
        View contentView = findViewById(R.id.contentView);
        View memoView = findViewById(R.id.memoView);

        titleInput = titleView.findViewById(R.id.textInput);
        titleInput.setHint(R.string.input_the_title);
        contentInput = contentView.findViewById(R.id.textInput);
        contentInput.setHint(R.string.input_the_content);
        memoInput = memoView.findViewById(R.id.textInput);
        memoInput.setHint(getString(R.string.input_the_memo) + " (optional)");

        typeInput = findViewById(R.id.typeInput);
        typeInput.setHint(R.string.select_the_type);

        clearButton = findViewById(R.id.clearButton);
        updateButton = findViewById(R.id.updateButton);
        newRandomButton = findViewById(R.id.newRandomButton);
    }

    protected void setupButtons() {
        clearButton.setOnClickListener(v -> clearInputs());
        updateButton.setOnClickListener(v -> updateSecret());
        newRandomButton.setOnClickListener(v -> generateRandomContent());
    }

    private void setupTypeDropdown() {
        Secret.Type[] types = Secret.Type.values();
        String[] displayNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            displayNames[i] = types[i].displayName;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, displayNames);
        typeInput.setAdapter(adapter);
        typeInput.setThreshold(1);
    }

    private void populateFields() {
        if (originalSecret == null) {
            return;
        }

        // Set title
        if (originalSecret.getTitle() != null) {
            titleInput.setText(originalSecret.getTitle());
        }

        // Set memo
        if (originalSecret.getMemo() != null) {
            memoInput.setText(originalSecret.getMemo());
        }

        // Set type
        if (originalSecret.getType() != null) {
            typeInput.setText(originalSecret.getType());
        }

        // Decrypt and set content
        decryptAndPopulateContent();
    }

    private void decryptAndPopulateContent() {
        if (originalSecret == null || originalSecret.getContentCipher() == null) {
            return;
        }

        try {


            // Perform decryption in background thread
            new Thread(() -> {
                try {
                    // Get private key without user confirmation dialog
                    byte[] symkey = ConfigureManager.getInstance().getSymkey();

                    if (symkey == null) {
                        runOnUiThread(() -> {
                            ToastUtils.makeText(this, getString(R.string.failed_to_get_symmetric_key));
                        });
                        return;
                    }

                    String cipher = originalSecret.getContentCipher();
                    if(cipher==null)return;
                    CryptoDataByte cryptoDataByte = Decryptor.decryptBySymkey(CryptoDataByte.fromJson(cipher),Hex.toHex(symkey));

                    if(cryptoDataByte.getCode() != 0 || cryptoDataByte.getData() == null){
                        runOnUiThread(() -> {
                            ToastUtils.makeText(this, getString(R.string.failed_to_decrypt));
                        });
                        return;
                    }

                    String decryptedContent = new String(cryptoDataByte.getData());

                    // Set content on main thread
                    runOnUiThread(() -> {
                        if (!decryptedContent.isEmpty()) {
                            contentInput.setText(decryptedContent);
                        } else {
                            ToastUtils.makeText(this, "Failed to decrypt content");
                        }
                    });

                } catch (Exception e) {
                    TimberLogger.e(TAG, "Error decrypting content: %s", e.getMessage());
                    runOnUiThread(() -> {
                        ToastUtils.makeText(this, "Error decrypting content: " + e.getMessage());
                    });
                }
            }).start();

        } catch (Exception e) {
            TimberLogger.e(TAG, "Error initiating content decryption: %s", e.getMessage());
            ToastUtils.makeText(this, "Error initiating decryption: " + e.getMessage());
        }
    }

    private void clearInputs() {
        titleInput.setText("");
        contentInput.setText("");
        memoInput.setText("");
        typeInput.setText("");
    }

    private void updateSecret() {
        String title = titleInput.getText() != null ? titleInput.getText().toString() : "";
        String content = contentInput.getText() != null ? contentInput.getText().toString() : "";
        String memo = memoInput.getText() != null ? memoInput.getText().toString() : "";
        String typeDisplay = typeInput.getText() != null ? typeInput.getText().toString() : "";

        if(typeDisplay.equals("TOTP" )) {
            if(!Base32.isBase32(content)) {
                ToastUtils.makeText(this, "Failed to update! TOTP key have to be base32 encoded");
                return;
            }
        }

        if ( content.isEmpty() ) {
            ToastUtils.makeText(this, R.string.please_fill_required_fields);
            return;
        }

        // Map display name back to enum name
        Secret.Type selectedType = null;
        for (Secret.Type t : Secret.Type.values()) {
            if (t.displayName.equals(typeDisplay)) {
                selectedType = t;
                break;
            }
        }
        if (selectedType == null) {
            ToastUtils.makeText(this, R.string.invalid_type_selected);
            return;
        }
        String type = selectedType.getDisplayName(selectedType);

        // Create updated secret detail
        Secret updatedSecret = new Secret();
        updatedSecret.setId(originalSecret.getId()); // Keep original ID
        if(!title.isEmpty()) updatedSecret.setTitle(title);
        if(!memo.isEmpty()) updatedSecret.setMemo(memo);
        if(!type.isEmpty()) updatedSecret.setType(type);
        updatedSecret.setContent(content); // Optionally store plain content

        byte[] symkey = ConfigureManager.getInstance().getSymkey();
        if (symkey == null) {
            ToastUtils.makeText(this, getString(R.string.symmetric_key_not_found_login_again));
            return;
        }
        String contentCipher = Encryptor.encryptBySymkeyToJson(content.getBytes(), symkey);
        if (contentCipher == null) {
            ToastUtils.makeText(this, getString(R.string.failed_to_encrypt));
            return;
        }
        updatedSecret.setContentCipher(contentCipher);
        updatedSecret.setContent(null);
        updatedSecret.setOnChain(false);

        SecretManager secretManager = SecretManager.getInstance(this);
        secretManager.addSecret(updatedSecret);
        secretManager.commit();

        ToastUtils.makeText(this, "Secret updated locally");
        setResult(Activity.RESULT_OK);
        finish();

    }

    private void generateRandomContent() {
        byte[] randomBytes = BytesUtils.getRandomBytes(16);
        String base32 = Base32.toBase32(randomBytes);
        contentInput.setText(base32);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_TITLE_REQUEST_CODE) {
            titleInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_CONTENT_REQUEST_CODE) {
            contentInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_MEMO_REQUEST_CODE) {
            memoInput.setText(qrContent);
        } else if (requestCode == QR_SCAN_TYPE_REQUEST_CODE) {
            typeInput.setText(qrContent);
        }
        // If requestCode does not match, do nothing
    }
}