package com.fc.safe.multisign;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.data.fchData.Multisig;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.SafeApplication;
import com.fc.safe.db.MultisignManager;
import com.fc.safe.home.MultisignActivity;
import com.fc.safe.home.MultisignDetailActivity;
import com.fc.safe.utils.IdUtils;

import java.util.ArrayList;
import java.util.List;
import com.fc.safe.utils.ToastUtils;

public class MultisignKeyCardManager {
    private static final String TAG = "MultisignKeyCardManager";
    private final Context context;
    private final ViewGroup keyListContainer;
    private final List<Multisig> multisigList;
    private final List<RadioButton> radioButtons;
    private final boolean withCheckBox;
    private final boolean isSingleChoice;

    public MultisignKeyCardManager(Context context, LinearLayout keyListContainer) {
        this(context, keyListContainer, true, false);
    }

    public MultisignKeyCardManager(Context context, LinearLayout keyListContainer, boolean withCheckBox) {
        this(context, keyListContainer, withCheckBox, false);
    }

    public MultisignKeyCardManager(Context context, LinearLayout keyListContainer, boolean withCheckBox, boolean isSingleChoice) {
        this.context = context;
        this.keyListContainer = keyListContainer;
        this.multisigList = new ArrayList<>();
        this.radioButtons = new ArrayList<>();
        this.withCheckBox = withCheckBox;
        this.isSingleChoice = isSingleChoice;
    }

    public void addKeyCard(Multisig multisig) {
        TimberLogger.d(TAG, "addKeyCard: Starting to add card for Multisig with ID: " + multisig.getId());
        
        int layoutResId;
        if (!withCheckBox) {
            layoutResId = R.layout.item_key_card;
        } else if (isSingleChoice) {
            layoutResId = R.layout.item_key_card_radio;
        } else {
            layoutResId = R.layout.item_key_card_checkbox;
        }
        
        View cardView = LayoutInflater.from(context).inflate(layoutResId, keyListContainer, false);
        
        RadioButton radioButton;
        if (withCheckBox) {
            radioButton = cardView.findViewById(R.id.key_checkbox);
            radioButtons.add(radioButton);
            if (isSingleChoice) {
                radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        // Uncheck all other radio buttons
                        for (RadioButton rb : radioButtons) {
                            if (rb != radioButton) {
                                rb.setChecked(false);
                            }
                        }
                    }
                });
            }
        } else {
            radioButton = null;
        }

        ImageView avatar = cardView.findViewById(R.id.key_avatar);
        TextView keyLabel = cardView.findViewById(R.id.key_label);
        TextView keyId = cardView.findViewById(R.id.key_id);

        try {
            byte[] avatarBytes = AvatarMaker.createAvatar(multisig.getId(), context);
            if (avatarBytes != null) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                avatar.setImageBitmap(bitmap);
                TimberLogger.d(TAG, "addKeyCard: Successfully created avatar for ID: " + multisig.getId());
            } else {
                TimberLogger.e(TAG, "addKeyCard: Failed to create avatar bytes for ID: " + multisig.getId());
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to create avatar for key ID %s: %s", multisig.getId(), e.getMessage());
            ToastUtils.showError(context, context.getString(R.string.failed_to_create_avatar));
        }
        keyLabel.setText(multisig.getLabel());
        keyLabel.setTextColor(context.getResources().getColor(R.color.field_name, context.getTheme()));
        keyLabel.setTypeface(keyLabel.getTypeface(), android.graphics.Typeface.BOLD);
        keyId.setText(multisig.getId());
        TimberLogger.d(TAG, "addKeyCard: Set label: " + multisig.getLabel() + ", ID: " + multisig.getId());

        avatar.setOnClickListener(v -> IdUtils.showAvatarDialog(context, multisig.getId()));
        
        // Create a common long press listener
        View.OnLongClickListener longPressListener = v -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            String[] options = {context.getString(R.string.delete), context.getString(R.string.create_tx), context.getString(R.string.add_to_fid_list), context.getString(R.string.clear_fid_list)};
            builder.setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // Delete
                        MultisignManager.getInstance(context.getApplicationContext()).removeMultisign(multisig);
                        MultisignManager.getInstance(context.getApplicationContext()).commit();
                        keyListContainer.removeView(cardView);
                        multisigList.remove(multisig);
                        MultisignActivity.setNeedsRefresh(true);
                        break;
                    case 1: // Create TX
                        Intent createTxIntent = new Intent(context, CreateMultisignTxActivity.class);
                        createTxIntent.putExtra("multisig", multisig);
                        RawTxInfo rawTxInfo = new RawTxInfo();
                        rawTxInfo.setMultisign(multisig);
                        createTxIntent.putExtra("rawTxInfo", rawTxInfo);
                        context.startActivity(createTxIntent);
                        break;
                    case 2: // Add to FID list
                        SafeApplication.addFid(multisig.getId());
                        ToastUtils.showInfo(context, context.getString(R.string.added_to_fid_list));
                        break;
                    case 3: // Clear FID list
                        SafeApplication.clearFidList();
                        ToastUtils.showInfo(context, context.getString(R.string.fid_list_cleared));
                        break;
                }
            });
            builder.show();
            return true;
        };

        // Apply long press listener to both the card and ID text
        cardView.setOnLongClickListener(longPressListener);
        keyId.setOnLongClickListener(longPressListener);
        
        // Set click listeners for showing details
        cardView.setOnClickListener(v -> {
            if (withCheckBox && v.getId() != R.id.key_checkbox) {
                radioButton.setChecked(!radioButton.isChecked());
            } else {
                showKeyDetail(multisig);
            }
        });
        keyId.setOnClickListener(v -> showKeyDetail(multisig));

        keyListContainer.addView(cardView);
        multisigList.add(multisig);
        TimberLogger.d(TAG, "addKeyCard: Successfully added card to container. Total cards: " + multisigList.size());
    }

    public void addSenderKeyCard(Multisig multisig) {
        TimberLogger.d(TAG, "addSenderKeyCard: Starting to add card for Multisig with ID: " + multisig.getId());
        View cardView = LayoutInflater.from(context).inflate(R.layout.item_sender_key_card, keyListContainer, false);
        
        ImageView avatar = cardView.findViewById(R.id.key_avatar);
        TextView keyLabel = cardView.findViewById(R.id.key_label);
        TextView keyId = cardView.findViewById(R.id.key_id);

        try {
            byte[] avatarBytes = AvatarMaker.createAvatar(multisig.getId(), context);
            if (avatarBytes != null) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                avatar.setImageBitmap(bitmap);
                TimberLogger.d(TAG, "addSenderKeyCard: Successfully created avatar for ID: " + multisig.getId());
            } else {
                TimberLogger.e(TAG, "addSenderKeyCard: Failed to create avatar bytes for ID: " + multisig.getId());
            }
        } catch (Exception e) {
            ToastUtils.showError(context, context.getString(R.string.failed_to_create_avatar));
        }
        keyLabel.setText(multisig.getLabel());
        keyLabel.setTextColor(context.getResources().getColor(R.color.field_name, context.getTheme()));
        keyLabel.setTypeface(keyLabel.getTypeface(), android.graphics.Typeface.BOLD);
        keyId.setText(multisig.getId());
        keyId.setTypeface(keyId.getTypeface(), android.graphics.Typeface.BOLD);
        TimberLogger.d(TAG, "addSenderKeyCard: Set label: " + multisig.getLabel() + ", ID: " + multisig.getId());

        avatar.setOnClickListener(v -> IdUtils.showAvatarDialog(context, multisig.getId()));
        
        // Create a common long press listener
        View.OnLongClickListener longPressListener = v -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            String[] options = {"Delete", "Create TX", "Add to FID list", "Clear FID list"};
            builder.setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // Delete
                        MultisignManager.getInstance(context.getApplicationContext()).removeMultisign(multisig);
                        MultisignManager.getInstance(context.getApplicationContext()).commit();
                        keyListContainer.removeView(cardView);
                        multisigList.remove(multisig);
                        MultisignActivity.setNeedsRefresh(true);
                        break;
                    case 1: // Create TX
                        Intent createTxIntent = new Intent(context, CreateMultisignTxActivity.class);
                        createTxIntent.putExtra("multisig", multisig);
                        RawTxInfo rawTxInfo = new RawTxInfo();
                        rawTxInfo.setMultisign(multisig);
                        createTxIntent.putExtra("rawTxInfo", rawTxInfo);
                        context.startActivity(createTxIntent);
                        break;
                    case 2: // Add to FID list
                        SafeApplication.addFid(multisig.getId());
                        ToastUtils.showInfo(context, context.getString(R.string.added_to_fid_list));
                        break;
                    case 3: // Clear FID list
                        SafeApplication.clearFidList();
                        ToastUtils.showInfo(context, context.getString(R.string.fid_list_cleared));
                        break;
                }
            });
            builder.show();
            return true;
        };

        // Apply long press listener to both the card and ID text
        cardView.setOnLongClickListener(longPressListener);
        keyId.setOnLongClickListener(longPressListener);
        
        // Set click listeners for showing details
        cardView.setOnClickListener(v -> showKeyDetail(multisig));
        keyId.setOnClickListener(v -> showKeyDetail(multisig));

        keyListContainer.addView(cardView);
        multisigList.add(multisig);
        TimberLogger.d(TAG, "addSenderKeyCard: Successfully added card to container. Total cards: " + multisigList.size());
    }

    private void copyKeyId(String keyId) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Key ID", keyId);
        clipboard.setPrimaryClip(clip);
        ToastUtils.showInfo(context, context.getString(R.string.copied));
    }

    private void showKeyDetail(Multisig multisig) {
        Intent intent = new Intent(context, MultisignDetailActivity.class);
        intent.putExtra("multisig", multisig);
        context.startActivity(intent);
    }

    public List<Multisig> getSelectedKeys() {
        List<Multisig> selectedKeys = new ArrayList<>();
        for (int i = 0; i < radioButtons.size(); i++) {
            if (radioButtons.get(i).isChecked()) {
                selectedKeys.add(multisigList.get(i));
                TimberLogger.d(TAG, "getSelectedKeys: Selected key: " + multisigList.get(i).getId());
            }
        }
        return selectedKeys;
    }

    public void clearAll() {
        TimberLogger.d(TAG, "clearAll: Starting to clear all cards. Current count: " + multisigList.size());
        multisigList.clear();
        keyListContainer.removeAllViews();
        radioButtons.clear();
        TimberLogger.d(TAG, "clearAll: Successfully cleared all cards and radio buttons");
    }

    public List<Multisig> getMultisignList() {
        return multisigList;
    }

    public void addMultisignCards(LinearLayout container, List<Multisig> multisigs) {
        TimberLogger.d(TAG, "addMultisignCards: Starting to add " + (multisigs != null ? multisigs.size() : 0) + " cards");
        if (multisigs == null || multisigs.isEmpty()) {
            TimberLogger.w(TAG, "addMultisignCards: No cards to add - multisigs is null or empty");
            return;
        }
        for (Multisig multisig : multisigs) {
            addKeyCard(multisig);
        }
        TimberLogger.d(TAG, "addMultisignCards: Finished adding all cards. Total cards in list: " + multisigList.size());
    }
} 