package com.fc.fc_ajdk.core.crypto;

/**
 * Central location for cryptographic constants used across the crypto package.
 */
public final class CryptoConstants {
    private CryptoConstants() {}

    // Key sizes (bytes)
    public static final int KEY_LENGTH_256 = 32;
    public static final int AES_KEY_SIZE = 32;
    public static final int EXTENDED_KEY_LENGTH = 64;
    public static final int IV_SIZE_AES_CBC = 16;
    public static final int IV_LENGTH_CBC = 16;
    public static final int IV_LENGTH_GCM = 12;
    public static final int IV_SIZE_CHACHA20 = 12;
    public static final int IV_LENGTH_CHACHA20 = 12;
    public static final int SYMKEY_SIZE = 32;
    public static final int PRIKEY_SIZE = 32;
    public static final int PUBKEY_COMPRESSED_SIZE = 33;
    public static final int PUBKEY_COMPRESSED_LENGTH = 33;
    public static final int PUBKEY_X25519_LENGTH = 32;
    public static final int PUBKEY_UNCOMPRESSED_SIZE = 65;

    // Hash sizes (bytes)
    public static final int SHA256_SIZE = 32;
    public static final int HMAC_SHA256_LENGTH = 32;
    public static final int SHA512_SIZE = 64;
    public static final int RIPEMD160_SIZE = 20;
    public static final int SUM_SIZE = 4;
    public static final int SUM_LENGTH = 4;
    public static final int KEY_NAME_SIZE = 6;
    public static final int KEY_NAME_LENGTH = 6;

    // Algorithm identifiers (byte arrays)
    public static final int ALG_ID_SIZE = 6;
    public static final int ALG_BYTES_LENGTH = 6;

    // GCM tag length
    public static final int GCM_TAG_LENGTH_BITS = 128;

    // PBKDF2 parameters
    public static final int PBKDF2_ITERATIONS = 2048;
    public static final int PBKDF2_DK_LEN = 64;

    // BIP44 constants
    public static final int BIP44_PURPOSE = 0x8000002C;   // 44'
    public static final int BIP44_COIN_BTC = 0x80000000;  // 0' (Bitcoin)
    public static final int BIP44_COIN_FCH = 0x80000000;  // 0' (Freecash uses same as BTC)
}
