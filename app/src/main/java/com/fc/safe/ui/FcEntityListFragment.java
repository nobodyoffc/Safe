package com.fc.safe.ui;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.utils.IdUtils;
import com.fc.fc_ajdk.core.crypto.KeyTools;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FcEntityListFragment<T extends FcEntity> extends Fragment {
    public final static String TAG = "FcEntityListFragment";
    // Constants
    private static final int ROW_HEIGHT = 60;
    private static final int AVATAR_SIZE = 48;
    private static final int AVATAR_FIELD_WIDTH = ROW_HEIGHT-6;
    private static final int CHECKBOX_FIELD_WIDTH = 36;
    
    // Sort states
    private static final String SORT_NONE = "none";
    private static final String SORT_ASC = "asc";
    private static final String SORT_DESC = "desc";
    
    // Instance variables
    private List<T> tList;
    private Class<T> tClass;
    private Boolean singleChoice;
    private List<String> idList;
    private float density;
    
    // UI components
    private ViewGroup allContainer;
    private HorizontalScrollView headerScrollView;
    private LinearLayout headerContainer;
    private ScrollView verticalListScrollView;
    private LinearLayout listContainer;
    private HorizontalScrollView orderScrollView;
    private LinearLayout orderContainer;
    
    // Selection tracking
    private final List<T> selectedObjects = new ArrayList<>();
    private final Map<T, Boolean> checkboxStates = new HashMap<>();
    private RadioButton selectedRadioButton;
    
    // Sort state tracking
    private final Map<String, String> sortStates = new LinkedHashMap<>();
    
    // Map to store sort buttons by field name
    private final Map<String, ImageButton> sortButtonsMap = new HashMap<>();
    
    // Avatar map
    private Map<String, byte[]> idImageBytesMap;
    
    // Field width map
    private LinkedHashMap<String, Integer> fieldWidthMap;
    
    // Field name map for display
    private Map<String, String> fieldNameMap;
    
    // Add field for All checkbox
    private CheckBox allCheckBox;
    // Add field for All checkbox listener
    private android.widget.CompoundButton.OnCheckedChangeListener allCheckBoxListener;

    // Whether to show avatar column (at least one good FID in the list)
    private boolean showAvatarColumn = true;

    // Selection change listener
    private OnSelectionChangeListener selectionChangeListener;
    
    // Interface for selection change events
    public interface OnSelectionChangeListener {
        void onSelectionChanged();
    }
    
    // Method to set the selection change listener
    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }
    
    // Method to notify selection changes
    private void notifySelectionChanged() {
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged();
        }
    }

    /**
     * Creates a new instance of FcEntityListFragment
     *
     * @param tList        List of FcEntity objects to display
     * @param tClass       Class of the FcEntity objects
     * @param singleChoice Whether to use single choice (RadioButton) or multiple choice (CheckBox)
     *                     null means no selection controls
     * @return A new FcEntityListFragment instance
     */
    public static <T extends FcEntity> FcEntityListFragment<T> newInstance(List<T> tList, Class<T> tClass, Boolean singleChoice) {
        FcEntityListFragment<T> fragment = new FcEntityListFragment<>();
        Bundle args = new Bundle();
        args.putSerializable("t_class", tClass);
        args.putBoolean("single_choice", singleChoice != null ? singleChoice : false);
        args.putBoolean("has_single_choice", singleChoice != null);
        fragment.setArguments(args);
        fragment.tList = tList;
        return fragment;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimberLogger.i(TAG, "FcEntityListFragment onCreate: Starting initialization");

        // Get display density
        density = getResources().getDisplayMetrics().density;
        TimberLogger.i(TAG, "FcEntityListFragment onCreate: Display density = " + density);
        
        // Get arguments
        Bundle args = getArguments();
        if (args != null) {
            tClass = (Class<T>) args.getSerializable("t_class");
            boolean hasSingleChoice = args.getBoolean("has_single_choice", false);
            if (hasSingleChoice) {
                singleChoice = args.getBoolean("single_choice", false);
            } else {
                singleChoice = null;
            }
            TimberLogger.i(TAG, "FcEntityListFragment onCreate: Arguments parsed - tClass = " + tClass + 
                ", hasSingleChoice = " + hasSingleChoice + ", singleChoice = " + singleChoice);
        } else {
            TimberLogger.w(TAG, "FcEntityListFragment onCreate: No arguments found");
        }
        
        // Initialize tList if null
        if (tList == null) {
            tList = new ArrayList<>();
            TimberLogger.w(TAG, "FcEntityListFragment onCreate: tList was null, initialized empty list");
        }
        
        // Log the size of tList
        int tListSize = tList.size();
        TimberLogger.i(TAG,"FcEntityListFragment onCreate: tList size = " + tListSize);
        
        // Create idList from tList
        idList = new ArrayList<>();
        if (tList != null && !tList.isEmpty()) {
            for (T entity : tList) {
                if (entity != null && entity.getId() != null) {
                    idList.add(entity.getId());
                }
            }
        }
        
        TimberLogger.i(TAG,"FcEntityListFragment onCreate: idList size = " + idList.size());
        
        // Try to load avatars from database or generate them
        idImageBytesMap = new HashMap<>();
        if (getContext() != null && idList != null && !idList.isEmpty()) {
            if (tClass.equals(KeyInfo.class)) {
                KeyInfoManager kim = KeyInfoManager.getInstance(getContext());
                LocalDB<KeyInfo> localKeyInfoDB = kim.getKeyInfoDB();
                List<String> idsToGenerate = new ArrayList<>();

                for (String id : idList) {
                    byte[] avatar = null;
                    if (localKeyInfoDB != null) {
                        // Assuming AVATAR_MAP is accessible via IdUtils or is a public static final in KeyInfoManager/IdUtils
                        avatar = localKeyInfoDB.getFromMap(IdUtils.AVATAR_MAP, id);
                    }
                    if (avatar != null) {
                        idImageBytesMap.put(id, avatar);
                    } else {
                        idsToGenerate.add(id); // Mark for generation
                    }
                }
                TimberLogger.i(TAG, "onCreate: Loaded %d avatars from KeyInfoDB", idImageBytesMap.size());

                if (!idsToGenerate.isEmpty()) {
                    TimberLogger.i(TAG, "onCreate: Generating %d missing KeyInfo avatars", idsToGenerate.size());
                    try {
                        // Assuming IdUtils.makeCircleImageFromIdList can take Context
                        Map<String, byte[]> generatedAvatars = AvatarMaker.makeAvatars(idsToGenerate.toArray(new String[0]),getContext());
                        for (Map.Entry<String, byte[]> entry : generatedAvatars.entrySet()) {
                            idImageBytesMap.put(entry.getKey(), entry.getValue());
                            kim.saveAvatar(entry.getKey(), entry.getValue()); // Save back to DB
                        }
                        TimberLogger.i(TAG, "onCreate: Generated and saved %d KeyInfo avatars", generatedAvatars.size());
                    } catch (IOException e) {
                        TimberLogger.e(TAG, "onCreate: Failed to generate KeyInfo avatars: %s", e.getMessage());
                    }
                }
            } else {
                // Generic fallback for other FcEntity types
                if (!idList.isEmpty()) {
                    TimberLogger.i(TAG, "onCreate: Attempting generic avatar generation for %s type for %d items", tClass.getSimpleName(), idList.size());
                    try {
                        // Assuming IdUtils.makeCircleImageFromIdList can take Context
                        Map<String, byte[]> generatedAvatars = IdUtils.makeCircleImageFromIdList(new ArrayList<>(idList));
                        idImageBytesMap.putAll(generatedAvatars);
                        TimberLogger.i(TAG, "onCreate: Generically generated %d avatars", generatedAvatars.size());
                    } catch (IOException e) {
                        TimberLogger.e(TAG, "onCreate: Failed to generically generate avatars: %s", e.getMessage());
                    }
                }
            }
        }
        
        // Get field width map and field order map using reflection
        try {
            Method getFieldWidthMapMethod = tClass.getMethod(FcEntity.METHOD_GET_FIELD_WIDTH_MAP);
            fieldWidthMap = (LinkedHashMap<String, Integer>) getFieldWidthMapMethod.invoke(null);
            TimberLogger.i(TAG,"FcEntityListFragment onCreate: fieldWidthMap size = " + fieldWidthMap.size());
            
            // Initialize sort states for all fields with SORT_NONE
            for (String fieldName : fieldWidthMap.keySet()) {
                sortStates.put(fieldName, SORT_NONE);
            }
            
            // Get field name map for display
            try {
                Method getFieldNameMapMethod = tClass.getMethod(FcEntity.METHOD_GET_SHOW_FIELD_NAME_AS_MAP);
                fieldNameMap = (Map<String, String>) getFieldNameMapMethod.invoke(null);
                TimberLogger.i(TAG,"FcEntityListFragment onCreate: fieldNameMap size = " + (fieldNameMap != null ? fieldNameMap.size() : 0));
            } catch (Exception e) {
                TimberLogger.e(TAG,"Failed to get field name map: %s", e.getMessage());
                fieldNameMap = new HashMap<>();
            }
        } catch (Exception e) {
            TimberLogger.e(TAG,"Failed to get field maps: %s", e.getMessage());
            fieldWidthMap = new LinkedHashMap<>();
            fieldNameMap = new HashMap<>();
        }

        // Determine if any entity has a good FID
        showAvatarColumn = false;
        if (tList != null) {
            for (T entity : tList) {
                String id = entity != null ? entity.getId() : null;
                if (id != null && KeyTools.isGoodFid(id)) {
                    showAvatarColumn = true;
                    break;
                }
            }
        }
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        TimberLogger.i(TAG,"FcEntityListFragment onCreateView: Starting view creation");
        
        // Create the main container
        allContainer = new LinearLayout(requireContext());
        ((LinearLayout) allContainer).setOrientation(LinearLayout.VERTICAL);
        
        // Set layout parameters for the main container
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        allContainer.setLayoutParams(containerParams);
        
        // Check if tList is null or empty
        if (tList == null || tList.isEmpty()) {
            TimberLogger.e(TAG,"FcEntityListFragment onCreateView: tList is null or empty");
            
            // Create a simple view with a message
            TextView messageView = new TextView(requireContext());
            messageView.setText("No data available");
            messageView.setTextSize(18);
            messageView.setGravity(Gravity.CENTER);
            
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            messageView.setLayoutParams(messageParams);
            
            allContainer.addView(messageView);
            return allContainer;
        }
        
        // Create a horizontal scroll view to wrap all content
        HorizontalScrollView mainScrollView = new HorizontalScrollView(requireContext());
        mainScrollView.setHorizontalScrollBarEnabled(false);
        
        // Set layout parameters for the main scroll view
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mainScrollView.setLayoutParams(scrollParams);
        
        // Create a container for all content
        LinearLayout contentContainer = new LinearLayout(requireContext());
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        
        // Set layout parameters for the content container
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        contentContainer.setLayoutParams(contentParams);
        
        // Add top horizontal line
        View topDivider = new View(requireContext());
        topDivider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.field_name));
        LinearLayout.LayoutParams topDividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
        topDivider.setLayoutParams(topDividerParams);
        contentContainer.addView(topDivider);
        
        // Create the three main containers
        createHeaderContainer();
        createListContainer();
        createOrderContainer();
        
        // Add the containers to the content container
        contentContainer.addView(headerScrollView);
        contentContainer.addView(verticalListScrollView);
        contentContainer.addView(orderScrollView);
        
        // Add bottom horizontal line
        View bottomDivider = new View(requireContext());
        bottomDivider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.field_name));
        LinearLayout.LayoutParams bottomDividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
        bottomDivider.setLayoutParams(bottomDividerParams);
        contentContainer.addView(bottomDivider);
        
        // Add the content container to the main scroll view
        mainScrollView.addView(contentContainer);
        
        // Add the main scroll view to the all container
        ((LinearLayout) allContainer).addView(mainScrollView);
        
        // Set up synchronized scrolling
        setupSynchronizedScrolling();
        
        TimberLogger.i(TAG,"FcEntityListFragment onCreateView: View creation completed");
        
        return allContainer;
    }
    
    private void createHeaderContainer() {
        // Create horizontal scroll view for header
        headerScrollView = new HorizontalScrollView(requireContext());
        headerScrollView.setHorizontalScrollBarEnabled(false);
        
        // Set layout parameters for the header scroll view
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerScrollView.setLayoutParams(scrollParams);
        
        // Create container for header
        headerContainer = new LinearLayout(requireContext());
        headerContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        // Set background color for header container
        headerContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.headerBackground));
        
        // Set layout parameters for the header container
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (ROW_HEIGHT * density));
        headerContainer.setLayoutParams(containerParams);
        
        // Create fixed part container (for checkbox and avatar spaces)
        LinearLayout fixedPart = new LinearLayout(requireContext());
        fixedPart.setOrientation(LinearLayout.HORIZONTAL);
        
        // Calculate fixed part width
        int fixedPartWidth = 0;
        if (singleChoice != null) {
            fixedPartWidth += (int) (CHECKBOX_FIELD_WIDTH * density);
        }
        if (showAvatarColumn) {
            fixedPartWidth += (int) (AVATAR_FIELD_WIDTH * density);
        }
        
        // Set layout parameters for the fixed part
        LinearLayout.LayoutParams fixedPartParams = new LinearLayout.LayoutParams(
                fixedPartWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        fixedPart.setLayoutParams(fixedPartParams);
        
        // Add space for checkbox/radio button if needed
        if (singleChoice != null) {
            if (singleChoice) {
                // Add space for radio button
                View radioSpace = new View(requireContext());
                LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                        (int) (CHECKBOX_FIELD_WIDTH * density), ViewGroup.LayoutParams.MATCH_PARENT);
                radioSpace.setLayoutParams(radioParams);
                fixedPart.addView(radioSpace);
            } else {
                // Add space for checkbox
                View checkboxSpace = new View(requireContext());
                LinearLayout.LayoutParams checkboxParams = new LinearLayout.LayoutParams(
                        (int) (CHECKBOX_FIELD_WIDTH * density), ViewGroup.LayoutParams.MATCH_PARENT);
                checkboxSpace.setLayoutParams(checkboxParams);
                fixedPart.addView(checkboxSpace);
            }
        }
        
        // Add space for avatar if needed
        if (showAvatarColumn) {
            View avatarSpace = new View(requireContext());
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                    (int) (AVATAR_FIELD_WIDTH * density), ViewGroup.LayoutParams.MATCH_PARENT);
            avatarSpace.setLayoutParams(avatarParams);
            fixedPart.addView(avatarSpace);
        }
        
        // Add the fixed part to the header container
        headerContainer.addView(fixedPart);
        
        // Add field headers
        TimberLogger.i(TAG,"FcEntityListFragment createHeaderContainer: Adding " + fieldWidthMap.size() + " field headers");
        for (Map.Entry<String, Integer> entry : fieldWidthMap.entrySet()) {
            String fieldName = entry.getKey();
            int fieldWidth = entry.getValue();
            
            TextView headerView = new TextView(requireContext());
            
            // Use display name from fieldNameMap if available, otherwise use the original field name
            String displayName = fieldName;
            if (fieldNameMap != null && fieldNameMap.containsKey(fieldName)) {
                displayName = fieldNameMap.get(fieldName);
            }
            
            headerView.setText(displayName);
            headerView.setTextSize(14);
            headerView.setTypeface(null, Typeface.BOLD);
            headerView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            headerView.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
            
            // Set text color to text_color
            headerView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color));
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    (int) (fieldWidth * 8 * density), ViewGroup.LayoutParams.MATCH_PARENT);
            headerView.setLayoutParams(params);
            
            headerContainer.addView(headerView);
        }
        
        // Add the container to the scroll view
        headerScrollView.addView(headerContainer);
        
        TimberLogger.i(TAG,"FcEntityListFragment createHeaderContainer: Header container creation completed");
    }
    
    private void createListContainer() {
        TimberLogger.i(TAG,"FcEntityListFragment createListContainer: Starting list container creation");
        
        // Create vertical scroll view for list
        verticalListScrollView = new ScrollView(requireContext());
        verticalListScrollView.setVerticalScrollBarEnabled(false);
        
        // Set layout parameters for the vertical scroll view
        LinearLayout.LayoutParams verticalScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        verticalListScrollView.setLayoutParams(verticalScrollParams);
        
        // Create container for list
        listContainer = new LinearLayout(requireContext());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        
        // Set layout parameters for the list container
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listContainer.setLayoutParams(containerParams);
        
        // Add rows for each entity
        TimberLogger.i(TAG,"FcEntityListFragment createListContainer: Adding " + tList.size() + " rows");
        for (T entity : tList) {
            LinearLayout row = createRow(entity);
            listContainer.addView(row);
        }
        
        // Add the container to the vertical scroll view
        verticalListScrollView.addView(listContainer);
        
        TimberLogger.i(TAG,"FcEntityListFragment createListContainer: List container creation completed");
    }
    
    private LinearLayout createRow(T entity) {
        // Create row container
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        
        // Use theme-aware background color
        row.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.main_background));
        
        // Set row height
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (ROW_HEIGHT * density));
        row.setLayoutParams(rowParams);

        // Add long press listener for FID list operations
        View.OnLongClickListener longPressListener = v -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
            String[] options = {requireContext().getString(R.string.add_to_fid_list), requireContext().getString(R.string.clear_fid_list)};
            builder.setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // Add to FID list
                        SafeApplication.addFid(entity.getId());
                        Toast.makeText(requireContext(), requireContext().getString(R.string.added_to_fid_list), Toast.LENGTH_SHORT).show();
                        break;
                    case 1: // Clear FID list
                        SafeApplication.clearFidList();
                        Toast.makeText(requireContext(), requireContext().getString(R.string.fid_list_cleared), Toast.LENGTH_SHORT).show();
                        break;
                }
            });
            builder.show();
            return true;
        };

        // Apply long press listener to the row
        row.setOnLongClickListener(longPressListener);
        
        // Add selection control (RadioButton or CheckBox) if singleChoice is not null
        if (singleChoice != null) {
            if (singleChoice) {
                // Use RadioButton for single choice
                RadioButton radioButton = new RadioButton(requireContext());
                LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                        (int) (CHECKBOX_FIELD_WIDTH * density), ViewGroup.LayoutParams.MATCH_PARENT);
                radioButton.setLayoutParams(radioParams);
                radioButton.setGravity(Gravity.CENTER);
                
                // Apply text color to the radio button
                radioButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color));
                radioButton.setButtonTintList(ContextCompat.getColorStateList(requireContext(), R.color.text_color));
                
                // Set up radio button click listener
                radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        // Uncheck previously selected radio button
                        if (selectedRadioButton != null && selectedRadioButton != buttonView) {
                            selectedRadioButton.setChecked(false);
                        }
                        selectedRadioButton = (RadioButton) buttonView;
                        
                        // Update selected objects
                        selectedObjects.clear();
                        selectedObjects.add(entity);
                        notifySelectionChanged();
                    }
                });
                
                row.addView(radioButton);
            } else {
                // Use CheckBox for multiple choice
                CheckBox checkBox = new CheckBox(requireContext());
                LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                        (int) (CHECKBOX_FIELD_WIDTH * density), ViewGroup.LayoutParams.MATCH_PARENT);
                checkBox.setLayoutParams(checkParams);
                checkBox.setGravity(Gravity.CENTER);
                
                // Apply text color to the checkbox
                checkBox.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color));
                checkBox.setButtonTintList(ContextCompat.getColorStateList(requireContext(), R.color.text_color));
                
                // Set initial state
                boolean isChecked = checkboxStates.getOrDefault(entity, false);
                checkBox.setChecked(isChecked);
                
                // Set up checkbox click listener
                checkBox.setOnClickListener(v -> {
                    boolean checked = checkBox.isChecked();
                    checkboxStates.put(entity, checked);
                    
                    // Update selected objects
                    if (checked && !selectedObjects.contains(entity)) {
                        selectedObjects.add(entity);
                    } else if (!checked) {
                        selectedObjects.remove(entity);
                    }
                    
                    // Update All checkbox state
                    if (allCheckBox != null) {
                        boolean allChecked = true;
                        for (T e : tList) {
                            if (!checkboxStates.getOrDefault(e, false)) {
                                allChecked = false;
                                break;
                            }
                        }
                        // Temporarily remove listener before setting checked state
                        allCheckBox.setOnCheckedChangeListener(null);
                        allCheckBox.setChecked(allChecked);
                        // Re-attach listener
                        allCheckBox.setOnCheckedChangeListener(allCheckBoxListener);
                    }
                    
                    notifySelectionChanged();
                });
                
                row.addView(checkBox);
            }
        }
        
        // Add avatar if needed
        if (showAvatarColumn) {
            ImageView avatarView = new ImageView(requireContext());
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                    (int) (AVATAR_SIZE * density), (int) (AVATAR_SIZE * density));
            avatarParams.gravity = Gravity.CENTER_VERTICAL;
            avatarParams.setMargins((int) ((AVATAR_FIELD_WIDTH - AVATAR_SIZE) / 2 * density), 0, 0, 0);
            avatarView.setLayoutParams(avatarParams);
            String id = entity.getId();
            if (id != null && KeyTools.isGoodFid(id)) {
                final byte[] finalImageBytes = getAvatar(id, avatarView);
                avatarView.setOnClickListener(v -> showAvatarPopup(finalImageBytes));
                avatarView.setVisibility(View.VISIBLE);
            } else {
                avatarView.setVisibility(View.GONE);
            }
            row.addView(avatarView);
        }
        
        // Add field values
        for (Map.Entry<String, Integer> entry : fieldWidthMap.entrySet()) {
            String fieldName = entry.getKey();
            int fieldWidth = entry.getValue();
            
            TextView valueView = new TextView(requireContext());
            Object fieldValue = getFieldValue(entity, fieldName);
            valueView.setText(fieldValue != null ? fieldValue.toString() : "");
            valueView.setTextSize(14);
            valueView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            valueView.setPadding((int) (8 * density), 0, (int) (8 * density), 0);
            valueView.setSingleLine(true);
            valueView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            
            // Set text color to text_color
            valueView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color));
            
            // Set text color for boolean values
            if (fieldValue != null && fieldValue.toString().equals("✓")) {
                valueView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark));
            } else if (fieldValue != null && fieldValue.toString().equals("✗")) {
                valueView.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark));
            }
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    (int) (fieldWidth * 8 * density), ViewGroup.LayoutParams.MATCH_PARENT);
            valueView.setLayoutParams(params);
            
            // Set up click listener for the value view
            valueView.setOnClickListener(v -> {
                // Navigate to DetailActivity using Intent extras (JSON)
                Intent intent = new Intent(requireContext(), DetailActivity.class);
                intent.putExtra(DetailActivity.EXTRA_ENTITY_JSON, entity.toJson());
                intent.putExtra(DetailActivity.EXTRA_ENTITY_CLASS, tClass.getName());
                startActivity(intent);
            });

            // Add long press listener to the value view
            valueView.setOnLongClickListener(longPressListener);
            
            row.addView(valueView);
        }
        
        return row;
    }

    public byte[] getAvatar(String id, ImageView avatarView) {
        byte[] imageBytes = null;
        if (id != null && idImageBytesMap.containsKey(id)) {
            imageBytes = idImageBytesMap.get(id);
            if (imageBytes != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                avatarView.setImageBitmap(bitmap);
            } else {
                // Set default gray background
                avatarView.setBackgroundColor(Color.LTGRAY);
            }
        } else {
            // Set default gray background
            avatarView.setBackgroundColor(Color.LTGRAY);
        }
        return imageBytes;
    }

    private void createOrderContainer() {
        TimberLogger.i(TAG,"FcEntityListFragment createOrderContainer: Starting order container creation");
        
        // Create horizontal scroll view for order buttons
        orderScrollView = new HorizontalScrollView(requireContext());
        orderScrollView.setHorizontalScrollBarEnabled(false);
        
        // Set layout parameters for the order scroll view
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        orderScrollView.setLayoutParams(scrollParams);
        
        // Create container for order buttons
        orderContainer = new LinearLayout(requireContext());
        orderContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        // Set background color for order container
        orderContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.headerBackground));
        
        // Set layout parameters for the order container
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (30 * density));
        orderContainer.setLayoutParams(containerParams);
        
        // Create fixed part container (for checkbox and avatar spaces)
        LinearLayout fixedPart = new LinearLayout(requireContext());
        fixedPart.setOrientation(LinearLayout.HORIZONTAL);
        
        // Calculate fixed part width
        int fixedPartWidth = 0;
        if (singleChoice != null) {
            fixedPartWidth += (int) (CHECKBOX_FIELD_WIDTH * density);
        }
        if (showAvatarColumn) {
            fixedPartWidth += (int) (AVATAR_FIELD_WIDTH * density);
        }
        
        // Set layout parameters for the fixed part
        LinearLayout.LayoutParams fixedPartParams = new LinearLayout.LayoutParams(
                fixedPartWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        fixedPart.setLayoutParams(fixedPartParams);
        
        // Add space for checkbox/radio button if needed
        if (singleChoice != null) {
            if (singleChoice) {
                // Add space for radio button
                View radioSpace = new View(requireContext());
                LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                        (int) (CHECKBOX_FIELD_WIDTH * density), ViewGroup.LayoutParams.MATCH_PARENT);
                radioSpace.setLayoutParams(radioParams);
                fixedPart.addView(radioSpace);
            } else {
                // Add All checkbox
                allCheckBox = new CheckBox(requireContext());
                LinearLayout.LayoutParams allCheckParams = new LinearLayout.LayoutParams(
                        (int) (CHECKBOX_FIELD_WIDTH * density), ViewGroup.LayoutParams.MATCH_PARENT);
                allCheckBox.setLayoutParams(allCheckParams);
                allCheckBox.setGravity(Gravity.CENTER);
                
                // Apply text color to the checkbox
                allCheckBox.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color));
                allCheckBox.setButtonTintList(ContextCompat.getColorStateList(requireContext(), R.color.text_color));
                
                // Set up click listener for the All checkbox
                allCheckBoxListener = (buttonView, isChecked) -> {
                    // Update all checkboxes in the list
                    for (T entity : tList) {
                        checkboxStates.put(entity, isChecked);
                        if (isChecked && !selectedObjects.contains(entity)) {
                            selectedObjects.add(entity);
                        } else if (!isChecked) {
                            selectedObjects.remove(entity);
                        }
                    }
                    refreshList();
                    notifySelectionChanged();
                };
                allCheckBox.setOnCheckedChangeListener(allCheckBoxListener);
                
                fixedPart.addView(allCheckBox);
            }
        }
        
        // Add space for avatar if needed
        if (showAvatarColumn) {
            View avatarSpace = new View(requireContext());
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                    (int) (AVATAR_FIELD_WIDTH * density), ViewGroup.LayoutParams.MATCH_PARENT);
            avatarSpace.setLayoutParams(avatarParams);
            fixedPart.addView(avatarSpace);
        }
        
        // Add the fixed part to the order container
        orderContainer.addView(fixedPart);
        
        // Add sort buttons for each field
        TimberLogger.i(TAG,"FcEntityListFragment createOrderContainer: Adding " + fieldWidthMap.size() + " sort buttons");
        for (Map.Entry<String, Integer> entry : fieldWidthMap.entrySet()) {
            String fieldName = entry.getKey();
            int fieldWidth = entry.getValue();
            
            ImageButton sortButton = new ImageButton(requireContext());
            sortButton.setBackgroundResource(android.R.color.transparent);
            updateSortButtonIcon(sortButton, fieldName);
            
            // Store the button reference in the map
            sortButtonsMap.put(fieldName, sortButton);
            
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    (int) (fieldWidth * 8 * density), (int) (30 * density));
            sortButton.setLayoutParams(params);
            
            // Set up click listener to cycle through sort states
            sortButton.setOnClickListener(v -> {
                String currentState = sortStates.get(fieldName);
                String newState;
                
                if (SORT_NONE.equals(currentState)) {
                    newState = SORT_ASC;
                } else if (SORT_ASC.equals(currentState)) {
                    newState = SORT_DESC;
                } else {
                    newState = SORT_NONE;
                }
                
                // Reset all other fields to SORT_NONE
                for (Map.Entry<String, String> entry1 : sortStates.entrySet()) {
                    if (!entry1.getKey().equals(fieldName)) {
                        sortStates.put(entry1.getKey(), SORT_NONE);
                        // Update the icon for this field's sort button
                        ImageButton otherButton = sortButtonsMap.get(entry1.getKey());
                        if (otherButton != null) {
                            updateSortButtonIcon(otherButton, entry1.getKey());
                        }
                    }
                }
                
                sortStates.put(fieldName, newState);
                updateSortButtonIcon(sortButton, fieldName);
                sortObjects();
            });
            
            orderContainer.addView(sortButton);
        }
        
        // Add the container to the scroll view
        orderScrollView.addView(orderContainer);
        
        TimberLogger.i(TAG,"FcEntityListFragment createOrderContainer: Order container creation completed");
    }
    
    private void setupSynchronizedScrolling() {
        // No need for synchronized scrolling anymore since we're using a single HorizontalScrollView
        // that wraps all content
    }
    
    private void updateSortButtonIcon(ImageButton button, String fieldName) {
        String sortState = sortStates.get(fieldName);
        int iconResId;
        
        if (SORT_ASC.equals(sortState)) {
            iconResId = android.R.drawable.arrow_up_float;
        } else if (SORT_DESC.equals(sortState)) {
            iconResId = android.R.drawable.arrow_down_float;
        } else {
            iconResId = android.R.drawable.ic_menu_sort_by_size;
        }
        
        button.setImageResource(iconResId);
        
        // Apply text color to the button icon
        button.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_color));
    }
    
    private void sortObjects() {
        // Find the first field with a non-NONE sort state
        String sortField = null;
        String sortDirection = null;
        
        for (Map.Entry<String, String> entry : sortStates.entrySet()) {
            if (!SORT_NONE.equals(entry.getValue())) {
                sortField = entry.getKey();
                sortDirection = entry.getValue();
                break;
            }
        }
        
        // If no sort field is selected, return without sorting
        if (sortField == null) {
            return;
        }
        
        // Sort the list
        String finalSortField = sortField;
        String finalSortDirection = sortDirection;
        tList.sort((o1, o2) -> {
            Object value1 = getFieldValue(o1, finalSortField);
            Object value2 = getFieldValue(o2, finalSortField);
            
            if (value1 == null && value2 == null) {
                return 0;
            } else if (value1 == null) {
                return SORT_ASC.equals(finalSortDirection) ? 1 : -1;
            } else if (value2 == null) {
                return SORT_ASC.equals(finalSortDirection) ? -1 : 1;
            }
            
            int comparison = value1.toString().compareTo(value2.toString());
            return SORT_ASC.equals(finalSortDirection) ? comparison : -comparison;
        });
        
        // Refresh the list
        refreshList();
    }
    
    private Object getFieldValue(T obj, String fieldName) {
        try {
            // Try standard getter naming convention first
            String getterName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            try {
                Method getter = tClass.getMethod(getterName);
                Object value = getter.invoke(obj);
                
                // Format boolean values with colored X or tick
                if (value instanceof Boolean) {
                    boolean boolValue = (Boolean) value;
                    if (boolValue) {
                        return "✓"; // Green tick for true
                    } else {
                        return "✗"; // Red X for false
                    }
                }
                
                return value;
            } catch (NoSuchMethodException e) {
                // If standard getter not found, try isXxx for boolean fields
                if (fieldName.startsWith("is")) {
                    // Already in isXxx format
                    Method getter = tClass.getMethod(fieldName);
                    Object value = getter.invoke(obj);
                    
                    // Format boolean values with colored X or tick
                    if (value instanceof Boolean) {
                        boolean boolValue = (Boolean) value;
                        if (boolValue) {
                            return "✓"; // Green tick for true
                        } else {
                            return "✗"; // Red X for false
                        }
                    }
                    
                    return value;
                } else {
                    // Try isXxx format for boolean fields
                    getterName = "is" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                    Method getter = tClass.getMethod(getterName);
                    Object value = getter.invoke(obj);
                    
                    // Format boolean values with colored X or tick
                    if (value instanceof Boolean) {
                        boolean boolValue = (Boolean) value;
                        if (boolValue) {
                            return "✓"; // Green tick for true
                        } else {
                            return "✗"; // Red X for false
                        }
                    }
                    
                    return value;
                }
            }
        } catch (Exception e) {
            // Log the specific error for debugging
            TimberLogger.e(TAG,"Failed to get field value for %s: %s", fieldName, e.getMessage());
            
            // Try direct field access as a fallback
            try {
                Field field = tClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(obj);
                
                // Format boolean values with colored X or tick
                if (value instanceof Boolean) {
                    boolean boolValue = (Boolean) value;
                    if (boolValue) {
                        return "✓"; // Green tick for true
                    } else {
                        return "✗"; // Red X for false
                    }
                }
                
                return value;
            } catch (Exception ex) {
                TimberLogger.e(TAG,"Failed to access field directly for %s: %s", fieldName, ex.getMessage());
                return null;
            }
        }
    }
    
    /**
     * Gets the list of selected objects
     * 
     * @return List of selected objects
     */
    public List<T> getSelectedObjects() {
        return selectedObjects;
    }
    
    /**
     * Updates the list with new data
     * 
     * @param newList New list of objects
     */
    public void updateList(List<T> newList) {
        TimberLogger.i(TAG, "Updating list with %d items", newList.size());
        
        // Store the current selection state
        List<T> previouslySelected = new ArrayList<>(selectedObjects);
        Map<T, Boolean> previousCheckboxStates = new HashMap<>(checkboxStates);
        
        // Update the list
        tList = newList;
        
        // Update idList
        idList.clear();
        for (T entity : newList) {
            if (entity.getId() != null) {
                idList.add(entity.getId());
            }
        }
        
        // Initialize avatar cache if null
        if (idImageBytesMap == null) {
            idImageBytesMap = new HashMap<>();
            TimberLogger.i(TAG, "Initialized empty avatar cache");
        }
        
        // Check for any IDs that don't have avatars in cache
        List<String> missingAvatarIdsInCache = new ArrayList<>();
        for (String id : idList) {
            if (!idImageBytesMap.containsKey(id) || idImageBytesMap.get(id) == null) {
                missingAvatarIdsInCache.add(id);
            }
        }

        if (getContext() != null && !missingAvatarIdsInCache.isEmpty()) {
            TimberLogger.i(TAG, "updateList: Found %d IDs missing avatars in cache", missingAvatarIdsInCache.size());

            if (tClass.equals(KeyInfo.class)) {
                KeyInfoManager kim = KeyInfoManager.getInstance(getContext());
                LocalDB<KeyInfo> localKeyInfoDB = kim.getKeyInfoDB();
                List<String> idsToGenerateForKeyInfo = new ArrayList<>();

                for (String id : missingAvatarIdsInCache) {
                    byte[] avatar = null;
                    if (localKeyInfoDB != null) {
                        avatar = localKeyInfoDB.getFromMap(IdUtils.AVATAR_MAP, id);
                    }
                    if (avatar != null) {
                        idImageBytesMap.put(id, avatar);
                    } else {
                        idsToGenerateForKeyInfo.add(id);
                    }
                }
                TimberLogger.i(TAG, "updateList: Loaded %d KeyInfo avatars from DB for missing cache", (missingAvatarIdsInCache.size() - idsToGenerateForKeyInfo.size()));

                if (!idsToGenerateForKeyInfo.isEmpty()) {
                    TimberLogger.i(TAG, "updateList: Generating %d missing KeyInfo avatars", idsToGenerateForKeyInfo.size());
                    try {
                        // Assuming IdUtils.makeCircleImageFromIdList can take Context
                        Map<String, byte[]> generatedAvatars = IdUtils.makeCircleImageFromIdList(idsToGenerateForKeyInfo);
                        for (Map.Entry<String, byte[]> entry : generatedAvatars.entrySet()) {
                            idImageBytesMap.put(entry.getKey(), entry.getValue());
                            kim.saveAvatar(entry.getKey(), entry.getValue()); // Save back to DB
                        }
                        TimberLogger.i(TAG, "updateList: Generated and saved %d KeyInfo avatars", generatedAvatars.size());
                    } catch (IOException e) {
                        TimberLogger.e(TAG, "updateList: Failed to generate KeyInfo avatars: %s", e.getMessage());
                    }
                }
            } else {
                // Generic fallback for other FcEntity types for missing avatars in cache.
                if (!missingAvatarIdsInCache.isEmpty()) {
                    TimberLogger.i(TAG, "updateList: Attempting generic avatar generation for %s type for %d items from missing cache", tClass.getSimpleName(), missingAvatarIdsInCache.size());
                    try {
                        // Assuming IdUtils.makeCircleImageFromIdList can take Context
                        Map<String, byte[]> generatedAvatars = IdUtils.makeCircleImageFromIdList(missingAvatarIdsInCache);
                        idImageBytesMap.putAll(generatedAvatars);
                        TimberLogger.i(TAG, "updateList: Generically generated %d avatars from missing cache", generatedAvatars.size());
                    } catch (IOException e) {
                        TimberLogger.e(TAG, "updateList: Failed to generically generate avatars from missing cache: %s", e.getMessage());
                    }
                }
            }
        }
        
        // Restore selection state for existing items
        selectedObjects.clear();
        checkboxStates.clear();
        for (T entity : tList) {
            if (previouslySelected.contains(entity)) {
                selectedObjects.add(entity);
                checkboxStates.put(entity, true);
            } else if (previousCheckboxStates.containsKey(entity)) {
                checkboxStates.put(entity, previousCheckboxStates.get(entity));
            }
        }
        
        // Apply current sort state before refreshing the list
        sortObjects();
        
        // Get the current view
        View currentView = getView();
        if (currentView != null) {
            // Remove all views from the container
            if (currentView instanceof ViewGroup) {
                ((ViewGroup) currentView).removeAllViews();
            }
            
            // Create a new view
            View newView = onCreateView(getLayoutInflater(), (ViewGroup) currentView, null);
            
            // Add the new view to the container
            if (currentView instanceof ViewGroup) {
                ((ViewGroup) currentView).addView(newView);
            }
        } else {
            TimberLogger.e(TAG, "Cannot update view - view is null");
        }
    }
    
    private void refreshList() {
        // Remove all rows
        listContainer.removeAllViews();
        
        // Add rows for each entity
        for (T entity : tList) {
            LinearLayout row = createRow(entity);
            listContainer.addView(row);
        }
    }
    
    private void showAvatarPopup(byte[] imageBytes) {
        // Get the ID from the imageBytes by finding the corresponding entity
        String id = null;
        for (Map.Entry<String, byte[]> entry : idImageBytesMap.entrySet()) {
            if (entry.getValue() == imageBytes) {
                id = entry.getKey();
                break;
            }
        }
        
        if (id != null) {
            IdUtils.showAvatarDialog(requireContext(), id);
        } else {
            Toast.makeText(requireContext(), getString(R.string.no_id_found_for_avatar), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void saveAvatarToGallery(byte[] imageBytes) {
        try {
            // Create a bitmap from the image bytes
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap == null) {
                Toast.makeText(requireContext(), "Failed to decode image", SafeApplication.TOAST_LASTING).show();
                return;
            }
            
            // Generate a unique filename
            String fileName = "Avatar_" + System.currentTimeMillis() + ".png";
            
            // Create content values for the image
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            
            // For Android 10 (API level 29) and above, use RELATIVE_PATH
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            }
            
            // Insert the image into the MediaStore
            Uri imageUri = requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri != null) {
                try (OutputStream out = requireContext().getContentResolver().openOutputStream(imageUri)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    Toast.makeText(requireContext(), "Avatar saved to gallery", SafeApplication.TOAST_LASTING).show();
                } catch (IOException e) {
                    Toast.makeText(requireContext(), "Error saving avatar: " + e.getMessage(), SafeApplication.TOAST_LASTING).show();
                }
            } else {
                Toast.makeText(requireContext(), "Failed to save avatar", SafeApplication.TOAST_LASTING).show();
            }
            
            // Recycle the bitmap to free memory
            bitmap.recycle();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error saving avatar: " + e.getMessage(), SafeApplication.TOAST_LASTING).show();
        }
    }
    
    /**
     * Gets the current sort states
     * 
     * @return Map of field names to sort states
     */
    public Map<String, String> getSortStates() {
        return sortStates;
    }

    /**
     * Sets the avatar cache for the fragment
     * @param avatarCache Map of FID to avatar bytes
     */
    public void setAvatarCache(Map<String, byte[]> avatarCache) {
        if (avatarCache != null) {
            this.idImageBytesMap = avatarCache;
            TimberLogger.i(TAG, "Avatar cache set with %d entries", avatarCache.size());
            // Only refresh if the view is initialized
            if (listContainer != null) {
                refreshList();
            }
        }
    }
} 