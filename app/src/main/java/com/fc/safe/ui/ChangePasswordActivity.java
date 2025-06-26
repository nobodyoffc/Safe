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
import com.fc.safe.initiate.CreatePasswordActivity;
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
    private ActivityResultLauncher<Intent> createPasswordLauncher;
    private WaitingDialog waitingDialog;
    private Configure oldConfigure;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Optionally set a blank layout, or none at all
        // setContentView(new View(this));
        registerActivityResultLaunchers();
        // Start the flow
        Intent intent = new Intent(this, CheckPasswordActivity.class);
        checkPasswordLauncher.launch(intent);
    }

    private void registerActivityResultLaunchers() {
        checkPasswordLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    oldConfigure = ConfigureManager.getInstance().getConfigure();
                    Intent intent = new Intent(this, CreatePasswordActivity.class);
                    createPasswordLauncher.launch(intent);
                } else {
                    finish();
                }
            }
        );
        createPasswordLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    showWaitingDialog("Re-encrypting all keys and secrets...");
                    new Thread(() -> {
                        Thread.currentThread().setName("PasswordChangeThread");
                        try {
                            // Get new symKey
                            Configure newConfigure = ConfigureManager.getInstance().getConfigure();
                            byte[] newSymkey = newConfigure.getSymkey();
                            // Save new configure
                            ConfigureManager.getInstance().storeConfigure(this, newConfigure);
                            
                            // Update database's current password name before removing old configure
                            DatabaseManager.getInstance(this).changePassword(newConfigure.getPasswordName());

                            // Reinitialize KeyInfoManager to use new password name
                            KeyInfoManager.getInstance(this).initialize(this);
                            SecretManager.getInstance(this).initialize(this);
                            MultisignManager.getInstance(this).initialize(this);

                            // Re-encrypt all KeyInfos
                            reEncryptPriKeyOfKeyInfos(newSymkey);

                            // Re-encrypt all SecretDetails
                            reEncryptContentOfSecrets(newSymkey);


                            // Remove old configure
                            if (oldConfigure.getPasswordName() != null) {
                                ConfigureManager.getInstance().removeConfigure(this,oldConfigure.getPasswordName());
                            }
                            runOnUiThread(() -> {
                                dismissWaitingDialog();
                                Toast.makeText(this, R.string.password_changed , Toast.LENGTH_LONG).show();
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
                } else {
                    finish();
                }
            }
        );
    }

    private void reEncryptContentOfSecrets(byte[] newSymKey) {
        SecretManager secretManager = SecretManager.getInstance(this);
        List<SecretDetail> secretList = secretManager.getAllSecretDetailList();
        for (SecretDetail secret : secretList) {
            try {
                String contentCipher = secret.getContentCipher();
                if (contentCipher != null) {
                    byte[] contentBytes = Decryptor.decryptPrikey(contentCipher, oldConfigure.getSymkey());
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
        secretManager.addAllSecretDetail(secretList);
        secretManager.commit();
    }

    private void reEncryptPriKeyOfKeyInfos(byte[] newSymKey) {
        KeyInfoManager keyInfoManager = KeyInfoManager.getInstance(this);
        Map<String,KeyInfo> keyInfoMap = keyInfoManager.getAllKeyInfos();
        for (KeyInfo keyInfo : keyInfoMap.values()) {
            try {
                String priKeyCipher = keyInfo.getPrikeyCipher();
                if (priKeyCipher != null) {
                    byte[] priKeyBytes = Decryptor.decryptPrikey(priKeyCipher, oldConfigure.getSymkey());
                    if (priKeyBytes != null) {
                        String newCipher = Encryptor.encryptBySymkeyToJson(priKeyBytes, newSymKey);
                        keyInfo.setPrikeyCipher(newCipher);
                        keyInfoMap.put(keyInfo.getId(), keyInfo);
                    }
                }
            } catch (Exception e) {
                TimberLogger.e("ChangePasswordActivity", "Error re-encrypting key info: " + e.getMessage());
                throw new RuntimeException("Failed to re-encrypt key info: " + e.getMessage());
            }
        }

        keyInfoManager.addAllKeyInfo(new ArrayList<>(keyInfoMap.values()));
        keyInfoManager.commit();
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