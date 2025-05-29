package com.fc.fc_ajdk.utils;

import java.util.Date;

public class FcUtils {
    public static final long genesisTime = 1577836802L *1000;
    public static Date heightToDate(long height){
        return new Date(genesisTime+height*60*1000);
    }

    public static String blockTimeToDate(long timestamp){
        return DateUtils.longToTime(timestamp*1000, DateUtils.TO_MINUTE);
    }

    public static String heightToShortDate(long height){
        return DateUtils.longToTime(genesisTime+height*60*1000, DateUtils.TO_MINUTE);
    }

    public static String heightToLongDate(long height){
        return DateUtils.longToTime(genesisTime+height*60*1000, DateUtils.LONG_FORMAT);
    }

    public static String heightToNiceDate(long height){
        return DateUtils.getNiceDate(new Date(genesisTime+height*60*1000));
    }

    public static long dateToHeight(Date date){
        long time = date.getTime();
        return (time-genesisTime)/(60*1000);
    }

    public long timeToHeight(long time){
        return (time-genesisTime)/(60*1000);
    }

}
