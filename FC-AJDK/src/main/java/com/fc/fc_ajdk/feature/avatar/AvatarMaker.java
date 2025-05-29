package com.fc.fc_ajdk.feature.avatar;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.fc.fc_ajdk.utils.TimberLogger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


public class AvatarMaker {
    private static String TAG = "AvatarMaker";

    private static Map<String, Integer> data = new HashMap<>();
    private static Context applicationContext;
    private static final int FEATURE_COUNT = 10;

    static {
        data.put("1", 0);
        data.put("2", 1);
        data.put("3", 2);
        data.put("4", 3);
        data.put("5", 4);
        data.put("6", 5);
        data.put("7", 6);
        data.put("8", 7);
        data.put("9", 8);
        data.put("A", 9);
        data.put("B", 10);
        data.put("C", 11);
        data.put("D", 12);
        data.put("E", 13);
        data.put("F", 14);
        data.put("G", 15);
        data.put("H", 16);
        data.put("J", 17);
        data.put("K", 18);
        data.put("L", 19);
        data.put("M", 20);
        data.put("N", 21);
        data.put("P", 22);
        data.put("Q", 23);
        data.put("R", 24);
        data.put("S", 25);
        data.put("T", 26);
        data.put("U", 27);
        data.put("V", 28);
        data.put("W", 29);
        data.put("X", 30);
        data.put("Y", 31);
        data.put("Z", 32);
        data.put("a", 33);
        data.put("b", 34);
        data.put("c", 35);
        data.put("d", 36);
        data.put("e", 37);
        data.put("f", 38);
        data.put("g", 39);
        data.put("h", 40);
        data.put("i", 41);
        data.put("j", 42);
        data.put("k", 43);
        data.put("m", 44);
        data.put("n", 45);
        data.put("o", 46);
        data.put("p", 47);
        data.put("q", 48);
        data.put("r", 49);
        data.put("s", 50);
        data.put("t", 51);
        data.put("u", 52);
        data.put("v", 53);
        data.put("w", 54);
        data.put("x", 55);
        data.put("y", 56);
        data.put("z", 57);
    }

    /**
     * Initialize the AvatarMaker with application context
     * 
     * @param context Application context
     */
    public static void init(Context context) {
        applicationContext = context.getApplicationContext();
    }

    public static Map<String, byte[]> makeAvatars(String[] addrArray,Context context) throws IOException {
        applicationContext = context.getApplicationContext();
        return makeAvatars(addrArray);
    }

    public static byte[] makeAvatar(String addr,Context context) throws IOException {
        applicationContext = context.getApplicationContext();
        return makeAvatar(addr);
    }

    /**
     * Creates avatars for the given addresses and returns them as a Map<String, byte[]>.
     * 
     * @param addrArray Array of addresses to create avatars for
     * @return Map with address as key and avatar image bytes as value
     * @throws IOException If there's an error creating the avatars
     */
    public static Map<String, byte[]> makeAvatars(String[] addrArray) throws IOException {
        if (applicationContext == null) {
            throw new IllegalStateException("AvatarMaker not initialized. Call init() first.");
        }

        Map<String, byte[]> avatarMap = new HashMap<>();
        for (String addr : addrArray) {
            String[] keys = getResourceIdByAddress(addr);
            byte[] avatarBytes = makeAvatar(keys);
            if (avatarBytes != null) {
                avatarMap.put(addr, avatarBytes);
            }
        }
        return avatarMap;
    }

    /**
     * Creates a single avatar for the given address and returns it as a byte array.
     * 
     * @param addr Address to create avatar for
     * @return Byte array containing the avatar image
     * @throws IOException If there's an error creating the avatar
     */
    public static byte[] makeAvatar(String addr) throws IOException {
        if (applicationContext == null) {
            throw new IllegalStateException("AvatarMaker not initialized. Call init() first.");
        }

        String[] resourceIds = getResourceIdByAddress(addr);
        return makeAvatar(resourceIds);
    }

    /**
     * Creates an avatar from the given keys and returns it as a byte array.
     * 
     * @param keys Array of keys for the avatar components
     * @return Byte array containing the avatar image
     * @throws IOException If there's an error creating the avatar
     */
    private static byte[] makeAvatar(String[] keys) throws IOException {
        if (applicationContext == null) {
            throw new IllegalStateException("AvatarMaker not initialized. Call init() first.");
        }

        // Create a bitmap for the base image
        Bitmap baseBitmap = getBitmapFromResource(keys[0]);
        if (baseBitmap == null) {
            return null;
        }

        // Create a mutable bitmap to draw on
        Bitmap resultBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(resultBitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Draw each watermark
        for (int i = 1; i < FEATURE_COUNT; i++) {
            Bitmap watermark = getBitmapFromResource(keys[i]);
            if (watermark != null) {
                canvas.drawBitmap(watermark, 0, 0, paint);
                watermark.recycle();
            }
        }

        // Convert bitmap to byte array
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
        byte[] byteArray = outputStream.toByteArray();

        // Clean up
        baseBitmap.recycle();
        resultBitmap.recycle();
        outputStream.close();

        return byteArray;
    }

    /**
     * Gets a bitmap from a resource identifier
     * 
     * @param resourcePath Resource path in format "type/resource_name"
     * @return Bitmap from the resource
     */
    private static Bitmap getBitmapFromResource(String resourcePath) {
        try {
            // Get resource ID
            Resources resources = applicationContext.getResources();

            int resourceId = resources.getIdentifier(
                resourcePath,
                "drawable",
                applicationContext.getPackageName()
            );
            
            if (resourceId == 0) {
                TimberLogger.w(TAG,"Resource not found:"+resourcePath);
                return null;
            }
            
            Bitmap bitmap = BitmapFactory.decodeResource(resources, resourceId);
            if (bitmap == null) {
                TimberLogger.w(TAG,"Failed to decode bitmap for resource: %s", resourcePath);
            }
            return bitmap;
        } catch (Exception e) {
            TimberLogger.e(TAG,"Error loading resource: %s", e.getMessage());
            return null;
        }
    }

    /**
     * 根据地址获取图片
     *
     * @param address 图片key
     * @return 图片路径
     */
//    private static String[] getPathByAddress(String address) {
//        if (address == null || address.length() < 33) {
//            // If address is too short, return a default avatar path
//            String[] defaultPath = new String[10];
//            for (int i = 0; i < FEATURE_COUNT; i++) {
//                defaultPath[i] = "avatar_" + i + "_0";
//            }
//            return defaultPath;
//        }
//
//        String[] tempStr = address.split("");
//        for (int i = 0; i < FEATURE_COUNT; i++) {
//            int index = 33 - 4 - i;
//            if (index < tempStr.length) {
//                String c = tempStr[index];
//                if (data.containsKey(c)) {
//                    tempStr[i] = getType(c, i);
//                } else {
//                    tempStr[i] = "avatar_" + i + "_0";
//                }
//            } else {
//                tempStr[i] = "avatar_" + i + "_0";
//            }
//        }
//
//        return tempStr;
//    }

    private static String[] getResourceIdByAddress(String address) {
        String[] tempStr = address.split("");
        String[] resourceIds = new String[FEATURE_COUNT];
        for (int i = 0; i < FEATURE_COUNT; i++) {
            resourceIds[i] = getResourceId(tempStr[33 - 4 - i], i);
        }
        return resourceIds;
    }

    /**
     * 获取头像类型
     */
    private static String getResourceId(String c, Integer i) {
        return "avatar_" + i + "_" + data.get(c);
    }

    // Legacy methods for file-based avatar creation
    public static String[] getAvatars(String[] addrArray, String basePath, String filePath) throws IOException {
        if (!filePath.endsWith("/")) filePath = filePath + "/";
        if (!basePath.endsWith("/")) basePath = basePath + "/";

        String[] pngFilePaths = new String[addrArray.length];
        for (int i = 0; i < addrArray.length; i++) {
            String addr = addrArray[i];
            pngFilePaths[i] = getAvatar(addr, basePath, filePath + addr + ".png");
        }
        return pngFilePaths;
    }

    private static String getAvatar(String addr, String basePath, String filePath) throws IOException {
        String[] keys = getResourceIdByAddress(addr);

        File fileFile = new File(filePath);
        if (!fileFile.getParentFile().exists()) {
            boolean success = fileFile.getParentFile().mkdirs();
            if (!success) {
                return null;
            }
        }
        return addImgs(keys, basePath, filePath);
    }

    /**
     * 合并图片
     *
     * @param keys 图片获取key
     * @return
     */
    private static String addImgs(String[] keys, String basePath, String filePath) throws IOException {
        // Create a bitmap for the base image
        Bitmap baseBitmap = BitmapFactory.decodeFile(basePath + keys[0]);
        if (baseBitmap == null) {
            return null;
        }

        // Create a mutable bitmap to draw on
        Bitmap resultBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(resultBitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Draw each watermark
        for (int i = 1; i < keys.length; i++) {
            Bitmap watermark = BitmapFactory.decodeFile(basePath + keys[i]);
            if (watermark != null) {
                canvas.drawBitmap(watermark, 0, 0, paint);
                watermark.recycle();
            }
        }

        // Save the result
        File outputFile = new File(filePath);
        if (!outputFile.getParentFile().exists()) {
            boolean success = outputFile.getParentFile().mkdirs();
            if (!success) {
                return null;
            }
        }

        try (FileOutputStream out = new FileOutputStream(outputFile)) {
            resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }

        // Clean up
        baseBitmap.recycle();
        resultBitmap.recycle();

        return filePath;
    }

    public static byte[] createAvatar(String fid, Context context) throws IOException {
        init(context);

        // Create avatar using the CidInfo ID
        return makeAvatar(fid);
    }
}

