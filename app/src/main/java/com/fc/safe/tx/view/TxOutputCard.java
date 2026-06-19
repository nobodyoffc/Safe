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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.NumberUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.utils.IdUtils;

import java.io.IOException;
import com.fc.safe.utils.ToastUtils;

public class TxOutputCard extends CardView {
    private ImageView avatarImage;
    private EditText fidText;
    private EditText amountText;
    private ImageButton deleteButton;
    private Cash sendTo;
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
                copyToClipboard(context, getContext().getString(R.string.fid), sendTo.getOwner());
            }
        });

        // Setup copy functionality for Amount
        amountText.setOnClickListener(v -> {
            if (sendTo != null) {
                String amount = NumberUtils.formatAmount(sendTo.getAmount()) + " " + getContext().getString(R.string.currency_fch);
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
                IdUtils.showAvatarDialog(context, sendTo.getOwner());
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
                            SafeApplication.addFid(sendTo.getOwner());
                            ToastUtils.showInfo(context, context.getString(R.string.add_to_fid_list));
                            break;
                        case 1: // Clear FID list
                            SafeApplication.clearFidList();
                            ToastUtils.showInfo(context, context.getString(R.string.fid_list_cleared));
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
        ToastUtils.showInfo(context, context.getString(R.string.copied));
    }

    public void setSendTo(Cash sendTo, Context context, boolean withDelete) {
        setSendTo(sendTo, context, withDelete, true);
    }

    public void setSendTo(Cash sendTo, Context context, boolean withDelete, boolean editable) {
        this.sendTo = sendTo;
        this.withDelete = withDelete;
        this.editable = editable;

        int width;
        if(withDelete) width= 21;
        else width = 27;
        fidText.setText(StringUtils.omitMiddle(sendTo.getOwner(), width));
        amountText.setText(NumberUtils.formatAmount(sendTo.getAmount()) + " " + getContext().getString(R.string.currency_fch));
        deleteButton.setVisibility(withDelete ? View.VISIBLE : View.GONE);

        fidText.setEnabled(editable);
        amountText.setEnabled(editable);

        renderLockTime(sendTo.getLockTime());

        // Load avatar
        try {
            byte[] avatarBytes = AvatarMaker.makeAvatar(sendTo.getOwner(),context);
            Bitmap avatar = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
            avatarImage.setImageBitmap(avatar);
        } catch (IOException e) {
            Toast.makeText(context, R.string.failed_to_load_avatar , SafeApplication.TOAST_LASTING).show();
        }
    }

    private void renderLockTime(Long lockTime) {
        LinearLayout container = findViewById(R.id.lockTimeContainer);
        if (container == null) return;
        if (lockTime == null || lockTime <= 0) {
            container.setVisibility(View.GONE);
            return;
        }
        TextView text = findViewById(R.id.lockTimeText);
        text.setText(getContext().getString(R.string.unlock_at_block, lockTime));
        container.setVisibility(View.VISIBLE);
    }

    public void setSendTo(Cash sendTo, Context context) {
        setSendTo(sendTo, context, false);
    }

    public Cash getSendTo() {
        return sendTo;
    }

    public void setOnDeleteListener(OnDeleteListener listener) {
        this.onDeleteListener = listener;
    }

    public void setOnValueChangeListener(OnValueChangeListener listener) {
        this.onValueChangeListener = listener;
    }
} 