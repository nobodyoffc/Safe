package com.fc.safe.ui;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.fc.safe.R;
import com.fc.safe.utils.QRCodeGenerator;
import com.fc.safe.utils.ToastUtils;

import java.util.List;

/**
 * Hands over the random password of an export. The export itself never carries it,
 * so the dialog can only be closed by confirming the password has been saved.
 */
public class RandomPasswordDialog extends Dialog {

    public RandomPasswordDialog(Context context, String password) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_random_password);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        TextView passwordTextView = findViewById(R.id.passwordTextView);
        ImageView qrImageView = findViewById(R.id.passwordQrImageView);
        Button copyButton = findViewById(R.id.copyPasswordButton);
        Button savedButton = findViewById(R.id.savedButton);

        passwordTextView.setText(password);
        List<Bitmap> qrBitmaps = QRCodeGenerator.generateQRBitmaps(password);
        if (!qrBitmaps.isEmpty()) qrImageView.setImageBitmap(qrBitmaps.get(0));

        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.random_password), password));
            ToastUtils.showInfo(context, context.getString(R.string.copied_to_clipboard));
        });
        savedButton.setOnClickListener(v -> dismiss());
    }
}
