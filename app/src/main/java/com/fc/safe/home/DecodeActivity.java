package com.fc.safe.home;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;

import com.fc.fc_ajdk.core.crypto.Base58;
import com.fc.fc_ajdk.utils.Base32;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Base64;
import java.util.Objects;

public class DecodeActivity extends BaseCryptoActivity {
    private static final String TAG = "DecodeActivity";
    private static final int QR_SCAN_TEXT_REQUEST_CODE = 1001;
    private TextInputEditText inputText;
    private TextInputEditText resultText;
    private RadioGroup optionContainer;
    private Button copyButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Replace deprecated onBackPressed() with OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
        
        // Initialize views
        initializeViews();

        // Set up bottom buttons
        setupButtons();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_decode;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.decode);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        inputText.setText(qrContent);
    }

    protected void initializeViews() {
        inputText = findViewById(R.id.inputView).findViewById(R.id.textInput);
        inputText.setHint(R.string.input_the_text_to_be_decoded);

        resultText = findViewById(R.id.resultView).findViewById(R.id.textBoxWithMakeQrLayout);
        resultText.setHint(R.string.result);

        optionContainer = findViewById(R.id.optionContainer);
        
        // Set up radio button listeners to ensure only one is selected at a time
        setupRadioButtonListeners();

        // Setup icons using shared methods
        setupTextIcons(R.id.inputView, R.id.scanIcon, QR_SCAN_TEXT_REQUEST_CODE);
        setupResultIcons(R.id.resultView, R.id.makeQrIcon, ()->handleQrGeneration(Objects.requireNonNull(resultText.getText()).toString()));
    }
    
    private void setupRadioButtonListeners() {
        // Get all radio buttons
        RadioButton hexButton = findViewById(R.id.radioHex);
        RadioButton base64Button = findViewById(R.id.radioBase64);
        RadioButton base58Button = findViewById(R.id.radioBase58);
        RadioButton utf8Button = findViewById(R.id.radioUtf8);
        RadioButton base32Button = findViewById(R.id.radioBase32);
        RadioButton unknownButton = findViewById(R.id.radioUnknown);
        
        RadioButton[] allButtons = {hexButton, base64Button, base58Button, utf8Button, base32Button, unknownButton};
        
        // Set up listeners for all buttons
        for (RadioButton button : allButtons) {
            button.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    // Uncheck all other buttons
                    for (RadioButton otherButton : allButtons) {
                        if (otherButton != buttonView) {
                            otherButton.setChecked(false);
                        }
                    }
                }
            });
        }
    }

    protected void setupButtons() {
        Button clearButton = findViewById(R.id.clearButton);
        setupButton(clearButton, v -> {
            inputText.setText("");
            resultText.setText("");
        });

        copyButton = findViewById(R.id.copyButton);
        copyButton.setEnabled(false); // Initially disabled
        setupButton(copyButton, v -> {
            String result = resultText.getText() != null ? resultText.getText().toString() : "";
            if (!result.isEmpty()) {
                copyToClipboard(result, "Decoded Text");
            }
        });

        Button decodeButton = findViewById(R.id.decodeButton);
        setupButton(decodeButton, v -> {
            String input = inputText.getText() != null ? inputText.getText().toString() : "";
            if (input.isEmpty()) {
                showToast("Please enter text to decode");
                return;
            }
            decodeInput(input);
        });
    }

    /**
     * Finds the selected radio button in a row
     * @param row The LinearLayout containing radio buttons
     * @return The selected RadioButton or null if none is selected
     */
    private RadioButton findSelectedRadioButton(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof RadioButton && ((RadioButton) child).isChecked()) {
                return (RadioButton) child;
            }
        }
        return null;
    }

    /**
     * Attempts to decode the input using the specified method
     * @param input The input text to decode
     * @param method The decoding method to use
     * @return A DecodeResult containing the decoded bytes and method name, or null if decoding failed
     */
    private DecodeResult tryDecode(String input, String method) {
        try {
            byte[] decodedBytes = switch (method) {
                case "Hex" -> Hex.fromHex(input);
                case "Base58" -> Base58.decode(input);
                case "Base64" -> Base64.getDecoder().decode(input);
                case "Base32" -> Base32.fromBase32(input);
                case "UTF-8" -> input.getBytes();
                default -> null;
            };

            if (decodedBytes != null) {
                return new DecodeResult(decodedBytes, method);
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error decoding " + method + ": " + e.getMessage());
        }
        
        return null;
    }

    private void decodeInput(String inputText) {
        resultText.setText("");
        try {
            // Get the selected radio buttons from both rows
            LinearLayout firstRow = (LinearLayout) optionContainer.getChildAt(0);
            LinearLayout secondRow = (LinearLayout) optionContainer.getChildAt(1);
            
            RadioButton selectedFirstRowButton = findSelectedRadioButton(firstRow);
            RadioButton selectedSecondRowButton = findSelectedRadioButton(secondRow);
            
            DecodeResult result = null;
            
            // Check which encoding type is selected in the first row
            if (selectedFirstRowButton != null) {
                if (selectedFirstRowButton.getId() == R.id.radioHex) {
                    result = tryDecode(inputText, "Hex");
                } else if (selectedFirstRowButton.getId() == R.id.radioBase58) {
                    result = tryDecode(inputText, "Base58");
                } else if (selectedFirstRowButton.getId() == R.id.radioBase64) {
                    result = tryDecode(inputText, "Base64");
                }
            }
            
            // Check which encoding type is selected in the second row
            if (result == null && selectedSecondRowButton != null) {
                if (selectedSecondRowButton.getId() == R.id.radioUtf8) {
                    result = tryDecode(inputText, "UTF-8");
                } else if (selectedSecondRowButton.getId() == R.id.radioBase32) {
                    result = tryDecode(inputText, "Base32");
                } else if (selectedSecondRowButton.getId() == R.id.radioUnknown) {
                    // Try different decoding methods in sequence
                    result = tryDecode(inputText, "Hex");
                    if (result == null && Hex.isHexString(inputText)) {
                        result = tryDecode(inputText, "Hex");
                    }
                    
                    if (result == null && Base58.isBase58Encoded(inputText)) {
                        result = tryDecode(inputText, "Base58");
                    }
                    
                    if (result == null) {
                        result = tryDecode(inputText, "Base64");
                    }
                    
                    if (result == null) {
                        result = tryDecode(inputText, "Base32");
                    }
                    
                    if (result == null) {
                        result = tryDecode(inputText, "UTF-8");
                    }
                }
            }

            if (result != null) {
                displayResults(result.bytes, result.method);
                copyButton.setEnabled(true);
            } else {
                resultText.setText(R.string.failed_decode);
                copyButton.setEnabled(false);
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error in decodeInput: " + e.getMessage(), e);
            showToast("Error: " + e.getMessage());
            copyButton.setEnabled(false);
        }
    }

    /**
         * Helper class to store decode results
         */
        private record DecodeResult(byte[] bytes, String method) {
    }

    private void displayResults(byte[] bytes, String decodeMethod) {
        StringBuilder resultBuilder = new StringBuilder();
        resultBuilder.append("Decoded using ").append(decodeMethod).append(":\n\n");

        // Define the encoding formats to display
        String[] formats = {"Hex", "Base58", "Base64", "Base32", "UTF-8"};
        String[] results = new String[formats.length];
        
        // Generate results for each format
        results[0] = Hex.toHex(bytes);
        results[1] = Base58.encode(bytes);
        results[2] = Base64.getEncoder().encodeToString(bytes);
        results[3] = Base32.toBase32(bytes);
        results[4] = new String(bytes);
        
        // Add each result to the builder
        for (int i = 0; i < formats.length; i++) {
            resultBuilder.append(formats[i]).append(": ").append(results[i]).append("\n\n");
        }

        // Create clickable spans for each result
        SpannableString spannableString = new SpannableString(resultBuilder.toString());
        
        // Make each result clickable
        for (int i = 0; i < formats.length; i++) {
            String format = formats[i];
            String result = results[i];
            
            int start = resultBuilder.indexOf(format + ": ") + format.length() + 2;
            int end = resultBuilder.indexOf("\n\n", start);
            makeTextClickable(spannableString, start, end, result);
        }

        resultText.setText(spannableString);
        resultText.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void makeTextClickable(SpannableString spannableString, int start, int end, final String textToCopy) {
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                copyToClipboard(textToCopy, "Decoded Text");
            }
        };
        spannableString.setSpan(clickableSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public void copyToClipboard(String text, String label) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        showToast("Copied to clipboard");
    }
} 