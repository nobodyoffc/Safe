package com.fc.safe.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.fcData.SecretDetail;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.db.SecretManager;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.initiate.CheckPasswordActivity;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.safe.db.DatabaseManager;
import com.fc.safe.db.MultisignManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Activity to handle the full change password flow:
 * 1. Check current password
 * 2. Create new password
 * 3. Re-encrypt all KeyInfos and SecretDetails with new symKey
 * 4. Save new configure and remove old configure
 * 5. Show result and finish
 *
 * No visible layout is needed.
 */
public class ChangePasswordActivity extends AppCompatActivity {
    private ActivityResultLauncher<Intent> checkPasswordLauncher;
    private ActivityResultLauncher<Intent> newPasswordLauncher;
    private WaitingDialog waitingDialog;
    private Configure oldConfigure;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Optionally set a blank layout, or none at all
        // setContentView(new View(this));
        registerActivityResultLaunchers();
        // Start the flow with a special flag to indicate this is for password change
        Intent intent = new Intent(this, CheckPasswordActivity.class);
        intent.putExtra("for_password_change", true);
        checkPasswordLauncher.launch(intent);
    }

    private void registerActivityResultLaunchers() {
        checkPasswordLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Store old configure BEFORE launching new password activity
                    oldConfigure = ConfigureManager.getInstance().getConfigure();
                    Intent intent = new Intent(this, ChangePasswordInputActivity.class);
                    newPasswordLauncher.launch(intent);
                } else {
                    finish();
                }
            }
        );
        newPasswordLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String newPassword = result.getData().getStringExtra("new_password");
                    if (newPassword != null) {
                        performPasswordChange(newPassword);
                    } else {
                        finish();
                    }
                } else {
                    finish();
                }
            }
        );
    }

    private void performPasswordChange(String newPassword) {
        showWaitingDialog("Re-encrypting all keys and secrets...");
        new Thread(() -> {
            Thread.currentThread().setName("PasswordChangeThread");
            try {
                // Create new configure from new password
                byte[] newPasswordBytes = newPassword.getBytes();
                String newPasswordName = IdNameUtils.makePasswordHashName(newPasswordBytes);
                
                Configure newConfigure = new Configure();
                newConfigure.makeSymkeyFromPassword(newPasswordBytes);
                newConfigure.setPasswordName(newPasswordName);
                byte[] newSymkey = newConfigure.getSymkey();

                // IMPORTANT: Read all data BEFORE changing password context
                // This must happen while we're still in the old password context
                Map<String,KeyInfo> keyInfoMap = KeyInfoManager.getInstance(this).getAllKeyInfos();
                List<SecretDetail> secretList = SecretManager.getInstance(this).getAllSecretDetailList();

                // Re-encrypt all data with new symkey (still using old context to read)
                reEncryptKeyInfoData(keyInfoMap, oldConfigure.getSymkey(), newSymkey);
                reEncryptSecretData(secretList, oldConfigure.getSymkey(), newSymkey);

                // Now update the database context
                DatabaseManager.getInstance(this).changePassword(newPasswordName);

                // Reinitialize managers with new password context
                KeyInfoManager.getInstance(this).initialize(this);
                SecretManager.getInstance(this).initialize(this);
                MultisignManager.getInstance(this).initialize(this);

                // Save all re-encrypted data to new password context
                KeyInfoManager.getInstance(this).addAllKeyInfo(new ArrayList<>(keyInfoMap.values()));
                KeyInfoManager.getInstance(this).commit();
                
                SecretManager.getInstance(this).addAllSecretDetail(secretList);
                SecretManager.getInstance(this).commit();

                // Store new configure and remove old one
                ConfigureManager.getInstance().setConfigure(newConfigure);
                ConfigureManager.getInstance().storeConfigure(this, newConfigure);
                
                if (oldConfigure.getPasswordName() != null) {
                    ConfigureManager.getInstance().removeConfigure(this, oldConfigure.getPasswordName());
                }

                runOnUiThread(() -> {
                    dismissWaitingDialog();
                    Toast.makeText(this, R.string.password_changed, Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dismissWaitingDialog();
                    Toast.makeText(this, getString(R.string.error_during_password_change) + e.getMessage(), Toast.LENGTH_LONG).show();
                    setResult(RESULT_CANCELED);
                    finish();
                });
            }
        }).start();
    }

    private void reEncryptSecretData(List<SecretDetail> secretList, byte[] oldSymKey, byte[] newSymKey) {
        for (SecretDetail secret : secretList) {
            try {
                String contentCipher = secret.getContentCipher();
                if (contentCipher != null) {
                    byte[] contentBytes = Decryptor.decryptPrikey(contentCipher, oldSymKey);
                    if (contentBytes != null) {
                        String newCipher = Encryptor.encryptBySymkeyToJson(contentBytes, newSymKey);
                        secret.setContentCipher(newCipher);
                    }
                }
            } catch (Exception e) {
                TimberLogger.e("ChangePasswordActivity", "Error re-encrypting secret: " + e.getMessage());
                throw new RuntimeException("Failed to re-encrypt secret: " + e.getMessage());
            }
        }
    }

    private void reEncryptKeyInfoData(Map<String,KeyInfo> keyInfoMap, byte[] oldSymKey, byte[] newSymKey) {
        for (KeyInfo keyInfo : keyInfoMap.values()) {
            try {
                String priKeyCipher = keyInfo.getPrikeyCipher();
                if (priKeyCipher != null) {
                    byte[] priKeyBytes = Decryptor.decryptPrikey(priKeyCipher, oldSymKey);
                    if (priKeyBytes != null) {
                        String newCipher = Encryptor.encryptBySymkeyToJson(priKeyBytes, newSymKey);
                        keyInfo.setPrikeyCipher(newCipher);
                    }
                }
            } catch (Exception e) {
                TimberLogger.e("ChangePasswordActivity", "Error re-encrypting key info: " + e.getMessage());
                throw new RuntimeException("Failed to re-encrypt key info: " + e.getMessage());
            }
        }
    }

    private void showWaitingDialog(String hint) {
        runOnUiThread(() -> {
            if (waitingDialog == null) {
                waitingDialog = new WaitingDialog(this, hint);
            } else {
                waitingDialog.setHint(hint);
            }
            waitingDialog.show();
        });
    }

    private void dismissWaitingDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.dismiss();
        }
    }
} 