package com.fc.safe.myKeys;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.fc.fc_ajdk.data.fcData.FcSubject;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.initiate.PasswordVerificationDialog;
import com.fc.safe.utils.KeyCardManager;
import com.fc.safe.utils.KeyLabelManager;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class FindNiceKeysActivity extends AppCompatActivity {
    private static final String TAG = "FindNiceKeys";
    private static final long PASSWORD_VERIFICATION_THRESHOLD = 30000; // 30 seconds in milliseconds
    
    private KeyCardManager keyCardManager;
    private KeyLabelManager keyLabelManager;
    private Map<String, byte[]> avatarCache = new HashMap<>();
    private PowerManager.WakeLock wakeLock;
    
    private TextView timerText;
    private TextInputEditText matchInput;
    private Button saveButton;
    private Button stopButton;
    private Button startButton;
    
    private ExecutorService executorService;
    private Handler mainHandler;
    private AtomicBoolean isFinding = new AtomicBoolean(false);
    private AtomicLong startTime = new AtomicLong(0);
    private Runnable timerRunnable;
    private boolean hasExceededThreshold = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_nice_keys);

        // Initialize WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Freer:FindNiceKeysWakeLock");

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("Find Nice Keys");
        toolbar.setNavigationOnClickListener(v -> {
            if (isFinding.get()) {
                Toast.makeText(this, R.string.stop_finding_progress_first, Toast.LENGTH_SHORT).show();
                return;
            }
            if (hasExceededThreshold) {
                showPasswordVerificationDialog();
            } else {
                finish();
            }
        });

        // Initialize views
        initializeViews();
        
        // Initialize managers
        LinearLayout keyListContainer = findViewById(R.id.keyListContainer);
        keyCardManager = new KeyCardManager(this, keyListContainer, false);
        keyLabelManager = new KeyLabelManager(this);
        
        // Setup buttons
        setupButtons();
        
        // Setup timer
        setupTimer();
        
        // Initialize executor service
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // Setup keyboard hiding on outside click
        setupKeyboardHiding();
    }

    private void initializeViews() {
        timerText = findViewById(R.id.timerText);
        matchInput = findViewById(R.id.matchInput);
        saveButton = findViewById(R.id.saveButton);
        stopButton = findViewById(R.id.stopButton);
        startButton = findViewById(R.id.startButton);
        
        // Disable buttons initially
        saveButton.setEnabled(false);
        stopButton.setEnabled(false);
        startButton.setEnabled(false);
        
        // Enable start button when match input has text
        matchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(Editable s) {
                startButton.setEnabled(!s.toString().isEmpty() && !isFinding.get());
            }
        });
    }

    private void setupButtons() {
        View.OnClickListener buttonClickListener = v -> {
            hideKeyboard();
            if (v.getId() == R.id.saveButton) {
                List<KeyInfo> selectedKeys = keyCardManager.getSelectedKeys();
                keyLabelManager.saveSelectedKeys(selectedKeys);
            } else if (v.getId() == R.id.stopButton) {
                stopFinding();
            } else if (v.getId() == R.id.startButton) {
                startFinding();
            }
        };

        saveButton.setOnClickListener(buttonClickListener);
        stopButton.setOnClickListener(buttonClickListener);
        startButton.setOnClickListener(buttonClickListener);
    }

    private void setupTimer() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isFinding.get()) {
                    long elapsedTime = System.currentTimeMillis() - startTime.get();
                    updateTimerText(elapsedTime);
                    
                    // Check if we've exceeded the threshold
                    if (elapsedTime > PASSWORD_VERIFICATION_THRESHOLD && !hasExceededThreshold) {
                        hasExceededThreshold = true;
                        TimberLogger.i(TAG, "Activity has exceeded time threshold");
                    }
                    
                    mainHandler.postDelayed(this, 1000);
                }
            }
        };
    }

    private void updateTimerText(long elapsedTime) {
        long seconds = (elapsedTime / 1000) % 60;
        long minutes = (elapsedTime / (1000 * 60)) % 60;
        long hours = (elapsedTime / (1000 * 60 * 60));
        
        String timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        timerText.setText(timeString);
    }

    private void startFinding() {
        if (isFinding.get()) {
            TimberLogger.i(TAG, "Finding already in progress, ignoring start request");
            return;
        }
        
        // If we've exceeded the threshold, show password verification dialog
        if (hasExceededThreshold) {
            showPasswordVerificationDialog(new PasswordVerificationDialog.PasswordVerificationListener() {
                @Override
                public void onPasswordVerified(byte[] passwordBytes) {
                    if (ConfigureManager.getInstance().verifyPasswordName(passwordBytes)) {
                        hasExceededThreshold = false; // Reset the flag after successful verification
                        // Now that password is verified, start the finding process
                        startFindingAfterVerification();
                    } else {
                        Toast.makeText(FindNiceKeysActivity.this, R.string.incorrect_password, Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onVerificationCancelled() {
                    Toast.makeText(FindNiceKeysActivity.this, R.string.password_verification_cancelled, Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }
        
        startFindingAfterVerification();
    }

    private void startFindingAfterVerification() {
        String matchChars = matchInput.getText().toString().toLowerCase();
        if (matchChars.isEmpty()) {
            TimberLogger.i(TAG, "Match characters empty, cannot start finding");
            Toast.makeText(this, R.string.please_enter_matching_characters , Toast.LENGTH_SHORT).show();
            return;
        }
        
        TimberLogger.i(TAG, "Starting find operation with match pattern: %s", matchChars);
        isFinding.set(true);
        startTime.set(System.currentTimeMillis());
        mainHandler.post(timerRunnable);
        
        // Acquire WakeLock
        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
            TimberLogger.i(TAG, "WakeLock acquired");
        }
        
        saveButton.setEnabled(false);
        stopButton.setEnabled(true);
        startButton.setEnabled(false);
        
        executorService.execute(() -> findNiceKeys(matchChars));
    }

    private void stopFinding() {
        TimberLogger.i(TAG, "Stopping find operation. Final key count: %d", keyCardManager.getKeyInfoList().size());
        isFinding.set(false);
        
        // Release WakeLock if held
        if (wakeLock.isHeld()) {
            wakeLock.release();
            TimberLogger.i(TAG, "WakeLock released");
        }
        
        saveButton.setEnabled(true);
        stopButton.setEnabled(false);
        startButton.setEnabled(true);
    }

    private void findNiceKeys(String matchChars) {
        TimberLogger.i(TAG, "Starting to find nice keys with match pattern: %s", matchChars);
        int currentKeyCount = keyCardManager.getKeyInfoList().size();
        int targetKeyCount = currentKeyCount + 10;
        
        while (isFinding.get() && keyCardManager.getKeyInfoList().size() < targetKeyCount) {
            FcSubject fcSubject = FcSubject.genPrikeyAndFid();
            String newFid = fcSubject.getId();
            byte[] prikeyBytes = fcSubject.getPrikeyBytes();
            
            if (newFid.toLowerCase().endsWith(matchChars)) {
                TimberLogger.i(TAG, "Found matching key with FID: %s", newFid);
                KeyInfo newKeyInfo = new KeyInfo("", prikeyBytes, ConfigureManager.getInstance().getSymkey());
                
                // Generate and cache avatar bytes
                try {
                    byte[] avatarBytes = AvatarMaker.createAvatar(newFid, this);
                    avatarCache.put(newFid, avatarBytes);
                    TimberLogger.i(TAG, "Generated and cached avatar for FID: %s", newFid);
                } catch (Exception e) {
                    TimberLogger.e(TAG, "Error generating avatar for FID %s: %s", newFid, e.getMessage());
                }
                
                mainHandler.post(() -> {
                    TimberLogger.i(TAG, "Adding new key to list. Current size: %d", keyCardManager.getKeyInfoList().size());
                    keyCardManager.addKeyCard(newKeyInfo);
                });
            }
        }
        
        mainHandler.post(() -> {
            stopFinding();
        });
        TimberLogger.i(TAG, "Find nice keys operation completed");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isFinding.set(false);
        
        // Release WakeLock if held
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            TimberLogger.i(TAG, "WakeLock released in onDestroy");
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
        // Clear the avatar cache
        avatarCache.clear();
    }

    private void setupKeyboardHiding() {
        View rootView = findViewById(android.R.id.content);
        rootView.setOnClickListener(v -> hideKeyboard());
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void finish() {
        if (hasExceededThreshold) {
            // Show password verification dialog
            showPasswordVerificationDialog();
        } else {
            super.finish();
        }
    }

    public void showPasswordVerificationDialog() {
        PasswordVerificationDialog dialog = new PasswordVerificationDialog(this, new PasswordVerificationDialog.PasswordVerificationListener() {
            @Override
            public void onPasswordVerified(byte[] passwordBytes) {
                if (ConfigureManager.getInstance().verifyPasswordName(passwordBytes)) {
                    hasExceededThreshold = false; // Reset the flag after successful verification
                    FindNiceKeysActivity.super.finish();
                } else {
                    Toast.makeText(FindNiceKeysActivity.this, R.string.incorrect_password, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onVerificationCancelled() {
                // Keep the activity open if verification is cancelled
                Toast.makeText(FindNiceKeysActivity.this, R.string.password_verification_cancelled, Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show();
    }

    private void showPasswordVerificationDialog(PasswordVerificationDialog.PasswordVerificationListener listener) {
        PasswordVerificationDialog dialog = new PasswordVerificationDialog(this, listener);
        dialog.show();
    }

    @Override
    public void onBackPressed() {
        if (isFinding.get()) {
            Toast.makeText(this, R.string.stop_finding_progress_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if (hasExceededThreshold) {
            showPasswordVerificationDialog();
        } else {
            super.onBackPressed();
        }
    }
} 