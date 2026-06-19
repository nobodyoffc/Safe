package com.fc.safe.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import android.app.AlertDialog;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyCardContainer {
    private static final String TAG = "KeyCardContainer";
    private final Context context;
    private final ViewGroup keyListContainer;
    private final List<KeyInfo> keyInfoList;
    private final List<CompoundButton> checkBoxes;
    private final ChooseMode chooseMode;
    private OnKeyListChangedListener onKeyListChangedListener;
    private final List<String> menuItems;
    private OnMenuItemClickListener onMenuItemClickListener;
    private OnCardClickListener onCardClickListener;
    private final Map<String, byte[]> avatarCache;
    private final int startPosition;  // Track starting position in parent container
    private TextView emptyStateTextView;  // TextView for empty state message

    public interface OnKeyListChangedListener {
        void onKeyListChanged(List<KeyInfo> updatedKeyList);
    }

    public interface OnMenuItemClickListener {
        void onMenuItemClick(String menuItem, KeyInfo keyInfo);
    }

    public interface OnCardClickListener {
        void onCardClick(KeyInfo keyInfo);
    }

    public KeyCardContainer(Context context, LinearLayout keyListContainer, ChooseMode chooseMode) {
        this(context, keyListContainer, chooseMode, null);
    }

    public KeyCardContainer(Context context, LinearLayout keyListContainer, ChooseMode chooseMode, List<String> menuItems) {
        this.context = context;
        this.keyListContainer = keyListContainer;
        this.keyInfoList = new ArrayList<>();
        this.checkBoxes = new ArrayList<>();
        this.chooseMode = chooseMode;
        this.menuItems = menuItems;
        this.avatarCache = new HashMap<>();
        this.startPosition = keyListContainer.getChildCount();  // Record current child count as starting position
    }

    public void setOnKeyListChangedListener(OnKeyListChangedListener listener) {
        this.onKeyListChangedListener = listener;
    }

    public void setOnMenuItemClickListener(OnMenuItemClickListener listener) {
        this.onMenuItemClickListener = listener;
    }

    public void setOnCardClickListener(OnCardClickListener listener) {
        this.onCardClickListener = listener;
    }

    private void notifyKeyListChanged() {
        if (onKeyListChangedListener != null) {
            onKeyListChangedListener.onKeyListChanged(new ArrayList<>(keyInfoList));
        }
    }

    public void addKeyCard(KeyInfo keyInfo) {
        addKeyCardAtPosition(keyInfo, keyInfoList.size());
        updateEmptyState();
    }

    private void showKeyDetailActivity(KeyInfo keyInfo) {
        android.content.Intent intent = new android.content.Intent(context, com.fc.safe.ui.DetailActivity.class);
        intent.putExtra(com.fc.safe.ui.DetailActivity.EXTRA_ENTITY_JSON, keyInfo.toJson());
        intent.putExtra(com.fc.safe.ui.DetailActivity.EXTRA_ENTITY_CLASS, KeyInfo.class.getName());
        context.startActivity(intent);
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
        avatarCache.clear();
        emptyStateTextView = null;  // Reset empty state view reference
        updateEmptyState();
    }

    public List<KeyInfo> getKeyList() {
        return keyInfoList;
    }

    public void selectAll(boolean selected) {
        if (chooseMode != ChooseMode.CHOOSE_MULTI && chooseMode != ChooseMode.CHOOSE_MULTI_WITHOUT_EDIT) {
            return;
        }

        for (CompoundButton checkBox : checkBoxes) {
            checkBox.setChecked(selected);
        }
    }

    public boolean areAllSelected() {
        if ((chooseMode != ChooseMode.CHOOSE_MULTI && chooseMode != ChooseMode.CHOOSE_MULTI_WITHOUT_EDIT) || checkBoxes.isEmpty()) {
            return false;
        }

        for (CompoundButton checkBox : checkBoxes) {
            if (!checkBox.isChecked()) {
                return false;
            }
        }
        return true;
    }

    public boolean areNoneSelected() {
        if ((chooseMode != ChooseMode.CHOOSE_MULTI && chooseMode != ChooseMode.CHOOSE_MULTI_WITHOUT_EDIT) || checkBoxes.isEmpty()) {
            return true;
        }

        for (CompoundButton checkBox : checkBoxes) {
            if (checkBox.isChecked()) {
                return false;
            }
        }
        return true;
    }

    public void removeSelectedKeys() {
        if ((chooseMode != ChooseMode.CHOOSE_MULTI && chooseMode != ChooseMode.CHOOSE_MULTI_WITHOUT_EDIT) || checkBoxes.isEmpty()) {
            return;
        }

        List<Integer> selectedIndices = new ArrayList<>();
        for (int i = checkBoxes.size() - 1; i >= 0; i--) {
            if (checkBoxes.get(i).isChecked()) {
                selectedIndices.add(i);
            }
        }

        for (int index : selectedIndices) {
            if (startPosition + index < keyListContainer.getChildCount()) {
                keyListContainer.removeViewAt(startPosition + index);
            }
            if (index < keyInfoList.size()) {
                keyInfoList.remove(index);
            }
            if (index < checkBoxes.size()) {
                checkBoxes.remove(index);
            }
        }

        notifyKeyListChanged();
        updateEmptyState();
    }

    public void removeFromBeginning(int count) {
        if (count <= 0 || count > keyInfoList.size()) {
            return;
        }

        for (int i = 0; i < count; i++) {
            keyListContainer.removeViewAt(startPosition);
        }

        for (int i = 0; i < count; i++) {
            keyInfoList.remove(0);
            if (!checkBoxes.isEmpty()) {
                checkBoxes.remove(0);
            }
        }

        notifyKeyListChanged();
    }

    public void removeFromEnd(int count) {
        if (count <= 0 || count > keyInfoList.size()) {
            return;
        }

        int size = keyInfoList.size();
        for (int i = 0; i < count; i++) {
            keyListContainer.removeViewAt(startPosition + size - 1 - i);
        }

        for (int i = 0; i < count; i++) {
            keyInfoList.remove(size - 1 - i);
            if (checkBoxes.size() > size - 1 - i) {
                checkBoxes.remove(size - 1 - i);
            }
        }

        notifyKeyListChanged();
    }

    public void addKeyCardsToBeginning(List<KeyInfo> keysToAdd) {
        if (keysToAdd == null || keysToAdd.isEmpty()) {
            return;
        }

        for (int i = keysToAdd.size() - 1; i >= 0; i--) {
            KeyInfo keyInfo = keysToAdd.get(i);
            addKeyCardAtPosition(keyInfo, 0);
        }
    }

    public void sortBySaveTime(boolean ascending, boolean enableSort) {
        sortKeys(enableSort, (pair1, pair2) -> {
            String saveTime1 = pair1.keyInfo.getSaveTime();
            String saveTime2 = pair2.keyInfo.getSaveTime();
            return compareNullable(saveTime1, saveTime2, ascending);
        });
    }

    public void sortById(boolean ascending, boolean enableSort) {
        sortKeys(enableSort, (pair1, pair2) -> {
            String id1 = pair1.keyInfo.getId();
            String id2 = pair2.keyInfo.getId();
            return compareNullable(id1, id2, ascending);
        });
    }

    public void sortByLabel(boolean ascending, boolean enableSort) {
        sortKeys(enableSort, (pair1, pair2) -> {
            String label1 = pair1.keyInfo.getLabel();
            String label2 = pair2.keyInfo.getLabel();
            return compareNullable(label1, label2, ascending);
        });
    }

    private void addKeyCardAtPosition(KeyInfo keyInfo, int position) {
        View cardView = createCardView();
        CompoundButton checkBox = setupCardViewInteractions(cardView, keyInfo);

        // Add view at startPosition + position to account for existing views in parent container
        keyListContainer.addView(cardView, startPosition + position);
        keyInfoList.add(position, keyInfo);
        if (checkBox != null) {
            checkBoxes.add(position, checkBox);
        }
    }

    private View createCardView() {
        int layoutResId = getLayoutResId();
        return LayoutInflater.from(context).inflate(layoutResId, keyListContainer, false);
    }

    private int getLayoutResId() {
        switch (chooseMode) {
            case CHOOSE_ONE_RETURN:
                return R.layout.item_key_card;
            case CHOOSE_ONE:
                return R.layout.item_key_card_radio;
            case CHOOSE_MULTI:
            case CHOOSE_MULTI_WITHOUT_EDIT:
                return R.layout.item_key_card_checkbox;
            case WITHOUT_CHOOSE:
                return R.layout.item_key_card;
            default:
                return R.layout.item_key_card;
        }
    }

    private CompoundButton setupCardViewInteractions(View cardView, KeyInfo keyInfo) {
        CompoundButton checkBox = setupCheckBox(cardView);
        setupCardData(cardView, keyInfo);
        setupClickListeners(cardView, keyInfo);
        return checkBox;
    }

    private CompoundButton setupCheckBox(View cardView) {
        if (chooseMode == ChooseMode.CHOOSE_ONE_RETURN || chooseMode == ChooseMode.WITHOUT_CHOOSE) {
            return null;
        }

        CompoundButton checkBox = cardView.findViewById(R.id.key_checkbox);
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
            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> notifyKeyListChanged());
        }
        return checkBox;
    }

    private void setupCardData(View cardView, KeyInfo keyInfo) {
        ImageView avatarView = cardView.findViewById(R.id.key_avatar);
        TextView labelView = cardView.findViewById(R.id.key_label);
        TextView idView = cardView.findViewById(R.id.key_id);
        TextView saveTimeView = cardView.findViewById(R.id.key_save_time);
        TextView cidView = cardView.findViewById(R.id.key_cid);
        LinearLayout firstRow = cardView.findViewById(R.id.first_row);

        // Set avatar
        if (avatarView != null) {
            loadAvatar(keyInfo.getId(), avatarView);
        }

        String cid = keyInfo.getCid();
        String label = keyInfo.getLabel();
        boolean hasCid = cid != null && !cid.isEmpty();
        boolean hasLabel = label != null && !label.isEmpty();

        // Hide first row if both cid and label are null/empty
        if (firstRow != null) {
            if (hasCid || hasLabel) {
                firstRow.setVisibility(View.VISIBLE);
            } else {
                firstRow.setVisibility(View.GONE);
            }
        }

        if(cidView!=null){
            if(hasCid){
                cidView.setText(cid);
                cidView.setVisibility(View.VISIBLE);
            }else{
                cidView.setVisibility(View.GONE);
            }
        }

        // Set label
        if (labelView != null) {
            if (hasLabel) {
                labelView.setText(label);
                labelView.setVisibility(View.VISIBLE);
            } else {
                labelView.setVisibility(View.GONE);
            }
        }

        // Set saveTime
        if (saveTimeView != null) {
            String saveTime = keyInfo.getSaveTime();
            if (saveTime != null && !saveTime.isEmpty()) {
                saveTimeView.setText(saveTime);
                saveTimeView.setVisibility(View.VISIBLE);
            } else {
                saveTimeView.setVisibility(View.GONE);
            }
        }

        // Set ID
        if (idView != null) {
            idView.setText(keyInfo.getId() != null ? keyInfo.getId() : "");
        }
    }

    private void loadAvatar(String fid, ImageView avatarView) {
        if (fid == null || fid.isEmpty()) {
            avatarView.setVisibility(View.GONE);
            return;
        }

        // Check cache first
        if (avatarCache.containsKey(fid)) {
            byte[] avatarBytes = avatarCache.get(fid);
            if (avatarBytes != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                avatarView.setImageBitmap(bitmap);
                avatarView.setVisibility(View.VISIBLE);
                return;
            }
        }

        // Try to load from database
        KeyInfoManager kim = KeyInfoManager.getInstance(context);
        byte[] avatarFromDb = kim.getAvatarById(fid);
        if (avatarFromDb != null) {
            avatarCache.put(fid, avatarFromDb);
            Bitmap bitmap = BitmapFactory.decodeByteArray(avatarFromDb, 0, avatarFromDb.length);
            avatarView.setImageBitmap(bitmap);
            avatarView.setVisibility(View.VISIBLE);
            return;
        }

        // Generate new avatar in background
        new Thread(() -> {
            try {
                Map<String, byte[]> generated = AvatarMaker.makeAvatars(new String[]{fid}, context);
                byte[] avatarBytes = generated.get(fid);
                if (avatarBytes != null) {
                    avatarCache.put(fid, avatarBytes);
                    // Save to database
                    kim.saveAvatar(fid, avatarBytes);

                    // Update UI on main thread
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            Bitmap bitmap = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                            avatarView.setImageBitmap(bitmap);
                            avatarView.setVisibility(View.VISIBLE);
                        });
                    }
                }
            } catch (IOException e) {
                TimberLogger.e(TAG, "Failed to generate avatar: %s", e.getMessage());
            }
        }).start();
    }

    private void setupClickListeners(View cardView, KeyInfo keyInfo) {
        TextView labelView = cardView.findViewById(R.id.key_label);
        TextView idView = cardView.findViewById(R.id.key_id);
        ImageView editIcon = cardView.findViewById(R.id.edit_icon);

        // For CHOOSE_ONE_RETURN mode with custom click listener, use the custom behavior
        // Otherwise, use default behavior (show detail activity)
        View.OnClickListener clickListener;
        if (chooseMode == ChooseMode.CHOOSE_ONE_RETURN && onCardClickListener != null) {
            clickListener = v -> onCardClickListener.onCardClick(keyInfo);
        } else {
            clickListener = v -> showKeyDetailActivity(keyInfo);
        }

        View.OnLongClickListener longPressListener = createLongPressListener(cardView, keyInfo);

        cardView.setOnClickListener(clickListener);
        cardView.setOnLongClickListener(longPressListener);

        if (labelView != null) {
            labelView.setOnClickListener(clickListener);
            labelView.setOnLongClickListener(longPressListener);
        }

        if (idView != null) {
            idView.setOnClickListener(clickListener);
            idView.setOnLongClickListener(longPressListener);
        }

        // Set up edit icon click listener - hide for CHOOSE_MULTI_WITHOUT_EDIT mode
        if (editIcon != null) {
            if (chooseMode == ChooseMode.CHOOSE_MULTI_WITHOUT_EDIT) {
                editIcon.setVisibility(View.GONE);
            } else {
                editIcon.setOnClickListener(v -> showEditLabelDialog(keyInfo, labelView));
            }
        }
    }

    private View.OnLongClickListener createLongPressListener(View cardView, KeyInfo keyInfo) {
        return v -> {
            if (menuItems != null && !menuItems.isEmpty()) {
                PopupMenu popup = new PopupMenu(context, v);
                for (String menuItem : menuItems) {
                    popup.getMenu().add(menuItem);
                }

                popup.setOnMenuItemClickListener(item -> {
                    if (item.getTitle() == null) return false;
                    String title = item.getTitle().toString();
                    if ("Delete".equals(title)) {
                        removeKeyCard(cardView, keyInfo);
                        return true;
                    } else if (onMenuItemClickListener != null) {
                        onMenuItemClickListener.onMenuItemClick(title, keyInfo);
                        return true;
                    }
                    return false;
                });

                popup.show();
                return true;
            }
            return false;
        };
    }

    private void removeKeyCard(View cardView, KeyInfo keyInfo) {
        keyListContainer.removeView(cardView);
        int index = keyInfoList.indexOf(keyInfo);
        if (index != -1) {
            keyInfoList.remove(index);
            if (index < checkBoxes.size()) {
                checkBoxes.remove(index);
            }
        }
        notifyKeyListChanged();
    }

    private void sortKeys(boolean enableSort, Comparator<KeyViewPair> comparator) {
        if (keyInfoList.isEmpty() || !enableSort) {
            return;
        }

        List<KeyViewPair> pairs = createKeyViewPairs();
        pairs.sort(comparator);
        updateListsFromPairs(pairs);
    }

    private List<KeyViewPair> createKeyViewPairs() {
        List<KeyViewPair> pairs = new ArrayList<>();
        for (int i = 0; i < keyInfoList.size(); i++) {
            KeyInfo keyInfo = keyInfoList.get(i);
            View cardView = keyListContainer.getChildAt(startPosition + i);
            CompoundButton checkBox = (i < checkBoxes.size()) ? checkBoxes.get(i) : null;
            pairs.add(new KeyViewPair(keyInfo, cardView, checkBox));
        }
        return pairs;
    }

    private void updateListsFromPairs(List<KeyViewPair> pairs) {
        keyInfoList.clear();
        checkBoxes.clear();
        keyListContainer.removeAllViews();

        for (KeyViewPair pair : pairs) {
            keyInfoList.add(pair.keyInfo);
            if (pair.checkBox != null) {
                checkBoxes.add(pair.checkBox);
            }
            keyListContainer.addView(pair.cardView);
        }
    }

    private <T extends Comparable<T>> int compareNullable(T value1, T value2, boolean ascending) {
        if (value1 == null && value2 == null) return 0;
        if (value1 == null) return 1;
        if (value2 == null) return -1;
        return ascending ? value1.compareTo(value2) : value2.compareTo(value1);
    }

    private void showEditLabelDialog(KeyInfo keyInfo, TextView labelView) {
        KeyLabelManager keyLabelManager = new KeyLabelManager(context);
        keyLabelManager.showEditLabelDialog(keyInfo, () -> {
            // Refresh the entire card to properly update all UI elements
            refreshCard(keyInfo);
        });
    }

    /**
     * Refreshes a specific card's UI to reflect updated KeyInfo data
     */
    public void refreshCard(KeyInfo keyInfo) {
        int index = keyInfoList.indexOf(keyInfo);
        if (index != -1 && startPosition + index < keyListContainer.getChildCount()) {
            View cardView = keyListContainer.getChildAt(startPosition + index);
            setupCardData(cardView, keyInfo);
        }
    }

    private record KeyViewPair(KeyInfo keyInfo, View cardView, CompoundButton checkBox) {
    }

    /**
     * Updates the empty state view based on whether there are keys in the list
     */
    private void updateEmptyState() {
        if (keyInfoList.isEmpty()) {
            showEmptyState();
        } else {
            hideEmptyState();
        }
    }

    /**
     * Shows the empty state message
     */
    private void showEmptyState() {
        if (emptyStateTextView == null) {
            emptyStateTextView = new TextView(context);
            emptyStateTextView.setText(R.string.please_create_your_keys);
            emptyStateTextView.setTextSize(16);
            emptyStateTextView.setGravity(android.view.Gravity.CENTER);
            emptyStateTextView.setPadding(16, 32, 16, 32);
            emptyStateTextView.setTextColor(context.getResources().getColor(android.R.color.darker_gray, null));

            keyListContainer.addView(emptyStateTextView, startPosition);
        } else if (emptyStateTextView.getParent() == null) {
            keyListContainer.addView(emptyStateTextView, startPosition);
        }
    }

    /**
     * Hides the empty state message
     */
    private void hideEmptyState() {
        if (emptyStateTextView != null && emptyStateTextView.getParent() != null) {
            keyListContainer.removeView(emptyStateTextView);
        }
    }
}
