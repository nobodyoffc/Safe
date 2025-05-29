package com.fc.safe.myKeys;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.activity.OnBackPressedCallback;

import com.fc.fc_ajdk.utils.Base32;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.utils.ToolbarUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.fc.fc_ajdk.data.fcData.SecretDetail;
import com.fc.safe.db.SecretManager;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.safe.utils.TextIconsUtils;
import com.fc.fc_ajdk.utils.BytesUtils;

public class CreateSecretActivity extends BaseCryptoActivity {
    private LinearLayout secretInfoContainer;
    private LinearLayout inputContainer;
    private LinearLayout buttonContainer;
    private TextInputEditText titleInput;
    private TextInputEditText contentInput;
    private TextInputEditText memoInput;
    private AutoCompleteTextView typeInput;
    private Button clearButton;
    private Button saveButton;
    private Button newRandomButton;

    // Define request codes for QR scan if not already defined
    private static final int QR_SCAN_TITLE_REQUEST_CODE = 1001;
    private static final int QR_SCAN_CONTENT_REQUEST_CODE = 1002;
    private static final int QR_SCAN_MEMO_REQUEST_CODE = 1003;
    private static final int QR_SCAN_TYPE_REQUEST_CODE = 1004;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_create_secret;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.create_secret);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        // Setup scan icons for the three input boxes
        TextIconsUtils.setupTextIcons(this, R.id.titleView, R.id.scanIcon, QR_SCAN_TITLE_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.contentView, R.id.scanIcon, QR_SCAN_CONTENT_REQUEST_CODE);
        TextIconsUtils.setupTextIcons(this, R.id.memoView, R.id.scanIcon, QR_SCAN_MEMO_REQUEST_CODE);

        // Handle type lock from intent
        String typeFromIntent = getIntent().getStringExtra("type");
        if (typeFromIntent != null && !typeFromIntent.isEmpty()) {
            typeInput.setText(typeFromIntent);
            typeInput.setEnabled(false);
            typeInput.setFocusable(false);
            typeInput.setFocusableInTouchMode(false);
            typeInput.setOnClickListener(null);
            typeInput.setKeyListener(null);
            typeInput.setLongClickable(false);
            typeInput.setCursorVisible(false);
            // Optionally, visually indicate it's locked (e.g., gray out)
            typeInput.setTextColor(getResources().getColor(android.R.color.darker_gray, getTheme()));
        }
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
        saveButton = findViewById(R.id.saveButton);
        newRandomButton = findViewById(R.id.newRandomButton);
    }

    protected void setupButtons() {
        clearButton.setOnClickListener(v -> clearInputs());
        saveButton.setOnClickListener(v -> saveSecret());
        newRandomButton.setOnClickListener(v -> generateRandomContent());
    }

    private void setupTypeDropdown() {
        SecretDetail.Type[] types = SecretDetail.Type.values();
        String[] displayNames = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            displayNames[i] = types[i].displayName;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, displayNames);
        typeInput.setAdapter(adapter);
        typeInput.setThreshold(1);
    }

    private void clearInputs() {
        titleInput.setText("");
        contentInput.setText("");
        memoInput.setText("");
        typeInput.setText("");
    }

    private void saveSecret() {
        String title = titleInput.getText() != null ? titleInput.getText().toString() : "";
        String content = contentInput.getText() != null ? contentInput.getText().toString() : "";
        String memo = memoInput.getText() != null ? memoInput.getText().toString() : "";
        String typeDisplay = typeInput.getText() != null ? typeInput.getText().toString() : "";

        if(typeDisplay.equals("TOTP" )) {
            if(!com.fc.fc_ajdk.utils.Base32.isBase32(content)) {
                Toast.makeText(this, "Failed to save! TOTP key have to be base32 encoded", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (title.isEmpty() || content.isEmpty() || typeDisplay.isEmpty()) {
            Toast.makeText(this, R.string.please_fill_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        // Map display name back to enum name
        SecretDetail.Type selectedType = null;
        for (SecretDetail.Type t : SecretDetail.Type.values()) {
            if (t.displayName.equals(typeDisplay)) {
                selectedType = t;
                break;
            }
        }
        if (selectedType == null) {
            Toast.makeText(this, R.string.invalid_type_selected, Toast.LENGTH_SHORT).show();
            return;
        }
        String type = selectedType.getDisplayName(selectedType);

        // Encrypt content
        byte[] symKey = ConfigureManager.getInstance().getSymkey();
        if (symKey == null) {
            Toast.makeText(this, "Symmetric key not found. Please login again.", Toast.LENGTH_LONG).show();
            return;
        }
        String contentCipher = Encryptor.encryptBySymkeyToJson(content.getBytes(), symKey);

        SecretDetail secretDetail = new SecretDetail();
        secretDetail.setTitle(title);
        secretDetail.setMemo(memo);
        secretDetail.setType(type);
        secretDetail.setContent(content); // Optionally store plain content
        secretDetail.setContentCipher(contentCipher);
        // Set content to null before saving to db
        secretDetail.setContent(null);

        secretDetail.checkIdWithCreate();
        SecretManager secretManager = SecretManager.getInstance(this);
        boolean existed = secretManager.checkIfExisted(secretDetail.getId());
        if (existed) {
            new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.secret_existed_title)
                .setMessage(R.string.secret_already_exists_message)
                .setPositiveButton(R.string.replace, (dialog, which) -> {
                    SecretManager.saveAndFinish(this, secretDetail);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        } else {
            SecretManager.saveAndFinish(this, secretDetail);
        }
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