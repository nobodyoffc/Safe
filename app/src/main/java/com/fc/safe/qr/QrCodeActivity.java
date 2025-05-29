package com.fc.safe.qr;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.utils.QRCodeGenerator;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;

import timber.log.Timber;

@OptIn(markerClass = ExperimentalGetImage.class)
public class QrCodeActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ?
            new String[] { Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE } :
            new String[] { Manifest.permission.CAMERA };
    private static final String EXTRA_IS_RETURN_STRING = "is_return_string";

    private PreviewView previewView;
    private View scanAreaOverlay;
    private EditText qrContentEditText;
    private View scanButton;
    private View makeButton;
    private View clearButton;
    private View copyButton;
    private View galleryButton;
    private View scanNotification;
    private boolean isReturnString;

    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private boolean isScanningEnabled = false;
    private boolean isCameraInitialized = false;

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        scanQRFromImage(imageUri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            
            // Get the isReturnString parameter
            isReturnString = getIntent().getBooleanExtra(EXTRA_IS_RETURN_STRING, false);
            
            // Force status bar color
            getWindow().setStatusBarColor(getResources().getColor(R.color.black, getTheme()));
            getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
            
            setContentView(R.layout.activity_qr_code);

            // Setup toolbar
            Toolbar toolbar = findViewById(R.id.toolbar);
            if (toolbar == null) {
                Timber.e("Toolbar not found in layout");
                showError("Failed to initialize: Toolbar not found");
                finish();
                return;
            }
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(getString(R.string.menu_qr_code));
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> {
                if (isReturnString && qrContentEditText != null && !qrContentEditText.getText().toString().isEmpty()) {
                    returnResult();
                } else {
                    finish();
                }
            });

            // Add back press callback
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    if (isReturnString && qrContentEditText != null && !qrContentEditText.getText().toString().isEmpty()) {
                        returnResult();
                    } else {
                        finish();
                    }
                }
            });

            // Initialize views
            previewView = findViewById(R.id.previewView);
            scanAreaOverlay = findViewById(R.id.scanAreaOverlay);
            scanNotification = findViewById(R.id.scanNotification);
            
            if (previewView == null || scanAreaOverlay == null || scanNotification == null) {
                Timber.e("Required views not found in layout");
                showError("Failed to initialize: Required views not found");
                finish();
                return;
            }

            cameraExecutor = Executors.newSingleThreadExecutor();

            // Check and request camera permissions
            if (allPermissionsGranted()) {
                initializeCamera();
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
            }

            // Set up UI elements
            initializeViews();
            setupListeners();
            
            Timber.i("QrCodeActivity initialized successfully");
        } catch (Exception e) {
            Timber.e(e, "Failed to initialize QrCodeActivity: %s", e.getMessage());
            showError("Failed to initialize: " + e.getMessage());
            finish();
        }
    }

    private void initializeViews() {
        qrContentEditText = findViewById(R.id.qrContentEditText);
        scanButton = findViewById(R.id.scanButtonContainer);
        makeButton = findViewById(R.id.makeButtonContainer);
        clearButton = findViewById(R.id.clearButtonContainer);
        copyButton = findViewById(R.id.copyButtonContainer);
        galleryButton = findViewById(R.id.galleryButton);

        // Update copy button icon and text if in return mode
        if (isReturnString) {
            try {
                ImageView copyButtonIcon = findViewById(R.id.copyButtonIcon);
                TextView copyButtonText = findViewById(R.id.copyButtonText);
                if (copyButtonIcon != null) {
                    copyButtonIcon.setImageResource(R.drawable.ic_return);
                }
                if (copyButtonText != null) {
                    copyButtonText.setText(R.string.return_text);
                }
            } catch (Exception e) {
                Timber.e("Error updating copy button: %s", e.getMessage());
            }
        }

        // Add click listener to root layout to dismiss keyboard
        View rootLayout = findViewById(R.id.main);
        if (rootLayout != null) {
            rootLayout.setOnClickListener(v -> {
                if (qrContentEditText != null && qrContentEditText.hasFocus()) {
                    qrContentEditText.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(qrContentEditText.getWindowToken(), 0);
                    }
                }
            });
        }
    }

    private void setupListeners() {
        if (scanButton != null) {
            scanButton.setOnClickListener(v -> toggleScanning());
        }
        if (makeButton != null) {
            makeButton.setOnClickListener(v -> generateQRCode());
        }
        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                if (qrContentEditText != null) {
                    qrContentEditText.setText("");
                }
            });
        }
        if (copyButton != null) {
            copyButton.setOnClickListener(v -> {
                if (isReturnString) {
                    returnResult();
                } else {
                    copyToClipboard();
                }
            });
        }
        if (galleryButton != null) {
            galleryButton.setOnClickListener(v -> openGallery());
        }

        if (qrContentEditText != null) {
            qrContentEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (qrContentEditText.hasFocus()) {
                        stopScanning();
                    }
                }
            });
        }
    }

    private void initializeCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                isCameraInitialized = true;
                Timber.i("Camera initialized successfully");
            } catch (ExecutionException | InterruptedException e) {
                Timber.e(e, "Error initializing camera: %s", e.getMessage());
                showError(getString(R.string.error_initializing_camera) + e.getMessage());
                isCameraInitialized = false;
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleScanning() {
        if (!isScanningEnabled) {
            if (!isCameraInitialized) {
                showError(getString(R.string.error_initializing_camera));
                return;
            }
            if (allPermissionsGranted()) {
                startScanning();
            } else {
                requestPermissions();
            }
        } else {
            stopScanning();
        }
    }

    private void requestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            startScanning();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startScanning();
            } else {
                showPermissionRationale();
            }
        }
    }

    private void showPermissionRationale() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.permission_camera_rationale)
                .setPositiveButton(R.string.ok, (dialog, which) -> requestPermissions())
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show();
    }

    private void startScanning() {
        if (cameraProvider == null || !isCameraInitialized) {
            showError(getString(R.string.error_initializing_camera));
            return;
        }

        try {
            Preview preview = new Preview.Builder()
                    .setTargetRotation(previewView.getDisplay().getRotation())
                    .build();

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            // Configure image analysis for better QR code detection
            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetRotation(previewView.getDisplay().getRotation())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build();

            imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

            try {
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                isScanningEnabled = true;
                previewView.setVisibility(View.VISIBLE);
                scanAreaOverlay.setVisibility(View.GONE);
                scanNotification.setVisibility(View.GONE);
                
            } catch (Exception e) {
                TimberLogger.e("QR-SCAN", "Error starting camera: %s", e.getMessage());
                showError(getString(R.string.error_initializing_camera) + e.getMessage());
                isScanningEnabled = false;
            }
        } catch (Exception e) {
            TimberLogger.e("QR-SCAN", "Error configuring camera: %s", e.getMessage());
            showError(getString(R.string.error_initializing_camera) + e.getMessage());
            isScanningEnabled = false;
        }
    }

    private void stopScanning() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        isScanningEnabled = false;
        previewView.setVisibility(View.GONE);
        scanAreaOverlay.setVisibility(View.VISIBLE);
        scanNotification.setVisibility(View.VISIBLE);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy imageProxy) {
        try {
            if (imageProxy.getImage() == null) {
                TimberLogger.e("QR-SCAN", "ImageProxy image is null");
                imageProxy.close();
                return;
            }

            // Convert ImageProxy to Bitmap
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) {
                TimberLogger.e("QR-SCAN", "Failed to convert ImageProxy to Bitmap");
                imageProxy.close();
                return;
            }

            TimberLogger.d("QR-SCAN", "Processing bitmap: %dx%d", bitmap.getWidth(), bitmap.getHeight());

            // Create a binary bitmap for ZXing
            com.google.zxing.BinaryBitmap binaryBitmap = new com.google.zxing.BinaryBitmap(
                new com.google.zxing.common.HybridBinarizer(
                    new com.google.zxing.LuminanceSource(bitmap.getWidth(), bitmap.getHeight()) {
                        @Override
                        public byte[] getMatrix() {
                            byte[] luminances = new byte[bitmap.getWidth() * bitmap.getHeight()];
                            int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
                            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                            
                            for (int i = 0; i < bitmap.getHeight(); i++) {
                                for (int j = 0; j < bitmap.getWidth(); j++) {
                                    int pixel = pixels[i * bitmap.getWidth() + j];
                                    int r = (pixel >> 16) & 0xff;
                                    int g = (pixel >> 8) & 0xff;
                                    int b = pixel & 0xff;
                                    luminances[i * bitmap.getWidth() + j] = (byte) ((r * 0.299f + g * 0.587f + b * 0.114f));
                                }
                            }
                            return luminances;
                        }

                        @Override
                        public byte[] getRow(int y, byte[] row) {
                            if (row == null || row.length < bitmap.getWidth()) {
                                row = new byte[bitmap.getWidth()];
                            }
                            int[] pixels = new int[bitmap.getWidth()];
                            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, y, bitmap.getWidth(), 1);
                            
                            for (int x = 0; x < bitmap.getWidth(); x++) {
                                int pixel = pixels[x];
                                int r = (pixel >> 16) & 0xff;
                                int g = (pixel >> 8) & 0xff;
                                int b = pixel & 0xff;
                                row[x] = (byte) ((r * 0.299f + g * 0.587f + b * 0.114f));
                            }
                            return row;
                        }

                        @Override
                        public boolean isCropSupported() {
                            return false;
                        }

                        @Override
                        public boolean isRotateSupported() {
                            return false;
                        }
                    }
                )
            );

            try {
                // Try to decode the QR code with multiple attempts
                com.google.zxing.Result result = null;
                try {
                    TimberLogger.d("QR-SCAN", "Attempting first QR code scan");
                    result = new com.google.zxing.MultiFormatReader().decode(binaryBitmap);
                } catch (com.google.zxing.NotFoundException e) {
                    TimberLogger.d("QR-SCAN", "First scan attempt failed, trying with hints");
                    // If first attempt fails, try with different hints
                    Map<com.google.zxing.DecodeHintType, Object> hints = new HashMap<>();
                    hints.put(com.google.zxing.DecodeHintType.TRY_HARDER, Boolean.TRUE);
                    hints.put(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS, 
                            java.util.Arrays.asList(com.google.zxing.BarcodeFormat.QR_CODE));
                    result = new com.google.zxing.MultiFormatReader().decode(binaryBitmap, hints);
                }

                if (result != null && result.getText() != null) {
                    com.google.zxing.Result finalResult = result;
                    runOnUiThread(() -> {
                        String currentText = qrContentEditText.getText().toString();
                        String newText = currentText + finalResult.getText();
                        qrContentEditText.setText(newText);
                        stopScanning();
                        Toast.makeText(QrCodeActivity.this, getText(R.string.done), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (com.google.zxing.NotFoundException e) {
                // QR code not found, continue scanning
                TimberLogger.d("QR-SCAN", "No QR code found in frame");
            } catch (Exception e) {
                TimberLogger.e("QR-SCAN", "Error scanning QR code: %s", e.getMessage());
            } finally {
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
                imageProxy.close();
            }

        } catch (Exception e) {
            TimberLogger.e("QR-SCAN", "Unexpected error in analyzeImage: %s", e.getMessage());
            imageProxy.close();
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            android.media.Image image = imageProxy.getImage();
            if (image == null) {
                TimberLogger.e("QR-SCAN", "Image is null in imageProxyToBitmap");
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int rotation = imageProxy.getImageInfo().getRotationDegrees();

            TimberLogger.d("QR-SCAN", "Converting image: %dx%d, rotation: %d", width, height, rotation);

            // Get the YUV planes
            android.media.Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length < 3) {
                TimberLogger.e("QR-SCAN", "Invalid YUV planes: %s", planes == null ? "null" : "length=" + planes.length);
                return null;
            }

            // Get the Y plane
            android.media.Image.Plane yPlane = planes[0];
            android.media.Image.Plane uPlane = planes[1];
            android.media.Image.Plane vPlane = planes[2];

            // Get the buffers
            java.nio.ByteBuffer yBuffer = yPlane.getBuffer();
            java.nio.ByteBuffer uBuffer = uPlane.getBuffer();
            java.nio.ByteBuffer vBuffer = vPlane.getBuffer();

            if (yBuffer == null || uBuffer == null || vBuffer == null) {
                TimberLogger.e("QR-SCAN", "One or more YUV buffers are null");
                return null;
            }

            // Get the row stride and pixel stride
            int yStride = yPlane.getRowStride();
            int uvStride = uPlane.getRowStride();
            int yPixelStride = yPlane.getPixelStride();
            int uvPixelStride = uPlane.getPixelStride();

            TimberLogger.d("QR-SCAN", "YUV strides - Y: %d, UV: %d, Y pixel stride: %d, UV pixel stride: %d", 
                yStride, uvStride, yPixelStride, uvPixelStride);

            // Create the output bitmap
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] argb = new int[width * height];

            // Convert YUV to RGB
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int yIndex = y * yStride + x * yPixelStride;
                    int uvIndex = (y / 2) * uvStride + (x / 2) * uvPixelStride;

                    int yValue = yBuffer.get(yIndex) & 0xff;
                    int uValue = uBuffer.get(uvIndex) & 0xff;
                    int vValue = vBuffer.get(uvIndex) & 0xff;

                    // Convert YUV to RGB
                    int r = yValue + (int) (1.402f * (vValue - 128));
                    int g = yValue - (int) (0.344f * (uValue - 128)) - (int) (0.714f * (vValue - 128));
                    int b = yValue + (int) (1.772f * (uValue - 128));

                    // Clamp RGB values
                    r = r < 0 ? 0 : (r > 255 ? 255 : r);
                    g = g < 0 ? 0 : (g > 255 ? 255 : g);
                    b = b < 0 ? 0 : (b > 255 ? 255 : b);

                    argb[y * width + x] = 0xff000000 | (r << 16) | (g << 8) | b;
                }
            }

            bitmap.setPixels(argb, 0, width, 0, 0, width, height);

            // Rotate bitmap if needed
            if (rotation != 0) {
                TimberLogger.d("QR-SCAN", "Rotating bitmap by %d degrees", rotation);
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(rotation);
                Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                bitmap.recycle();
                return rotatedBitmap;
            }

            return bitmap;
        } catch (Exception e) {
            TimberLogger.e("QR-SCAN", "Error in imageProxyToBitmap: %s", e.getMessage());
            if (e.getCause() != null) {
                TimberLogger.e("QR-SCAN", "Caused by: %s", e.getCause().getMessage());
            }
            e.printStackTrace();
            return null;
        }
    }

    private void generateQRCode() {
        String content = qrContentEditText.getText().toString();
        if (content.isEmpty()) {
            showError(getString(R.string.error_creating_qr));
            return;
        }
        QRCodeGenerator.generateAndShowQRCode(this, content);
    }

    private void showQRDialog(List<Bitmap> qrBitmaps) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_qr_display, null);
        
        ViewPager2 viewPager = dialogView.findViewById(R.id.qrViewPager);
        TextView pageIndicator = dialogView.findViewById(R.id.pageIndicator);
        
        viewPager.setOffscreenPageLimit(1);
        viewPager.setUserInputEnabled(true);
        
        QRPagerAdapter adapter = new QRPagerAdapter(qrBitmaps);
        viewPager.setAdapter(adapter);
        
        // Get the text color based on the current theme
        int textColor = getResources().getColor(R.color.text_color, getTheme());
        
        if (qrBitmaps.size() > 1) {
            pageIndicator.setVisibility(View.VISIBLE);
            pageIndicator.setText(String.format(Locale.US, "1/%d", qrBitmaps.size()));
            pageIndicator.setTextColor(textColor);
            
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    pageIndicator.setText(String.format(Locale.US, "%d/%d", position + 1, qrBitmaps.size()));
                }
            });
        } else {
            pageIndicator.setVisibility(View.GONE);
        }
        
        builder.setView(dialogView)
               .setPositiveButton(android.R.string.ok, null)
               .setNeutralButton(R.string.save, (dialog, which) -> saveQRCodes(qrBitmaps));
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Set the button text color
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(textColor);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(textColor);
    }

    private void saveQRCodes(List<Bitmap> qrBitmaps) {
        int savedCount = 0;
        int index = 0;
        for (Bitmap bitmap : qrBitmaps) {
            String fileName = "QR_" + System.currentTimeMillis() + "_" + index + ".png";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            }

            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(imageUri)) {
                    if (out != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                        savedCount++;
                    }
                } catch (IOException e) {
                    // Continue with next image if one fails
                }
            }
            index++;
        }

        if (savedCount > 0) {
            Toast.makeText(this, getString(R.string.qr_saved_count, savedCount), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.error_saving_qr)+"[3]", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard() {
        String content = qrContentEditText.getText().toString();
        if (!content.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("QR Content", content);
            clipboard.setPrimaryClip(clip);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void scanQRFromImage(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                Toast.makeText(this, getString(R.string.cannot_open_image), Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                Toast.makeText(this, getString(R.string.cannot_decode_image), Toast.LENGTH_SHORT).show();
                return;
            }

            // Convert bitmap to RGB_565 format for better compatibility
            Bitmap rgbBitmap = bitmap.copy(Bitmap.Config.RGB_565, true);
            if (bitmap != rgbBitmap) {
                bitmap.recycle();
            }

            int width = rgbBitmap.getWidth();
            int height = rgbBitmap.getHeight();
            int[] pixels = new int[width * height];
            rgbBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            // Create a binary bitmap for ZXing
            com.google.zxing.BinaryBitmap binaryBitmap = new com.google.zxing.BinaryBitmap(
                new com.google.zxing.common.HybridBinarizer(
                    new com.google.zxing.LuminanceSource(width, height) {
                        @Override
                        public byte[] getMatrix() {
                            byte[] luminances = new byte[width * height];
                            for (int i = 0; i < height; i++) {
                                for (int j = 0; j < width; j++) {
                                    int pixel = pixels[i * width + j];
                                    int r = (pixel >> 16) & 0xff;
                                    int g = (pixel >> 8) & 0xff;
                                    int b = pixel & 0xff;
                                    luminances[i * width + j] = (byte) ((r + g + b) / 3);
                                }
                            }
                            return luminances;
                        }

                        @Override
                        public byte[] getRow(int y, byte[] row) {
                            if (row == null || row.length < width) {
                                row = new byte[width];
                            }
                            for (int x = 0; x < width; x++) {
                                int pixel = pixels[y * width + x];
                                int r = (pixel >> 16) & 0xff;
                                int g = (pixel >> 8) & 0xff;
                                int b = pixel & 0xff;
                                row[x] = (byte) ((r + g + b) / 3);
                            }
                            return row;
                        }

                        @Override
                        public boolean isCropSupported() {
                            return false;
                        }

                        @Override
                        public boolean isRotateSupported() {
                            return false;
                        }
                    }
                )
            );

            try {
                // Try to decode the QR code
                com.google.zxing.Result result = new com.google.zxing.MultiFormatReader().decode(binaryBitmap);
                if (result != null && result.getText() != null) {
                    String currentText = qrContentEditText.getText().toString();
                    String newText = currentText + result.getText();
                    qrContentEditText.setText(newText);
                } else {
                    Toast.makeText(this, getString(R.string.cannot_open_image), Toast.LENGTH_SHORT).show();
                }
            } catch (com.google.zxing.NotFoundException e) {
                Toast.makeText(this, getString(R.string.cannot_decode_image) ,Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Error scanning QR: " + e.getMessage(), Toast.LENGTH_LONG).show();
            } finally {
                if (!rgbBitmap.isRecycled()) {
                    rgbBitmap.recycle();
                }
            }

        } catch (IOException e) {
            Toast.makeText(this, "Error reading image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Unexpected error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void handleQRCodeResult(String qrContent) {

        runOnUiThread(() -> {
            if (isReturnString) {
                // If we're in return mode, set the result and finish
                Intent resultIntent = new Intent();
                resultIntent.putExtra("qr_content", qrContent);
                int requestCode = getIntent().getIntExtra("request_code", 0);
                Timber.i("Setting result with request_code: %s", requestCode);
                resultIntent.putExtra("request_code", requestCode);
                setResult(RESULT_OK, resultIntent);
                Timber.i("Setting result OK and finishing activity");
                finish();
            } else {
                // Otherwise, append to the current text
                String currentText = qrContentEditText.getText().toString();
                String newText = currentText + qrContent;
                Timber.i("Appending QR content to current text. New text: %s", newText);
                qrContentEditText.setText(newText);
            }
        });
    }

    private void returnResult() {
        try {
            String content = qrContentEditText != null ? qrContentEditText.getText().toString() : "";
            if (!content.isEmpty()) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("qr_content", content);
                
                // Get the request_code from the intent that started this activity
                int requestCode = getIntent().getIntExtra("request_code", 0);
                resultIntent.putExtra("request_code", requestCode);
                
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        } catch (Exception e) {
            Timber.e("Error returning result: %s", e.getMessage());
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    // Add a helper method for showing error messages
    private void showError(String message) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Timber.e(message);
        } catch (Exception e) {
            Timber.e(e, "Error showing error message: %s", e.getMessage());
        }
    }
}