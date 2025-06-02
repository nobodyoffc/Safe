package com.fc.safe.multisign;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.RadioGroup;
import android.widget.RadioButton;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.core.fch.TxCreator;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.fchData.Multisign;
import com.fc.fc_ajdk.utils.DateUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.MultisignManager;
import com.fc.safe.home.MultisignActivity;
import com.fc.safe.ui.IoIconsView;
import com.fc.safe.utils.KeyCardManager;
import com.fc.safe.utils.KeyboardUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.myKeys.ChooseKeyInfoActivity;
import com.fc.safe.home.BaseCryptoActivity;

import java.util.ArrayList;
import java.util.List;

public class CreateMultisignIdActivity extends BaseCryptoActivity {
    private static final String TAG = "CreateMultisignIdActivity";
    private static final int QR_SCAN_KEY_REQUEST_CODE = 1001;
    
    private LinearLayout keyListContainer;
    private KeyCardManager keyCardManager;
    private RadioGroup signerNumberRadioGroup;
    private TextInputEditText keyInput;
    private LinearLayout buttonContainer;
    private Button clearButton;
    private Button addMemberButton;
    private Button createButton;
    private MultisignManager multisignManager;
    private List<KeyInfo> memberList;
    private KeyInfoManager keyInfoManager;
    private KeyInfo selectedKeyInfo;
    private RadioButton[] radioButtons;

    private ActivityResultLauncher<Intent> qrScanLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize managers
        multisignManager = MultisignManager.getInstance(this);
        keyInfoManager = KeyInfoManager.getInstance(this);
        memberList = new ArrayList<>();
        
        // Initialize QR scan launcher
        qrScanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String qrContent = result.getData().getStringExtra("qr_content");
                    if (qrContent != null) {
                        if (KeyTools.isPubkey(qrContent)) {
                            String fid = KeyTools.pubkeyToFchAddr(qrContent);
                            String text = "Pubkey of " + StringUtils.omitMiddle(fid, 13);
                            keyInput.setText(text);
                            keyInput.setTextColor(getResources().getColor(R.color.disabled, getTheme()));
                            keyInput.setEnabled(false);
                            keyInput.setTag(qrContent);
                        } else {
                            showToast("Invalid public key");
                        }
                    }
                }
            }
        );

        // Replace deprecated onBackPressed() with OnBackPressedCallback
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
        
        // Initialize views
        initializeViews();

        // Set up buttons
        setupButtons();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_create_multisign_id;
    }

    @Override
    protected String getActivityTitle() {
        return getString(R.string.create_multisign_fid);
    }

    @Override
    protected void initializeViews() {
        keyListContainer = findViewById(R.id.keyListContainer);
        keyCardManager = new KeyCardManager(this, keyListContainer, null);
        keyCardManager.setOnKeyListChangedListener(updatedKeyInfoList -> {
            memberList.clear();
            memberList.addAll(updatedKeyInfoList);
            updateRadioButtonsState();
        });
        signerNumberRadioGroup = findViewById(R.id.signerNumberRadioGroup);
        View keyView = findViewById(R.id.keyView);
        keyInput = keyView.findViewById(R.id.keyInputWithPeopleAndScanLayout).findViewById(R.id.keyInput);
        keyInput.setHint(R.string.input_the_pubkey);
        buttonContainer = findViewById(R.id.buttonContainer);
        
        // Initialize radio buttons array
        radioButtons = new RadioButton[16];
        for (int i = 0; i < 16; i++) {
            int radioId = getResources().getIdentifier("radio" + (i + 1), "id", getPackageName());
            radioButtons[i] = findViewById(radioId);
            // Set initial color to hint color
            radioButtons[i].setTextColor(getResources().getColor(R.color.disabled, getTheme()));
            radioButtons[i].setButtonTintList(ColorStateList.valueOf(getResources().getColor(R.color.disabled, getTheme())));
            radioButtons[i].setEnabled(false);
            
            final int index = i;
            radioButtons[i].setOnClickListener(v -> {
                // Only allow selection if the number is not larger than memberList size
                if ((index + 1) <= memberList.size()) {
                    // Let the RadioGroup handle the selection
                    signerNumberRadioGroup.check(radioButtons[index].getId());
                } else {
                    // If the button is not selectable, uncheck it
                    radioButtons[index].setChecked(false);
                }
            });
        }
        
        // Set up RadioGroup listener
        signerNumberRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            // This will be called when a radio button is selected
            TimberLogger.d(TAG, "Radio button selected with ID: " + checkedId);
        });
        
        // Set up key input icons
        IoIconsView keyIcons = keyView.findViewById(R.id.peopleAndScanIcons);
        keyIcons.init(this, false, true, true, false);
        keyIcons.setSingleChoice(false);
        keyIcons.setOnPeopleClickListener(isSingleChoice -> {
            showChooseKeyInfoDialog(false);
        });
        keyIcons.setOnScanClickListener(() -> startQrScan(QR_SCAN_KEY_REQUEST_CODE));

        // Set up keyboard hiding
        KeyboardUtils.setupKeyboardHiding(this);
    }

    @Override
    protected void setupButtons() {
        clearButton = findViewById(R.id.clearButton);
        addMemberButton = findViewById(R.id.addMemberButton);
        createButton = findViewById(R.id.createButton);
        
        // Set height for all buttons
        setButtonHeight(clearButton);
        setButtonHeight(addMemberButton);
        setButtonHeight(createButton);
        
        // Set click listeners
        clearButton.setOnClickListener(v -> {
            hideKeyboard();
            clearInputs();
        });
        addMemberButton.setOnClickListener(v -> {
            hideKeyboard();
            handleAddMember();
        });
        createButton.setOnClickListener(v -> {
            hideKeyboard();
            handleCreate();
        });
    }

    @Override
    protected void handleQrScanResult(int requestCode, String qrContent) {
        if (requestCode == QR_SCAN_KEY_REQUEST_CODE && qrContent != null) {
            if (KeyTools.isPubkey(qrContent)) {
                String fid = KeyTools.pubkeyToFchAddr(qrContent);
                String text = "Pubkey of " + StringUtils.omitMiddle(fid, 13);
                keyInput.setText(text);
                keyInput.setTextColor(getResources().getColor(R.color.disabled, getTheme()));
                keyInput.setEnabled(false);
                keyInput.setTag(qrContent);
            } else {
                showToast("Invalid public key");
            }
        }
    }

    private void updateRadioButtonsState() {
        int memberCount = memberList.size();
        for (int i = 0; i < radioButtons.length; i++) {
            boolean isEnabled = (i + 1) <= memberCount;
            radioButtons[i].setEnabled(isEnabled);
            if (!isEnabled) {
                radioButtons[i].setChecked(false);
                radioButtons[i].setTextColor(getResources().getColor(R.color.disabled, getTheme()));
                radioButtons[i].setButtonTintList(ColorStateList.valueOf(getResources().getColor(R.color.disabled, getTheme())));
            } else {
                radioButtons[i].setTextColor(getResources().getColor(R.color.text_color, getTheme()));
                radioButtons[i].setButtonTintList(ColorStateList.valueOf(getResources().getColor(R.color.text_color, getTheme())));
            }
        }
    }

    @Override
    protected void handleChooseKeyResult(Intent data) {
        List<KeyInfo> selectedKeyInfos = ChooseKeyInfoActivity.getSelectedKeyInfo(data, keyInfoManager);
        if (selectedKeyInfos != null && !selectedKeyInfos.isEmpty()) {
            if(memberList.size()+selectedKeyInfos.size()>16){
                showToast(getString(R.string.the_members_can_t_more_than_16));
                return;
            }
            // Add all selected keys to member list
            for (KeyInfo keyInfo : selectedKeyInfos) {
                if (keyInfo.getPubkey() != null) {
                    memberList.add(keyInfo);
                    keyCardManager.addKeyCard(keyInfo);
                }
            }

            // Clear and reset key input
            keyInput.setText("");
            keyInput.setEnabled(true);
            keyInput.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
            selectedKeyInfo = null;

            // Update radio buttons state
            updateRadioButtonsState();

            if (memberList.isEmpty()) {
                showToast("Selected keys have no public keys");
            }
        }
    }

    private void clearInputs() {
        signerNumberRadioGroup.clearCheck();
        keyInput.setText("");
        keyInput.setEnabled(true);
        keyInput.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
        selectedKeyInfo = null;
        memberList.clear();
        keyCardManager.clearAll();
        updateRadioButtonsState();
    }

    private void handleAddMember() {
        if (selectedKeyInfo == null && keyInput.getText() == null) {
            showToast(getString(R.string.please_input_a_public_key));
            return;
        }
        if(memberList.size()>=16){
            showToast(getString(R.string.the_members_can_t_more_than_16));
            return;
        }
        String pubkey = selectedKeyInfo == null ? keyInput.getText().toString() : selectedKeyInfo.getPubkey();

        if (!KeyTools.isPubkey(pubkey)) {
            showToast(getString(R.string.invalid_public_key));
            return;
        }

        // Add the KeyInfo to member list
        KeyInfo newKeyInfo = new KeyInfo(null, pubkey);
        memberList.add(newKeyInfo);
        keyCardManager.addKeyCard(newKeyInfo);
        
        // Clear key input
        keyInput.setText("");
        keyInput.setEnabled(true);
        keyInput.setTextColor(getResources().getColor(R.color.text_color, getTheme()));
        selectedKeyInfo = null;

        // Update radio buttons state
        updateRadioButtonsState();
    }

    private void handleCreate() {
        // Get keys from memberList instead of KeyCardManager
        if (memberList.isEmpty()) {
            showToast(getString(R.string.please_input_required_signer_number));
            return;
        }

        int selectedRadioId = signerNumberRadioGroup.getCheckedRadioButtonId();
        if (selectedRadioId == -1) {
            showToast(getString(R.string.please_input_required_signer_number));
            return;
        }

        int signerNumber = 0;
        for (int i = 0; i < radioButtons.length; i++) {
            if (radioButtons[i].getId() == selectedRadioId) {
                signerNumber = i + 1;
                break;
            }
        }
        
        if (memberList.size() < signerNumber) {
            showToast(getString(R.string.no_enough_members));
            return;
        }
        
        try {
            // Create Multisign
            List<byte[]> pubkeyList = new ArrayList<>();
            for (KeyInfo keyInfo : memberList) {
                if(keyInfo.getPubkey()==null){
                    showToast("Failed to get the pubkey of "+keyInfo.getId());
                    return;
                }
                pubkeyList.add(Hex.fromHex(keyInfo.getPubkey()));
            }
            
            Multisign multisign = TxCreator.createMultisign(pubkeyList, signerNumber);
            if(multisign ==null){
                showToast("Failed to create multisign ID.");
                return;
            }
            // Set save time before adding to database
            multisign.setSaveTime(DateUtils.longToTime(System.currentTimeMillis(), DateUtils.TO_MINUTE));
            
            // Show label dialog
            showLabelDialog(multisign);
        } catch (Exception e) {
            String errorMessage = "Failed to create multisign ID: " + e.getMessage();
            showToast(errorMessage);
            TimberLogger.e(TAG, errorMessage);
        }
    }

    private void showLabelDialog(Multisign multisign) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Label for Multisign ID");
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_multisign_label, null);
        builder.setView(dialogView);
        
        TextView idTextView = dialogView.findViewById(R.id.id_text);
        EditText labelInput = dialogView.findViewById(R.id.label_input);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        
        idTextView.setText(multisign.getId());
        
        builder.setPositiveButton("OK", (dialog, which) -> {
            String label = labelInput.getText().toString().trim();
            if (!label.isEmpty()) {
                multisign.setLabel(label);
            }
            
            try {
                // Add to database
                multisignManager.getMultisignDB().put(multisign.getId(), multisign);
                multisignManager.commit();
                
                dialog.dismiss();
                showToast("Multisign ID created successfully");
                MultisignActivity.setNeedsRefresh(true);
                finish();
            } catch (Exception e) {
                String errorMessage = "Failed to save multisign ID: " + e.getMessage();
                TimberLogger.e(TAG, errorMessage);
                showToast(errorMessage);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Hide keyboard when dialog is shown
        if(dialog.getWindow()!=null)
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void setButtonHeight(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = (int) getResources().getDimension(R.dimen.button_height);
        view.setLayoutParams(params);
    }
} 