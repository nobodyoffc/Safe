package com.fc.safe;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.fc_ajdk.ui.UIManager;
import com.fc.safe.db.DatabaseManager;
import com.fc.safe.home.HomeActivity;
import com.fc.safe.initiate.CheckPasswordActivity;
import com.fc.safe.initiate.CreatePasswordActivity;
import com.fc.safe.initiate.ConfigureManager;

import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "CryptoSign";
    private DatabaseManager dbManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TimberLogger is now initialized in SafeApplication
        setContentView(R.layout.activity_main);
        
        setupEdgeToEdge();
//        setupUI();
        initializeDatabase();
        initiate();
    }

    private void setupEdgeToEdge() {
        try {
            EdgeToEdge.enable(this);
        } catch (Exception ex) {
            TimberLogger.e(TAG, "Failed to enable EdgeToEdge: " + ex.getMessage(), ex);
        }
    }

//    private void setupUI() {
//        UIManager uiManager = new UIManager(this, this);
//        uiManager.setDisplayContainer(findViewById(R.id.main));
//
//        try {
//            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//                return insets;
//            });
//        } catch (Exception ex) {
//            TimberLogger.e(TAG, "Failed to set window insets listener: " + ex.getMessage(), ex);
//        }
//    }

    private void initializeDatabase() {
        dbManager = DatabaseManager.getInstance(this);
    }

    private void initiate() {
        ActivityResultLauncher<Intent> passwordLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                this::handlePasswordResult
        );
        
        Intent intent;
        
        // Check if there are any Configure objects in ConfigureManager
        if (ConfigureManager.getInstance().isConfigEmpty(this)) {
            intent = new Intent(this, CreatePasswordActivity.class);
        } else {
            intent = new Intent(this, CheckPasswordActivity.class);
        }
        
        passwordLauncher.launch(intent);
    }

    private void handlePasswordResult(androidx.activity.result.ActivityResult result) {
        String activityName = result.getData() != null ? 
            Objects.requireNonNull(result.getData().getComponent()).getClassName() : "Unknown";
        
        if (result.getResultCode() == RESULT_OK) {
            Configure configure = ConfigureManager.getInstance().getConfigure();
            if (configure == null) {
                handleError("Configure object not found in ConfigureManager");
                return;
            }
            
            launchHomeActivity();
        } else {
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

    /**
     * Clears all data
     */
    private void clearDatabase() {
        if (dbManager != null) {
            dbManager.clearAllDatabases();
            ConfigureManager.getInstance().clearConfig(this);
        }
    }
}