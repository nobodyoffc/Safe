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
import androidx.appcompat.app.AppCompatActivity;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.fc.safe.utils.ToastUtils;

public class KeyLabelManager {
    private static final String TAG = "KeyLabelManager";
    private final Context context;
    private final KeyInfoManager keyInfoManager;
    private List<KeyInfo> keyQueue;
    private int currentKeyIndex;

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
                        ToastUtils.showInfo(context, context.getString(R.string.keys_saved_successfully));

                        if (remainingKeys != null && remainingKeys.decrementAndGet() == 0) {
                            ((AppCompatActivity) context).finish();
                        }
                    });

                } catch (Exception e) {
                    TimberLogger.e(TAG, "Failed to save key ID %s: %s", keyInfo.getId(), e.getMessage());
                    ((AppCompatActivity) context).runOnUiThread(() -> {
                        dialog.dismiss();
                        ToastUtils.showError(context, context.getString(R.string.failed_to_save_key));
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
            ToastUtils.showInfo(context, context.getString(R.string.choose_key));
            return;
        }

        // Initialize the queue and show the first dialog
        keyQueue = new ArrayList<>(selectedKeys);
        currentKeyIndex = 0;
        showNextKeyDialog();
    }

    private void showNextKeyDialog() {
        if (keyQueue == null || currentKeyIndex >= keyQueue.size()) {
            // All dialogs completed
            keyQueue = null;
            currentKeyIndex = 0;
            return;
        }

        KeyInfo keyInfo = keyQueue.get(currentKeyIndex);
        showLabelInputDialogSequential(keyInfo);
    }

    private void showLabelInputDialogSequential(KeyInfo keyInfo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        int remaining = keyQueue.size() - currentKeyIndex;
        builder.setTitle("Enter Label for Key (" + remaining + " remaining)");

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

                        currentKeyIndex++;
                        if (currentKeyIndex >= keyQueue.size()) {
                            // All keys saved
                            ToastUtils.showInfo(context, context.getString(R.string.keys_saved_successfully));
                            ((AppCompatActivity) context).finish();
                        } else {
                            // Show next dialog
                            showNextKeyDialog();
                        }
                    });

                } catch (Exception e) {
                    TimberLogger.e(TAG, "Failed to save key ID %s: %s", keyInfo.getId(), e.getMessage());
                    ((AppCompatActivity) context).runOnUiThread(() -> {
                        dialog.dismiss();
                        ToastUtils.showError(context, context.getString(R.string.failed_to_save_key));
                        // Continue to next key even on error
                        currentKeyIndex++;
                        showNextKeyDialog();
                    });
                }
            }).start();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            // Cancel all remaining dialogs and finish activity
            keyQueue = null;
            currentKeyIndex = 0;
            ((AppCompatActivity) context).finish();
        });

        builder.setCancelable(false); // Prevent dismissing by tapping outside
        builder.create().show();
    }

    public void showEditLabelDialog(KeyInfo keyInfo, Runnable onLabelUpdated) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Label");

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_key_label, null);
        builder.setView(dialogView);

        ImageView avatarView = dialogView.findViewById(R.id.avatar_view);
        TextView idTextView = dialogView.findViewById(R.id.id_text);
        EditText labelInput = dialogView.findViewById(R.id.label_input);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);

        idTextView.setText(keyInfo.getId());

        // Set current label in the input field
        String currentLabel = keyInfo.getLabel();
        if (currentLabel != null && !currentLabel.isEmpty()) {
            labelInput.setText(currentLabel);
            labelInput.setSelection(currentLabel.length()); // Move cursor to end
        }

        try {
            byte[] avatarBytes = AvatarMaker.createAvatar(keyInfo.getId(), context);
            if (avatarBytes != null) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                avatarView.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to create avatar for key ID %s: %s", keyInfo.getId(), e.getMessage());
        }

        builder.setPositiveButton(context.getString(R.string.ok), (dialog, which) -> {
            String newLabel = labelInput.getText().toString().trim();
            keyInfo.setLabel(newLabel);

            progressBar.setVisibility(View.VISIBLE);
            ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEGATIVE).setEnabled(false);

            new Thread(() -> {
                try {
                    keyInfoManager.addKeyInfo(keyInfo);
                    keyInfoManager.commit();

                    ((AppCompatActivity) context).runOnUiThread(() -> {
                        dialog.dismiss();
                        ToastUtils.showInfo(context, context.getString(R.string.label_updated));

                        // Call the callback to update UI
                        if (onLabelUpdated != null) {
                            onLabelUpdated.run();
                        }
                    });

                } catch (Exception e) {
                    TimberLogger.e(TAG, "Failed to update label for key ID %s: %s", keyInfo.getId(), e.getMessage());
                    ((AppCompatActivity) context).runOnUiThread(() -> {
                        dialog.dismiss();
                        ToastUtils.showError(context, context.getString(R.string.failed_to_save_key));
                    });
                }
            }).start();
        });

        builder.setNegativeButton(context.getString(R.string.cancel), (dialog, which) -> dialog.cancel());

        AlertDialog alertDialog = builder.create();
        alertDialog.show();

        // Show keyboard automatically
        labelInput.requestFocus();
        labelInput.postDelayed(() -> {
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(labelInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);
    }
} 