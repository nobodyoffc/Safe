package com.fc.fc_ajdk.utils;

import java.util.Arrays;

public class Base32 {
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final char[] BASE32_LOOKUP = BASE32_CHARS.toCharArray();
    private static final int[] BASE32_REVERSE_LOOKUP = new int[128];
    static {
        Arrays.fill(BASE32_REVERSE_LOOKUP, -1);
        for (int i = 0; i < BASE32_LOOKUP.length; i++) {
            BASE32_REVERSE_LOOKUP[BASE32_LOOKUP[i]] = i;
        }
    }

    public static String toBase32(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder base32 = new StringBuilder((bytes.length * 8 + 4) / 5);
        int currByte, digit, i = 0, index = 0;
        int nextByte;
        while (i < bytes.length) {
            currByte = (bytes[i] >= 0) ? bytes[i] : (bytes[i] + 256);
            if (index > 3) {
                if ((i + 1) < bytes.length) {
                    nextByte = (bytes[i + 1] >= 0) ? bytes[i + 1] : (bytes[i + 1] + 256);
                } else {
                    nextByte = 0;
                }
                digit = currByte & (0xFF >> index);
                index = (index + 5) % 8;
                digit <<= index;
                digit |= nextByte >> (8 - index);
                i++;
            } else {
                digit = (currByte >> (8 - (index + 5))) & 0x1F;
                index = (index + 5) % 8;
                if (index == 0) i++;
            }
            base32.append(BASE32_LOOKUP[digit]);
        }
        return base32.toString();
    }

    public static byte[] fromBase32(String base32Str) {
        if (base32Str == null || base32Str.isEmpty()) return new byte[0];
        String base32 = base32Str.toUpperCase();
        int numBytes = base32.length() * 5 / 8;
        byte[] bytes = new byte[numBytes];
        int buffer = 0, bitsLeft = 0, count = 0;
        for (char c : base32.toCharArray()) {
            if (c >= BASE32_REVERSE_LOOKUP.length || BASE32_REVERSE_LOOKUP[c] == -1) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            buffer <<= 5;
            buffer |= BASE32_REVERSE_LOOKUP[c];
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bytes[count++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return bytes;
    }

    public static boolean isBase32(String str) {
        if (str == null || str.isEmpty()) return false;
        for (char c : str.toUpperCase().toCharArray()) {
            if (c >= BASE32_REVERSE_LOOKUP.length || BASE32_REVERSE_LOOKUP[c] == -1) {
                return false;
            }
        }
        return true;
    }
} 