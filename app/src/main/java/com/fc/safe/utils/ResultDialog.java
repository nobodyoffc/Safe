package com.fc.safe.utils;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import com.fc.safe.R;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.core.crypto.KeyTools;

public class ResultDialog {
    public interface ResultDialogListener {
        void onMakeQr(String message);
        void onCopy(String message);
        void onDone();
    }

    public static void show(Context context, String title, byte[] messageBytes, ResultDialogListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_result, null);

        EditText messageText = dialogView.findViewById(R.id.message_text);
        CheckBox hexCheckBox = dialogView.findViewById(R.id.hex_checkbox);
        String message = new String(messageBytes);
        messageText.setText(message);
        messageText.setSelection(0);

        dialogView.<android.widget.TextView>findViewById(R.id.title_text).setText(title);

        Button makeQrButton = dialogView.findViewById(R.id.makeQrButton);
        Button copyButton = dialogView.findViewById(R.id.copyButton);
        Button doneButton = dialogView.findViewById(R.id.doneButton);

        AlertDialog dialog = builder.setView(dialogView).create();

        makeQrButton.setOnClickListener(v -> {
            String value = hexCheckBox.isChecked() ? Hex.toHex(messageBytes) : message;
            QRCodeGenerator.generateAndShowQRCode(context, value);
            dialog.dismiss();
        });
        copyButton.setOnClickListener(v -> {
            String value = hexCheckBox.isChecked() ? Hex.toHex(messageBytes) : message;
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("result_message", value);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show();
            if (listener != null) listener.onCopy(value);
            dialog.dismiss();
        });
        doneButton.setOnClickListener(v -> {
            if (listener != null) listener.onDone();
            dialog.dismiss();
        });

        // Add Hex checkbox logic
        hexCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                String hexMessage = Hex.toHex(messageBytes);
                messageText.setText(hexMessage);
            } else {
                messageText.setText(message);
            }
            messageText.setSelection(0);
        });

        dialog.show();
    }

    /**
     * Attempts to decrypt the given cipher text and show the result in a dialog.
     */
    public static void showDecryptDialogForCipher(Context context, String cipherText, ResultDialogListener listener) {
        try {
            com.fc.fc_ajdk.core.crypto.CryptoDataByte cryptoDataByte = null;
            byte[] symkey = com.fc.safe.initiate.ConfigureManager.getInstance().getSymkey();
            if (symkey == null) {
                Toast.makeText(context, context.getString(R.string.no_symmetric_key_found), Toast.LENGTH_SHORT).show();
                return;
            }
            if (com.fc.fc_ajdk.utils.JsonUtils.isJson(cipherText)) {
                cryptoDataByte = com.fc.fc_ajdk.core.crypto.CryptoDataByte.fromJson(cipherText);
            } else if (com.fc.fc_ajdk.utils.StringUtils.isBase64(cipherText)) {
                byte[] bundle = android.util.Base64.decode(cipherText, android.util.Base64.DEFAULT);
                cryptoDataByte = com.fc.fc_ajdk.core.crypto.CryptoDataByte.fromBundle(bundle);
            } else {
                Toast.makeText(context, context.getString(R.string.not_valid_cipher_format), Toast.LENGTH_SHORT).show();
                return;
            }
            com.fc.fc_ajdk.core.crypto.Decryptor.decryptBySymkey(cryptoDataByte, com.fc.fc_ajdk.utils.Hex.toHex(symkey));
            if (cryptoDataByte.getCode() != null && cryptoDataByte.getCode() != 0) {
                Toast.makeText(context, context.getString(R.string.decrypt_failed, cryptoDataByte.getMessage()), Toast.LENGTH_SHORT).show();
                return;
            }
            show(context, context.getString(R.string.result), cryptoDataByte.getData(), listener);
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.decrypt_error, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Attempts to decrypt the given priKeyCipher and show the result in WIF compressed format.
     */
    public static void showDecryptDialogForPrikeyCipher(Context context, String cipherText, ResultDialogListener listener) {
        try {
            com.fc.fc_ajdk.core.crypto.CryptoDataByte cryptoDataByte = null;
            byte[] symkey = com.fc.safe.initiate.ConfigureManager.getInstance().getSymkey();
            if (symkey == null) {
                Toast.makeText(context, context.getString(R.string.no_symmetric_key_found), Toast.LENGTH_SHORT).show();
                return;
            }
            if (com.fc.fc_ajdk.utils.JsonUtils.isJson(cipherText)) {
                cryptoDataByte = com.fc.fc_ajdk.core.crypto.CryptoDataByte.fromJson(cipherText);
            } else if (com.fc.fc_ajdk.utils.StringUtils.isBase64(cipherText)) {
                byte[] bundle = android.util.Base64.decode(cipherText, android.util.Base64.DEFAULT);
                cryptoDataByte = com.fc.fc_ajdk.core.crypto.CryptoDataByte.fromBundle(bundle);
            } else {
                Toast.makeText(context, context.getString(R.string.not_valid_cipher_format), Toast.LENGTH_SHORT).show();
                return;
            }
            com.fc.fc_ajdk.core.crypto.Decryptor.decryptBySymkey(cryptoDataByte, com.fc.fc_ajdk.utils.Hex.toHex(symkey));
            if (cryptoDataByte.getCode() != null && cryptoDataByte.getCode() != 0) {
                Toast.makeText(context, context.getString(R.string.decrypt_failed, cryptoDataByte.getMessage()), Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Get the decrypted private key bytes
            byte[] messageBytes = cryptoDataByte.getData();
            
            // Create a custom dialog that handles both WIF and hex formats
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_result, null);

            EditText messageText = dialogView.findViewById(R.id.message_text);
            CheckBox hexCheckBox = dialogView.findViewById(R.id.hex_checkbox);
            
            // Set initial text as WIF compressed format
            String wifCompressed = com.fc.fc_ajdk.core.crypto.KeyTools.prikey32To38WifCompressed(com.fc.fc_ajdk.utils.Hex.toHex(messageBytes));
            messageText.setText(wifCompressed);
            messageText.setSelection(0);

            dialogView.<android.widget.TextView>findViewById(R.id.title_text).setText(context.getString(R.string.result));

            Button makeQrButton = dialogView.findViewById(R.id.makeQrButton);
            Button copyButton = dialogView.findViewById(R.id.copyButton);
            Button doneButton = dialogView.findViewById(R.id.doneButton);

            AlertDialog dialog = builder.setView(dialogView).create();

            makeQrButton.setOnClickListener(v -> {
                String value = hexCheckBox.isChecked() ? com.fc.fc_ajdk.utils.Hex.toHex(messageBytes) : wifCompressed;
                QRCodeGenerator.generateAndShowQRCode(context, value);
                dialog.dismiss();
            });
            copyButton.setOnClickListener(v -> {
                String value = hexCheckBox.isChecked() ? com.fc.fc_ajdk.utils.Hex.toHex(messageBytes) : wifCompressed;
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("result_message", value);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show();
                if (listener != null) listener.onCopy(value);
                dialog.dismiss();
            });
            doneButton.setOnClickListener(v -> {
                if (listener != null) listener.onDone();
                dialog.dismiss();
            });

            // Add Hex checkbox logic
            hexCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    String hexMessage = com.fc.fc_ajdk.utils.Hex.toHex(messageBytes);
                    messageText.setText(hexMessage);
                } else {
                    messageText.setText(wifCompressed);
                }
                messageText.setSelection(0);
            });

            dialog.show();
        } catch (Exception e) {
            Toast.makeText(context, context.getString(R.string.decrypt_error, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }
} 