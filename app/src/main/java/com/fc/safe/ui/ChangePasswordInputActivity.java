package com.fc.safe.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.fc.safe.R;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.qr.QrCodeActivity;
import com.fc.safe.utils.ToolbarUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Objects;

/**
 * Activity to input new password for password change.
 * Unlike CreatePasswordActivity, this only captures the new password
 * without creating a new password context.
 */
public class ChangePasswordInputActivity extends AppCompatActivity {

    private EditText passwordInput;
    private EditText confirmPasswordInput;
    private TextView errorText;
    private TextInputLayout passwordInputLayout;
    private TextInputLayout confirmPasswordInputLayout;
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int QR_CODE_REQUEST_CODE = 1001;
    private static final int QR_CODE_REQUEST_CODE_CONFIRM = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_password);

        // Set up toolbar
        ToolbarUtils.setupToolbar(this, "Enter New Password");

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

        createButton.setText(R.string.change);
        createButton.setOnClickListener(v -> validateAndReturnPassword());
        cancelButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
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

    private void validateAndReturnPassword() {
        String password = Objects.requireNonNull(passwordInput.getText()).toString();
        String confirmPassword = Objects.requireNonNull(confirmPasswordInput.getText()).toString();

        if (!isPasswordValid(password, confirmPassword)) {
            return;
        }

        // Return the new password via Intent
        Intent resultIntent = new Intent();
        resultIntent.putExtra("new_password", password);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private boolean isPasswordValid(String password, String confirmPassword) {
        if (password.isEmpty()) {
            showError("Please enter a password");
            return false;
        }

        if (confirmPassword.isEmpty()) {
            showError("Please confirm your password");
            return false;
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            showError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
            return false;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return false;
        }

        // Check if password already exists
        byte[] passwordBytes = password.getBytes();
        if (ConfigureManager.getInstance().passwordExists(this, passwordBytes)) {
            showDuplicatePasswordDialog();
            return false;
        }

        return true;
    }

    private void showDuplicatePasswordDialog() {
        RemindDialog dialog = new RemindDialog(this, getString(R.string.password_already_exists), true);
        dialog.setOnDismissListener(dialogInterface -> clearInputFields());
        dialog.show();
    }

    private void clearInputFields() {
        passwordInput.setText("");
        confirmPasswordInput.setText("");
        errorText.setVisibility(View.GONE);
        passwordInput.requestFocus();
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}