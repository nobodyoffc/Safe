package com.fc.safe.initiate;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.DatabaseManager;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.db.MultisignManager;
import com.fc.safe.db.SecretManager;
import com.fc.safe.qr.QrCodeActivity;
import com.fc.safe.ui.RemindDialog;
import com.fc.safe.utils.ToolbarUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;
import com.fc.safe.utils.ToastUtils;

public class CreatePasswordActivity extends AppCompatActivity {

    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private TextView errorText;
    private TextInputLayout passwordInputLayout;
    private TextInputLayout confirmPasswordInputLayout;
    private static final String TAG = "CryptoSign";
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int QR_CODE_REQUEST_CODE = 1001;
    private static final int QR_CODE_REQUEST_CODE_CONFIRM = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_password);

        // Set up toolbar
        ToolbarUtils.setupToolbar(this, getString(R.string.create_password));

        initializeViews();
        setupClickListeners();
        
        // Request focus for the password input
        passwordInput.requestFocus();
    }

    private void initializeViews() {
        passwordInputLayout = findViewById(R.id.passwordInputLayout);
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        errorText = findViewById(R.id.errorText);
    }

    private void setupClickListeners() {
        MaterialButton createButton = findViewById(R.id.createButton);
        MaterialButton cancelButton = findViewById(R.id.cancelButton);
        MaterialButton clearButton = findViewById(R.id.clearButton);

        createButton.setOnClickListener(v -> validateAndCreatePassword());
        cancelButton.setOnClickListener(v -> handleCancel());
        clearButton.setOnClickListener(v -> {
            passwordInput.setText("");
            confirmPasswordInput.setText("");
            errorText.setVisibility(View.GONE);
            // Hide keyboard when clear button is clicked
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(passwordInput.getWindowToken(), 0);
            }
        });
        
        // Add click listeners for the scan icons
        passwordInputLayout.setEndIconOnClickListener(v -> startQrCodeScanner(QR_CODE_REQUEST_CODE));
        confirmPasswordInputLayout.setEndIconOnClickListener(v -> startQrCodeScanner(QR_CODE_REQUEST_CODE_CONFIRM));
    }

    private void startQrCodeScanner(int requestCode) {
        Intent intent = new Intent(this, QrCodeActivity.class);
        intent.putExtra("is_return_string", true);
        startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            String scannedText = data.getStringExtra("qr_content");
            if (scannedText != null && !scannedText.isEmpty()) {
                if (requestCode == QR_CODE_REQUEST_CODE) {
                    passwordInput.setText(scannedText);
                    confirmPasswordInput.requestFocus();
                } else if (requestCode == QR_CODE_REQUEST_CODE_CONFIRM) {
                    confirmPasswordInput.setText(scannedText);
                }
            }
        }
    }

    private void handleCancel() {
        if (isTaskRoot()) {
            finishAffinity();
        } else {
            finish();
        }
    }

    private void validateAndCreatePassword() {
        String password = Objects.requireNonNull(passwordInput.getText()).toString();
        String confirmPassword = Objects.requireNonNull(confirmPasswordInput.getText()).toString();

        if (!isPasswordValid(password, confirmPassword)) {
            return;
        }

        try {
            savePassword();
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error creating password: " + e.getMessage(), e);
            showError(getString(R.string.error_with_message,e.getMessage()));
        }
    }

    private boolean isPasswordValid(String password, String confirmPassword) {
        if (password.isEmpty()) {
            showError(getString(R.string.please_enter_a_password));
            return false;
        }

        if (confirmPassword.isEmpty()) {
            showError(getString(R.string.please_confirm_your_password));
            return false;
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            showError(getString(R.string.password_must_be_at_least_d_characters_long,MIN_PASSWORD_LENGTH));
            return false;
        }

        if (!password.equals(confirmPassword)) {
            showError(getString(R.string.passwords_do_not_match));
            return false;
        }

        return true;
    }

    private void savePassword() {
        String password = passwordInput.getText().toString();
        if (password.isEmpty()) {
            ToastUtils.showInfo(this, getString(R.string.please_enter_password));
            return;
        }

        byte[] passwordBytes = password.getBytes();
        String passwordName = IdNameUtils.makePasswordHashName(passwordBytes);

        // Create new Configure object
        Configure configure = new Configure();
        configure.makeSymkeyFromPassword(passwordBytes);
        configure.setPasswordName(passwordName);

        // Get DatabaseManager instance
        DatabaseManager dbManager = DatabaseManager.getInstance(this);

        // Set the new password name, which will trigger database cleanup if needed
        dbManager.setCurrentPasswordName(passwordName);

        // Reinitialize all managers with the new password
        KeyInfoManager.getInstance(this).initialize(this);
        SecretManager.getInstance(this).initialize(this);
        MultisignManager.getInstance(this).initialize(this);

        // Store the Configure object in ConfigureManager
        ConfigureManager.getInstance().setConfigure(configure);
        ConfigureManager.getInstance().storeConfigure(this, configure);

        // Check if this is from background timeout - if so, clear stack and go to HomeActivity
        boolean fromBackgroundTimeout = getIntent().getBooleanExtra("from_background_timeout", false);

        // Show reminder dialog and only finish activity after dialog is dismissed
        RemindDialog dialog = new RemindDialog(this, getString(R.string.remember_backup_keys));
        dialog.setOnDismissListener(d -> {
            if (fromBackgroundTimeout) {
                // Clear all activities and go to HomeActivity since password context changed
                Intent homeIntent = new Intent(CreatePasswordActivity.this, com.fc.safe.home.HomeActivity.class);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(homeIntent);
                finish();
            } else {
                setResult(RESULT_OK);
                finish();
            }
        });
        dialog.show();
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
} 