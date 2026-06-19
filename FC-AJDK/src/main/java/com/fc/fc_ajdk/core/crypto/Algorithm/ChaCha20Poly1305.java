package com.fc.fc_ajdk.core.crypto.Algorithm;

import com.google.common.hash.Hashing;
import com.fc.fc_ajdk.constants.CodeMessage;
import com.fc.fc_ajdk.core.crypto.CryptoConstants;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.EncryptType;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;

/**
 * ChaCha20-Poly1305 AEAD cipher implementation (RFC 8439).
 */
public class ChaCha20Poly1305 {

    private static final String ALGORITHM = "ChaCha20";
    private static final String TRANSFORMATION = "ChaCha20-Poly1305";
    private static final int TAG_LENGTH_BITS = CryptoConstants.GCM_TAG_LENGTH_BITS;

    public static CryptoDataByte encrypt(InputStream inputStream, OutputStream outputStream,
                                         byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        if (key == null || key.length != CryptoConstants.KEY_LENGTH_256) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4008WrongKeyLength,
                "ChaCha20-Poly1305 requires a 32-byte key");
            return cryptoDataByte;
        }

        if (iv == null || iv.length != CryptoConstants.IV_LENGTH_CHACHA20) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv,
                "ChaCha20-Poly1305 requires a 12-byte nonce");
            return cryptoDataByte;
        }

        cryptoDataByte.setSymkey(key);
        cryptoDataByte.setIv(iv);

        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            GCMParameterSpec paramSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec);

            var hasherIn = Hashing.sha256().newHasher();

            byte[] buffer = new byte[4096];
            int bytesRead;

            try (CipherOutputStream cos = new CipherOutputStream(outputStream, cipher)) {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    hasherIn.putBytes(buffer, 0, bytesRead);
                    cos.write(buffer, 0, bytesRead);
                }
            }

            byte[] did = Decryptor.sha256(hasherIn.hash().asBytes());
            cryptoDataByte.setDid(did);

            if (cryptoDataByte.getAlg() == null) {
                cryptoDataByte.setAlg(com.fc.fc_ajdk.data.fcData.AlgorithmId.FC_ChaCha20Poly1305_No1_NrC7);
            }

            if (cryptoDataByte.getType() == null) {
                cryptoDataByte.setType(EncryptType.Symkey);
            }

            cryptoDataByte.set0CodeMessage();
            return cryptoDataByte;

        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt,
                "ChaCha20-Poly1305 encryption failed: " + e.getMessage());
            return cryptoDataByte;
        }
    }

    public static CryptoDataByte encrypt(ByteArrayInputStream bisMsg, ByteArrayOutputStream bosCipher,
                                         byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        return encrypt((InputStream) bisMsg, (OutputStream) bosCipher, key, iv, cryptoDataByte);
    }

    public static CryptoDataByte decrypt(CryptoDataByte cryptoDataByte) {
        byte[] cipherBytes = cryptoDataByte.getCipher();
        if (cipherBytes == null) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher, "Cipher is null");
            return cryptoDataByte;
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(cipherBytes);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            decryptStream(bis, bos, cryptoDataByte);

            if (cryptoDataByte.getCode() == 0) {
                cryptoDataByte.setData(bos.toByteArray());
                cryptoDataByte.makeDid();
            }

            return cryptoDataByte;
        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1029FailedToDecrypt,
                "ChaCha20-Poly1305 decryption failed: " + e.getMessage());
            return cryptoDataByte;
        }
    }

    public static void decryptStream(InputStream inputStream, OutputStream outputStream,
                                     CryptoDataByte cryptoDataByte) {
        byte[] key = cryptoDataByte.getSymkey();
        byte[] iv = cryptoDataByte.getIv();

        if (key == null || key.length != CryptoConstants.KEY_LENGTH_256) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4008WrongKeyLength,
                "ChaCha20-Poly1305 requires a 32-byte key");
            return;
        }

        if (iv == null || iv.length != CryptoConstants.IV_LENGTH_CHACHA20) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv,
                "ChaCha20-Poly1305 requires a 12-byte nonce");
            return;
        }

        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            GCMParameterSpec paramSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);

            var hasherIn = Hashing.sha256().newHasher();

            try (CipherOutputStream cos = new CipherOutputStream(outputStream, cipher)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    hasherIn.putBytes(buffer, 0, bytesRead);
                    cos.write(buffer, 0, bytesRead);
                }
            }

            byte[] cipherId = Decryptor.sha256(hasherIn.hash().asBytes());
            cryptoDataByte.setCipherId(cipherId);
            cryptoDataByte.set0CodeMessage();

        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1029FailedToDecrypt,
                "ChaCha20-Poly1305 decryption failed: " + e.getMessage());
        }
    }
}
