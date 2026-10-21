package com.fc.safe.myKeys;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.JsonUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tells what a typed, pasted or scanned key text is, so one import screen can take
 * a private key, public key, FID, password cipher or key backup JSON.
 *
 * <p>The order of the checks matters: hex, WIF and FID text are all valid Base64
 * too, so the checksummed and fixed-shape forms are tried before a Base64 cipher
 * bundle. A phrase is never guessed: any text could be one, and a mistyped key
 * must not quietly become a different key.
 */
public final class KeyInputDetector {

    public enum Kind {
        EMPTY,
        /** 64 hex chars, 0x-prefixed hex, or WIF. */
        PRIKEY,
        /** Compressed or uncompressed public key hex. Watch-only. */
        PUBKEY,
        /** Watch-only. */
        FID,
        /** One password-encrypted object, as JSON or a Base64 bundle. */
        CIPHER,
        /** Encrypted, but not with a password, so it can't be opened here. */
        NON_PASSWORD_CIPHER,
        /** Key JSON: KeyInfo, backup header/key lines, or several ciphers. */
        BACKUP,
        /** Shaped like a private key, but not a valid one. */
        BAD_PRIKEY,
        /** Shaped like a public key, but not a valid one. */
        BAD_PUBKEY,
        /** Shaped like an FID, but the checksum fails. */
        BAD_FID,
        /** Several items; only backup JSON may carry more than one key. */
        MULTIPLE,
        UNKNOWN
    }

    private static final Pattern BASE58 = Pattern.compile("[1-9A-HJ-NP-Za-km-z]+");
    private static final Pattern ALPHANUMERIC = Pattern.compile("[0-9A-Za-z]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private KeyInputDetector() {}

    public static Kind detect(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) return Kind.EMPTY;

        if (text.startsWith("{")) return detectJson(text);
        if (WHITESPACE.matcher(text).find()) return Kind.MULTIPLE;

        if (toPrikey32(text) != null) return Kind.PRIKEY;
        if (KeyTools.isPubkey(text)) return Kind.PUBKEY;
        if (isFid(text)) return Kind.FID;

        CryptoDataByte bundle = parseBundle(text);
        if (bundle != null) return isPasswordCipher(bundle) ? Kind.CIPHER : Kind.NON_PASSWORD_CIPHER;

        if (looksLikePrikey(text)) return Kind.BAD_PRIKEY;
        if (looksLikePubkey(text)) return Kind.BAD_PUBKEY;
        if (looksLikeFid(text)) return Kind.BAD_FID;
        return Kind.UNKNOWN;
    }

    /** The 32-byte private key the text encodes, or null. Never throws. */
    public static byte[] toPrikey32(String text) {
        try {
            return KeyTools.getPrikey32(text);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A fresh parse of a {@link Kind#CIPHER} input, or null. Decryption fills in the
     * parsed object, so parse again for every attempt.
     */
    public static CryptoDataByte parseCipher(String input) {
        String text = input == null ? "" : input.trim();
        if (!text.startsWith("{")) return parseBundle(text);
        List<String> objects = readJsonObjects(text);
        return objects.size() == 1 ? parseCipherJson(objects.get(0)) : null;
    }

    /**
     * The private key inside a decrypted cipher: the raw 32 bytes, or its hex or WIF
     * text (BackupPrikeyDialog encrypts the WIF text for QR codes). Null when the
     * plaintext is something else, such as a key JSON. The input is left untouched.
     */
    public static byte[] prikeyFromPlaintext(byte[] data) {
        if (data == null) return null;
        if (data.length == 32) return Arrays.copyOf(data, 32);
        return toPrikey32(new String(data, StandardCharsets.UTF_8).trim());
    }

    public static boolean isJson(byte[] data) {
        return data != null && new String(data, StandardCharsets.UTF_8).trim().startsWith("{");
    }

    private static Kind detectJson(String text) {
        List<String> objects = readJsonObjects(text);
        if (objects.isEmpty()) return Kind.UNKNOWN;
        if (objects.size() == 1) {
            CryptoDataByte cipher = parseCipherJson(objects.get(0));
            if (cipher != null) return isPasswordCipher(cipher) ? Kind.CIPHER : Kind.NON_PASSWORD_CIPHER;
        }
        return Kind.BACKUP;
    }

    private static List<String> readJsonObjects(String text) {
        List<String> objects = new ArrayList<>();
        try (InputStream is = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
            byte[] json;
            while ((json = JsonUtils.readOneJsonFromInputStream(is)) != null) {
                objects.add(new String(json, StandardCharsets.UTF_8));
            }
        } catch (Exception ignore) {
        }
        return objects;
    }

    private static CryptoDataByte parseCipherJson(String json) {
        try {
            return withCipher(CryptoDataByte.fromJson(json));
        } catch (Exception e) {
            return null;
        }
    }

    private static CryptoDataByte parseBundle(String text) {
        try {
            return withCipher(CryptoDataByte.fromBase64(text));
        } catch (Exception e) {
            return null;
        }
    }

    private static CryptoDataByte withCipher(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte == null || cryptoDataByte.getCipher() == null || cryptoDataByte.getIv() == null) {
            return null;
        }
        return cryptoDataByte;
    }

    /** A JSON cipher without a type was always treated as password-encrypted before. */
    private static boolean isPasswordCipher(CryptoDataByte cryptoDataByte) {
        return cryptoDataByte.getType() == null || cryptoDataByte.getType() == EncryptType.Password;
    }

    private static boolean isFid(String text) {
        return text.length() >= 26 && text.length() <= 35
                && BASE58.matcher(text).matches() && KeyTools.isGoodFid(text);
    }

    private static boolean looksLikePrikey(String text) {
        int len = text.length();
        if (BASE58.matcher(text).matches()) {
            if (len == 52 && (text.startsWith("K") || text.startsWith("L"))) return true;
            if (len == 51 && text.startsWith("5")) return true;
        }
        if (len == 66 && (text.startsWith("0x") || text.startsWith("0X"))) {
            return ALPHANUMERIC.matcher(text.substring(2)).matches();
        }
        return len == 64 && ALPHANUMERIC.matcher(text).matches();
    }

    private static boolean looksLikePubkey(String text) {
        int len = text.length();
        return (len == 66 || len == 130) && ALPHANUMERIC.matcher(text).matches();
    }

    private static boolean looksLikeFid(String text) {
        int len = text.length();
        return (len == 33 || len == 34) && (text.startsWith("F") || text.startsWith("3"))
                && BASE58.matcher(text).matches();
    }
}
