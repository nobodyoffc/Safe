package com.fc.safe.home;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.ui.IoIconsView;
import com.fc.safe.utils.FileUtils;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class HashActivity extends BaseCryptoActivity {
    private static final String TAG = "HashActivity";
    private static final int QR_SCAN_REQUEST_CODE = 1001;

    private TextInputEditText textInput;
    private TextInputEditText resultText;
    private RadioGroup optionContainer;
    private Button copyButton;
    private LinearLayout buttonContainer;
    private Uri currentFileUri;
    private boolean isFileMode = false;
    private CheckBox asHexCheckBox;
    
    // Activity result launcher
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize file picker launcher
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleFileSelection(uri);
                    }
                }
            }
        );

        // Initialize views
        initializeViews();

        // Set up bottom buttons
        setupButtons();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_hash;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.hash);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        textInput.setText(qrContent);
    }

    protected void setupButtons() {
        Button clearButton = findViewById(R.id.clearButton);
        setupButton(clearButton, v -> {
            textInput.setText("");
            resultText.setText("");
            resetFileMode();
            copyButton.setEnabled(false);
        });

        copyButton = findViewById(R.id.copyButton);
        copyButton.setEnabled(false); // Initially disabled
        setupButton(copyButton, v -> {
            String result = resultText.getText() != null ? resultText.getText().toString() : "";
            if (!TextUtils.isEmpty(result)) {
                copyToClipboard(result, "Hash");
            }
        });

        Button hashButton = findViewById(R.id.hashButton);
        setupButton(hashButton, v -> {
            resultText.setText("");
            copyButton.setEnabled(false);
            String text = textInput.getText() != null ? textInput.getText().toString() : "";
            if (text.isEmpty()) {
                showToast("Please input text");
                return;
            }
            hashInput();
        });
    }

    protected void initializeViews() {
        textInput = findViewById(R.id.textView).findViewById(R.id.textInput);
        textInput.setHint(R.string.input_text_to_be_hashed);
        resultText = findViewById(R.id.resultView).findViewById(R.id.textBoxWithMakeQrLayout);
        resultText.setHint(R.string.result);
        optionContainer = findViewById(R.id.optionContainer);
        buttonContainer = findViewById(R.id.buttonContainer);
        asHexCheckBox = findViewById(R.id.asHexCheckBox);
        TimberLogger.d(TAG, "initializeViews: initial checkbox state: " + asHexCheckBox.isChecked());
        
        // Set up checkbox listener
        asHexCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            TimberLogger.d(TAG, "Checkbox state changed to: " + isChecked + ", stack trace: " + Log.getStackTraceString(new Throwable()));
        });
        
        // Set up radio button listeners to ensure only one is selected at a time
        setupRadioButtonListeners();
        
        setupIoIconsView(R.id.textView, R.id.scanIcon, false, false, true, true,
                null, null, () -> startQrScan(QR_SCAN_REQUEST_CODE), this::openFilePicker);

        setupIoIconsView(R.id.resultView, R.id.makeQrIcon, true, false, false, false,
                () -> {
                    if(resultText.getText()==null){
                        showToast("Please input text");
                        return;
                    }
                    String content = resultText.getText().toString();
                    if (!TextUtils.isEmpty(content)) {
                        IoIconsView.launchQrGenerator(this, content);
                    } else {
                        showToast("No content to generate QR code");
                    }
                }, null, null, null);
    }
    
    private void setupRadioButtonListeners() {
        // Get the two rows
        LinearLayout firstRow = (LinearLayout) optionContainer.getChildAt(0);
        LinearLayout secondRow = (LinearLayout) optionContainer.getChildAt(1);
        
        // Get all radio buttons
        RadioButton[] firstRowButtons = new RadioButton[3];
        RadioButton[] secondRowButtons = new RadioButton[3];
        
        for (int i = 0; i < 3; i++) {
            firstRowButtons[i] = (RadioButton) firstRow.getChildAt(i);
            secondRowButtons[i] = (RadioButton) secondRow.getChildAt(i);
        }
        
        // Set up listeners for all radio buttons
        setupRadioButtonRowListeners(firstRowButtons, secondRowButtons);
        setupRadioButtonRowListeners(secondRowButtons, firstRowButtons);
    }
    
    private void setupRadioButtonRowListeners(RadioButton[] currentRowButtons, RadioButton[] otherRowButtons) {
        for (RadioButton button : currentRowButtons) {
            button.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Uncheck all other buttons
                    for (RadioButton otherButton : currentRowButtons) {
                        if (otherButton != buttonView) {
                            otherButton.setChecked(false);
                        }
                    }
                    for (RadioButton otherButton : otherRowButtons) {
                        otherButton.setChecked(false);
                    }
                }
            });
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void handleFileSelection(Uri uri) {
        String fileName = FileUtils.getFileNameFromUri(this, uri);
        
        if (fileName != null) {
            currentFileUri = uri;
            isFileMode = true;
            
            // Display file name in input box
            textInput.setText(fileName);
            textInput.setTextColor(Color.GRAY);
            
            // Disable non-file compatible hash options
            disableNonFileCompatibleOptions();
        } else {
            showToast("Failed to load file");
        }
    }

    private void disableNonFileCompatibleOptions() {
        // Disable all radio buttons in the first row except SHA256 and SHA256x2
        LinearLayout firstRow = (LinearLayout) optionContainer.getChildAt(0);
        LinearLayout secondRow = (LinearLayout) optionContainer.getChildAt(1);
        
        // Disable all radio buttons in both rows
        setRadioButtonsEnabled(firstRow, false);
        setRadioButtonsEnabled(secondRow, false);
        
        // Enable only SHA256 in the first row
        RadioButton sha256Radio = firstRow.findViewById(R.id.radioSha256);
        RadioButton sha256x2Radio = firstRow.findViewById(R.id.radioSha256x2);
        if (sha256Radio != null) {
            sha256Radio.setEnabled(true);
            sha256x2Radio.setEnabled(true);
            sha256x2Radio.setChecked(true);
        }
    }

    private void setRadioButtonsEnabled(ViewGroup viewGroup, boolean enabled) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof RadioButton) {
                child.setEnabled(enabled);
            }
        }
    }

    private void resetFileMode() {
        isFileMode = false;
        currentFileUri = null;
        textInput.setEnabled(true);
        textInput.setTextColor(getResources().getColor(R.color.text_color, null));
        
        // Enable all radio buttons
        enableAllRadioButtons();
    }

    private void enableAllRadioButtons() {
        // Enable all radio buttons in both rows
        LinearLayout firstRow = (LinearLayout) optionContainer.getChildAt(0);
        LinearLayout secondRow = (LinearLayout) optionContainer.getChildAt(1);
        
        setRadioButtonsEnabled(firstRow, true);
        setRadioButtonsEnabled(secondRow, true);
        
        // Set default selection
        RadioButton sha256Radio = firstRow.findViewById(R.id.radioSha256);
        if (sha256Radio != null) {
            sha256Radio.setChecked(true);
        }
    }

    private void setResult(String result, boolean success) {
        if (success && result != null) {
            resultText.setText(result);
            copyButton.setEnabled(true);
        } else {
            resultText.setText("");
            copyButton.setEnabled(false);
            // Only show toast if there's an actual error message
            if (result != null && !result.isEmpty()) {
                showToast(result);
            }
        }
    }

    private void hashInput() {
        // Always clear the result first
        setResult("", false);

        Editable text = textInput.getText();
        if(text==null){
            showToast("Please input text");
            return;
        }
        String inputText = text.toString();
        if (inputText.isEmpty()) {
            showToast("Please enter text to hash");
            return;
        }

        try {
            String hashResult;
            byte[] inputBytes;

            // Check if we should parse the input as hex
            if (asHexCheckBox.isChecked()) {
                try {
                    // Try to parse the input as hex
                    inputBytes = Hex.fromHex(inputText);
                } catch (Exception e) {
                    // If parsing fails, use the original input
                    setResult("Input is not a valid hex string, using as text", false);
                    asHexCheckBox.setChecked(false);
                    return;
                }
            } else {
                // Use the original input
                inputBytes = inputText.getBytes();
            }

            if (isFileMode) {
                // Handle file hashing using content URI
                try (InputStream inputStream = getContentResolver().openInputStream(currentFileUri)) {
                    if (inputStream == null) {
                        setResult("Failed to open file", false);
                        return;
                    }
                    
                    // Get the selected hash algorithm
                    int selectedId = getSelectedHashAlgorithmId();
                    
                    // Read the file content as raw bytes
                    int available = inputStream.available();
                    TimberLogger.d(TAG, "Available bytes in stream: " + available);
                    
                    byte[] fileBytes = new byte[available];
                    int read = inputStream.read(fileBytes);
                    if(read!=available)TimberLogger.d(TAG, "Failed to read all Available bytes in stream");
                    // Apply the selected hash algorithm
                    hashResult = applyHashAlgorithm(selectedId, fileBytes);
                } catch (IOException e) {
                    TimberLogger.e(TAG, "Error reading file: " + e.getMessage(), e);
                    setResult("Failed to read file: " + e.getMessage(), false);
                    return;
                }
            } else {
                // Handle text hashing
                // Get the selected hash algorithm
                int selectedId = getSelectedHashAlgorithmId();
                
                // Apply the selected hash algorithm
                hashResult = applyHashAlgorithm(selectedId, inputBytes);
            }

            // Set the result
            setResult(hashResult, hashResult != null);
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error in hashInput: " + e.getMessage(), e);
            setResult("Hash failed: " + e.getMessage(), false);
        }
    }
    
    private int getSelectedHashAlgorithmId() {
        LinearLayout firstRow = (LinearLayout) optionContainer.getChildAt(0);
        LinearLayout secondRow = (LinearLayout) optionContainer.getChildAt(1);
        
        // Check first row options
        if (((RadioButton)firstRow.findViewById(R.id.radioSha256)).isChecked()) {
            return R.id.radioSha256;
        } else if (((RadioButton)firstRow.findViewById(R.id.radioSha256x2)).isChecked()) {
            return R.id.radioSha256x2;
        } else if (((RadioButton)firstRow.findViewById(R.id.radioMd5)).isChecked()) {
            return R.id.radioMd5;
        }
        
        // Check second row options
        if (((RadioButton)secondRow.findViewById(R.id.radioSha1)).isChecked()) {
            return R.id.radioSha1;
        } else if (((RadioButton)secondRow.findViewById(R.id.radioSha3)).isChecked()) {
            return R.id.radioSha3;
        } else if (((RadioButton)secondRow.findViewById(R.id.radioRipemd160)).isChecked()) {
            return R.id.radioRipemd160;
        }
        
        // Default to SHA256
        return R.id.radioSha256;
    }
    
    private String applyHashAlgorithm(int selectedId, byte[] inputBytes) {
        try {
            if (selectedId == R.id.radioSha256) {
                return Hex.toHex(Hash.sha256(inputBytes));
            } else if (selectedId == R.id.radioSha256x2) {
                return Hex.toHex(Hash.sha256x2(inputBytes));
            } else if (selectedId == R.id.radioMd5) {
                return Hex.toHex(Objects.requireNonNull(Hash.md5(inputBytes)));
            } else if (selectedId == R.id.radioSha1) {
                return Hex.toHex(Objects.requireNonNull(Hash.sha1(inputBytes)));
            } else if (selectedId == R.id.radioSha3) {
                return Hash.sha3String(Hex.toHex(inputBytes));
            } else if (selectedId == R.id.radioRipemd160) {
                return Hex.toHex(Hash.Ripemd160(inputBytes));
            }
            // Default to SHA256
            return Hex.toHex(Hash.sha256(inputBytes));
        }catch (Exception e){
            return null;
        }
    }
} 