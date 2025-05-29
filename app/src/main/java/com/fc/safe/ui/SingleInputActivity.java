package com.fc.safe.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.TextView;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.google.android.material.textfield.TextInputEditText;

public class SingleInputActivity extends BaseCryptoActivity {
    public static final String EXTRA_PROMOTE = "promote";
    public static final String EXTRA_INPUT_TYPE = "inputType";
    public static final String EXTRA_RESULT = "result";

    private TextView promoteTextView;
    private TextInputEditText inputText;
    private Button copyButton;
    private Button clearButton;
    private Button doneButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Views and buttons are initialized in initializeViews and setupButtons
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_single_input;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.input);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        inputText.setText(qrContent);
    }

    @Override
    protected void initializeViews() {
        promoteTextView = findViewById(R.id.promoteTextView);
        inputText = findViewById(R.id.inputView).findViewById(R.id.textInput);

        // Get parameters from intent
        Intent intent = getIntent();
        String promote = intent.getStringExtra(EXTRA_PROMOTE);
        String inputTypeStr = intent.getStringExtra(EXTRA_INPUT_TYPE);
        if (promote != null) {
            promoteTextView.setText(promote);
        }
        if (inputTypeStr != null) {
            try {
                int inputType = Integer.parseInt(inputTypeStr);
                inputText.setInputType(inputType);
            } catch (NumberFormatException e) {
                // fallback: treat as InputType string constant
                switch (inputTypeStr) {
                    case "text":
                        inputText.setInputType(InputType.TYPE_CLASS_TEXT);
                        break;
                    case "number":
                        inputText.setInputType(InputType.TYPE_CLASS_NUMBER);
                        break;
                    case "password":
                        inputText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        break;
                    default:
                        inputText.setInputType(InputType.TYPE_CLASS_TEXT);
                }
            }
        }
        // Setup scan icon
        setupTextIcons(R.id.inputView, R.id.scanIcon, 1001);
    }

    @Override
    protected void setupButtons() {
        clearButton = findViewById(R.id.clearButton);
        copyButton = findViewById(R.id.copyButton);
        doneButton = findViewById(R.id.doneButton);

        setupButton(clearButton, v -> inputText.setText(""));
        setupButton(copyButton, v -> {
            String text = inputText.getText() != null ? inputText.getText().toString() : "";
            if (!text.isEmpty()) {
                copyToClipboard(text, "Input Text");
            }
        });
        setupButton(doneButton, v -> {
            String text = inputText.getText() != null ? inputText.getText().toString() : "";
            Intent resultIntent = new Intent();
            resultIntent.putExtra(EXTRA_RESULT, text);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
} 