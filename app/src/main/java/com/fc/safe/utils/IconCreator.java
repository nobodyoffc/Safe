package com.fc.safe.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IconCreator {
    private static final int[] ICON_SIZES = {48, 72, 96, 144, 192};
    private static final String[] DENSITY_NAMES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};

    public static Bitmap createSquareIcon(String title, int size, int margin, int textColor, int backgroundColor) {
        // Create a new bitmap with the specified size
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Draw background
        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(backgroundColor);
        canvas.drawRect(0, 0, size, size, backgroundPaint);

        // Process title
        String[] words = title.split("\\s+");
        String[] displayLines;
        if (words.length > 3) {
            // Take first letter of each word and capitalize
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    sb.append(Character.toUpperCase(word.charAt(0)));
                }
            }
            displayLines = new String[]{sb.toString()};
        } else {
            displayLines = words;
        }

        // Draw text
        Paint textPaint = new Paint();
        textPaint.setColor(textColor);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD); // Make text bold

        // Calculate text size to fit within margins
        int maxTextWidth = size - (2 * margin);
        int maxTextHeight = size - (2 * margin);
        
        // Start with a more conservative initial size
        float textSize = size / (4f * displayLines.length);
        textPaint.setTextSize(textSize);

        // Calculate line height and total text height
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float lineHeight = fm.bottom - fm.top;
        float totalTextHeight = lineHeight * displayLines.length;

        // Adjust text size to fit while trying to maximize size
        boolean fits = false;
        float lastFittingSize = textSize;
        while (!fits) {
            Rect bounds = new Rect();
            float maxLineWidth = 0;
            for (String line : displayLines) {
                textPaint.getTextBounds(line, 0, line.length(), bounds);
                maxLineWidth = Math.max(maxLineWidth, bounds.width());
            }
            
            if (maxLineWidth <= maxTextWidth && totalTextHeight <= maxTextHeight) {
                lastFittingSize = textSize;
                // Try a slightly larger size
                textSize *= 1.05f;  // Reduced from 1.1f to be more conservative
                textPaint.setTextSize(textSize);
                fm = textPaint.getFontMetrics();
                lineHeight = fm.bottom - fm.top;
                totalTextHeight = lineHeight * displayLines.length;
            } else {
                // If we've gone too far, use the last fitting size
                textSize = lastFittingSize;
                textPaint.setTextSize(textSize);
                fm = textPaint.getFontMetrics();
                lineHeight = fm.bottom - fm.top;
                totalTextHeight = lineHeight * displayLines.length;
                fits = true;
            }
        }

        // Calculate starting Y position to center the text block
        float startY = (size - totalTextHeight) / 2f + Math.abs(fm.top);  // Added Math.abs(fm.top) to better center text

        // Draw each line
        for (int i = 0; i < displayLines.length; i++) {
            float y = startY + (i * lineHeight);
            canvas.drawText(displayLines[i], size / 2f, y, textPaint);
        }

        return bitmap;
    }

    public static Bitmap createRoundIcon(String title, int size, int margin, int textColor, int backgroundColor) {
        // Create square icon first
        Bitmap squareIcon = createSquareIcon(title, size, margin, textColor, backgroundColor);
        
        // Create a new bitmap for the round icon
        Bitmap roundIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(roundIcon);

        // Create a circular clip path
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(backgroundColor);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);

        // Use PorterDuff to clip the square icon to the circle
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(squareIcon, 0, 0, paint);

        // Clean up
        squareIcon.recycle();

        return roundIcon;
    }

    public static List<String> createSquareIconFiles(String title, float marginPercentage, String path, int textColor, int backgroundColor) {
        return createIconFiles(title, marginPercentage, path, textColor, backgroundColor, false);
    }

    public static List<String> createRoundIconFiles(String title, float marginPercentage, String path, int textColor, int backgroundColor) {
        return createIconFiles(title, marginPercentage, path, textColor, backgroundColor, true);
    }

    private static List<String> createIconFiles(String title, float marginPercentage, String path, int textColor, int backgroundColor, boolean isRound) {
        List<String> savedPaths = new ArrayList<>();
        for (int i = 0; i < ICON_SIZES.length; i++) {
            int size = ICON_SIZES[i];
            String density = DENSITY_NAMES[i];
            
            // Convert percentage margin to actual pixel value
            int margin = Math.round(size * (marginPercentage / 100f));
            
            // Create the icon
            Bitmap icon = isRound ? 
                createRoundIcon(title, size, margin, textColor, backgroundColor) :
                createSquareIcon(title, size, margin, textColor, backgroundColor);

            // Create directory if it doesn't exist
            String iconPath = path + "/mipmap-" + density;
            File directory = new File(iconPath);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Save the icon
            String fileName = isRound ? "ic_launcher_round.png" : "ic_launcher.png";
            File file = new File(directory, fileName);
            try {
                FileOutputStream out = new FileOutputStream(file);
                icon.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.close();
                icon.recycle();
                savedPaths.add(file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return savedPaths;
    }
} 