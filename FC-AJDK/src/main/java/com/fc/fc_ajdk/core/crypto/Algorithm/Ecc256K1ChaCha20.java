package com.fc.fc_ajdk.core.crypto.Algorithm;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Ecc256K1ChaCha20 combines secp256k1 ECDH with HKDF key derivation and ChaCha20 encryption.
 */
public class Ecc256K1ChaCha20 implements AsymmetricCipher {

    private static final Ecc256K1ChaCha20 INSTANCE = new Ecc256K1ChaCha20();

    public static Ecc256K1ChaCha20 getInstance() {
        return INSTANCE;
    }

    public static final String INFO = "hkdf-chacha20";

    @Override
    public byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        return Ecc256K1Hkdf.getSharedSecret(priKeyBytes, pubKeyBytes);
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
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);

        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(plaintext);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();

        ChaCha20.encrypt(bis, bos, symkey, nonce, cryptoDataByte);

        if (cryptoDataByte.getCode() == 0 || cryptoDataByte.getCode() == null) {
            cryptoDataByte.setCipher(bos.toByteArray());
            cryptoDataByte.setAlg(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);
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
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);

        ChaCha20.decrypt(cryptoDataByte);

        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);

        return cryptoDataByte;
    }

    public static CryptoDataByte encryptStream(java.io.InputStream inputStream, java.io.OutputStream outputStream,
                                               byte[] priKey, byte[] pubKey, byte[] nonce, CryptoDataByte cryptoDataByte) throws Exception {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        byte[] symkey = INSTANCE.asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);

        ChaCha20.encrypt(inputStream, outputStream, symkey, nonce, cryptoDataByte);

        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7);

        return cryptoDataByte;
    }

    public static void decryptStream(java.io.InputStream inputStream, java.io.OutputStream outputStream,
                                     byte[] priKey, byte[] pubKey, byte[] nonce, CryptoDataByte cryptoDataByte) throws Exception {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        byte[] symkey = INSTANCE.asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);

        ChaCha20.decryptStream(inputStream, outputStream, cryptoDataByte);
    }
}
