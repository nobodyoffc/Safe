package com.fc.safe;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

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

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    private DatabaseManager dbManager;
    private ActivityResultLauncher<Intent> passwordLauncher;
    private boolean hasLaunchedPasswordActivity = false;

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
        dbManager = DatabaseManager.getInstance(this);
    }

    private void initiate() {
        // Check if there are any Configure objects in ConfigureManager
        if (ConfigureManager.getInstance().isConfigEmpty(this)) {
            // Show security guidelines dialog before creating password
            RemindDialog dialog = new RemindDialog(this, getString(R.string.safe_v_by_no1_nrc7)+"\n\n"+getString(R.string.offline_notation));
            dialog.setOnDismissListener(dialogInterface -> {
                if (!hasLaunchedPasswordActivity) {
                    hasLaunchedPasswordActivity = true;
                    Intent createPasswordIntent = new Intent(this, CreatePasswordActivity.class);
                    passwordLauncher.launch(createPasswordIntent);
                }
            });
            dialog.show();
        } else {
            Intent checkPasswordIntent = new Intent(this, CheckPasswordActivity.class);
            passwordLauncher.launch(checkPasswordIntent);
        }
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
        Toast.makeText(this, getString(R.string.failed_to_initiate), Toast.LENGTH_SHORT).show();
    }

    private void launchHomeActivity() {
        try {
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
            finish(); // Close MainActivity
        } catch (Exception ex) {
            TimberLogger.e(TAG, "Error launching HomeActivity: " + ex.getMessage(), ex);
            Toast.makeText(this, getString(R.string.error_launching_homeactivity) + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

}