package com.fc.fc_ajdk.utils;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;


public class BytesUtils {

    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final Pattern BASE64_PATTERN = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

    public static Boolean getBit(byte b, int bitPosition) {
        if (bitPosition < 0 || bitPosition > 7) {
            new IllegalArgumentException("Bit position must be between 0 and 7").printStackTrace();
            return null;
        }
        return ((b >> bitPosition) & 1) == 1;
    }

    // Method to set a bit at a specific position
    public static Byte setBit(Byte b, Integer bitPosition, boolean value) {
        if (bitPosition < 0 || bitPosition > 7) {
            System.out.println(new IllegalArgumentException("Bit position must be between 0 and 7").getMessage());
            return null;
        }

        if (value) {
            // Set the bit to 1 using bitwise OR
            return (byte) (b | (1 << bitPosition));
        } else {
            // Set the bit to 0 using bitwise AND with the negation of the mask
            return (byte) (b & ~(1 << bitPosition));
        }
    }

    public static boolean contains(byte[] array, byte[] subArray) {
        // If either array is null or if the subArray is longer than array, return false
        if (array == null || subArray == null || subArray.length > array.length) {
            return false;
        }

        // Iterate through the main array to check if the subArray exists within it
        outerLoop:
        for (int i = 0; i <= array.length - subArray.length; i++) {
            // Check if subArray matches at the current position
            for (int j = 0; j < subArray.length; j++) {
                if (array[i + j] != subArray[j]) {
                    continue outerLoop; // If a mismatch is found, skip to the next position in array
                }
            }
            return true; // subArray was found within array
        }

        return false; // subArray was not found
    }

    public static byte [] readAllBytes(InputStream inputStream) throws IOException {
        byte[] rawTx;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        rawTx = outputStream.toByteArray();
        return rawTx;
    }

    public static class ByteArrayAsKey implements Comparable<ByteArrayAsKey>{
        private final byte[] bytes;

        public ByteArrayAsKey(byte[] data) {
            this.bytes = data;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ByteArrayAsKey that = (ByteArrayAsKey) o;
            return Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(bytes);
        }

        @Override
        public int compareTo(@NotNull ByteArrayAsKey other) {
            return compareByteArrays(this.bytes, other.getBytes());
        }

        public byte[] getBytes() {
            return bytes;
        }
    }

    /**
     * Creates a copy of bytes and appends b to the end of it
     */
    public static byte[] appendByte(byte[] bytes, byte b) {
        byte[] result = Arrays.copyOf(bytes, bytes.length + 1);
        result[result.length - 1] = b;
        return result;
    }

    public static char[] byteArrayToCharArray(byte[] bytes, Charset charset) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        CharBuffer charBuffer = charset.decode(byteBuffer);
        char[] chars = Arrays.copyOfRange(charBuffer.array(),
                charBuffer.position(), charBuffer.limit());
        Arrays.fill(byteBuffer.array(), (byte) 0); // Clear sensitive data
        return chars;
    }

    public static byte[] charArrayToByteArray(char[] chars, Charset charset) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = charset.encode(charBuffer);
        return Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
    }

    public static char[] byteArrayToUtf8CharArray(byte[] bytes) {
        Charset charset = StandardCharsets.UTF_8;
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        CharBuffer charBuffer = charset.decode(byteBuffer);
        return Arrays.copyOfRange(charBuffer.array(),
                charBuffer.position(), charBuffer.limit());
    }

    public static byte[] utf8CharArrayToByteArray(char[] chars) {
        Charset charset = StandardCharsets.UTF_8;
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = charset.encode(charBuffer);
        return Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit());
    }

    public static byte[] hexCharArrayToByteArray(char[] hex) {
        int length = hex.length;
        byte[] byteArray = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            int high = Character.digit(hex[i], 16) << 4;
            int low = Character.digit(hex[i + 1], 16);
            byteArray[i / 2] = (byte) (high | low);
        }
        return byteArray;
    }

    public static boolean isBase64Encoded(String s) {
        if (s == null) return false;
        // Check length
        if (s.length() % 4 != 0) return false;
        // Check valid Base64 characters
        return BASE64_PATTERN.matcher(s).matches();
    }

    public static char[] byteArrayToHexCharArray(byte[] byteArray) {
        char[] hexChars = new char[byteArray.length * 2];
        for (int i = 0; i < byteArray.length; i++) {
            int v = byteArray[i] & 0xFF;
            hexChars[i * 2] = Character.forDigit((v >>> 4) & 0x0F, 16);
            hexChars[i * 2 + 1] = Character.forDigit(v & 0x0F, 16);
        }
        return hexChars;
    }

    public static char[] byteArrayToBase64CharArray(byte[] byteArray) {
        byte[] base64Bytes = Base64.getEncoder().encode(byteArray);
        char[] base64Chars = new char[base64Bytes.length];
        for (int i = 0; i < base64Bytes.length; i++) {
            base64Chars[i] = (char) (base64Bytes[i] & 0xFF);
        }
        return base64Chars;
    }

    public static byte[] base64CharArrayToByteArray(char[] base64Chars) {
        byte[] base64Bytes = new byte[base64Chars.length];
        for (int i = 0; i < base64Chars.length; i++) {
            base64Bytes[i] = (byte) base64Chars[i];
        }
        return Base64.getDecoder().decode(base64Bytes);
    }

    /**
     * The regular {@link BigInteger#toByteArray()} method isn't quite what we often need:
     * it appends a leading zero to indicate that the number is positive and may need padding.
     *
     * @param b        the integer to format into a byte array
     * @param numBytes the desired size of the resulting byte array
     * @return numBytes byte long array.
     */
    public static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        if (b == null)
            return null;
        byte[] bytes = new byte[numBytes];
        byte[] biBytes = b.toByteArray();
        int start = (biBytes.length == numBytes + 1) ? 1 : 0;
        int length = Math.min(biBytes.length, numBytes);
        System.arraycopy(biBytes, start, bytes, numBytes - length, length);
        return bytes;
    }

    /**
     * "a2acdb" --> "dbaca2"
     *
     * @param rawStr
     * @return
     */
    public static String revertHexBy2(String rawStr) {
        rawStr = rawStr.replaceAll(" ", "");
        String newStr = "";
        for (int i = 0; i < (int) rawStr.length() / 2; i = i + 1) {
            int lenth = rawStr.length();
            newStr = newStr + rawStr.substring((lenth - i * 2 - 2), (lenth - i * 2));
        }
        return newStr;
    }

    public static byte[] bigIntegerToBytes(BigInteger value) {
        if (value == null)
            return null;

        byte[] data = value.toByteArray();

        if (data.length != 1 && data[0] == 0) {
            byte[] tmp = new byte[data.length - 1];
            System.arraycopy(data, 1, tmp, 0, tmp.length);
            data = tmp;
        }
        return data;
    }

    //byte数组转char数组
    public static char[] bytesToChars(byte[] b) {
        char[] c = new char[b.length];
        for (byte i : b) {
            c[i] = (char) b[i];
        }
        return c;
    }

    //byte 与 int 的相互转换
    public static byte intToByte(int x) {
        return (byte) x;
    }

    public static int byteToUnsignedInt(byte b) {
        return b & 0xFF;
    }

    //byte 数组与 int 的相互转换
    public static int bytesToIntLE(byte[] a) {
        byte[] b = BytesUtils.invertArray(a);
        return b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
    }

    /**
     * Cast hex encoded value from byte[] to int
     * <p>
     * Limited to Integer.MAX_VALUE: 2^32-1 (4 bytes)
     *
     * @param b array contains the values
     * @return unsigned positive int value.
     */
    public static int bytesToIntBE(byte[] b) {
        if (b == null || b.length == 0)
            return 0;
        return new BigInteger(1, b).intValue();
    }

    public static byte[] intToByteArray(int a) {
        return new byte[]{
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

    //byte 数组与 long 的相互转换

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(0, x);
        return buffer.array();
    }

    public static long bytes8ToLong(byte[] input, boolean littleEndian) {
        long value = 0;
        for (int count = 0; count < 8; ++count) {
            int shift = (littleEndian ? count : (7 - count)) << 3;
            value |= ((long) 0xff << shift) & ((long) input[count] << shift);
        }
        return value;
    }

    public static long bytes4ToLongLE(byte[] bytes) {

        return ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    }

    public static long bytes4ToLongBE(byte[] bytes) {
        return ByteBuffer.wrap(invertArray(bytes))
                .order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;
    }

    public static String bytesToHexStringLE(byte[] data) {
        byte[] bytes = BytesUtils.invertArray(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }


    public static String bytesToHexStringBE(byte[] b) {
        return Hex.toHex(b);
    }


    /**
     * hex字符串转byte数组
     *
     * @param inHex 待转换的Hex字符串
     * @return 转换后的byte数组结果
     */
    public static byte[] hexToByteArray(String inHex) {
        int hexlen = inHex.length();
        boolean isOdd = hexlen % 2 == 1;

        if (isOdd) {
            hexlen++;
            inHex = "0" + inHex;
        }

        byte[] result = new byte[hexlen / 2];

        for (int i = 0, j = 0; i < hexlen; i += 2, j++) {
            result[j] = (byte) Integer.parseInt(inHex.substring(i, i + 2), 16);
        }

        return result;
    }


    public static Date bytesToDate(byte[] b) {
        int i = b[3] & 0xFF |
                (b[2] & 0xFF) << 8 |
                (b[1] & 0xFF) << 16 |
                (b[0] & 0xFF) << 24;
        long l = i * 1000;
        Date t = new Date(l);
        return t;
    }

    public static byte[] invertArray(byte[] a) {
        byte[] b = new byte[a.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = a[b.length - i - 1];
        }
        return b;
    }

    public static String bytesToBinaryString(byte[] b) {
        String s = "";
        for (int i = 0; i < b.length; i++) {
            s = s + Integer.toBinaryString(b[i]);
        }
        return s;
    }

    /**
     * A builder class for efficiently constructing byte arrays.
     * Similar to StringBuilder but for byte arrays.
     */
    public static class ByteArrayBuilder {
        private byte[] buffer;
        private int position;
        private static final int DEFAULT_CAPACITY = 32;

        /**
         * Creates a new ByteArrayBuilder with default initial capacity.
         */
        public ByteArrayBuilder() {
            this(DEFAULT_CAPACITY);
        }

        /**
         * Creates a new ByteArrayBuilder with the specified initial capacity.
         *
         * @param initialCapacity the initial capacity of the buffer
         */
        public ByteArrayBuilder(int initialCapacity) {
            buffer = new byte[initialCapacity];
            position = 0;
        }

        /**
         * Appends a byte array to this builder.
         *
         * @param bytes the byte array to append
         * @return this builder
         */
        public ByteArrayBuilder append(byte[] bytes) {
            if (bytes == null || bytes.length == 0) {
                return this;
            }
            
            ensureCapacity(position + bytes.length);
            System.arraycopy(bytes, 0, buffer, position, bytes.length);
            position += bytes.length;
            return this;
        }

        /**
         * Appends a single byte to this builder.
         *
         * @param b the byte to append
         * @return this builder
         */
        public ByteArrayBuilder append(byte b) {
            ensureCapacity(position + 1);
            buffer[position++] = b;
            return this;
        }

        /**
         * Ensures that the buffer has at least the specified capacity.
         *
         * @param minCapacity the minimum capacity needed
         */
        private void ensureCapacity(int minCapacity) {
            if (minCapacity > buffer.length) {
                int newCapacity = Math.max(buffer.length * 2, minCapacity);
                byte[] newBuffer = new byte[newCapacity];
                System.arraycopy(buffer, 0, newBuffer, 0, position);
                buffer = newBuffer;
            }
        }

        /**
         * Returns a new byte array containing the bytes in this builder.
         *
         * @return a new byte array
         */
        public byte[] toByteArray() {
            byte[] result = new byte[position];
            System.arraycopy(buffer, 0, result, 0, position);
            return result;
        }

        /**
         * Returns the current size of the builder.
         *
         * @return the number of bytes in the builder
         */
        public int size() {
            return position;
        }

        /**
         * Resets this builder, removing all content.
         *
         * @return this builder
         */
        public ByteArrayBuilder reset() {
            position = 0;
            return this;
        }
    }

    /**
     * Merges two byte arrays using ByteArrayBuilder for better efficiency.
     *
     * @param bt1 the first byte array
     * @param bt2 the second byte array
     * @return a new byte array containing the merged content
     */
    public static byte[] bytesMerger(byte[] bt1, byte[] bt2) {
        // Handle null inputs
        if (bt1 == null) {
            return bt2 != null ? bt2.clone() : EMPTY_BYTE_ARRAY;
        }
        if (bt2 == null) {
            return bt1.clone();
        }
        
        return new ByteArrayBuilder(bt1.length + bt2.length)
                .append(bt1)
                .append(bt2)
                .toByteArray();
    }


    

    /**
     * Merges multiple byte arrays using ByteArrayBuilder for better efficiency.
     *
     * @param bytesList the list of byte arrays to merge
     * @return a new byte array containing the merged content
     */
    public static byte[] bytesMerger(List<byte[]> bytesList) {
        // Handle null input
        if (bytesList == null || bytesList.isEmpty()) {
            return EMPTY_BYTE_ARRAY;
        }
        
        // Calculate total length, handling null elements
        int totalLength = 0;
        for (byte[] bytes : bytesList) {
            if (bytes != null) {
                totalLength += bytes.length;
            }
        }
        
        // If all elements were null, return empty array
        if (totalLength == 0) {
            return EMPTY_BYTE_ARRAY;
        }
        
        // Use ByteArrayBuilder to efficiently build the result
        ByteArrayBuilder builder = new ByteArrayBuilder(totalLength);
        for (byte[] src : bytesList) {
            if (src != null) {
                builder.append(src);
            }
        }
        
        return builder.toByteArray();
    }

    /**
     * Merges multiple byte arrays using ByteArrayBuilder for better efficiency.
     * This method accepts a variable number of byte arrays as parameters.
     *
     * @param byteArrays the byte arrays to merge
     * @return a new byte array containing the merged content
     */
    public static byte[] bytesMerger(byte[]... byteArrays) {
        // Handle null input
        if (byteArrays == null || byteArrays.length == 0) {
            return EMPTY_BYTE_ARRAY;
        }
        
        // Calculate total length, handling null elements
        int totalLength = 0;
        for (byte[] bytes : byteArrays) {
            if (bytes != null) {
                totalLength += bytes.length;
            }
        }
        
        // If all elements were null, return empty array
        if (totalLength == 0) {
            return EMPTY_BYTE_ARRAY;
        }
        
        // Use ByteArrayBuilder to efficiently build the result
        ByteArrayBuilder builder = new ByteArrayBuilder(totalLength);
        for (byte[] src : byteArrays) {
            if (src != null) {
                builder.append(src);
            }
        }
        
        return builder.toByteArray();
    }

    public static byte[] getRandomBytes(int len) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[len];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    public static void clearByteArray(byte[] array) {
        if (array != null) {
            Arrays.fill(array, (byte) 0);
            array = null;
        }
    }

    public static void clearCharArray(char[] array) {
        if (array != null) {
            Arrays.fill(array, (char) 0);
        }
    }

    public static boolean isBase64Encoded(char[] chars) {
        int length = chars.length;

        // Check if length is a multiple of 4
        if (length % 4 != 0) {
            return false;
        }

        int countPadding = 0;

        for (int i = 0; i < length; i++) {
            char c = chars[i];

            boolean isBase64Char = (c >= 'A' && c <= 'Z') ||
                    (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') ||
                    (c == '+') ||
                    (c == '/');

            // Handle padding characters
            if (c == '=') {
                countPadding++;
                // Padding characters should only be at the end
                if (i < length - 2) {
                    return false;
                }
            } else if (countPadding > 0) {
                // If we have seen a padding character, no other character is allowed after it
                return false;
            }

            if (!isBase64Char && c != '=') {
                return false;
            }
        }

        // Check if there are no more than 2 padding characters
        if (countPadding > 2) {
            return false;
        }

        return true;
    }

    public static boolean isFilledKey(byte[] key) {
        for (byte b : key) {
            if (b != (byte) 0) return false;
        }
        return true;
    }

    public static boolean isFilledKey(char[] key) {
        for (char c : key) {
            if (c != (byte) 0) return false;
        }
        return true;
    }

    public static int bytes2ToIntBE(byte[] byteArray) {
        return ((byteArray[0] & 0xFF) << 8) | (byteArray[1] & 0xFF);
    }
    public static byte[] intTo2ByteArray(int value) {
        byte[] result = new byte[2];
        result[0] = (byte) (value >> 8);  // Extract high byte
        result[1] = (byte) (value);       // Extract low byte
        return result;
    }
    public static int bytes2ToIntLE(byte[] byteArray) {
        return ((byteArray[1] & 0xFF) << 8) | (byteArray[0] & 0xFF);
    }

    public static byte[] addByteArray(byte[] original, byte[] add) {
        byte[] total = new byte[original.length + add.length];  // For AES-256
        System.arraycopy(original, 0, total, 0, original.length);
        System.arraycopy(add, 0, total, original.length, add.length);
        return total;
    }

    public static long byte4ArrayToUnsignedInt(byte[] byteArray) {
        if (byteArray.length != 4) {
            throw new IllegalArgumentException("Input byte array must have exactly 4 bytes.");
        }

        long result = 0;

        for (int i = 0; i < 4; i++) {
            // Convert each byte to a long (treat it as unsigned)
            long unsignedByte = byteArray[i] & 0xFFL;

            // Shift and combine the bytes into the result
            result |= unsignedByte << (8 * i);
        }

        return result;
    }

    public static byte[] getPartOfBytes(byte[] original, int offset, int length) {
        byte[] part = new byte[length];
        System.arraycopy(original, offset, part, 0, part.length);
        return part;
    }

    /**
     * Compares two byte arrays lexicographically.
     * This is a replacement for Arrays.compare() which is only available in API 33+.
     *
     * @param a the first byte array to compare
     * @param b the second byte array to compare
     * @return the value 0 if the first and second byte array are equal and contain the same elements in the same order;
     *         a value less than 0 if the first byte array is lexicographically less than the second byte array;
     *         and a value greater than 0 if the first byte array is lexicographically greater than the second byte array.
     */
    public static int compareByteArrays(byte[] a, byte[] b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        
        int length = Math.min(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int result = Byte.compare(a[i], b[i]);
            if (result != 0) {
                return result;
            }
        }
        return a.length - b.length;
    }
}


