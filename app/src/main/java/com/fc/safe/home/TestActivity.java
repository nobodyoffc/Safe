package com.fc.safe.home;

import static com.fc.safe.utils.IdUtils.AVATAR_MAP;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.fcData.FcSubject;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.ui.SingleInputActivity;
import com.fc.safe.utils.IconCreator;
import com.fc.safe.utils.IdUtils;
import com.fc.safe.initiate.ConfigureManager;

import android.content.Intent;
import com.fc.safe.ui.UserConfirmDialog;
import com.fc.safe.ui.WaitingDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import android.graphics.Color;

public class TestActivity extends AppCompatActivity {

    private static final String TAG = "TestActivity";
    private ImageView imageView;
    private Button generateButton;
    private Button saveButton;
    private Button generateKeysButton;
    private Button checkAvatarButton;
//    private Button generateAppIconButton;
//    private Button generateRoundIconButton;
    private Button showInputActivityButton;
    private Button showUserConfirmDialogButton;
    private Button testSquareIconButton;
    private Button testRoundIconButton;
    private Button testSquareIconFilesButton;
    private Button testRoundIconFilesButton;
    private String activityInputResult;
    private byte[] currentImageData;
    private byte[] currentImageBytes;
    private Dialog avatarDialog;
    private ImageView avatarImageView;
    private ImageView smallAvatarImageView;
    private TextView idTextView;
    private TextView countTextView;
    private Button stopButton;
    private Button nextButton;
    private List<KeyInfo> avatarKeyInfos;
    private int currentAvatarIndex = 0;
    private LocalDB<KeyInfo>keyInfoDB;
    private static final int REQUEST_CODE_SINGLE_INPUT = 2001;
    private WaitingDialog waitingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Circle Image Test");
        }

        // Initialize views
        imageView = findViewById(R.id.imageView);
        generateButton = findViewById(R.id.generateButton);
        saveButton = findViewById(R.id.saveButton);
        generateKeysButton = findViewById(R.id.generateKeysButton);
        checkAvatarButton = findViewById(R.id.checkAvatarButton);
//        generateAppIconButton = findViewById(R.id.generateAppIconButton);
//        generateRoundIconButton = findViewById(R.id.generateRoundIconButton);
        showInputActivityButton = findViewById(R.id.showInputDialogButton);
        showUserConfirmDialogButton = findViewById(R.id.showUserConfirmDialogButton);
        testSquareIconButton = findViewById(R.id.testSquareIconButton);
        testRoundIconButton = findViewById(R.id.testRoundIconButton);
        testSquareIconFilesButton = findViewById(R.id.testSquareIconFilesButton);
        testRoundIconFilesButton = findViewById(R.id.testRoundIconFilesButton);

        // Set up button click listeners
        generateButton.setOnClickListener(v -> generateNewImage());
        saveButton.setOnClickListener(v -> saveImage());
        generateKeysButton.setOnClickListener(v -> generateAndSaveKeys());
        checkAvatarButton.setOnClickListener(v -> checkAvatars());
//        generateAppIconButton.setOnClickListener(v -> generateAppIcon());
//        generateRoundIconButton.setOnClickListener(v -> generateRoundIcon());
        showInputActivityButton.setOnClickListener(v -> showSingleInputActivity());
        showUserConfirmDialogButton.setOnClickListener(v -> showUserConfirmDialog());
        testSquareIconButton.setOnClickListener(v -> testSquareIcon());
        testRoundIconButton.setOnClickListener(v -> testRoundIcon());
        testSquareIconFilesButton.setOnClickListener(v -> testSquareIconFiles());
        testRoundIconFilesButton.setOnClickListener(v -> testRoundIconFiles());

        // Initialize KeyInfoManager
        KeyInfoManager keyInfoManager = KeyInfoManager.getInstance(this);
        keyInfoDB = keyInfoManager.getKeyInfoDB();

        // Initialize avatar dialog
        setupAvatarDialog();

        // Generate initial image
        generateNewImage();
    }

    private void setupAvatarDialog() {
        avatarDialog = new Dialog(this);
        avatarDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        avatarDialog.setContentView(R.layout.dialog_avatar);
        avatarDialog.setCancelable(false);

        avatarImageView = avatarDialog.findViewById(R.id.avatar_view);
        smallAvatarImageView = avatarDialog.findViewById(R.id.avatar_view);
        idTextView = avatarDialog.findViewById(R.id.id_text);
        countTextView = avatarDialog.findViewById(R.id.countTextView);
        stopButton = avatarDialog.findViewById(R.id.stopButton);
        nextButton = avatarDialog.findViewById(R.id.nextButton);

        stopButton.setOnClickListener(v -> stopAvatarChecking());
        nextButton.setOnClickListener(v -> showNextAvatar());
    }

    private void stopAvatarChecking() {
        if (avatarDialog != null && avatarDialog.isShowing()) {
            avatarDialog.dismiss();
            Toast.makeText(this, "Avatar checking stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAvatarDialog(byte[] avatarBytes, KeyInfo keyInfo) {
        if (avatarBytes != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
            avatarImageView.setImageBitmap(bitmap);
            smallAvatarImageView.setImageBitmap(bitmap);

            // Update ID text
            idTextView.setText(keyInfo.getId());

            // Update count text
            updateCountText();

            avatarDialog.show();
        }
    }

    private void updateCountText() {
        if (avatarKeyInfos != null && !avatarKeyInfos.isEmpty()) {
            int totalCount = avatarKeyInfos.size();
            int currentCount = currentAvatarIndex + 1; // 1-based index for display
            countTextView.setText(currentCount + "/" + totalCount);
        } else {
            countTextView.setText("0/0");
        }
    }

    private void showNextAvatar() {
        if (avatarKeyInfos != null && !avatarKeyInfos.isEmpty()) {
            currentAvatarIndex = (currentAvatarIndex + 1) % avatarKeyInfos.size();
            KeyInfo nextKeyInfo = avatarKeyInfos.get(currentAvatarIndex);

            try {
                byte[] avatarBytes = keyInfoDB
                        .getFromMap(AVATAR_MAP, nextKeyInfo.getId());
                if (avatarBytes != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                    avatarImageView.setImageBitmap(bitmap);
                    smallAvatarImageView.setImageBitmap(bitmap);
                } else {
                    // If avatar doesn't exist, create it
                    AvatarMaker.init(this);
                    avatarBytes = AvatarMaker.makeAvatar(nextKeyInfo.getId());
                    if (avatarBytes != null) {
                        keyInfoDB
                                .putInMap(AVATAR_MAP, nextKeyInfo.getId(), avatarBytes);
                        Bitmap bitmap = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
                        avatarImageView.setImageBitmap(bitmap);
                        smallAvatarImageView.setImageBitmap(bitmap);
                    }
                }

                // Update ID text
                idTextView.setText(nextKeyInfo.getId());

                // Update count text
                updateCountText();

            } catch (Exception e) {
                TimberLogger.e(TAG, "Error showing next avatar: %s", e.getMessage());
                avatarDialog.dismiss();
            }
        } else {
            avatarDialog.dismiss();
        }
    }

    private void generateNewImage() {
        try {
            // Generate 32 random bytes
            currentImageData = new byte[32];
            new Random().nextBytes(currentImageData);

            // Generate the circle image
            currentImageBytes = IdUtils.makeCircleImageFromData(currentImageData);

            // Convert byte array to Bitmap and display
            Bitmap bitmap = BitmapFactory.decodeByteArray(currentImageBytes, 0, currentImageBytes.length);
            imageView.setImageBitmap(bitmap);

            // Show data info
            String dataInfo = "Generated image from 32 bytes of data";
            Toast.makeText(this, dataInfo, Toast.LENGTH_SHORT).show();
            TimberLogger.i(TAG, dataInfo);
        } catch (IOException e) {
            String errorMsg = "Error generating image: " + e.getMessage();
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            TimberLogger.e(TAG, errorMsg, e);
        }
    }

    private void saveImage() {
        if (currentImageBytes == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Here you would typically save the image to a file or database
            // For this test, we'll just show a success message
            Toast.makeText(this, "Image saved successfully", Toast.LENGTH_SHORT).show();
            TimberLogger.i(TAG, "Image saved successfully");
        } catch (Exception e) {
            String errorMsg = "Error saving image: " + e.getMessage();
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            TimberLogger.e(TAG, errorMsg, e);
        }
    }

    private void showWaitingDialog(String hint) {
        if (waitingDialog == null) {
            waitingDialog = new WaitingDialog(this, hint);
        } else {
            waitingDialog.setHint(hint);
        }
        waitingDialog.show();
    }

    private void dismissWaitingDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) {
            waitingDialog.dismiss();
        }
    }

    private void generateAndSaveKeys() {
        showWaitingDialog("Generating and saving keys, please wait...");
        new Thread(() -> {
            try {
                int successCount = 0;
                for (int i = 1; i <= 10; i++) {
                    FcSubject fcSubject = FcSubject.createNew();
                    byte[] prikeyBytes = fcSubject.getPrikeyBytes();
                    String label = "key" + i;
                    byte[] symkey = ConfigureManager.getInstance().getSymkey();
                    KeyInfo keyInfo = KeyInfo.newKeyInfo(label, prikeyBytes, symkey);
                    IdUtils.addCidInfoToDb(keyInfo, this, keyInfoDB);
                    successCount++;
                }
                String successMsg = "Successfully generated and saved " + successCount + " keys";
                runOnUiThread(() -> {
                    Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show();
                    TimberLogger.i(TAG, successMsg);
                    dismissWaitingDialog();
                });
            } catch (Exception e) {
                String errorMsg = "Error generating keys: " + e.getMessage();
                runOnUiThread(() -> {
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
                    TimberLogger.e(TAG, errorMsg, e);
                    dismissWaitingDialog();
                });
            }
        }).start();
    }

    private void checkAvatars() {
        try {
            // Get all CidInfos from the database
            Map<String, KeyInfo> cidInfoMap = keyInfoDB.getAll();

            int successCount = 0;
            int totalCount = cidInfoMap.size();
            avatarKeyInfos = new ArrayList<>();

            // Check each CidInfo for an avatar
            for (String id : cidInfoMap.keySet()) {
                KeyInfo keyInfo = cidInfoMap.get(id);
                try {
                    // Try to load the avatar from the database
                    byte[] avatarBytes;
                    try {
                        avatarBytes = keyInfoDB.getFromMap(AVATAR_MAP, keyInfo.getId());
                    } catch (Exception e) {
                        TimberLogger.e(TAG, "Error getting avatar for %s: %s", keyInfo.getId(), e.getMessage());
                        avatarBytes = null;
                    }

                    // If avatar doesn't exist, create it
                    if (avatarBytes == null) {
                        // Initialize AvatarMaker if not already initialized
                        AvatarMaker.init(this);

                        // Create avatar using the CidInfo ID
                        avatarBytes = AvatarMaker.makeAvatar(keyInfo.getId());

                        // Save avatar to database
                        if (avatarBytes != null) {
                            keyInfoDB.putInMap(AVATAR_MAP, keyInfo.getId(), avatarBytes);
                            successCount++;
                            avatarKeyInfos.add(keyInfo);
                            TimberLogger.i(TAG, "Created and saved avatar for %s", keyInfo.getId());
                        }
                    } else {
                        successCount++;
                        avatarKeyInfos.add(keyInfo);
                        TimberLogger.i(TAG, "Avatar already exists for %s", keyInfo.getId());
                    }
                } catch (Exception e) {
                    TimberLogger.e(TAG, "Error processing avatar for %s: %s", keyInfo.getId(), e.getMessage());
                }
            }

            String successMsg = "Processed " + successCount + " out of " + totalCount + " avatars";
            Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show();
            TimberLogger.i(TAG, successMsg);

            // Show the first avatar if any were found
            if (!avatarKeyInfos.isEmpty()) {
                currentAvatarIndex = 0;
                KeyInfo firstKeyInfo = avatarKeyInfos.get(0);
                byte[] firstAvatarBytes = keyInfoDB.getFromMap(AVATAR_MAP, firstKeyInfo.getId());
                showAvatarDialog(firstAvatarBytes, firstKeyInfo);
            }
        } catch (Exception e) {
            String errorMsg = "Error checking avatars: " + e.getMessage();
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            TimberLogger.e(TAG, errorMsg, e);
        }
    }
//
//    private void generateAppIcon() {
//        try {
//            // Generate icon with text "Safe"
//            Bitmap iconBitmap = IconGenerator.generateIcon("Safe", 512, this);
//
//            // Save the icon to mipmap directories
//            saveIconToMipmap(iconBitmap, false);
//
//            Toast.makeText(this, "App icon generated successfully", Toast.LENGTH_SHORT).show();
//            TimberLogger.i(TAG, "App icon generated successfully");
//        } catch (Exception e) {
//            String errorMsg = "Error generating app icon: " + e.getMessage();
//            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
//            TimberLogger.e(TAG, errorMsg, e);
//        }
//    }

//    private void generateRoundIcon() {
//        try {
//            // Generate round icon with text "Safe"
//            Bitmap iconBitmap = IconGenerator.generateRoundIcon("Safe", 512, this);
//
//            // Save the icon to mipmap directories
//            saveIconToMipmap(iconBitmap, true);
//
//            // Display the generated icon
//            imageView.setImageBitmap(iconBitmap);
//
//            Toast.makeText(this, "Round app icon generated successfully", Toast.LENGTH_SHORT).show();
//            TimberLogger.i(TAG, "Round app icon generated successfully");
//        } catch (Exception e) {
//            String errorMsg = "Error generating round app icon: " + e.getMessage();
//            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
//            TimberLogger.e(TAG, errorMsg, e);
//        }
//    }

    private void saveIconToMipmap(Bitmap iconBitmap, boolean isRound) {
        try {
            // Define mipmap directories and sizes
            int[] sizes = {48, 72, 96, 144, 192};
            String[] directories = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};

            for (int i = 0; i < sizes.length; i++) {
                // Create resized bitmap for each density
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(iconBitmap, sizes[i], sizes[i], true);

                // Save to PNG file
                String fileName = isRound ? "ic_launcher_round.png" : "ic_launcher.png";
                String directory = getFilesDir().getParent() + "/res/" + directories[i];

                // Create directory if it doesn't exist
                File dir = new File(directory);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                // Save bitmap to file
                File file = new File(dir, fileName);
                FileOutputStream out = new FileOutputStream(file);
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.close();

                TimberLogger.i(TAG, "Saved icon to %s", file.getAbsolutePath());
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error saving icon to mipmap: %s", e.getMessage());
            throw new RuntimeException("Failed to save icon to mipmap", e);
        }
    }

    private void showSingleInputActivity() {
        Intent intent = new Intent(this, SingleInputActivity.class);
        intent.putExtra(SingleInputActivity.EXTRA_PROMOTE, "Input password");
        intent.putExtra(SingleInputActivity.EXTRA_INPUT_TYPE, "password");
        startActivityForResult(intent, REQUEST_CODE_SINGLE_INPUT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SINGLE_INPUT && resultCode == RESULT_OK && data != null) {
            String input = data.getStringExtra(SingleInputActivity.EXTRA_RESULT);
            if (input != null) {
                Toast.makeText(this, "Password: " + input, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showUserConfirmDialog() {
        UserConfirmDialog dialog = new UserConfirmDialog(this, "Are you sure you want to perform this action?", choice -> {
            String msg;
            switch (choice) {
                case STOP:
                    msg = "User chose: Stop";
                    break;
                case NO:
                    msg = "User chose: No";
                    break;
                case YES:
                    msg = "User chose: Yes";
                    break;
                default:
                    msg = "Unknown choice";
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private void testSquareIcon() {
        try {
            // Create a square icon with test text
            Bitmap icon = IconCreator.createSquareIcon(
                "Test",
                512,
                50,
                getColor(R.color.text_color),
                getColor(R.color.colorAccent)
            );
            
            // Display the icon
            imageView.setImageBitmap(icon);
            
            Toast.makeText(this, "Square icon created successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            String errorMsg = "Error creating square icon: " + e.getMessage();
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            TimberLogger.e(TAG, errorMsg, e);
        }
    }

    private void testRoundIcon() {
        try {
            // Create a round icon with test text
            Bitmap icon = IconCreator.createRoundIcon(
                "Test The Round Icon",
                512,
                50,
                Color.WHITE,
                Color.BLUE
            );
            
            // Display the icon
            imageView.setImageBitmap(icon);
            
            Toast.makeText(this, "Round icon created successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            String errorMsg = "Error creating round icon: " + e.getMessage();
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            TimberLogger.e(TAG, errorMsg, e);
        }
    }

    private void testSquareIconFiles() {
        try {
            // Create square icon files
            String path = getFilesDir().getParent() + "/res";
            List<String> savedPaths = IconCreator.createSquareIconFiles(
                "Safe",
                10,
                path,
                getColor(R.color.text_color),
                getColor(R.color.colorAccent)
            );
            
            // Show paths in toast
            StringBuilder message = new StringBuilder("Square icon files created at:\n");
            for (String filePath : savedPaths) {
                message.append(filePath).append("\n");
            }
            Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            String errorMsg = "Error creating square icon files: " + e.getMessage();
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            TimberLogger.e(TAG, errorMsg, e);
        }
    }

    private void testRoundIconFiles() {
        try {
            // Create round icon files
            String path = getFilesDir().getParent() + "/res";
            List<String> savedPaths = IconCreator.createRoundIconFiles(
                "Safe",
                10,
                path,
                getColor(R.color.text_color),
                getColor(R.color.colorAccent)
            );
            
            // Show paths in toast
            StringBuilder message = new StringBuilder("Round icon files created at:\n");
            for (String filePath : savedPaths) {
                message.append(filePath).append("\n");
            }
            Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            String errorMsg = "Error creating round icon files: " + e.getMessage();
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show();
            TimberLogger.e(TAG, errorMsg, e);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}