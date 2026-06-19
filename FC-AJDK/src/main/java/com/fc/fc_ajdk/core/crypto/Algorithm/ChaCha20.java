package com.fc.fc_ajdk.core.crypto.Algorithm;

import com.fc.fc_ajdk.constants.CodeMessage;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.google.common.hash.Hashing;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Security;

/**
 * ChaCha20 stream cipher implementation using BouncyCastle provider.
 */
public class ChaCha20 {

    private static final String ALGORITHM = "ChaCha20";
    private static final String TRANSFORMATION = "ChaCha20";
    private static final String PROVIDER = "BC";

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static CryptoDataByte encrypt(InputStream inputStream, OutputStream outputStream,
                                         byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte == null) {
            cryptoDataByte = new CryptoDataByte();
        }

        if (key == null || key.length != 32) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4008WrongKeyLength,
                "ChaCha20 requires a 32-byte key");
            return cryptoDataByte;
        }

        if (iv == null || iv.length != 12) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv,
                "ChaCha20 requires a 12-byte nonce");
            return cryptoDataByte;
        }

        cryptoDataByte.setSymkey(key);
        cryptoDataByte.setIv(iv);

        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            var hashFunction = Hashing.sha256();
            var hasherIn = hashFunction.newHasher();

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

            if(cryptoDataByte.getAlg() == null) {
                cryptoDataByte.setAlg(AlgorithmId.FC_ChaCha20_No1_NrC7);
            }

            if(cryptoDataByte.getType() == null) {
                cryptoDataByte.setType(EncryptType.Symkey);
            }

            cryptoDataByte.makeSum4();

            cryptoDataByte.set0CodeMessage();
            return cryptoDataByte;

        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt,
                "ChaCha20 encryption failed: " + e.getMessage());
            return cryptoDataByte;
        }
    }

    public static CryptoDataByte encrypt(ByteArrayInputStream bisMsg, ByteArrayOutputStream bosCipher,
                                         byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        return encrypt((InputStream) bisMsg, (OutputStream) bosCipher, key, iv, cryptoDataByte);
    }

    public static CryptoDataByte decrypt(CryptoDataByte cryptoDataByte) {
        byte[] cipher = cryptoDataByte.getCipher();
        if (cipher == null) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher, "Cipher is null");
            return cryptoDataByte;
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(cipher);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            decryptStream(bis, bos, cryptoDataByte);

            if (cryptoDataByte.getCode() == 0) {
                cryptoDataByte.setData(bos.toByteArray());
                cryptoDataByte.makeDid();

                if (!cryptoDataByte.checkSum()) {
                    cryptoDataByte.setCodeMessage(CodeMessage.Code4011BadSum);
                }
            }

            return cryptoDataByte;
        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1029FailedToDecrypt,
                "ChaCha20 decryption failed: " + e.getMessage());
            return cryptoDataByte;
        }
    }

    public static void decryptStream(InputStream inputStream, OutputStream outputStream,
                                     CryptoDataByte cryptoDataByte) {
        byte[] key = cryptoDataByte.getSymkey();
        byte[] iv = cryptoDataByte.getIv();

        if (key == null || key.length != 32) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4008WrongKeyLength,
                "ChaCha20 requires a 32-byte key");
            return;
        }

        if (iv == null || iv.length != 12) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv,
                "ChaCha20 requires a 12-byte nonce");
            return;
        }

        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            var hashFunction = Hashing.sha256();
            var hasherIn = hashFunction.newHasher();

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
                "ChaCha20 decryption failed: " + e.getMessage());
        }
    }
}
