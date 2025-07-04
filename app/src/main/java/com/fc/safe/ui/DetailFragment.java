package com.fc.safe.ui;

import static com.fc.safe.utils.IdUtils.AVATAR_MAP;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;
import com.fc.safe.R;
import com.fc.safe.db.DatabaseManager;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.utils.IdUtils;
import com.fc.fc_ajdk.core.crypto.KeyTools;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DetailFragment extends Fragment {
    public final static String TAG = "DetailFragment";
    
    // Constants
    private static final int INDENT = 20; // Indent for nested elements
    private static final int AVATAR_SIZE = 100; // Size of the avatar in dp
    
    // Instance variables
    private float density;
    private FcEntity currentEntity;
    private Class<? extends FcEntity> currentEntityClass;
    private KeyInfoManager keyInfoManager;
    
    // Add field for satoshi field list
    private List<String> satoshiFieldList;
    
    // Add field for timestamp field list
    private List<String> timestampFieldList;
    
    // UI components
    private ImageView avatarView;
    private LinearLayout detailContainer;

    public static DetailFragment newInstance(FcEntity entity, Class<?> entityClass) {
        TimberLogger.d(TAG, "DetailFragment newInstance: Creating new fragment with entity ID = " + 
            (entity != null ? entity.getId() : "null") + ", entityClass = " + 
            (entityClass != null ? entityClass.getSimpleName() : "null"));
        DetailFragment fragment = new DetailFragment();
        Bundle args = new Bundle();
        args.putString(DetailActivity.EXTRA_ENTITY_JSON, entity != null ? entity.toJson() : null);
        args.putString(DetailActivity.EXTRA_ENTITY_CLASS, entityClass != null ? entityClass.getName() : null);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TimberLogger.d(TAG, "DetailFragment onCreate: Starting fragment creation");

        // Get entity and class from arguments
        Bundle args = getArguments();
        if (args != null) {
            String entityJson = args.getString(DetailActivity.EXTRA_ENTITY_JSON);
            String className = args.getString(DetailActivity.EXTRA_ENTITY_CLASS);
            try {
                if (className != null) {
                    currentEntityClass = (Class<? extends FcEntity>) Class.forName(className);
                }
                if (entityJson != null && currentEntityClass != null) {
                    currentEntity = FcEntity.fromJson(entityJson, currentEntityClass);
                }
            } catch (Exception e) {
                TimberLogger.e(TAG, "DetailFragment onCreate: Error loading entity: " + e.getMessage());
            }
        }

        if (currentEntity == null || currentEntityClass == null) {
            TimberLogger.e(TAG, "DetailFragment onCreate: No entity data available. Entity: " + 
                (currentEntity != null ? "not null" : "null") + ", EntityClass: " + 
                (currentEntityClass != null ? "not null" : "null"));
            return;
        }

        // Get display density
        density = getResources().getDisplayMetrics().density;
        TimberLogger.d(TAG, "DetailFragment onCreate: Display density = " + density);

        // Get database manager
        DatabaseManager dbManager = DatabaseManager.getInstance(requireContext());
        TimberLogger.d(TAG, "DetailFragment onCreate: Database manager obtained");

        // Get key info database
        keyInfoManager = KeyInfoManager.getInstance(requireContext());
        TimberLogger.d(TAG, "DetailFragment onCreate: Key info database obtained");
        
        // Get satoshi field list from entity class
        try {
            Method getSatoshiFieldListMethod = currentEntityClass.getMethod(FcEntity.METHOD_GET_SATOSHI_FIELD_LIST);
            satoshiFieldList = (List<String>) getSatoshiFieldListMethod.invoke(null);
            TimberLogger.d(TAG, "DetailFragment onCreate: satoshiFieldList size = " + (satoshiFieldList != null ? satoshiFieldList.size() : 0));
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to get satoshi field list: %s", e.getMessage());
            satoshiFieldList = new ArrayList<>();
        }
        
        // Get timestamp field list from entity class
        try {
            Method getTimestampFieldListMethod = currentEntityClass.getMethod(FcEntity.METHOD_GET_TIMESTAMP_FIELD_LIST);
            timestampFieldList = (List<String>) getTimestampFieldListMethod.invoke(null);
            TimberLogger.d(TAG, "DetailFragment onCreate: timestampFieldList size = " + (timestampFieldList != null ? timestampFieldList.size() : 0));
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to get timestamp field list: %s", e.getMessage());
            timestampFieldList = new ArrayList<>();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        TimberLogger.d(TAG, "DetailFragment onCreateView: Starting view creation");
        View view = inflater.inflate(R.layout.fragment_detail, container, false);
        
        detailContainer = view.findViewById(R.id.detail_container);
        if (detailContainer == null) {
            TimberLogger.e(TAG, "DetailFragment onCreateView: detail_container not found in layout");
        } else {
            TimberLogger.d(TAG, "DetailFragment onCreateView: detail_container found");
        }
        
        avatarView = view.findViewById(R.id.avatar_view);
        if (avatarView == null) {
            TimberLogger.e(TAG, "DetailFragment onCreateView: avatar_view not found in layout");
        } else {
            TimberLogger.d(TAG, "DetailFragment onCreateView: avatar_view found");
        }
        
        // Setup Make QR and Copy buttons for entity JSON
        View btnMakeQr = view.findViewById(R.id.btn_make_qr);
        View btnCopyJson = view.findViewById(R.id.btn_copy_json);
        if (btnMakeQr != null && btnCopyJson != null && currentEntity != null) {
            btnMakeQr.setOnClickListener(v -> {
                String json = currentEntity.toNiceJson();
                com.fc.safe.utils.QRCodeGenerator.generateAndShowQRCode(requireContext(), json);
            });
            btnCopyJson.setOnClickListener(v -> {
                String json = currentEntity.toNiceJson();
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("entity_json", json);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), getString(R.string.copied), SafeApplication.TOAST_LASTING).show();
            });
        }
        
        setupView();
        TimberLogger.d(TAG, "DetailFragment onCreateView: View setup completed");
        return view;
    }

    private void setupView() {
        TimberLogger.i(TAG, "Setting up view");
        
        // Check if entity is null
        if (currentEntity == null) {
            TimberLogger.e(TAG, "Entity is null");
            
            // Create a simple view with a message
            TextView messageView = new TextView(requireContext());
            messageView.setText("No data available");
            messageView.setTextSize(18);
            messageView.setGravity(Gravity.CENTER);
            
            // Remove existing views from detail container
            detailContainer.removeAllViews();
            detailContainer.addView(messageView);
            return;
        }
        
        // Check if avatar should be shown
        boolean showAvatar = true;
        String id = currentEntity.getId();
        if (id == null || !KeyTools.isGoodFid(id)) {
            showAvatar = false;
        }
        
        // Load the avatar if allowed
        if (showAvatar) {
            loadAvatar();
        } else {
            avatarView.setVisibility(View.GONE);
        }
        
        // Create a container for the ID field
        LinearLayout idContainer = new LinearLayout(requireContext());
        idContainer.setOrientation(LinearLayout.VERTICAL);
        
        // Set layout parameters for the ID container
        LinearLayout.LayoutParams idContainerParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        idContainer.setLayoutParams(idContainerParams);
        
        // Add the ID field at the first row with bold if ID is not null
        if (currentEntity.getId() != null) {
            LinearLayout idRow = createDetailRow("ID", currentEntity.getId(), true);
            idContainer.addView(idRow);
        }
        
        // Create a container for the avatar and ID
        LinearLayout avatarIdContainer = new LinearLayout(requireContext());
        avatarIdContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        // Set layout parameters for the avatar ID container
        LinearLayout.LayoutParams avatarIdParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        avatarIdParams.setMargins(0, 0, 0, (int) (16 * density));
        avatarIdContainer.setLayoutParams(avatarIdParams);
        
        // Remove the avatar view from its current parent
        ViewGroup avatarParent = (ViewGroup) avatarView.getParent();
        if (avatarParent != null) {
            avatarParent.removeView(avatarView);
        }
        
        // Add the avatar and ID container to the avatar ID container
        if (showAvatar) {
            avatarView.setVisibility(View.VISIBLE);
            avatarIdContainer.addView(avatarView);
        }
        avatarIdContainer.addView(idContainer);
        
        // Remove existing views from detail container
        detailContainer.removeAllViews();
        
        // Add the avatar ID container to the detail container
        detailContainer.addView(avatarIdContainer);
        
        // Add other fields
        addEntityFields(currentEntity);
    }

    private void loadAvatar() {
        if (currentEntity != null && currentEntity.getId() != null) {
            try {
                // First try to load from database
                Map<String, byte[]> avatarMap = IdUtils.loadAvatarFromDb(List.of(currentEntity.getId()), keyInfoManager.getKeyInfoDB());

                byte[] avatarBytes = null;
                
                if (avatarMap!=null && avatarMap.containsKey(currentEntity.getId())) {
                    avatarBytes = avatarMap.get(currentEntity.getId());
                }
                
                // If not found in DB, generate a new one
                if (avatarBytes == null) {
                    avatarBytes = IdUtils.generateAvatar(currentEntity.getId());
                    if(avatarBytes != null){
                        keyInfoManager.getKeyInfoDB().putInMap(AVATAR_MAP, currentEntity.getId(), avatarBytes);
                    }
                }
                
                // Set the avatar image
                if (avatarBytes != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                    if (bitmap != null) {
                        avatarView.setImageBitmap(bitmap);
                        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        // Add click listener to show avatar dialog
                        avatarView.setOnClickListener(v -> {
                            if (currentEntity != null && currentEntity.getId() != null) {
                                IdUtils.showAvatarDialog(requireContext(), currentEntity.getId());
                            }
                        });
                        return;
                    }
                }
                
                // If everything fails, set a default gray background
                avatarView.setBackgroundColor(Color.LTGRAY);
                
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error setting avatar: %s", e.getMessage());
                avatarView.setBackgroundColor(Color.LTGRAY);
            }
        }
    }

    private void addEntityFields(FcEntity entity) {
        // Collect all fields in the order they are defined in the class hierarchy
        List<Field> orderedFields = new ArrayList<>();
        
        // Get all fields including inherited ones in the correct order
        Class<?> currentClass = entity.getClass();
        while (currentClass != null && currentClass != Object.class) {
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                String fieldName = field.getName();
                
                // Skip static, final, or transient fields
                int modifiers = field.getModifiers();
                if (java.lang.reflect.Modifier.isStatic(modifiers) || 
                    java.lang.reflect.Modifier.isTransient(modifiers)) {
                    continue;
                }
                
                // Skip the "id" field since it's already displayed at the top
                if ("id".equals(fieldName)) {
                    continue;
                }
                
                orderedFields.add(field);
            }
            // Move to superclass
            currentClass = currentClass.getSuperclass();
        }
        
        // Process fields in the collected order
        for (Field field : orderedFields) {
            String fieldName = field.getName();
            
            try {
                Object value = field.get(entity);
                
                // Skip fields with null values
                if (value == null) {
                    continue;
                }
                
                String displayValue = value.toString();
                
                // Format satoshi values using FchUtils.formatSatoshiValue()
                if (satoshiFieldList != null && satoshiFieldList.contains(fieldName) && value instanceof Number) {
                    try {
                        long satoshiValue = ((Number) value).longValue();
                        displayValue = com.fc.fc_ajdk.utils.FchUtils.formatSatoshiValue(satoshiValue);
                    } catch (Exception e) {
                        TimberLogger.e(TAG, "Failed to format satoshi value for field %s: %s", fieldName, e.getMessage());
                    }
                }
                
                // Format timestamp values using DateUtils.longShortToTime()
                if (timestampFieldList != null && timestampFieldList.contains(fieldName) && value instanceof Number) {
                    try {
                        long timestampValue = ((Number) value).longValue();
                        displayValue = com.fc.fc_ajdk.utils.DateUtils.longShortToTime(timestampValue, com.fc.fc_ajdk.utils.DateUtils.TO_SECOND);
                    } catch (Exception e) {
                        TimberLogger.e(TAG, "Failed to format timestamp value for field %s: %s", fieldName, e.getMessage());
                    }
                }
                
                // Create a row for each field
                LinearLayout row = new LinearLayout(requireContext());
                row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 16, 0, 16);

                // Add field name with colon
                TextView nameView = new TextView(requireContext());
                nameView.setText(fieldName + ": ");
                nameView.setTextSize(16);
                nameView.setTypeface(null, Typeface.BOLD);
                nameView.setTextColor(ContextCompat.getColor(requireContext(), R.color.field_name));
                nameView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ));

                // Add field value
                TextView valueView = new TextView(requireContext());
                valueView.setText(displayValue);
                valueView.setTextSize(16);
                valueView.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                
                // Make value copyable when clicked
                valueView.setOnClickListener(v -> {
                    String text = valueView.getText().toString();
                    if (!text.isEmpty()) {
                        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Copied text", text);
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(requireContext(), getString(R.string.copied), SafeApplication.TOAST_LASTING).show();
                    }
                });

                // Add long-press decrypt menu for cipher fields
                if (fieldName.toLowerCase().contains("cipher")) {
                    // Add decrypt icon to the end of the row
                    ImageView decryptIcon = new ImageView(requireContext());
                    decryptIcon.setImageResource(R.drawable.ic_decrypt);
                    int iconSize = (int) (24 * density);
                    LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                        iconSize, iconSize);
                    iconParams.setMargins((int)(8 * density), 0, 0, 0);
                    decryptIcon.setLayoutParams(iconParams);
                    decryptIcon.setClickable(true);
                    decryptIcon.setFocusable(true);
                    decryptIcon.setContentDescription(getString(R.string.decrypt));
                    // Special handling for priKeyCipher field
                    String finalDisplayValue = displayValue;
                    String finalFieldName = fieldName;
                    decryptIcon.setOnClickListener(v -> {
                        try {
                            if ("prikeyCipher".equals(finalFieldName)) {
                                // Special handling for priKeyCipher - show WIF compressed format
                                com.fc.safe.utils.ResultDialog.showDecryptDialogForPrikeyCipher(requireContext(), finalDisplayValue, null);
                            } else {
                                // Regular cipher decryption
                                com.fc.safe.utils.ResultDialog.showDecryptDialogForCipher(requireContext(), finalDisplayValue, null);
                            }
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), getString(R.string.failed_to_decrypt) + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                    // Add decryptIcon after valueView
                    row.addView(nameView);
                    row.addView(valueView);
                    row.addView(decryptIcon);
                } else {
                    // Add views to row
                    row.addView(nameView);
                    row.addView(valueView);
                }

                // Add row to container
                detailContainer.addView(row);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private LinearLayout createDetailRow(String label, String value, boolean isBold) {
        // Create row container
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        
        // Set layout parameters for the row
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, (int) (8 * density));
        row.setLayoutParams(rowParams);
        
        // Create label view
        TextView labelView = new TextView(requireContext());
        labelView.setText(label + ": ");
        labelView.setTextSize(16);
        // Always make the label (field name) bold
        labelView.setTypeface(null, Typeface.BOLD);
        // Set the color of the field name to the 'field_name' color resource
        labelView.setTextColor(ContextCompat.getColor(requireContext(), R.color.field_name));
        
        // Set layout parameters for the label view
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelView.setLayoutParams(labelParams);
        
        // Create value view
        TextView valueView = new TextView(requireContext());
        valueView.setText(value);
        valueView.setTextSize(16);
        valueView.setTypeface(null, isBold ? Typeface.BOLD : Typeface.NORMAL);
        
        // Set layout parameters for the value view
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        valueView.setLayoutParams(valueParams);
        
        // Set up click listener to copy text to clipboard
        valueView.setOnClickListener(v -> {
            String text = valueView.getText().toString();
            if (!text.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Copied text", text);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(requireContext(), getString(R.string.copied_to_clipboard), SafeApplication.TOAST_LASTING).show();
            }
        });
        
        // Add views to row
        row.addView(labelView);
        row.addView(valueView);
        
        return row;
    }

    public FcEntity getCurrentEntity() {
        return currentEntity;
    }
} 