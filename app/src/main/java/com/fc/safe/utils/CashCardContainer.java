package com.fc.safe.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.PopupMenu;
import android.widget.ImageButton;

import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.SafeApplication;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CashCardContainer {
    private static final String TAG = "CashCardContainer";
    private final Context context;
    private final ViewGroup cashListContainer;
    private final List<Cash> cashList;
    private final List<CompoundButton> checkBoxes;
    private final ChooseMode chooseMode;
    private OnCashListChangedListener onCashListChangedListener;
    private List<String> menuItems;
    private OnMenuItemClickListener onMenuItemClickListener;
    private TextView emptyStateTextView;

    public interface OnCashListChangedListener {
        void onCashListChanged(List<Cash> updatedCashList);
    }

    public interface OnMenuItemClickListener {
        void onMenuItemClick(String menuItem, Cash cash);
    }

    public CashCardContainer(Context context, LinearLayout cashListContainer, ChooseMode chooseMode) {
        this(context, cashListContainer, chooseMode, null);
    }

    public CashCardContainer(Context context, LinearLayout cashListContainer, ChooseMode chooseMode, List<String> menuItems) {
        this.context = context;
        this.cashListContainer = cashListContainer;
        this.cashList = new ArrayList<>();
        this.checkBoxes = new ArrayList<>();
        this.chooseMode = chooseMode;
        this.menuItems = menuItems;
        initializeEmptyStateView();
    }

    public void setOnCashListChangedListener(OnCashListChangedListener listener) {
        this.onCashListChangedListener = listener;
    }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        this.onMenuItemClickListener = listener;
    }

    public void setMenuItems(List<String> menuItems) {
        this.menuItems = menuItems;
    }

    private void notifyCashListChanged() {
        if (onCashListChangedListener != null) {
            onCashListChangedListener.onCashListChanged(new ArrayList<>(cashList));
        }
    }

    private void initializeEmptyStateView() {
        emptyStateTextView = new TextView(context);
        emptyStateTextView.setText(R.string.please_import_or_create_cash);
        emptyStateTextView.setGravity(android.view.Gravity.CENTER);
        emptyStateTextView.setTextSize(16);
        emptyStateTextView.setTextColor(context.getResources().getColor(R.color.hint, null));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = (int) (48 * context.getResources().getDisplayMetrics().density); // Convert dp to pixels
        emptyStateTextView.setLayoutParams(params);
        emptyStateTextView.setVisibility(View.GONE);
        cashListContainer.addView(emptyStateTextView, 0); // Add at the beginning
    }

    private void updateEmptyState() {
        if (emptyStateTextView != null) {
            emptyStateTextView.setVisibility(cashList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    public void addCashCard(Cash cash) {
        View cardView = createCardView();
        CompoundButton checkBox = setupCheckBox(cardView);

        ImageView avatar = cardView.findViewById(R.id.cash_avatar);
        TextView ownerValue = cardView.findViewById(R.id.cash_owner_value);
        TextView amountValue = cardView.findViewById(R.id.cash_amount_value);
        TextView cdValue = cardView.findViewById(R.id.cash_cd_value);
        ImageButton deleteButton = cardView.findViewById(R.id.deleteButton);

        // Set up click listeners for the card view
        cardView.setOnClickListener(v -> {
            if (chooseMode != ChooseMode.WITHOUT_CHOOSE && chooseMode != ChooseMode.CHOOSE_ONE_RETURN && v.getId() != R.id.cash_checkbox && checkBox != null) {
                checkBox.setChecked(!checkBox.isChecked());
            } else {
                showCashDetail(cash);
            }
        });

        // Set owner avatar
        try {
            byte[] avatarBytes = AvatarMaker.createAvatar(cash.getOwner(), context);
            if (avatarBytes != null) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                avatar.setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to create avatar for owner %s: %s", cash.getOwner(), e.getMessage());
            ToastUtils.showError(context, context.getString(R.string.failed_to_create_avatar));
        }

        // Set owner value (no label)
        ownerValue.setText(cash.getOwner());

        // Set amount value (no label)
        DecimalFormat df = new DecimalFormat("0.########");
        df.setDecimalSeparatorAlwaysShown(false);
        String amountText = df.format(FchUtils.satoshiToCoin(cash.getValue())) + " F";
        amountValue.setText(amountText);

        // Set CD value (no label)
        Long cd = cash.getCd();
        if (cd != null) {
            cdValue.setText(String.format(Locale.US, "%d cd", cd));
        } else {
            cdValue.setText("0 cd");
        }

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
                        cashListContainer.removeView(cardView);
                        // Remove the cash from the list
                        int index = cashList.indexOf(cash);
                        if (index != -1) {
                            cashList.remove(index);
                            if (index < checkBoxes.size()) {
                                checkBoxes.remove(index);
                            }
                        }
                        updateEmptyState();
                        notifyCashListChanged();
                        return true;
                    } else if (context.getString(R.string.add_to_fid_list).equals(title)) {
                        SafeApplication.addFid(cash.getOwner());
                        ToastUtils.showInfo(context, context.getString(R.string.added_to_fid_list));
                        return true;
                    } else if (context.getString(R.string.clear_fid_list).equals(title)) {
                        SafeApplication.clearFidList();
                        ToastUtils.showInfo(context, context.getString(R.string.fid_list_cleared));
                        return true;
                    } else if (onMenuItemClickListener != null) {
                        onMenuItemClickListener.onMenuItemClick(title, cash);
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

        // Only set long press listeners for visible elements
        ownerValue.setOnLongClickListener(longPressListener);
        amountValue.setOnLongClickListener(longPressListener);
        cdValue.setOnLongClickListener(longPressListener);
        cardView.setOnLongClickListener(longPressListener);

        avatar.setOnClickListener(v -> IdUtils.showAvatarDialog(context, cash.getOwner()));
        ownerValue.setOnClickListener(v -> copyOwner(cash.getOwner()));
        amountValue.setOnClickListener(v -> copyAmount(cash.getValue()));
        // Display the cd carried with the cash (height-based, computed upstream); no offline recompute.
        if(cash.getCd()!=null) {
            cdValue.setOnClickListener(v -> copyCd(cash.getCd()));
        }

        // Add delete button functionality
        deleteButton.setOnClickListener(v -> {
            // Remove the card from the container
            cashListContainer.removeView(cardView);
            // Remove the cash from the list
            int index = cashList.indexOf(cash);
            if (index != -1) {
                cashList.remove(index);
                if (index < checkBoxes.size()) {
                    checkBoxes.remove(index);
                }
            }
            updateEmptyState();
            notifyCashListChanged();
        });

        cashListContainer.addView(cardView);
        cashList.add(cash);
        updateEmptyState();
    }

    private void copyOwner(String owner) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("Owner", owner);
        clipboard.setPrimaryClip(clip);
        ToastUtils.showInfo(context, context.getString(R.string.copied));
    }

    private void copyAmount(Long value) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        DecimalFormat df = new DecimalFormat("0.########");
        df.setDecimalSeparatorAlwaysShown(false);
        String amountText = df.format(FchUtils.satoshiToCoin(value)) + " F";
        android.content.ClipData clip = android.content.ClipData.newPlainText("Amount", amountText);
        clipboard.setPrimaryClip(clip);
        ToastUtils.showInfo(context, context.getString(R.string.copied));
    }

    private void copyCd(Long cd) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        String cdText = cd != null ? String.format(Locale.US, "%d cd", cd) : "0 cd";
        android.content.ClipData clip = android.content.ClipData.newPlainText("CD", cdText);
        clipboard.setPrimaryClip(clip);
        ToastUtils.showInfo(context, context.getString(R.string.copied));
    }

    private void showCashDetail(Cash cash) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_cash_detail, null);
        
        android.widget.FrameLayout fragmentContainer = dialogView.findViewById(R.id.fragment_container);
        com.fc.safe.ui.DetailFragment detailFragment = com.fc.safe.ui.DetailFragment.newInstance(cash, Cash.class);
        ((androidx.appcompat.app.AppCompatActivity) context).getSupportFragmentManager().beginTransaction()
                .replace(fragmentContainer.getId(), detailFragment)
                .commit();
        
        builder.setView(dialogView)
               .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
               .show();
    }

    public List<Cash> getSelectedCashes() {
        List<Cash> selectedCashes = new ArrayList<>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isChecked()) {
                selectedCashes.add(cashList.get(i));
            }
        }
        return selectedCashes;
    }

    public void clearAll() {
        cashList.clear();
        cashListContainer.removeAllViews();
        checkBoxes.clear();
        initializeEmptyStateView(); // Reinitialize empty state view after clearing
        updateEmptyState();
    }

    public List<Cash> getCashList() {
        return cashList;
    }

    public void sortByOwner(boolean ascending, boolean refresh) {
        cashList.sort((c1, c2) -> {
            String owner1 = c1.getOwner() != null ? c1.getOwner() : "";
            String owner2 = c2.getOwner() != null ? c2.getOwner() : "";
            int comparison = owner1.compareTo(owner2);
            return ascending ? comparison : -comparison;
        });
        if (refresh) {
            refreshCards();
        }
    }

    public void sortByValue(boolean ascending, boolean refresh) {
        cashList.sort((c1, c2) -> {
            Long value1 = c1.getValue() != null ? c1.getValue() : 0L;
            Long value2 = c2.getValue() != null ? c2.getValue() : 0L;
            int comparison = value1.compareTo(value2);
            return ascending ? comparison : -comparison;
        });
        if (refresh) {
            refreshCards();
        }
    }

    public void sortByCd(boolean ascending, boolean refresh) {
        cashList.sort((c1, c2) -> {
            Long cd1 = c1.getCd() != null ? c1.getCd() : 0L;
            Long cd2 = c2.getCd() != null ? c2.getCd() : 0L;
            int comparison = cd1.compareTo(cd2);
            return ascending ? comparison : -comparison;
        });
        if (refresh) {
            refreshCards();
        }
    }

    private void refreshCards() {
        // Save selection states
        List<Boolean> selectionStates = new ArrayList<>();
        for (CompoundButton cb : checkBoxes) {
            selectionStates.add(cb.isChecked());
        }

        // Clear and rebuild
        cashListContainer.removeAllViews();
        checkBoxes.clear();

        List<Cash> tempList = new ArrayList<>(cashList);
        cashList.clear();

        // Reinitialize empty state view
        initializeEmptyStateView();

        for (Cash cash : tempList) {
            addCashCard(cash);
        }

        // Restore selection states
        for (int i = 0; i < Math.min(selectionStates.size(), checkBoxes.size()); i++) {
            if (checkBoxes.get(i) != null) {
                checkBoxes.get(i).setChecked(selectionStates.get(i));
            }
        }

        updateEmptyState();
    }

    public void selectAll(boolean select) {
        for (CompoundButton cb : checkBoxes) {
            if (cb != null) {
                cb.setChecked(select);
            }
        }
    }

    public void removeSelectedCashes() {
        List<Cash> selectedCashes = getSelectedCashes();

        // Collect views and indices to remove first
        List<View> viewsToRemove = new ArrayList<>();
        List<Integer> indicesToRemove = new ArrayList<>();

        for (Cash cash : selectedCashes) {
            int index = cashList.indexOf(cash);
            if (index != -1) {
                indicesToRemove.add(index);
                // Account for empty state view at position 0
                int viewIndex = index + 1; // +1 because empty state view is at position 0
                if (viewIndex < cashListContainer.getChildCount()) {
                    View cardView = cashListContainer.getChildAt(viewIndex);
                    viewsToRemove.add(cardView);
                }
            }
        }

        // Remove views from container
        for (View view : viewsToRemove) {
            cashListContainer.removeView(view);
        }

        // Sort indices in descending order to remove from end to start
        indicesToRemove.sort((a, b) -> b - a);

        // Remove from lists in reverse order to avoid index shifting issues
        for (int index : indicesToRemove) {
            if (index < cashList.size()) {
                cashList.remove(index);
            }
            if (index < checkBoxes.size()) {
                checkBoxes.remove(index);
            }
        }

        updateEmptyState();
        notifyCashListChanged();
    }

    private View createCardView() {
        int layoutResId = getLayoutResId();
        return LayoutInflater.from(context).inflate(layoutResId, cashListContainer, false);
    }

    private int getLayoutResId() {
        switch (chooseMode) {
            case CHOOSE_ONE_RETURN:
                return R.layout.item_cash_card;
            case CHOOSE_ONE:
                return R.layout.item_cash_card_radio;
            case CHOOSE_MULTI:
            case CHOOSE_MULTI_WITHOUT_EDIT:
                return R.layout.item_cash_card_checkbox;
            case WITHOUT_CHOOSE:
                return R.layout.item_cash_card;
            default:
                return R.layout.item_cash_card;
        }
    }

    private CompoundButton setupCheckBox(View cardView) {
        if (chooseMode == ChooseMode.CHOOSE_ONE_RETURN || chooseMode == ChooseMode.WITHOUT_CHOOSE) {
            return null;
        }

        CompoundButton checkBox = cardView.findViewById(R.id.cash_checkbox);
        if (checkBox == null) {
            return null;
        }

        if (chooseMode == ChooseMode.CHOOSE_ONE) {
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    for (CompoundButton cb : checkBoxes) {
                        if (cb != checkBox) {
                            cb.setChecked(false);
                        }
                    }
                }
            });
        } else if (chooseMode == ChooseMode.CHOOSE_MULTI || chooseMode == ChooseMode.CHOOSE_MULTI_WITHOUT_EDIT) {
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> notifyCashListChanged());
        }

        checkBoxes.add(checkBox);
        return checkBox;
    }
} 