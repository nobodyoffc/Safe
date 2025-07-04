package com.fc.fc_ajdk.utils;

import org.jetbrains.annotations.NotNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {
    public static final String LONG_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String SHORT_FORMAT = "dd/MM/yyyy";
    public static final String TO_MINUTE = "yy-MM-dd HH:mm";
    public static final String TO_SECOND = "yy-MM-dd HH:mm:ss";

    public static String longShortToTime(long timestamp,String format) {
        return longToTime(timestamp * 1000,format);
    }
    public static String longToTime(long timestamp,String format) {
        Date date = new Date(timestamp);
        return getNiceDate(date,format);
    }
    public static long dateToLong(String dateString, String format) {
        SimpleDateFormat dateFormat = new SimpleDateFormat(format);
        try {
            Date date = dateFormat.parse(dateString);
            return date.getTime();
        } catch (ParseException e) {
            System.err.println("Error parsing date: " + e.getMessage());
            return -1; // Return -1 or handle the error as needed
        }
    }
    public static long dayToLong(long days) {
        // 1 day = 24 hours = 24 * 60 minutes = 24 * 60 * 60 seconds = 24 * 60 * 60 * 1000 milliseconds
        return days * 24L * 60 * 60 * 1000;
    }
    @NotNull
    public static String getNiceDate(Date date) {
        return getNiceDate(date, LONG_FORMAT);
    }

    @NotNull
    public static String getNiceDate(Date date, String pattern) {
        // Create a SimpleDateFormat with the desired format
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        // Format the date as a string
        return sdf.format(date);
    }
    public static String now(String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(new Date());
    }
}
