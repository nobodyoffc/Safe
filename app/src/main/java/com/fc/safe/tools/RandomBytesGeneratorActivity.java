package com.fc.safe.tools;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.RadioGroup;

import com.fc.fc_ajdk.core.crypto.Base58;
import com.fc.fc_ajdk.utils.Base32;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.safe.R;
import com.fc.safe.home.BaseCryptoActivity;
import com.fc.safe.ui.IoIconsView;
import com.google.android.material.textfield.TextInputEditText;

import java.math.BigInteger;

public class RandomBytesGeneratorActivity extends BaseCryptoActivity {
    private TextInputEditText bytesInput;
    private RadioGroup bytesOptionGroup;
    private RadioGroup formatOptionGroup;
    private IoIconsView resultIcons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize views
        initializeViews();

        // Set up buttons
        setupButtons();

        // Set up radio groups
        setupRadioGroups();

        // Set up result icons
        setupResultIcons();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_random_bytes_generator;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.random_bytes_generator);
    }

    protected void initializeViews() {
        bytesInput = findViewById(R.id.bytesInput);
        resultTextView = findViewById(R.id.textBoxWithMakeQrLayout);
        resultTextView.setHint(R.string.result);
        bytesOptionGroup = findViewById(R.id.bytesOptionGroup);
        formatOptionGroup = findViewById(R.id.formatOptionGroup);
        copyButton = findViewById(R.id.copyButton);
        resultIcons = findViewById(R.id.makeQrIcon);
    }

    protected void setupButtons() {
        Button clearButton = findViewById(R.id.clearButton);
        Button newButton = findViewById(R.id.newButton);

        clearButton.setOnClickListener(v -> clearInputs());
        copyButton.setOnClickListener(v -> copyConvertedDataToClipboard());
        newButton.setOnClickListener(v -> generateRandomNumber());

        // Initially disable copy button
        copyButton.setEnabled(false);
    }

    private void setupRadioGroups() {
        bytesOptionGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int bytes = 32; // default
            if (checkedId == R.id.bytes1Option) bytes = 1;
            else if (checkedId == R.id.bytes4Option) bytes = 4;
            else if (checkedId == R.id.bytes8Option) bytes = 8;
            else if (checkedId == R.id.bytes16Option) bytes = 16;

            bytesInput.setText(String.valueOf(bytes));
        });
    }

    private void setupResultIcons() {
        resultIcons.init(this, true, false, false, false);
        resultIcons.setOnMakeQrClickListener(this::handleQrGeneration);
    }

    private void handleQrGeneration() {
        if (resultTextView.getText() == null) {
            showToast(getString(R.string.please_generate_a_random_number_first));
            return;
        }
        String content = resultTextView.getText().toString();
        if (!TextUtils.isEmpty(content)) {
            IoIconsView.launchQrGenerator(this, content);
        } else {
            showToast(getString(R.string.no_content_to_generate_qr_code));
        }
    }

    private void clearInputs() {
        bytesInput.setText("");
        resultTextView.setText("");
        copyButton.setEnabled(false);
        bytesOptionGroup.check(R.id.bytes32Option);
    }

    private String formatBytes(byte[] bytes, int formatId) {
        if (formatId == R.id.formatIntegerOption) {
            BigInteger number = new BigInteger(1, bytes);
            return number.toString();
        } else if (formatId == R.id.formatBase58Option) {
            return Base58.encode(bytes);
        } else if (formatId == R.id.formatBase32Option) {
            return Base32.toBase32(
                    bytes);
        } else {
            // Default to Hex
            return Hex.toHex(bytes);
        }
    }

    private void generateRandomNumber() {
        String bytesStr = bytesInput.getText() != null ? bytesInput.getText().toString() : "";
        int bytesLength;
        
        if (bytesStr.isEmpty()) {
            bytesLength = 32; // Default to 32 bytesLength if no input
        } else {
            try {
                bytesLength = Integer.parseInt(bytesStr);
                if (bytesLength <= 0) {
                    showToast(getString(R.string.number_of_bytes_length_must_be_positive));
                    return;
                }
            } catch (NumberFormatException e) {
                showToast(getString(R.string.invalid_number_format));
                return;
            }
        }

        byte[] randomBytes = BytesUtils.getRandomBytes(bytesLength);
        String result = formatBytes(randomBytes, formatOptionGroup.getCheckedRadioButtonId());
        updateResultText(result);
        copyButton.setEnabled(true);
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        // Not used in this activity
    }
} 