package com.fc.safe.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.SafeApplication;

import java.util.ArrayList;
import java.util.List;

public class KeyCardManager {
    private static final String TAG = "KeyCardManager";
    private final Context context;
    private final ViewGroup keyListContainer;
    private final List<KeyInfo> keyInfoList;
    private final List<CompoundButton> checkBoxes;
    private final Boolean isSingleChoice;
    private OnKeyListChangedListener onKeyListChangedListener;
    private List<String> menuItems;
    private OnMenuItemClickListener onMenuItemClickListener;

    public interface OnKeyListChangedListener {
        void onKeyListChanged(List<KeyInfo> updatedKeyInfoList);
    }

    public interface OnMenuItemClickListener {
        void onMenuItemClick(String menuItem, KeyInfo keyInfo);
    }

    public KeyCardManager(Context context, LinearLayout keyListContainer) {
        this(context, keyListContainer, null);
    }

    public KeyCardManager(Context context, LinearLayout keyListContainer, Boolean isSingleChoice) {
        this(context, keyListContainer, isSingleChoice, null);
    }

    public KeyCardManager(Context context, LinearLayout keyListContainer, Boolean isSingleChoice, List<String> menuItems) {
        this.context = context;
        this.keyListContainer = keyListContainer;
        this.keyInfoList = new ArrayList<>();
        this.checkBoxes = new ArrayList<>();
        this.isSingleChoice = isSingleChoice;
        this.menuItems = menuItems;
    }

    public void setOnKeyListChangedListener(OnKeyListChangedListener listener) {
        this.onKeyListChangedListener = listener;
    }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        this.onMenuItemClickListener = listener;
    }

    public void setMenuItems(List<String> menuItems) {
        this.menuItems = menuItems;
    }

    private void notifyKeyListChanged() {
        if (onKeyListChangedListener != null) {
            onKeyListChangedListener.onKeyListChanged(new ArrayList<>(keyInfoList));
        }
    }

    public void addKeyCard(KeyInfo keyInfo) {
        int layoutResId;
        if (isSingleChoice == null) {
            layoutResId = R.layout.item_key_card;
        } else if (isSingleChoice) {
            layoutResId = R.layout.item_key_card_radio;
        } else {
            layoutResId = R.layout.item_key_card_checkbox;
        }
        
        View cardView = LayoutInflater.from(context).inflate(layoutResId, keyListContainer, false);
        
        CompoundButton checkBox;
        if (isSingleChoice != null) {
            checkBox = cardView.findViewById(R.id.key_checkbox);
            checkBoxes.add(checkBox);
            if (isSingleChoice) {
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        // Uncheck all other checkboxes
                        for (CompoundButton cb : checkBoxes) {
                            if (cb != checkBox) {
                                cb.setChecked(false);
                            }
                        }
                    }
                });
            } else {
                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    notifyKeyListChanged();
                });
            }
        } else {
            checkBox = null;
        }

        ImageView avatar = cardView.findViewById(R.id.key_avatar);
        TextView keyLabel = cardView.findViewById(R.id.key_label);
        TextView keyId = cardView.findViewById(R.id.key_id);

        // Set up click listeners for the card view
        cardView.setOnClickListener(v -> {
            if (isSingleChoice != null && v.getId() != R.id.key_checkbox) {
                checkBox.setChecked(!checkBox.isChecked());
            } else {
                showKeyDetail(keyInfo);
            }
        });

        try {
            byte[] avatarBytes = AvatarMaker.createAvatar(keyInfo.getId(), context);
            if (avatarBytes != null) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                avatar.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to create avatar for key ID %s: %s", keyInfo.getId(), e.getMessage());
            Toast.makeText(context, R.string.failed_to_create_avatar, Toast.LENGTH_SHORT).show();
        }
        keyLabel.setText(keyInfo.getLabel());
        keyLabel.setTextColor(context.getResources().getColor(R.color.field_name, context.getTheme()));
        keyLabel.setTypeface(keyLabel.getTypeface(), android.graphics.Typeface.BOLD);
        keyId.setText(keyInfo.getId());

        // Add long press listeners for text views
        View.OnLongClickListener longPressListener = v -> {
            if (menuItems != null && !menuItems.isEmpty()) {
                PopupMenu popup = new PopupMenu(context, v);
                for (String menuItem : menuItems) {
                    popup.getMenu().add(menuItem);
                }
                
                popup.setOnMenuItemClickListener(item -> {
                    String title = item.getTitle().toString();
                    if ("Delete".equals(title)) {
                        // Remove the card from the container
                        keyListContainer.removeView(cardView);
                        // Remove the key info from the list
                        int index = keyInfoList.indexOf(keyInfo);
                        if (index != -1) {
                            keyInfoList.remove(index);
                            if (index < checkBoxes.size()) {
                                checkBoxes.remove(index);
                            }
                        }
                        notifyKeyListChanged();
                        return true;
                    } else if (context.getString(R.string.add_to_fid_list).equals(title)) {
                        SafeApplication.addFid(keyInfo.getId());
                        Toast.makeText(context, context.getString(R.string.added_to_fid_list), Toast.LENGTH_SHORT).show();
                        return true;
                    } else if (context.getString(R.string.clear_fid_list).equals(title)) {
                        SafeApplication.clearFidList();
                        Toast.makeText(context, R.string.fid_list_cleared, Toast.LENGTH_SHORT).show();
                        return true;
                    } else if (onMenuItemClickListener != null) {
                        onMenuItemClickListener.onMenuItemClick(title, keyInfo);
                        return true;
                    }
                    return false;
                });
                
                // Add the new menu items
                popup.getMenu().add(context.getString(R.string.add_to_fid_list));
                popup.getMenu().add(context.getString(R.string.clear_fid_list));
                
                popup.show();
                return true;
            }
            return false;
        };

        keyLabel.setOnLongClickListener(longPressListener);
        keyId.setOnLongClickListener(longPressListener);
        cardView.setOnLongClickListener(longPressListener);

        avatar.setOnClickListener(v -> IdUtils.showAvatarDialog(context, keyInfo.getId()));
        keyId.setOnClickListener(v -> copyKeyId(keyInfo.getId()));

        keyListContainer.addView(cardView);
        keyInfoList.add(keyInfo);
    }

    private void copyKeyId(String keyId) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Key ID", keyId);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void showKeyDetail(KeyInfo keyInfo) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_key_detail, null);
        
        android.widget.FrameLayout fragmentContainer = dialogView.findViewById(R.id.fragment_container);
        com.fc.safe.ui.DetailFragment detailFragment = com.fc.safe.ui.DetailFragment.newInstance(keyInfo, KeyInfo.class);
        ((androidx.appcompat.app.AppCompatActivity) context).getSupportFragmentManager().beginTransaction()
                .replace(fragmentContainer.getId(), detailFragment)
                .commit();
        
        builder.setView(dialogView)
               .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
               .show();
    }

    public List<KeyInfo> getSelectedKeys() {
        List<KeyInfo> selectedKeys = new ArrayList<>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isChecked()) {
                selectedKeys.add(keyInfoList.get(i));
            }
        }
        return selectedKeys;
    }

    public void clearAll() {
        keyInfoList.clear();
        keyListContainer.removeAllViews();
        checkBoxes.clear();
    }

    public List<KeyInfo> getKeyInfoList() {
        return keyInfoList;
    }
} 