package com.fc.fc_ajdk.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A utility class for scanning QR codes using a camera.
 * This class uses the Android Camera2 API to access the camera
 * and ZXing for QR code detection and decoding.
 */
public class QRCodeScanner {
    private static final String TAG = "QRCodeScanner";
    private static final Map<DecodeHintType, Object> HINTS = new HashMap<>();
    private static final MultiFormatReader READER = new MultiFormatReader();
    
    private Context context;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private boolean running = false;
    private ScheduledExecutorService executor;
    private QRCodeCallback callback;
    private SurfaceView surfaceView;
    private Size previewSize;
    private String cameraId;
    
    static {
        HINTS.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        HINTS.put(DecodeHintType.POSSIBLE_FORMATS, BarcodeFormat.QR_CODE);
    }

    /**
     * Callback interface for QR code detection results
     */
    public interface QRCodeCallback {
        void onQRCodeDetected(String text);
        void onError(Exception e);
    }
    
    /**
     * Initialize the QR code scanner with the default camera
     */
    public QRCodeScanner(Context context) {
        this.context = context;
        initializeCamera();
    }
    
    /**
     * Initialize the camera using Android Camera2 API
     */
    private void initializeCamera() {
        try {
            CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            
            // Find the back camera
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            
            if (cameraId == null) {
                TimberLogger.w("No back camera found");
                return;
            }
            
            // Get camera characteristics
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            
            // Get supported preview sizes
            android.util.Size[] sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(Surface.class);
            
            // Choose a reasonable size
            previewSize = chooseOptimalSize(sizes);
            
            TimberLogger.i(TAG,"Camera initialized with preview size: %dx%d",
                    previewSize.getWidth(), previewSize.getHeight());
            
            // Start background thread
            startBackgroundThread();
            
        } catch (CameraAccessException e) {
            TimberLogger.e("Error initializing camera: %s", e.getMessage());
        }
    }
    
    /**
     * Choose the optimal preview size
     */
    private Size chooseOptimalSize(Size[] choices) {
        // Default to the first size
        if (choices.length == 0) {
            return new Size(640, 480);
        }
        
        // Find the smallest size that's at least 640x480
        Size optimalSize = choices[0];
        for (Size size : choices) {
            if (size.getWidth() >= 640 && size.getHeight() >= 480) {
                optimalSize = size;
                break;
            }
        }
        
        return optimalSize;
    }
    
    /**
     * Start the background thread for camera operations
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    /**
     * Stop the background thread
     */
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Get the camera device
     * 
     * @return the camera device or null if no camera is available
     */
    public Object getWebcam() {
        return cameraDevice;
    }
    
    /**
     * Decode QR code from a file
     * 
     * @param file the image file containing a QR code
     * @return the decoded text or null if no QR code was found
     * @throws IOException if the file cannot be read
     * @throws NotFoundException if no QR code is detected
     */
    public static String decodeQRCode(File file) throws IOException, NotFoundException {
        Bitmap bitmap = BitmapFactory.decodeStream(new FileInputStream(file));
        if (bitmap == null) {
            throw new IOException("Could not decode image");
        }
        return decodeQRCode(bitmap);
    }
    
    /**
     * Decode QR code from a Bitmap
     * 
     * @param bitmap the image containing a QR code
     * @return the decoded text or null if no QR code was found
     * @throws NotFoundException if no QR code is detected
     */
    public static String decodeQRCode(Bitmap bitmap) throws NotFoundException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        byte[] luminances = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;
                // Convert to luminance
                luminances[y * width + x] = (byte) ((r + g + b) / 3);
            }
        }
        
        LuminanceSource source = new PlanarYUVLuminanceSource(luminances, width, height, 0, 0, width, height, false);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        
        Result result = READER.decode(binaryBitmap, HINTS);
        return result.getText();
    }
    
    /**
     * Scan a single QR code using the camera and return the decoded text
     * 
     * @return the decoded text from the QR code, or null if no QR code was found
     * @throws IOException if there's an error accessing the camera
     * @throws IllegalStateException if no camera is available
     */
    public String scanQRCode() throws IOException {
        if (cameraDevice == null) {
            throw new IllegalStateException("No camera available");
        }
        
        // This is a simplified implementation
        // In a real app, you would capture an image and process it
        // For now, we'll just return null to indicate no QR code was found
        return null;
    }
    
    /**
     * Scan multiple QR codes one by one and concatenate their contents
     * without any separators. After each scan, prompts the user if they want to continue.
     * 
     * @param reader BufferedReader to read user input for confirmation
     * @return the concatenated text from all QR codes
     * @throws IOException if there's an error accessing the camera or reading input
     * @throws IllegalStateException if no camera is available
     * @throws InterruptedException if the scanning process is interrupted
     */
    public String scanQRCodeList(BufferedReader reader) 
            throws IOException, InterruptedException {
        if (cameraDevice == null) {
            throw new IllegalStateException("No camera available");
        }
        
        // This is a simplified implementation
        // In a real app, you would capture images and process them
        // For now, we'll just return an empty string
        return "";
    }
    
    /**
     * Start scanning for QR codes with the camera
     * 
     * @param callback the callback to receive QR code detection results
     * @param intervalMillis the interval between scans in milliseconds
     */
    public void startScanning(QRCodeCallback callback, long intervalMillis) {
        if (running || cameraDevice == null) {
            if (callback != null && cameraDevice == null) {
                callback.onError(new IllegalStateException("No camera available"));
            }
            return;
        }
        
        this.callback = callback;
        
        try {
            // Create an ImageReader to get images from the camera
            imageReader = ImageReader.newInstance(
                    previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.YUV_420_888, 2);
            
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        if (image != null) {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] data = new byte[buffer.remaining()];
                            buffer.get(data);
                            
                            // Convert YUV to RGB
                            YuvImage yuvImage = new YuvImage(data, ImageFormat.YUV_420_888, 
                                    previewSize.getWidth(), previewSize.getHeight(), null);
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            yuvImage.compressToJpeg(new Rect(0, 0, previewSize.getWidth(), previewSize.getHeight()), 100, out);
                            byte[] jpegData = out.toByteArray();
                            
                            // Convert to Bitmap
                            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
                            
                            // Try to decode QR code
                            try {
                                String text = decodeQRCode(bitmap);
                                if (callback != null) {
                                    callback.onQRCodeDetected(text);
                                }
                            } catch (NotFoundException e) {
                                // No QR code found in this frame - that's normal
                            }
                        }
                    } catch (Exception e) {
                        if (callback != null) {
                            callback.onError(e);
                        }
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
            }, backgroundHandler);
            
            // Create a capture session
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(imageReader.getSurface());
            
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    try {
                        // Create a capture request
                        CaptureRequest.Builder builder = 
                                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        builder.addTarget(imageReader.getSurface());
                        
                        // Set auto-focus mode
                        builder.set(CaptureRequest.CONTROL_AF_MODE, 
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        
                        // Start capturing
                        captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
                        
                        running = true;
                        TimberLogger.i("QR code scanner started");
                    } catch (CameraAccessException e) {
                        if (callback != null) {
                            callback.onError(e);
                        }
                        TimberLogger.e("Error starting scanner: %s", e.getMessage());
                    }
                }
                
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    if (callback != null) {
                        callback.onError(new IllegalStateException("Failed to configure camera session"));
                    }
                }
            }, backgroundHandler);
            
        } catch (CameraAccessException e) {
            if (callback != null) {
                callback.onError(e);
            }
            TimberLogger.e("Error starting scanner: %s", e.getMessage());
        }
    }
    
    /**
     * Stop scanning for QR codes
     */
    public void stopScanning() {
        running = false;
        
        if (executor != null) {
            try {
                executor.shutdown();
                executor.awaitTermination(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                executor = null;
            }
        }
        
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
                captureSession.close();
                captureSession = null;
            } catch (CameraAccessException e) {
                TimberLogger.w("Error stopping camera session: %s", e.getMessage());
            }
        }
        
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        
        stopBackgroundThread();
        
        TimberLogger.i("QR code scanner stopped");
    }
} 