package com.fc.fc_ajdk.core.crypto.Algorithm;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HKDF {

    private static final String HMAC_ALGO = "HmacSHA512";
    private static final int HASH_LEN = 64; // SHA-512 output bytes

    private HKDF() {}

    /**
     * HKDF-Extract(salt, IKM) -> PRK
     */
    public static byte[] extract(final byte[] salt, final byte[] ikm) throws Exception {
        byte[] effectiveSalt = salt;
        if (effectiveSalt == null || effectiveSalt.length == 0) {
            effectiveSalt = new byte[HASH_LEN]; // all zeros per RFC5869
        }
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(effectiveSalt, HMAC_ALGO));
        return mac.doFinal(ikm);
    }

    /**
     * HKDF-Expand(PRK, info, L) -> OKM (length L bytes)
     */
    public static byte[] expand(final byte[] prk, final byte[] info, final int length) throws Exception {
        if (length <= 0 || length > 255 * HASH_LEN) {
            throw new IllegalArgumentException("length must be between 1 and " + (255 * HASH_LEN));
        }
        int n = (int) Math.ceil((double) length / HASH_LEN);
        byte[] okm = new byte[length];
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(prk, HMAC_ALGO));

        byte[] previousT = new byte[0];
        int copied = 0;
        for (int i = 1; i <= n; i++) {
            mac.reset();
            mac.update(previousT);
            if (info != null) mac.update(info);
            mac.update((byte) i);
            byte[] t = mac.doFinal();
            int toCopy = Math.min(t.length, length - copied);
            System.arraycopy(t, 0, okm, copied, toCopy);
            copied += toCopy;
            previousT = t;
        }
        return okm;
    }

    /**
     * Convenience: full HKDF (Extract then Expand)
     * salt may be null/empty, info may be null
     */
    public static byte[] hkdf(final byte[] ikm, final byte[] salt, final byte[] info, final int length) throws Exception {
        byte[] prk = extract(salt, ikm);
        return expand(prk, info, length);
    }
}
