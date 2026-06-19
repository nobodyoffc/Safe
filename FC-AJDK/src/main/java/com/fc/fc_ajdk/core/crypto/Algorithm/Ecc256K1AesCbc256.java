package com.fc.fc_ajdk.core.crypto.Algorithm;


import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Ecc256K1AesCbc256 combines secp256k1 ECDH with SHA512 key derivation and AES-CBC-256 encryption.
 */
public class Ecc256K1AesCbc256 implements AsymmetricCipher {

    private static final Ecc256K1AesCbc256 INSTANCE = new Ecc256K1AesCbc256();

    public static Ecc256K1AesCbc256 getInstance() {
        return INSTANCE;
    }

    @Override
    public byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes) {
        return Ecc256K1.getSharedSecret(priKeyBytes, pubKeyBytes);
    }

    @Override
    @NotNull
    public byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) {
        return Ecc256K1.sharedSecretToSymkey(sharedSecret, nonce);
    }

    @Override
    public byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey, byte[] nonce) {
        return Ecc256K1.asyKeyToSymkey(priKey, pubKey, nonce);
    }

    @Override
    public CryptoDataByte encrypt(byte[] plaintext, byte[] priKey, byte[] pubKey, byte[] nonce) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        byte[] symkey = asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);

        ByteArrayInputStream bis = new ByteArrayInputStream(plaintext);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        AesCbc256.encrypt(bis, bos, symkey, nonce, cryptoDataByte);

        if (cryptoDataByte.getCode() == 0 || cryptoDataByte.getCode() == null) {
            cryptoDataByte.setCipher(bos.toByteArray());
            cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        }

        return cryptoDataByte;
    }

    @Override
    public CryptoDataByte decrypt(byte[] ciphertext, byte[] priKey, byte[] pubKey, byte[] nonce) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        byte[] symkey = asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setCipher(ciphertext);
        cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);

        AesCbc256.decrypt(cryptoDataByte);

        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);

        return cryptoDataByte;
    }

    public static CryptoDataByte encryptStream(java.io.InputStream inputStream, java.io.OutputStream outputStream,
                                               byte[] priKey, byte[] pubKey, byte[] nonce, CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        byte[] symkey = Ecc256K1.asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);

        AesCbc256.encrypt(inputStream, outputStream, symkey, nonce, cryptoDataByte);

        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);

        return cryptoDataByte;
    }

    public static void decryptStream(java.io.InputStream inputStream, java.io.OutputStream outputStream,
                                     byte[] priKey, byte[] pubKey, byte[] nonce, CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        byte[] symkey = Ecc256K1.asyKeyToSymkey(priKey, pubKey, nonce);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(nonce);

        AesCbc256.decryptStream(inputStream, outputStream, cryptoDataByte);
    }
}
