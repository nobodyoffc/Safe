package com.fc.fc_ajdk.core.crypto.Algorithm;

import com.fc.fc_ajdk.core.crypto.Encryptor;

import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class X25519 {

    public static final int PRIVATE_KEY_SIZE = 32;
    public static final int PUBLIC_KEY_SIZE = 32;
    public static final int SHARED_SECRET_SIZE = 32;

    public static byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        if (priKeyBytes == null || priKeyBytes.length != PRIVATE_KEY_SIZE) {
            throw new IllegalArgumentException("Private key must be " + PRIVATE_KEY_SIZE + " bytes");
        }
        if (pubKeyBytes == null || pubKeyBytes.length != PUBLIC_KEY_SIZE) {
            throw new IllegalArgumentException("Public key must be " + PUBLIC_KEY_SIZE + " bytes");
        }

        X25519PrivateKeyParameters priKey = new X25519PrivateKeyParameters(priKeyBytes, 0);
        X25519PublicKeyParameters pubKey = new X25519PublicKeyParameters(pubKeyBytes, 0);

        X25519Agreement agreement = new X25519Agreement();
        agreement.init(priKey);

        byte[] sharedSecret = new byte[SHARED_SECRET_SIZE];
        agreement.calculateAgreement(pubKey, sharedSecret, 0);

        return sharedSecret;
    }

    @NotNull
    public static byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) {
        byte[] symkey;
        byte[] secretHashWithNonce = new byte[sharedSecret.length + nonce.length];
        System.arraycopy(nonce, 0, secretHashWithNonce, 0, nonce.length);
        System.arraycopy(sharedSecret, 0, secretHashWithNonce, nonce.length, sharedSecret.length);
        byte[] hash = Encryptor.sha512(secretHashWithNonce);

        symkey = new byte[32];
        System.arraycopy(hash, 0, symkey, 0, 32);
        return symkey;
    }

    public static byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey, byte[] iv) {
        byte[] symkey;

        byte[] sharedSecret = getSharedSecret(priKey, pubKey);

        symkey = sharedSecretToSymkey(sharedSecret, iv);

        Arrays.fill(sharedSecret, (byte) 0);
        return symkey;
    }

    public static byte[] generatePublicKey(byte[] priKeyBytes) {
        if (priKeyBytes == null || priKeyBytes.length != PRIVATE_KEY_SIZE) {
            throw new IllegalArgumentException("Private key must be " + PRIVATE_KEY_SIZE + " bytes");
        }

        X25519PrivateKeyParameters priKey = new X25519PrivateKeyParameters(priKeyBytes, 0);
        byte[] pubKey = new byte[PUBLIC_KEY_SIZE];
        priKey.generatePublicKey().encode(pubKey, 0);

        return pubKey;
    }
}
