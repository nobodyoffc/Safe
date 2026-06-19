package com.fc.safe;

import android.content.Intent;
import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.db.DatabaseManager;
import com.fc.safe.home.HomeActivity;
import com.fc.safe.initiate.CheckPasswordActivity;
import com.fc.safe.initiate.CreatePasswordActivity;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.ui.RemindDialog;

import java.util.Objects;
import com.fc.safe.utils.ToastUtils;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    private ActivityResultLauncher<Intent> passwordLauncher;
    private boolean hasLaunchedPasswordActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TimberLogger is now initialized in SafeApplication
        setContentView(R.layout.activity_main);
        
        // Register the activity result launcher
        passwordLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handlePasswordResult
        );
        
        setupEdgeToEdge();
        initializeDatabase();
        
        // Only initiate if this is a fresh start (not returning from another activity)
        if (savedInstanceState == null) {
            initiate();
        }
    }

    private void setupEdgeToEdge() {
        try {
            EdgeToEdge.enable(this);
        } catch (Exception ex) {
            TimberLogger.e(TAG, "Failed to enable EdgeToEdge: " + ex.getMessage(), ex);
        }
    }


    private void initializeDatabase() {
        DatabaseManager.getInstance(this);
    }

    private void initiate() {

        // Show security guidelines dialog before creating password
            RemindDialog dialog = new RemindDialog(this, getString(R.string.safe_v_by_no1_nrc7)+"\n\n"+getString(R.string.offline_notation));
            dialog.setOnDismissListener(dialogInterface -> {
                if (!hasLaunchedPasswordActivity) {
                    hasLaunchedPasswordActivity = true;
                    Intent checkPasswordIntent = new Intent(this, CheckPasswordActivity.class);
                    passwordLauncher.launch(checkPasswordIntent);
                }
            });
            dialog.show();
    }

    private void handlePasswordResult(androidx.activity.result.ActivityResult result) {
        if (result.getResultCode() == RESULT_OK) {
            // Password creation/verification was successful, proceed to home
            launchHomeActivity();
        } else {
            String activityName = result.getData() != null ? 
                Objects.requireNonNull(result.getData().getComponent()).getClassName() : "Unknown";
            handleError(activityName + " failed with result code: " + result.getResultCode());
        }
    }

    private void handleError(String message) {
        TimberLogger.e(TAG, message);
        ToastUtils.showError(this, getString(R.string.failed_to_initiate));
    }

    private void launchHomeActivity() {
        try {
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
            finish(); // Close MainActivity
        } catch (Exception ex) {
            TimberLogger.e(TAG, "Error launching HomeActivity: " + ex.getMessage(), ex);
            ToastUtils.showError(this, getString(R.string.error_launching_homeactivity) + ex.getMessage());
        }
    }

}