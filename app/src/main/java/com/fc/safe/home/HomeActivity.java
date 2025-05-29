package com.fc.safe.home;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.DatabaseManager;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.utils.IconCreator;
import com.fc.safe.ui.PopupMenuHelper;


public class HomeActivity extends AppCompatActivity {
    private static final String TAG = "HomeActivity";
    private PopupMenuHelper popupMenuHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // Log that we're starting the HomeActivity
            TimberLogger.d(TAG, "HomeActivity onCreate started");
            
            // Initialize PopupMenuHelper
            popupMenuHelper = new PopupMenuHelper(this);
            
            // Get the Configure object from ConfigureManager
            Configure configure = ConfigureManager.getInstance().getConfigure();
            if (configure != null) {
                TimberLogger.d(TAG, "Configure object retrieved from ConfigureManager");
                
                // Set the current password name in DatabaseManager
                String passwordName = configure.getPasswordName();
                if (passwordName != null) {
                    DatabaseManager.getInstance(this).setCurrentPasswordName(passwordName);
                    TimberLogger.d(TAG, "Set current password name to: " + passwordName);
                }
            } else {
                TimberLogger.w(TAG, "No Configure object available in ConfigureManager");
            }
            
            // Set the content view
            setContentView(R.layout.activity_home);
            TimberLogger.d(TAG, "Content view set successfully");

            // Generate icons for all menu items
            generateMenuIcons();
            TimberLogger.d(TAG, "Menu icons generated successfully");

            // Initialize click listeners for all menu items
            setupMenuClickListeners();
            TimberLogger.d(TAG, "Menu click listeners setup completed");

            // Set initial scroll position to bottom
            NestedScrollView iconContainer = findViewById(R.id.iconContainer);
            if (iconContainer != null) {
                iconContainer.post(() -> {
                    iconContainer.fullScroll(View.FOCUS_DOWN);
                });
            }

        } catch (Exception ex) {
            // Log any exceptions that occur during initialization
            TimberLogger.e(TAG, "Error in HomeActivity onCreate: " + ex.getMessage(), ex);
            Toast.makeText(this, "Error initializing HomeActivity: " + ex.getMessage(), SafeApplication.TOAST_LASTING).show();
        }
    }

    private void generateMenuIcons() {
        try {
            // Generate icons for all menu items except settings
            generateIconForMenuItem(R.id.menu_list, getString(R.string.menu_list));
            generateIconForMenuItem(R.id.menu_totp, getString(R.string.menu_totp));
            generateIconForMenuItem(R.id.menu_qr_code, getString(R.string.menu_qr_code));
        //    generateIconForMenuItem(R.id.menu_test, getString(R.string.menu_test));
            generateIconForMenuItem(R.id.menu_decode, getString(R.string.menu_decode));
            generateIconForMenuItem(R.id.menu_hash, getString(R.string.menu_hash));
            generateIconForMenuItem(R.id.menu_decrypt, getString(R.string.menu_decrypt));
            generateIconForMenuItem(R.id.menu_encrypt, getString(R.string.menu_encrypt));
            generateIconForMenuItem(R.id.menu_verify_message, getString(R.string.menu_verify));
            generateIconForMenuItem(R.id.menu_sign_message, getString(R.string.menu_sign_words));
            generateIconForMenuItem(R.id.menu_multisign, getString(R.string.menu_multi_sign));
            generateIconForMenuItem(R.id.menu_sign_tx, getString(R.string.menu_sign_tx));
            generateIconForMenuItem(R.id.menu_convert, getString(R.string.menu_convert));
            generateIconForMenuItem(R.id.menu_secrets, getString(R.string.menu_secrets));
            generateIconForMenuItem(R.id.menu_my_keys, getString(R.string.menu_my_keys));
            generateIconForMenuItem(R.id.menu_random, getString(R.string.menu_random));
            // Skip settings as it uses a custom gear icon
        } catch (Exception ex) {
            TimberLogger.e(TAG, "Error generating menu icons: " + ex.getMessage(), ex);
        }
    }

    private void generateIconForMenuItem(int menuItemId, String menuText) {
        View menuItem = findViewById(menuItemId);
        if (menuItem != null) {
            ImageView iconView = menuItem.findViewWithTag("icon");
            if (iconView == null) {
                // Find the ImageView within the LinearLayout
                if (menuItem instanceof ViewGroup) {
                    ViewGroup viewGroup = (ViewGroup) menuItem;
                    for (int i = 0; i < viewGroup.getChildCount(); i++) {
                        View child = viewGroup.getChildAt(i);
                        if (child instanceof ImageView) {
                            iconView = (ImageView) child;
                            break;
                        }
                    }
                }
            }
            
            if (iconView != null) {
                // Generate icon with the menu text
                Bitmap iconBitmap = IconCreator.createSquareIcon(menuText, 82, 2, getColor(R.color.main_background), getColor(R.color.colorAccent));
                iconView.setImageBitmap(iconBitmap);
            } else {
                TimberLogger.e(TAG, "ImageView not found in menu item: " + menuText);
            }
        } else {
            TimberLogger.e(TAG, "Menu item not found: " + menuText);
        }
    }

    private void setupMenuClickListeners() {
        try {
            // List
            View listView = findViewById(R.id.menu_list);
            if (listView != null) {
                listView.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, FidListActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_list view not found");
            }

            // My Keys
            View myKeysView = findViewById(R.id.menu_my_keys);
            if (myKeysView != null) {
                myKeysView.setOnClickListener(v -> {
                    // Launch MyKeysActivity
                    Intent intent = new Intent(HomeActivity.this, MyKeysActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_my_keys view not found");
            }

            // Test
        //    View testView = findViewById(R.id.menu_test);
        //    if (testView != null) {
        //        testView.setOnClickListener(v -> {
        //            Intent intent = new Intent(HomeActivity.this, TestActivity.class);
        //            startActivity(intent);
        //        });
        //    } else {
        //        TimberLogger.e(TAG, "menu_test view not found");
        //    }

            // Secrets
            View secretsView = findViewById(R.id.menu_secrets);
            if (secretsView != null) {
                secretsView.setOnClickListener(v -> {
                    // Launch SecretsActivity
                    Intent intent = new Intent(HomeActivity.this, SecretActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_secrets view not found");
            }

            // Key Tools
            View keyToolsView = findViewById(R.id.menu_convert);
            if (keyToolsView != null) {
                keyToolsView.setOnClickListener(v -> {
                    popupMenuHelper.showKeyToolsMenu(keyToolsView);
                });
            } else {
                TimberLogger.e(TAG, "menu_key_tools view not found");
            }

            // Sign TX
            View signTxView = findViewById(R.id.menu_sign_tx);
            if (signTxView != null) {
                signTxView.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, CreateTxActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_sign_tx view not found");
            }

            // Multisign
            View multisignView = findViewById(R.id.menu_multisign);
            if (multisignView != null) {
                multisignView.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, MultisignActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_multisign view not found");
            }

            // Sign Message
            View signMessageView = findViewById(R.id.menu_sign_message);
            if (signMessageView != null) {
                signMessageView.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, SignMsgActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_sign_message view not found");
            }

            // Verify Message
            View verifyMessageView = findViewById(R.id.menu_verify_message);
            if (verifyMessageView != null) {
                verifyMessageView.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, VerifyActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_verify_message view not found");
            }

            // Encrypt
            View encryptView = findViewById(R.id.menu_encrypt);
            if (encryptView != null) {
                encryptView.setOnClickListener(v -> {
                    // Launch EncryptActivity
                    Intent intent = new Intent(HomeActivity.this, EncryptActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_encrypt view not found");
            }

            // Decrypt
            View decryptView = findViewById(R.id.menu_decrypt);
            if (decryptView != null) {
                decryptView.setOnClickListener(v -> {
                    // Launch DecryptActivity
                    Intent intent = new Intent(HomeActivity.this, DecryptActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_decrypt view not found");
            }

            // Decode
            View decodeView = findViewById(R.id.menu_decode);
            if (decodeView != null) {
                decodeView.setOnClickListener(v -> {
                    // Launch DecodeActivity
                    Intent intent = new Intent(HomeActivity.this, DecodeActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_decode view not found");
            }

            // Hash
            View hashView = findViewById(R.id.menu_hash);
            if (hashView != null) {
                hashView.setOnClickListener(v -> {
                    // Launch HashActivity
                    Intent intent = new Intent(HomeActivity.this, HashActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_hash view not found");
            }

            // QR Code
            View qrCodeView = findViewById(R.id.menu_qr_code);
            if (qrCodeView != null) {
                qrCodeView.setOnClickListener(v -> {
                    // Launch QRCodeActivity
                    Intent intent = new Intent(HomeActivity.this, com.fc.safe.qr.QrCodeActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_qr_code view not found");
            }

            // TOTP
            View totpView = findViewById(R.id.menu_totp);
            if (totpView != null) {
                totpView.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, TotpActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_totp view not found");
            }

            // Random
            View randomView = findViewById(R.id.menu_random);
            if (randomView != null) {
                randomView.setOnClickListener(v -> {
                    Intent intent = new Intent(HomeActivity.this, com.fc.safe.tools.RandomBytesGeneratorActivity.class);
                    startActivity(intent);
                });
            } else {
                TimberLogger.e(TAG, "menu_random view not found");
            }

            // Settings
            View settingsView = findViewById(R.id.menu_settings);
            if (settingsView != null) {
                settingsView.setOnClickListener(v -> {
                    popupMenuHelper.showSettingsMenu(settingsView);
                });
            } else {
                TimberLogger.e(TAG, "menu_settings view not found");
            }
        } catch (Exception ex) {
            TimberLogger.e(TAG, "Error in setupMenuClickListeners: " + ex.getMessage(), ex);
            Toast.makeText(this, "Error setting up menu: " + ex.getMessage(), SafeApplication.TOAST_LASTING).show();
        }
    }
} 