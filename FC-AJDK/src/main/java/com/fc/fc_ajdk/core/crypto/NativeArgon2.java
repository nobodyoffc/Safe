package com.fc.fc_ajdk.core.crypto;

/**
 * Argon2id in native code (the reference implementation, see src/main/cpp). It hashes exactly as
 * BouncyCastle's Argon2BytesGenerator does, many times faster on a phone, where ART runs the Java
 * version cold. Where the library is absent, as in JVM unit tests, {@link #isAvailable()} is false.
 */
final class NativeArgon2 {
    /** The reference implementation refuses shorter salts; BouncyCastle takes any. */
    static final int MIN_SALT_LENGTH = 8;

    private static final boolean AVAILABLE = load();

    private NativeArgon2() {
    }

    private static boolean load() {
        try {
            System.loadLibrary("fcargon2");
            return true;
        } catch (UnsatisfiedLinkError | SecurityException e) {
            return false;
        }
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    /** @return the raw Argon2id v1.3 hash, or null if the inputs are out of range or memory runs out. */
    static native byte[] argon2id(byte[] password, byte[] salt, int iterations, int memoryKib, int parallelism, int outLen);
}
