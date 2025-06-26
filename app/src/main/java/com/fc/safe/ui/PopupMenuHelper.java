package com.fc.safe.ui;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.fc.safe.R;
import com.fc.safe.db.DatabaseManager;
import com.fc.safe.home.ArticleDisplayActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.myKeys.CreateKeyByFidActivity;
import com.fc.safe.myKeys.CreateKeyByPhraseActivity;
import com.fc.safe.myKeys.CreateKeyByPrikeyActivity;
import com.fc.safe.myKeys.CreateKeyByPrikeyCipherActivity;
import com.fc.safe.myKeys.CreateKeyByPubkeyActivity;
import com.fc.safe.myKeys.RandomNewKeysActivity;
import com.fc.safe.myKeys.FindNiceKeysActivity;
import com.fc.safe.tools.PrikeyConverterActivity;
import com.fc.safe.tools.PubkeyConverterActivity;
import com.fc.safe.tools.AddressConverterActivity;
import com.fc.safe.myKeys.ImportKeyInfoActivity;
import com.fc.safe.secret.ImportSecretActivity;
import com.fc.safe.tools.JsonConvertActivity;


public class PopupMenuHelper {
    private static final String TAG = "PopupMenu";

    private final Context context;
    private PopupWindow popupWindow;

    public PopupMenuHelper(Context context) {
        this.context = context;
    }

    public void showCreateKeyMenu(View anchorView) {
        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_menu_create, null);
        popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(10f);

        setupCreateKeyMenuItems(popupView);
        showPopup(anchorView);
    }

    public void showKeyToolsMenu(View anchorView) {
        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_menu_key_tools, null);
        popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(10f);

        setupKeyToolsMenuItems(popupView);
        showPopup(anchorView);
    }

    public void showSettingsMenu(View anchorView) {
        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_menu_settings, null);
        popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setElevation(10f);

        setupSettingsMenuItems(popupView);
        showPopup(anchorView);
    }

    private void setupCreateKeyMenuItems(View popupView) {
        TextView fromPriKey = popupView.findViewById(R.id.from_pri_key);
        TextView fromPriKeyCipher = popupView.findViewById(R.id.from_pri_key_cipher);
        TextView fromPubKey = popupView.findViewById(R.id.from_pub_key);
        TextView inputFid = popupView.findViewById(R.id.input_fid);
        TextView findNiceFid = popupView.findViewById(R.id.find_nice_fid);
        TextView createNewKeys = popupView.findViewById(R.id.create_new_keys);
        TextView fromPhrase = popupView.findViewById(R.id.input_phrase);
        TextView importKeys = popupView.findViewById(R.id.import_keys);

        createNewKeys.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, RandomNewKeysActivity.class));
        });

        fromPhrase.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, CreateKeyByPhraseActivity.class));
        });

        fromPriKey.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, CreateKeyByPrikeyActivity.class));
        });

        fromPriKeyCipher.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, CreateKeyByPrikeyCipherActivity.class));
        });

        fromPubKey.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, CreateKeyByPubkeyActivity.class));
        });

        inputFid.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, CreateKeyByFidActivity.class));
        });

        findNiceFid.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, FindNiceKeysActivity.class));
        });

        importKeys.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, ImportKeyInfoActivity.class));
        });
    }

    private void setupKeyToolsMenuItems(View popupView) {
        TextView privateKeyConverter = popupView.findViewById(R.id.private_key_converter);
        TextView publicKeyConverter = popupView.findViewById(R.id.public_key_converter);
        TextView addressConverter = popupView.findViewById(R.id.address_converter);
        TextView jsonConverter = popupView.findViewById(R.id.json_converter);

        privateKeyConverter.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, PrikeyConverterActivity.class));
        });

        publicKeyConverter.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, PubkeyConverterActivity.class));
        });

        addressConverter.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, AddressConverterActivity.class));
        });

        jsonConverter.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, JsonConvertActivity.class));
        });
    }

    private void setupSettingsMenuItems(View popupView) {
        TextView changePassword = popupView.findViewById(R.id.change_password);
        TextView backupAllKeys = popupView.findViewById(R.id.backup_all_keys);
        TextView backupAllSecrets = popupView.findViewById(R.id.backup_all_secrets);
        TextView importKeys = popupView.findViewById(R.id.import_keys);
        TextView importSecrets = popupView.findViewById(R.id.import_secrets);
        TextView security_guidelines = popupView.findViewById(R.id.security_guidelines);
        TextView clearAllData = popupView.findViewById(R.id.clear_all_data);
        TextView removeMe = popupView.findViewById(R.id.remove_me);

        // Set click listeners for each menu item
        changePassword.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, ChangePasswordActivity.class));
        });

        backupAllKeys.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, com.fc.safe.myKeys.BackupKeysActivity.class));
        });

        backupAllSecrets.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, com.fc.safe.secret.BackupSecretsActivity.class));
        });

        importKeys.setOnClickListener(v -> {
            popupWindow.dismiss();
            context.startActivity(new Intent(context, ImportKeyInfoActivity.class));
        });

        importSecrets.setOnClickListener(v -> {
            popupWindow.dismiss();
            Intent intent = new Intent(context, ImportSecretActivity.class);
            intent.putExtra("type", "secret");
            context.startActivity(intent);
        });

        security_guidelines.setOnClickListener(v -> {
            popupWindow.dismiss();
            Intent intent = ArticleDisplayActivity.newIntent(context,
                context.getString(R.string.security_guidelines),
                context.getString(R.string.security_guidelines_text));
            context.startActivity(intent);
        });

        clearAllData.setOnClickListener(v -> {
            popupWindow.dismiss();
            showClearAllDataConfirmation();
        });

        removeMe.setOnClickListener(v -> {
            popupWindow.dismiss();
            showRemoveMeConfirmation();
        });
    }

    private void showClearAllDataConfirmation() {
        UserConfirmDialog firstDialog = new UserConfirmDialog(context, 
            context.getString(R.string.you_are_clear_all_the_data_of_safe_do_you_sure_to_do_it),
            choice -> {
                if (choice == UserConfirmDialog.Choice.YES) {
                    showSecondConfirmation();
                }
            }, true);
        firstDialog.show();
    }

    private void showSecondConfirmation() {
        UserConfirmDialog secondDialog = new UserConfirmDialog(context,
                context.getString(R.string.cleared_data_cannot_be_recovered_are_you_sure_to_clear_all_data),
            choice -> {
                if (choice == UserConfirmDialog.Choice.YES) {
                    DatabaseManager databaseManager = DatabaseManager.getInstance(context);
                    databaseManager.clearAllDatabases();
//                    ConfigureManager.getInstance().removeConfigure(context,databaseManager.getCurrentPasswordName());
                    Toast.makeText(context, R.string.all_data_cleared_successfully , Toast.LENGTH_SHORT).show();
                }
            }, true);
        secondDialog.show();
    }

    private void showRemoveMeConfirmation() {
        UserConfirmDialog firstDialog = new UserConfirmDialog(context, 
            context.getString(R.string.you_are_clear_all_the_data_of_safe_do_you_sure_to_do_it),
            choice -> {
                if (choice == UserConfirmDialog.Choice.YES) {
                    showSecondRemoveMeConfirmation();
                }
            }, true);
        firstDialog.show();
    }

    private void showSecondRemoveMeConfirmation() {
        UserConfirmDialog secondDialog = new UserConfirmDialog(context,
                context.getString(R.string.cleared_data_cannot_be_recovered_are_you_sure_to_clear_all_data),
            choice -> {
                if (choice == UserConfirmDialog.Choice.YES) {
                    // Clear all databases
                    DatabaseManager databaseManager = DatabaseManager.getInstance(context);
                    String currentPasswordName = databaseManager.getCurrentPasswordName();
                    databaseManager.clearAllDatabases();
                    
                    // Remove the configure for current password name
                    ConfigureManager.getInstance().removeConfigure(context, currentPasswordName);
                    
                    // Show success message
                    Toast.makeText(context, R.string.all_data_cleared_successfully, Toast.LENGTH_SHORT).show();
                    
                    // Close the app
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).finish();
                    }
                }
            }, true);
        secondDialog.show();
    }

    private void showPopup(View anchorView) {
        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);
        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, 
            location[0], location[1] - popupWindow.getHeight());
    }
} 