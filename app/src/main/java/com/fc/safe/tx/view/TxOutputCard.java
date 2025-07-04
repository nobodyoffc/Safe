package com.fc.safe.tx.view;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.fc.fc_ajdk.data.fchData.SendTo;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.NumberUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.utils.IdUtils;

import java.io.IOException;

public class TxOutputCard extends CardView {
    private ImageView avatarImage;
    private EditText fidText;
    private EditText amountText;
    private ImageButton deleteButton;
    private SendTo sendTo;
    private OnDeleteListener onDeleteListener;
    private OnValueChangeListener onValueChangeListener;
    private boolean withDelete;
    private boolean editable;

    public interface OnDeleteListener {
        void onDelete(TxOutputCard card);
    }

    public interface OnValueChangeListener {
        void onFidChanged(String newFid);
        void onAmountChanged(double newAmount);
    }

    public TxOutputCard(Context context) {
        super(context);
        init(context);
    }

    public TxOutputCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public TxOutputCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.layout_tx_output_card, this, true);
        avatarImage = findViewById(R.id.avatarImage);
        fidText = findViewById(R.id.fidText);
        amountText = findViewById(R.id.amountText);
        deleteButton = findViewById(R.id.deleteButton);

        // Setup copy functionality for FID
        fidText.setOnClickListener(v -> {
            if (sendTo != null) {
                copyToClipboard(context, getContext().getString(R.string.fid), sendTo.getFid());
            }
        });

        // Setup copy functionality for Amount
        amountText.setOnClickListener(v -> {
            if (sendTo != null) {
                String amount = NumberUtils.formatAmount(sendTo.getAmount()) + " F";
                copyToClipboard(context, getContext().getString(R.string.amount), amount);
            }
        });

        // Add text change listeners
        fidText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (onValueChangeListener != null && sendTo != null) {
                    onValueChangeListener.onFidChanged(s.toString());
                }
            }
        });

        amountText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (onValueChangeListener != null && sendTo != null) {
                    try {
                        double amount = Double.parseDouble(s.toString());
                        onValueChangeListener.onAmountChanged(amount);
                    } catch (NumberFormatException e) {
                        // Invalid number format, ignore
                    }
                }
            }
        });

        // Setup focus change listeners to hide keyboard when focus is lost
        fidText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                hideKeyboard(context, v);
            }
        });

        amountText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                hideKeyboard(context, v);
            }
        });

        deleteButton.setOnClickListener(v -> {
            if (onDeleteListener != null) {
                onDeleteListener.onDelete(this);
            }
        });

        // Setup click listener for avatar to show big image
        avatarImage.setOnClickListener(v -> {
            if (sendTo != null) {
                IdUtils.showAvatarDialog(context, sendTo.getFid());
            }
        });

        // Add long press listener for FID list operations
        View.OnLongClickListener longPressListener = v -> {
            if (sendTo != null) {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
                String[] options = {context.getString(R.string.add_to_fid_list), context.getString(R.string.clear_fid_list)};
                builder.setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Add to FID list
                            SafeApplication.addFid(sendTo.getFid());
                            Toast.makeText(context, context.getString(R.string.add_to_fid_list), Toast.LENGTH_SHORT).show();
                            break;
                        case 1: // Clear FID list
                            SafeApplication.clearFidList();
                            Toast.makeText(context, context.getString(R.string.fid_list_cleared), Toast.LENGTH_SHORT).show();
                            break;
                    }
                });
                builder.show();
                return true;
            }
            return false;
        };

        // Apply long press listener to both the card and FID text
        this.setOnLongClickListener(longPressListener);
        fidText.setOnLongClickListener(longPressListener);

        // Setup touch listener for the card to handle clicks outside edit fields
        this.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                // Check if the touch is outside the edit fields
                if (!isTouchInsideEditFields(event)) {
                    // Clear focus from both edit fields
                    fidText.clearFocus();
                    amountText.clearFocus();
                    // Hide keyboard
                    hideKeyboard(context, v);
                }
            }
            return false;
        });
    }

    private boolean isTouchInsideEditFields(MotionEvent event) {
        // Get the touch coordinates
        float x = event.getX();
        float y = event.getY();

        // Check if the touch is inside the FID edit field
        if (isPointInsideView(x, y, fidText)) {
            return true;
        }

        // Check if the touch is inside the Amount edit field
        if (isPointInsideView(x, y, amountText)) {
            return true;
        }

        return false;
    }

    private boolean isPointInsideView(float x, float y, View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        int left = location[0];
        int top = location[1];
        int right = left + view.getWidth();
        int bottom = top + view.getHeight();

        return x >= left && x <= right && y >= top && y <= bottom;
    }

    private void hideKeyboard(Context context, View view) {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void copyToClipboard(Context context, String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    public void setSendTo(SendTo sendTo, Context context, boolean withDelete) {
        setSendTo(sendTo, context, withDelete, true);
    }

    public void setSendTo(SendTo sendTo, Context context, boolean withDelete, boolean editable) {
        this.sendTo = sendTo;
        this.withDelete = withDelete;
        this.editable = editable;

        int width;
        if(withDelete) width= 21;
        else width = 27;
        fidText.setText(StringUtils.omitMiddle(sendTo.getFid(), width));
        amountText.setText(NumberUtils.formatAmount(sendTo.getAmount()) + " F");
        deleteButton.setVisibility(withDelete ? View.VISIBLE : View.GONE);
        
        fidText.setEnabled(editable);
        amountText.setEnabled(editable);
        
        // Load avatar
        try {
            byte[] avatarBytes = AvatarMaker.makeAvatar(sendTo.getFid(),context);
            Bitmap avatar = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
            avatarImage.setImageBitmap(avatar);
        } catch (IOException e) {
            Toast.makeText(context, R.string.failed_to_load_avatar , SafeApplication.TOAST_LASTING).show();
        }
    }

    public void setSendTo(SendTo sendTo, Context context) {
        setSendTo(sendTo, context, false);
    }

    public SendTo getSendTo() {
        return sendTo;
    }

    public void setOnDeleteListener(OnDeleteListener listener) {
        this.onDeleteListener = listener;
    }

    public void setOnValueChangeListener(OnValueChangeListener listener) {
        this.onValueChangeListener = listener;
    }
} 