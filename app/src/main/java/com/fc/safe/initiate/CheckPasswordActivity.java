package com.fc.safe.initiate;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.DatabaseManager;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.db.MultisignManager;
import com.fc.safe.db.SecretManager;
import com.fc.safe.qr.QrCodeActivity;
import com.google.android.material.textfield.TextInputLayout;
import com.fc.safe.utils.ToolbarUtils;


public class CheckPasswordActivity extends AppCompatActivity {
    
    private EditText passwordInput;
    private TextInputLayout passwordInputLayout;
    private static final String TAG = "CryptoSign";
    private static final int QR_CODE_REQUEST_CODE = 1001;
    private ActivityResultLauncher<Intent> createPasswordLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(R.layout.activity_check_password);

        // Set up toolbar
        ToolbarUtils.setupToolbar(this, getString(R.string.check_password));
        // Override toolbar navigation click to prevent bypassing password check
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> {
            // Only allow back navigation if not from background timeout
            if (!getIntent().getBooleanExtra("from_background_timeout", false)) {
                finishAffinity();
            }
        });

        // Initialize the activity result launcher for CreatePassword
        createPasswordLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        // Get the Configure object from ConfigureManager
                        Configure configure = ConfigureManager.getInstance().getConfigure();
                        if (configure != null) {
                            // Simply return success result
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            TimberLogger.e(TAG, "Configure object not found in ConfigureManager");
                            Toast.makeText(this, "Error: Configuration not found", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
        
        // Initialize UI components
        passwordInputLayout = findViewById(R.id.passwordInputLayout);
        passwordInput = findViewById(R.id.password_input);
        Button verifyButton = findViewById(R.id.verify_button);
        Button createPasswordButton = findViewById(R.id.create_password_button);
        Button clearButton = findViewById(R.id.clear_button);
        
        // Add click listener for the scan icon
        passwordInputLayout.setEndIconOnClickListener(v -> startQrCodeScanner());
        
        // Add Enter key listener to password input
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || 
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                verifyPassword();
                return true;
            }
            return false;
        });
        
        verifyButton.setOnClickListener(v -> verifyPassword());
        createPasswordButton.setOnClickListener(v -> startCreatePasswordActivity());
        clearButton.setOnClickListener(v -> {
            passwordInput.setText("");
            // Hide keyboard when clear button is clicked
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(passwordInput.getWindowToken(), 0);
            }
        });

        // If launched from background timeout, show a message
        if (getIntent().getBooleanExtra("from_background_timeout", false)) {
            Toast.makeText(this, R.string.please_verify_your_password , Toast.LENGTH_SHORT).show();
        }
    }

    private void startQrCodeScanner() {
        Intent intent = new Intent(this, QrCodeActivity.class);
        intent.putExtra("is_return_string", true);
        startActivityForResult(intent, QR_CODE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == QR_CODE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            String scannedText = data.getStringExtra("qr_content");
            if (scannedText != null && !scannedText.isEmpty()) {
                passwordInput.setText(scannedText);
            }
        }
    }
    
    private void startCreatePasswordActivity() {
        Intent intent = new Intent(this, CreatePasswordActivity.class);
        // Add flag to indicate this is from background timeout
        if (getIntent().getBooleanExtra("from_background_timeout", false)) {
            intent.putExtra("from_background_timeout", true);
        }
        createPasswordLauncher.launch(intent);
    }
    
    private void verifyPassword() {
        String enteredPassword = passwordInput.getText().toString();
        
        if (enteredPassword.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_enter_password), Toast.LENGTH_SHORT).show();
            return;
        }
        
        byte[] passwordBytes = enteredPassword.getBytes();
        String passwordName = IdNameUtils.makePasswordHashName(passwordBytes);
        Configure configure = ConfigureManager.getInstance().getConfigure(this, passwordName);

        if (configure != null) {
            configure.makeSymkeyFromPassword(passwordBytes);
            
            // Get DatabaseManager instance
            DatabaseManager dbManager = DatabaseManager.getInstance(this);
            
            // Set the new password name, which will trigger database cleanup if needed
            dbManager.setCurrentPasswordName(passwordName);
            
            // Reinitialize all managers with the new password
            KeyInfoManager.getInstance(this).initialize(this);
            SecretManager.getInstance(this).initialize(this);
            MultisignManager.getInstance(this).initialize(this);
            
            // Store the Configure object in ConfigureManager for sharing across activities
            ConfigureManager.getInstance().setConfigure(configure);
            
            // Simply return success result
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, getString(R.string.incorrect_password), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        // if (isTaskRoot()) {
            finishAffinity();
        // } else {
        //     finish();
        // }
    }
} 