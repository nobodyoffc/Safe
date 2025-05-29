package com.fc.fc_ajdk.utils;

import java.security.SecureRandom;

import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.core.crypto.Hash;

import org.jetbrains.annotations.NotNull;


public class IdNameUtils {
        public static byte[] genNew32BytesKey() {
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[32];
        random.nextBytes(keyBytes);
        return keyBytes;
    }

    public static String makeDid(String text) {
        return Hash.sha256x2(text);
    }

    public static String makeDid(byte[] bytes) {
        return Hex.toHex(Hash.sha256x2(bytes));
    }

    /**
     * Generate key name
     * @param oid object ID(such as sid, codeId, etc.)
     * @param name Name
     * @param trueForLowerCaseAndFalseForUpperCase null for no change, true for lowercase, false for uppercase
     * @return Key name
     */
    public static String makeKeyName(String fid, String oid, String name,Boolean trueForLowerCaseAndFalseForUpperCase){
        StringBuilder sb =new StringBuilder();

        if(fid!=null){
            if(fid.length()>6)sb.append(fid, fid.length()-6,fid.length());
            else sb.append(fid);
        }

        if(oid!=null){
            oid = oid.replaceAll(" ", "_");
            if(fid!=null)sb.append("_");
            if(Hex.isHexString(oid) && oid.length()>6)sb.append(oid, 0, 6);
            else sb.append(oid);
        }

        if(name!=null)sb.append("_").append(name);
        String str =sb.toString();
        if(trueForLowerCaseAndFalseForUpperCase!=null){
            if(Boolean.TRUE.equals(trueForLowerCaseAndFalseForUpperCase))
                str = str.toLowerCase();
            else str = str.toUpperCase();
        }

        return str;
    }
    public static String makeKeyName(byte[] key) {
        return Hex.toHex(Hash.sha256(key)).substring(0,12);
    }

    public static String makeIdByTime(long time, String hex){
        return makeIdByTime(time,hex,null);
    }
    public static String makeIdByTime(long time, Integer nonce){
        return makeIdByTime(time,null,nonce);
    }
    private static String makeIdByTime(long time, String hex, Integer nonce){
        String suffix;
        if(hex!=null){
            if(hex.length()<8){
                System.out.println("The length of the Hex can not less than 8.");
                return null;
            }
            suffix= hex.substring(0,8);
        }else if(nonce!=null){
            suffix = Hex.toHex(BytesUtils.intToByteArray(nonce));
        }else return null;
        String date = DateUtils.longToTime(time, Constants.YYYYMMDD_HHMMSSSSS);
        return date + "_" + suffix;//Hex.toHex(BytesTools.intToByteArray(nonce));

    }
    public static long getTimeFromId(byte[] idBytes){
        return getTimeFromId(idBytes,null);
    }
    public static long getTimeFromId(String id){
        return getTimeFromId(null,id);
    }
    private static long getTimeFromId(byte[] idBytes, String id){
        if(idBytes!=null){
            byte[] timeBytes = new byte[8];
            System.arraycopy(idBytes,0,timeBytes,0,8);
            return BytesUtils.bytes8ToLong(timeBytes,false);
        }else if(id!=null){
            return DateUtils.dateToLong(id.substring(0,id.lastIndexOf("_")), Constants.YYYYMMDD_HHMMSSSSS);
        }else return -1;
    }
    public static byte[] makeIdBytesByTime(long time, String hex){
        return makeIdBytesByTime(time,hex,null);
    }
    public static byte[] makeIdBytesByTime(long time, Integer nonce){
        return makeIdBytesByTime(time,null,nonce);
    }
    private static byte[] makeIdBytesByTime(long time, String hex, Integer nonce){
        if(hex==null && nonce==null)return null;
        byte[] idBytes = new byte[8+4];
        System.arraycopy(BytesUtils.longToBytes(time),0,idBytes,0,8);
        if(hex!=null)System.arraycopy(Hex.fromHex(hex),0,idBytes,8,4);
        else System.arraycopy(BytesUtils.intToByteArray(nonce),0,idBytes,8,4);
        return idBytes;
    }

    public static String makeShortName(String id){
        return id.substring(0,6);
    }

    public static String makeShortName(byte[] id){
        String hex = Hex.toHex(id);
        return makeShortName(hex);
    }

    @NotNull
    public static String makePasswordHashName(byte[] passwordBytes) {
        return Hex.toHex(Hash.sha256x2(passwordBytes)).substring(0, 6);
    }
}
