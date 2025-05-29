package com.fc.safe.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class KeyLabelManager {
    private static final String TAG = "KeyLabelManager";
    private final Context context;
    private final KeyInfoManager keyInfoManager;

    public KeyLabelManager(Context context) {
        this.context = context;
        this.keyInfoManager = KeyInfoManager.getInstance(context);
    }

    public void showLabelInputDialog(KeyInfo keyInfo, AtomicInteger remainingKeys) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter Label for Key");
        
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_key_label, null);
        builder.setView(dialogView);
        
        ImageView avatarView = dialogView.findViewById(R.id.avatar_view);
        TextView idTextView = dialogView.findViewById(R.id.id_text);
        EditText labelInput = dialogView.findViewById(R.id.label_input);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        
        idTextView.setText(keyInfo.getId());
        
        try {
            byte[] avatarBytes = AvatarMaker.createAvatar(keyInfo.getId(), context);
            if (avatarBytes != null) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                avatarView.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to create avatar for key ID %s: %s", keyInfo.getId(), e.getMessage());
        }

        builder.setPositiveButton("OK", (dialog, which) -> {
            String label = labelInput.getText().toString().trim();
            if (!label.isEmpty()) {
                keyInfo.setLabel(label);
            }
            progressBar.setVisibility(View.VISIBLE);
            ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);

            new Thread(() -> {
                try {
                    keyInfoManager.addKeyInfo(keyInfo);
                    keyInfoManager.commit();

                    byte[] avatarBytes = AvatarMaker.createAvatar(keyInfo.getId(), context);
                    if (avatarBytes != null) {
                        keyInfoManager.saveAvatar(keyInfo.getId(), avatarBytes);
                    }

                    ((AppCompatActivity) context).runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(context, R.string.keys_saved_successfully, Toast.LENGTH_LONG).show();

                        if (remainingKeys != null && remainingKeys.decrementAndGet() == 0) {
                            ((AppCompatActivity) context).finish();
                        }
                    });

                } catch (Exception e) {
                    TimberLogger.e(TAG, "Failed to save key ID %s: %s", keyInfo.getId(), e.getMessage());
                    ((AppCompatActivity) context).runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(context, R.string.failed_to_save_key , Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            ((androidx.appcompat.app.AppCompatActivity) context).finish();
        });
        builder.create().show();
    }

    public void saveSelectedKeys(List<KeyInfo> selectedKeys) {
        if (selectedKeys.isEmpty()) {
            Toast.makeText(context, R.string.choose_key, Toast.LENGTH_SHORT).show();
            return;
        }

        AtomicInteger remainingKeys = new AtomicInteger(selectedKeys.size());
        for (KeyInfo keyInfo : selectedKeys) {
            showLabelInputDialog(keyInfo, remainingKeys);
        }
    }
} 