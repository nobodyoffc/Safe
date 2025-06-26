package com.fc.safe;

import static com.fc.fc_ajdk.constants.FieldNames.LABEL;
import static com.fc.fc_ajdk.constants.FieldNames.PRI_KEY;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.ObjectUtils;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.utils.IdUtils;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.utils.ScanUtils;

import java.util.HashMap;
import java.util.Map;

public class CreateKeyInfoByPrikeyActivity<T extends FcEntity> extends AppCompatActivity {
    private static final String EXTRA_CLASS_NAME = "class_name";
    private Class<T> tClass;
    private final Map<String, EditText> fieldInputMap = new HashMap<>();
    private final Map<String, CheckBox> fieldCheckBoxMap = new HashMap<>();
    private final Map<String, ImageButton> fieldScanButtonMap = new HashMap<>();
    private LinearLayout inputContainer;
    private Button clearButton;
    private Button confirmButton;
    private LocalDB<KeyInfo> keyInfoDB;

    public static <T extends FcEntity> Intent newIntent(Context context, Class<T> tClass) {
        Intent intent = new Intent(context, CreateKeyInfoByPrikeyActivity.class);
        intent.putExtra(EXTRA_CLASS_NAME, tClass.getName());
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_by_input);
        
        if (getIntent() != null) {
            try {
                String className = getIntent().getStringExtra(EXTRA_CLASS_NAME);
                tClass = (Class<T>) Class.forName(className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                finish();
                return;
            }
        }
        
        // Initialize containers and buttons
        inputContainer = findViewById(R.id.input_container);
        clearButton = findViewById(R.id.clear_button);
        confirmButton = findViewById(R.id.confirm_button);

        // Get KeyInfoManager
        KeyInfoManager keyInfoManager = KeyInfoManager.getInstance(this);
        keyInfoDB = keyInfoManager.getKeyInfoDB();
        
        // Setup input fields
        setupInputFields();
        
        // Setup buttons
        setupButtons();
    }

    private void setupInputFields() {
        try {
            Map<String, Object> fieldMap = (Map<String, Object>) tClass.getMethod("getInputFieldDefaultValueMap").invoke(null);
            for (Map.Entry<String, Object> entry : fieldMap.entrySet()) {
                String fieldName = entry.getKey();
                Object defaultValue = entry.getValue();
                boolean isBoolean = defaultValue instanceof Boolean;

                LinearLayout fieldContainer = new LinearLayout(this);
                fieldContainer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                fieldContainer.setOrientation(LinearLayout.HORIZONTAL);

                if (isBoolean) {
                    CheckBox checkBox = new CheckBox(this);
                    checkBox.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    checkBox.setText(fieldName);
                    checkBox.setChecked((Boolean) defaultValue);
                    checkBox.setTag(fieldName);
                    fieldContainer.addView(checkBox);
                    fieldCheckBoxMap.put(fieldName, checkBox);
                } else {
                    TextView label = new TextView(this);
                    label.setText(fieldName);
                    label.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));

                    EditText input = new EditText(this);
                    input.setLayoutParams(new LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1.0f));
                    input.setText(ObjectUtils.objectToString(defaultValue));
                    input.setTag(fieldName);

                    ImageButton scanButton = new ImageButton(this);
                    scanButton.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    scanButton.setImageResource(R.drawable.ic_scan_small);
                    scanButton.setBackgroundResource(android.R.color.transparent);
                    scanButton.setContentDescription("Scan for " + fieldName);
                    ScanUtils.setupScanButton(this, scanButton, fieldName, fieldScanButtonMap);

                    fieldContainer.addView(label);
                    fieldContainer.addView(input);
                    fieldContainer.addView(scanButton);
                    fieldInputMap.put(fieldName, input);
                }

                inputContainer.addView(fieldContainer);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, getString(R.string.error_setting_up_input_fields, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void setupButtons() {
        // Calculate button width based on screen width
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int buttonCount = 2; // Number of buttons
        int buttonWidth = (int) (((double) screenWidth / buttonCount) * 0.8); // 80% of the divided width

        // Set button widths
        LinearLayout.LayoutParams clearParams = (LinearLayout.LayoutParams) clearButton.getLayoutParams();
        clearParams.width = buttonWidth;
        clearButton.setLayoutParams(clearParams);

        LinearLayout.LayoutParams confirmParams = (LinearLayout.LayoutParams) confirmButton.getLayoutParams();
        confirmParams.width = buttonWidth;
        confirmButton.setLayoutParams(confirmParams);

        // Set button text
        confirmButton.setText("Add");

        // Set click listeners
        clearButton.setOnClickListener(v -> clearAllInputs());
        confirmButton.setOnClickListener(v -> createObject());
    }

    private void clearAllInputs() {
        for (EditText editText : fieldInputMap.values()) {
            editText.setText("");
        }
        for (CheckBox checkBox : fieldCheckBoxMap.values()) {
            checkBox.setChecked(false);
        }
    }

    private void createObject() {
        try {
            // Get the input values
            String label = fieldInputMap.get(LABEL).getText().toString();
            String prikey = fieldInputMap.get(PRI_KEY).getText().toString();
            
            if (label.isEmpty() || prikey.isEmpty()) {
                Toast.makeText(this, "Please input label and private key", SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Get private key bytes
            byte[] prikeyBytes = KeyTools.getPrikey32(prikey);
            if (prikeyBytes == null) {
                Toast.makeText(this, "Invalid private key format", SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Get symmetric key
            byte[] symkey = ConfigureManager.getInstance().getSymkey();
            if (symkey == null) {
                Toast.makeText(this, "Failed to get symmetric key", SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Create new CidInfo instance
            KeyInfo keyInfo = KeyInfo.newKeyInfo(label, prikeyBytes, symkey);
            if (keyInfo.getId() == null) {
                Toast.makeText(this, "Failed to create CidInfo", SafeApplication.TOAST_LASTING).show();
                return;
            }

            // Add to database using MyKeysActivity's method
            IdUtils.addCidInfoToDb(keyInfo, this, keyInfoDB);
            Toast.makeText(this, "KeyInfo added successfully", Toast.LENGTH_LONG).show();
            finish();
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error creating CidInfo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ScanUtils.handleScanResult(this, requestCode, resultCode, data, fieldScanButtonMap, fieldInputMap);
    }
} 