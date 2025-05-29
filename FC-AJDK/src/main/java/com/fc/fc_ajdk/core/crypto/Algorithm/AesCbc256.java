package com.fc.fc_ajdk.core.crypto.Algorithm;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.Hash;

import com.fc.fc_ajdk.data.fcData.AlgorithmId;

import java.io.*;

public class AesCbc256 {
    public static CryptoDataByte encrypt(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv, CryptoDataByte cryptoDataByte) {
        String algorithm = "AES";
        String transformation = "AES/CBC/PKCS7Padding";
        String provider = "BC";
        if(cryptoDataByte==null) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setSymkey(key);
            cryptoDataByte.setIv(iv);
            if(cryptoDataByte.getAlg()==null) cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
        }else if(cryptoDataByte.getSymkey()==null)cryptoDataByte.setSymkey(key);
        else if(cryptoDataByte.getIv()==null)cryptoDataByte.setIv(iv);
        if(cryptoDataByte.getAlg()==null) cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
        return Encryptor.encryptBySymkeyBase(algorithm,transformation,provider,inputStream,outputStream,cryptoDataByte);
    }
    public static CryptoDataByte encrypt(InputStream inputStream, OutputStream outputStream,CryptoDataByte cryptoDataByte) {
        return encrypt(inputStream,outputStream,null,null,cryptoDataByte);
    }

    public static CryptoDataByte encryptStream(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv) {
        return encrypt(inputStream,outputStream,key,iv,null);
    }

    /*
    Decrypt
     */

//    public static CryptoDataByte decrypt(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv, @Nullable CryptoDataByte cryptoDataByte) {
//
//        String algorithm = "AES";
//        String transformation = "AES/CBC/PKCS7Padding";
//        String provider = "BC";
//        return DecryptorSym.decryptBase(algorithm,transformation,provider,inputStream,outputStream,key,iv,cryptoDataByte);
//    }

    public static void decryptStream(InputStream inputStream, OutputStream outputStream, CryptoDataByte cryptoDataByte) {

        String algorithm = "AES";
        String transformation = "AES/CBC/PKCS7Padding";
        String provider = "BC";
        Decryptor.decryptBySymkeyBase(algorithm,transformation,provider,inputStream,outputStream,cryptoDataByte);
    }
    public static CryptoDataByte decryptStream(InputStream inputStream, OutputStream outputStream, byte[] key, byte[] iv ){
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setSymkey(key);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setType(EncryptType.Symkey);
        cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
        decryptStream(inputStream,outputStream,cryptoDataByte);
        return cryptoDataByte;
    }

    public static CryptoDataByte decrypt(CryptoDataByte cryptoDataByte) {
        try (ByteArrayInputStream bisCipher = new ByteArrayInputStream(cryptoDataByte.getCipher());
             ByteArrayOutputStream bosData = new ByteArrayOutputStream()) {
            decryptStream(bisCipher, bosData, cryptoDataByte);
            byte[] data = bosData.toByteArray();
            byte[] did = Hash.sha256x2(data);

            cryptoDataByte.setDid(did);
            if(cryptoDataByte.checkSum(AlgorithmId.FC_AesCbc256_No1_NrC7)) {
                cryptoDataByte.setData(data);
                cryptoDataByte.set0CodeMessage();
            }
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(10);
            return cryptoDataByte;
        }
        return cryptoDataByte;
    }
}
