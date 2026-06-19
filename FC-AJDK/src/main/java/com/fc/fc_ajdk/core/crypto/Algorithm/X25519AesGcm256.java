package com.fc.fc_ajdk.core.crypto.Algorithm;


import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * X25519AesGcm256 combines X25519 key exchange with HKDF key derivation and AES-GCM-256 encryption.
 */
public class X25519AesGcm256 implements AsymmetricCipher {

    public static final String INFO = "hkdf";

    private static final X25519AesGcm256 INSTANCE = new X25519AesGcm256();

    public static X25519AesGcm256 getInstance() {
        return INSTANCE;
    }

    @Override
    public byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        return X25519.getSharedSecret(priKeyBytes, pubKeyBytes);
    }

    @Override
    @NotNull
    public byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) throws Exception {
        return HKDF.hkdf(sharedSecret, nonce, INFO.getBytes(), 32);
    }

    @Override
    public byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        byte[] symkey;

        byte[] sharedSecret = getSharedSecret(priKey, pubKey);

        symkey = sharedSecretToSymkey(sharedSecret, nonce);

        Arrays.fill(sharedSecret, (byte) 0);

        return symkey;
    }

    @Override
    public CryptoDataByte encrypt(byte[] plaintext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        byte[] symkey = asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setAlg(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);

        ByteArrayInputStream bis = new ByteArrayInputStream(plaintext);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        AesGcm256.encrypt(bis, bos, symkey, nonce, cryptoDataByte);

        if (cryptoDataByte.getCode() == 0 || cryptoDataByte.getCode() == null) {
            cryptoDataByte.setCipher(bos.toByteArray());
            cryptoDataByte.setAlg(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);
        }

        return cryptoDataByte;
    }

    @Override
    public CryptoDataByte decrypt(byte[] ciphertext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        byte[] symkey = asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setCipher(ciphertext);
        cryptoDataByte.setAlg(AlgorithmId.FC_AesGcm256_No1_NrC7);

        AesGcm256.decrypt(cryptoDataByte);

        cryptoDataByte.setAlg(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);

        return cryptoDataByte;
    }

    public static CryptoDataByte encryptStream(java.io.InputStream inputStream, java.io.OutputStream outputStream,
                                               byte[] priKey, byte[] pubKey, byte[] nonce, CryptoDataByte cryptoDataByte) throws Exception {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        byte[] symkey = X25519.asyKeyToSymkey(priKey, pubKey, nonce);
        try {
            cryptoDataByte.setSymkey(symkey);
            cryptoDataByte.setIv(nonce);
            cryptoDataByte.setAlg(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);

            AesGcm256.encrypt(inputStream, outputStream, symkey, nonce, cryptoDataByte);

            cryptoDataByte.setAlg(AlgorithmId.FC_X25519AesGcm256_No1_NrC7);

            return cryptoDataByte;
        } finally {
            java.util.Arrays.fill(symkey, (byte) 0);
        }
    }

    public static void decryptStream(java.io.InputStream inputStream, java.io.OutputStream outputStream,
                                     byte[] priKey, byte[] pubKey, byte[] nonce, CryptoDataByte cryptoDataByte) throws Exception {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        byte[] symkey = X25519.asyKeyToSymkey(priKey, pubKey, nonce);
        try {
            cryptoDataByte.setSymkey(symkey);
            cryptoDataByte.setIv(nonce);

            AesGcm256.decryptStream(inputStream, outputStream, cryptoDataByte);
        } finally {
            java.util.Arrays.fill(symkey, (byte) 0);
        }
    }

    public static byte[] generatePublicKey(byte[] priKeyBytes) {
        return X25519.generatePublicKey(priKeyBytes);
    }
}
