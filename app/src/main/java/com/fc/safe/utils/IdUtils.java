package com.fc.safe.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.DateUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IdUtils {
    public final static String TAG = "CidUtils";

    public static final String AVATAR_MAP = "avatarMap";
    // Default size for the circular image (same as AvatarMaker avatars)
    private static final int CIRCLE_SIZE = 200;

    public static void addCidInfoToDb(KeyInfo newObject, Context context, LocalDB<KeyInfo> keyInfoDB) {
        if (newObject != null) {
            // Save the new CidInfo to the database
            newObject.setSaveTime(DateUtils.longToTime(System.currentTimeMillis(), DateUtils.TO_MINUTE));
            keyInfoDB.put(newObject.getId(),newObject);
            // Create avatar for the new CidInfo
            try {
                // Initialize AvatarMaker if not already initialized
                AvatarMaker.init(context);

                // Create avatar using the CidInfo ID
                byte[] avatarBytes = AvatarMaker.makeAvatar(newObject.getId());

                // Save avatar to database
                if (avatarBytes != null) {
                    keyInfoDB.putInMap(AVATAR_MAP, newObject.getId(), avatarBytes);
                    TimberLogger.i(TAG,"Avatar created and saved for %s", newObject.getId());
                }
            } catch (IOException e) {
                TimberLogger.e(TAG,"Failed to create avatar: %s", e.getMessage());
            }

            // Show success message and finish
            TimberLogger.i(TAG,"CidInfo added successfully");
        }
    }

    public static byte[] generateAvatar(String id) {
        if(id==null || id.isEmpty())return null;
        try {
            if (KeyTools.isGoodFid(id)) {
                return AvatarMaker.makeAvatar(id);
            }

            return makeCircleImageFromId(id);
        }catch (Exception e){
            TimberLogger.e(TAG,"Failed to create avatar: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Loads avatars from the database for the given addresses
     *
     * @param addrs     Array of addresses to load avatars for
     * @return Map of address to avatar byte array
     */
    public static <T> Map<String, byte[]> loadAvatarFromDb(List<String> addrs, LocalDB<T> keyInfoDB) {
        try {
            return loadImagesFromDb(addrs, AVATAR_MAP, keyInfoDB);
        }catch (Exception e){
            return null;
        }
    }

    @NonNull
    public static <T> Map<String, byte[]> loadImagesFromDb(List<String> addrs, String mapName, LocalDB<T> keyInfoDB) {

        Map<String, byte[]> avatarMap = new HashMap<>();

        if (addrs == null || addrs.isEmpty()) {
            TimberLogger.w(TAG,"No addresses provided to load avatars");
            return avatarMap;
        }

        for (String addr : addrs) {
            if (addr != null && !addr.isEmpty()) {
                byte[] avatarBytes = keyInfoDB.getFromMap(mapName, addr);
                if (avatarBytes != null) {
                    avatarMap.put(addr, avatarBytes);
                } else {
                    TimberLogger.w(TAG,"No avatar found for %s", addr);
                }
            }
        }

        TimberLogger.i(TAG,"Loaded %d avatars from database", avatarMap.size());
        return avatarMap;
    }

    public static byte[] makeCircleImageFromId(String id) throws IOException {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID cannot be null or empty");
        }

        byte[] data = Hash.sha256(id.getBytes());
        return makeCircleImageFromData(data);
    }

    /**
     * Creates circle images for a list of IDs and returns a map of ID to image bytes.
     *
     * @param ids List of IDs to generate circle images for
     * @return Map of ID to circle image bytes in PNG format
     * @throws IOException If there's an error creating any of the images
     */
    public static Map<String, byte[]> makeCircleImageFromIdList(List<String> ids) throws IOException {
        if (ids == null || ids.isEmpty()) {
            TimberLogger.w(TAG,"No IDs provided to generate circle images");
            return new HashMap<>();
        }

        Map<String, byte[]> resultMap = new HashMap<>();

        for (String id : ids) {
            if (id != null && !id.isEmpty()) {
                try {
                    byte[] imageBytes = makeCircleImageFromId(id);
                    resultMap.put(id, imageBytes);
                    TimberLogger.d(TAG,"Circle image created for ID: %s", id);
                } catch (Exception e) {
                    TimberLogger.e(TAG,"Failed to create circle image for ID %s: %s", id, e.getMessage());
                    // Continue with other IDs even if one fails
                }
            }
        }

        TimberLogger.i(TAG,"Created %d circle images from %d IDs", resultMap.size(), ids.size());
        return resultMap;
    }

    /**
     * Creates a circular image from 32 bytes of data.
     * The image will have the same size as avatars created by AvatarMaker.
     *
     * @param data 32 bytes of data to generate the image from
     * @return Byte array containing the circular image in PNG format
     * @throws IOException If there's an error creating the image
     */
    public static byte[] makeCircleImageFromData(byte[] data) throws IOException {
        if (data == null || data.length != 32) {
            throw new IllegalArgumentException("Data must be exactly 32 bytes");
        }

        // Create a bitmap with the same size as AvatarMaker avatars
        Bitmap bitmap = Bitmap.createBitmap(CIRCLE_SIZE, CIRCLE_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Set up paint for drawing
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Generate background color based on the first few bytes
        int bgHue = ((data[0] & 0xFF) * 360) / 256;
        int bgColor = Color.HSVToColor(new float[]{bgHue, 0.3f, 0.95f});

        // Draw background circle with generated color
        paint.setColor(bgColor);
        canvas.drawCircle(CIRCLE_SIZE / 2f, CIRCLE_SIZE / 2f, CIRCLE_SIZE / 2f, paint);

        // Draw border
        paint.setColor(Color.GRAY);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawCircle(CIRCLE_SIZE / 2f, CIRCLE_SIZE / 2f, CIRCLE_SIZE / 2f - 1f, paint);

        // Draw pattern based on data
        paint.setStyle(Paint.Style.FILL);

        // Use the data to generate colors and patterns
        int centerX = CIRCLE_SIZE / 2;
        int centerY = CIRCLE_SIZE / 2;
        int radius = CIRCLE_SIZE / 2 - 10; // Leave some margin

        // Draw larger overlapping circles in different positions
        // Use bytes 1-4 for these circles
        for (int i = 1; i <= 4; i++) {
            int value = data[i] & 0xFF;

            // Generate color based on the byte value
            int hue = (value * 360) / 256;
            int color = Color.HSVToColor(new float[]{hue, 0.6f, 0.8f});
            paint.setColor(color);

            // Position circles at top, right, bottom, left
            float x = centerX;
            float y = centerY;

            switch (i) {
                case 1: // Top
                    y = centerY - (float) radius / 2;
                    break;
                case 2: // Right
                    x = centerX + (float) radius / 2;
                    break;
                case 3: // Bottom
                    y = centerY + (float) radius / 2;
                    break;
                case 4: // Left
                    x = centerX - (float) radius / 2;
                    break;
            }

            // Size varies based on the byte value
            float size = 30 + ((float) value / 8);
            canvas.drawCircle(x, y, size, paint);
        }

        // Draw segments based on data (using bytes 5-36)
        for (int i = 5; i < 32; i++) {
            // Use each byte to determine color and position
            int value = data[i] & 0xFF;

            // Generate color based on the byte value
            int hue = (value * 360) / 256;
            int color = Color.HSVToColor(new float[]{hue, 0.7f, 0.9f});
            paint.setColor(color);

            // Calculate position in the circle
            float angle = ((i - 5) * 360f) / 27; // Adjusted for the new range
            float radians = (float) Math.toRadians(angle);
            float x = centerX + (float) (radius * Math.cos(radians));
            float y = centerY + (float) (radius * Math.sin(radians));

            // Draw a small circle at this position
            float size = 5 + ((float) value / 51); // Size varies based on the byte value
            canvas.drawCircle(x, y, size, paint);
        }

        // Draw a center circle with color based on the last byte
        int centerValue = data[31] & 0xFF;
        int centerHue = (centerValue * 360) / 256;
        int centerColor = Color.HSVToColor(new float[]{centerHue, 0.8f, 0.7f});
        paint.setColor(centerColor);

        // Center circle size varies based on the last byte
        float centerSize = 15 + ((float) centerValue / 17);
        canvas.drawCircle(centerX, centerY, centerSize, paint);

        // Add a small inner circle with contrasting color
        int innerHue = (centerHue + 180) % 360; // Complementary color
        int innerColor = Color.HSVToColor(new float[]{innerHue, 0.9f, 0.9f});
        paint.setColor(innerColor);
        canvas.drawCircle(centerX, centerY, centerSize / 2, paint);

        // Convert bitmap to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        byte[] byteArray = outputStream.toByteArray();

        // Clean up
        bitmap.recycle();
        outputStream.close();

        return byteArray;
    }

    private static void setAvatarImage(ImageView avatarView, byte[] avatarBytes) {
        if (avatarBytes != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
            if (bitmap != null) {
                avatarView.setImageBitmap(bitmap);
                return;
            }
        }
        avatarView.setBackgroundColor(Color.LTGRAY);
    }

    public static void showAvatarDialog(Context context, String id) {
        TimberLogger.d(TAG, "showAvatarDialog called with id: %s", id);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_avatar_utils, null);

        TextView idText = dialogView.findViewById(R.id.id_text);
        idText.setText(id);
        idText.setTextColor(ContextCompat.getColor(context, R.color.field_name));

        ImageView avatarView = dialogView.findViewById(R.id.avatar_view);
        byte[] avatarBytes = null;

        try {
            avatarBytes = AvatarMaker.createAvatar(id, context);
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to create avatar: %s", e.getMessage());
            Toast.makeText(context, "Failed to create avatar", Toast.LENGTH_LONG).show();
        }
        setAvatarImage(avatarView, avatarBytes);

        AlertDialog dialog = builder.setView(dialogView).create();

        byte[] finalAvatarBytes = avatarBytes;
        dialogView.findViewById(R.id.save_button).setOnClickListener(v -> saveAvatarToGallery(context, id, finalAvatarBytes));

        dialogView.findViewById(R.id.ok_button).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private static void saveAvatarToGallery(Context context, String id, byte[] avatarBytes) {
        if (avatarBytes == null) {
            Toast.makeText(context, "No avatar to save", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.length);
            String fileName = "avatar_" + id + ".png";
            File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            if(!picturesDir.mkdirs())TimberLogger.e(TAG, "Failed to create directory.");

            File file = new File(picturesDir, fileName);

            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            // Notify the media scanner
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(file);
            mediaScanIntent.setData(contentUri);
            context.sendBroadcast(mediaScanIntent);

            Toast.makeText(context, "Avatar saved to gallery", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to save avatar: %s", e.getMessage());
            Toast.makeText(context, "Failed to save avatar", Toast.LENGTH_LONG).show();
        }
    }
}
