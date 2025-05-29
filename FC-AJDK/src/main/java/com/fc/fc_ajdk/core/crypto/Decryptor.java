package com.fc.fc_ajdk.core.crypto;

import com.google.common.hash.Hashing;

import com.fc.fc_ajdk.constants.CodeMessage;
import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.core.crypto.Algorithm.AesCbc256;
import com.fc.fc_ajdk.core.crypto.Algorithm.Bitcore;
import com.fc.fc_ajdk.core.crypto.Algorithm.Ecc256K1;
import com.fc.fc_ajdk.core.crypto.old.EccAes256K1P7;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.FileUtils;
import com.fc.fc_ajdk.utils.Hex;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.util.Arrays;

public class Decryptor {

    public Decryptor() {}

    public static byte[] decryptPrikey(String userPrikeyCipher, byte[] symkey) {
        CryptoDataByte cryptoResult = new Decryptor().decryptJsonBySymkey(userPrikeyCipher, symkey);
        if (cryptoResult.getCode() != 0) {
            cryptoResult.printCodeMessage();
            return null;
        }
        return cryptoResult.getData();
    }

    @org.jetbrains.annotations.Nullable
    public static String decryptFile(String path, String gotFile,byte[]symkey,String prikeyCipher) {
        CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymkey(prikeyCipher,symkey);
        if(cryptoDataByte.getCode()!=0){
            TimberLogger.d("Failed to decrypt the user prikey.");
            TimberLogger.d(cryptoDataByte.getMessage());
            return null;
        }
        byte[] prikey = cryptoDataByte.getData();
        CryptoDataByte cryptoDataByte1 = new Decryptor().decryptFileToDidByAsyOneWay(path, gotFile, path, prikey);
        if(cryptoDataByte1.getCode()!=0){
            TimberLogger.d("Failed to decrypt file "+ Paths.get(path, gotFile));
            return null;
        }
        BytesUtils.clearByteArray(prikey);
        return Hex.toHex(cryptoDataByte1.getDid());
    }

    public CryptoDataByte decrypt(byte[] bundle, byte[] key){
        return decryptBundleBySymkey(bundle,key);
    }
    public CryptoDataByte decrypt(String cryptoDataJson, byte[] key){
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        }catch (Exception e){
            cryptoDataByte= new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
            return cryptoDataByte;
        }
        switch (cryptoDataByte.getType()) {
            case Symkey: cryptoDataByte.setSymkey(key);
            case Password: cryptoDataByte.setPassword(key);
            case AsyOneWay,AsyTwoWay: cryptoDataByte.setPrikeyB(key);
        }
        return decrypt(cryptoDataByte);
    }

    public CryptoDataByte decrypt(CryptoDataByte cryptoDataByte){
        switch (cryptoDataByte.getAlg()){
            case FC_AesCbc256_No1_NrC7 -> decryptBySymkey(cryptoDataByte);
            case FC_EccK1AesCbc256_No1_NrC7 -> decryptByAsyKey(cryptoDataByte);
            case BitCore_EccAes256 -> decryptBitcore(cryptoDataByte);
            default -> cryptoDataByte.setCodeMessage(CodeMessage.Code4002NoSuchAlgorithm);
        }
        return cryptoDataByte;
    }

    private CryptoDataByte decryptBitcore(CryptoDataByte cryptoDataByte) {
        byte[] cipher = Bitcore.fromCryptoDataByte(cryptoDataByte);
        try {
            byte[] data = Bitcore.decrypt(cipher,cryptoDataByte.getPrikeyB());
            cryptoDataByte.setData(data);
            cryptoDataByte.setCodeMessage(CodeMessage.Code0Success);
            return cryptoDataByte;
        } catch (Exception e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code1020OtherError);
            cryptoDataByte.setMessage(e.getMessage());
            return cryptoDataByte;
        }
    }

    public String decryptJsonBySymkey(@NotNull String cryptoDataJson, @NotNull String symkeyHex) {
        byte[] key;
        try {
            key = Hex.fromHex(symkeyHex);
        }catch (Exception ignore){
            CryptoDataStr cryptoDataStr = new CryptoDataStr();
            cryptoDataStr.setCodeMessage(CodeMessage.Code4007FailedToParseHex);
            return "Error:" + CodeMessage.Code4007FailedToParseHex + "_" + CodeMessage.getMsg(CodeMessage.Code4007FailedToParseHex);
        }

        CryptoDataByte cryptoDataByte = decryptJsonBySymkey(cryptoDataJson,key);
        if(cryptoDataByte.getCode()!=0)
            return "Error:" + cryptoDataByte.getCode() + "_" + CodeMessage.getMsg(cryptoDataByte.getCode());
        return new String(cryptoDataByte.getData());
    }


    public CryptoDataByte decryptJsonByPassword(@NotNull String cryptoDataJson, @NotNull char[]password) {
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        }catch (Exception e){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
            return cryptoDataByte;
        }
        return decryptByPassword(cryptoDataByte, password);
    }

    @NotNull
    public static CryptoDataByte decryptByPassword(@NotNull CryptoDataByte cryptoDataByte, @NotNull char[] password) {
        byte[] symkey = Encryptor.passwordToSymkey(password, cryptoDataByte.getIv());
        cryptoDataByte.setType(EncryptType.Symkey);
        cryptoDataByte.setSymkey(symkey);
        decryptBySymkey(cryptoDataByte);
        cryptoDataByte.setType(EncryptType.Password);
        return cryptoDataByte;
    }

    public static CryptoDataByte decryptByPassword(String cipher, @NotNull String password) {
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson(cipher);;
        byte[] symkey = Encryptor.passwordToSymkey(password.toCharArray(), cryptoDataByte.getIv());
        cryptoDataByte.setType(EncryptType.Symkey);
        cryptoDataByte.setSymkey(symkey);
        decryptBySymkey(cryptoDataByte);
        cryptoDataByte.setType(EncryptType.Password);
        return cryptoDataByte;
    }

    public CryptoDataByte decryptJsonBySymkey(@NotNull String cryptoDataJson, @NotNull byte[]symkey) {
        CryptoDataByte cryptoDataByte;
        try {
            cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        }catch (Exception e){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
            return cryptoDataByte;
        }
        cryptoDataByte.setSymkey(symkey);
        return decryptBySymkey(cryptoDataByte);
    }
    public static CryptoDataByte decryptBySymkey(CryptoDataByte cryptoDataByte, String key) {
        cryptoDataByte.setSymkey(Hex.fromHex(key));
        return decryptBySymkey(cryptoDataByte);
    }
    @NotNull
    public static CryptoDataByte decryptBySymkey(CryptoDataByte cryptoDataByte) {
        switch (cryptoDataByte.getAlg()) {
            case FC_AesCbc256_No1_NrC7 -> AesCbc256.decrypt(cryptoDataByte);
            default -> new EccAes256K1P7().decrypt(cryptoDataByte);
        }
        return cryptoDataByte;
    }

    public CryptoDataByte decryptFileByPassword(@NotNull String cipherFileName, @NotNull String dataFileName, @NotNull char[] password){
        FileUtils.createFileWithDirectories(dataFileName);


        CryptoDataByte cryptoDataByte = decryptFileBySymkey(new File(cipherFileName), new File(dataFileName), null, password);
        cryptoDataByte.setPassword(BytesUtils.charArrayToByteArray(password, StandardCharsets.UTF_8));
        cryptoDataByte.setType(EncryptType.Password);

        return cryptoDataByte;
    }

    public CryptoDataByte decryptFileBySymkey(@NotNull String cipherFileName, @NotNull String dataFileName, @NotNull byte[]key){
        FileUtils.createFileWithDirectories(dataFileName);
        return decryptFileBySymkey(new File(cipherFileName),new File(dataFileName),key, null);
    }
    public static String recoverEncryptedFileName(String encryptedFileName){
        int endIndex1 = encryptedFileName.lastIndexOf('_');
        int endIndex2 = encryptedFileName.lastIndexOf('.');
        String oldSuffix;
        if(endIndex1!=-1) {
            oldSuffix = encryptedFileName.substring(endIndex1 + 1, endIndex2);
            return encryptedFileName.substring(0, endIndex1) + "." + oldSuffix;
        }else return encryptedFileName+ Constants.DOT_DECRYPTED;
    }
    public CryptoDataByte decryptFileBySymkey(@NotNull File cipherFile, @NotNull File dataFile,  byte[]key, char[] password){

        CryptoDataByte cryptoDataByte;
        try(FileOutputStream fos = new FileOutputStream(dataFile);
            FileInputStream fis = new FileInputStream(cipherFile)){

            cryptoDataByte = CryptoDataByte.readFromFileStream(fis);
            if(cryptoDataByte ==null){
                cryptoDataByte = new CryptoDataByte();
                cryptoDataByte.setCodeMessage(CodeMessage.Code4013BadCipher);
                return cryptoDataByte;
            }
            if(cryptoDataByte.getIv()==null){
                cryptoDataByte.setCodeMessage(CodeMessage.Code4009MissingIv);
                return cryptoDataByte;
            }
            if(key==null){
                if(password!=null){
                    key = Encryptor.passwordToSymkey(password,cryptoDataByte.getIv());
                }else {
                    cryptoDataByte.setCodeMessage(CodeMessage.Code4006InvalidKey);
                    return cryptoDataByte;
                }
            }
            cryptoDataByte.setSymkey(key);

            switch (cryptoDataByte.getAlg()){
                case FC_AesCbc256_No1_NrC7 -> AesCbc256.decryptStream(fis,fos,cryptoDataByte);
                default -> AesCbc256.decryptStream(fis,fos,cryptoDataByte);
            }
        } catch (FileNotFoundException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code1011DataNotFound);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
            return cryptoDataByte;
        }
        if(cryptoDataByte.getCode()==0) {
            byte[] did;
            try {
                did = Hash.sha256x2Bytes(dataFile);
            } catch (IOException e) {
                cryptoDataByte = new CryptoDataByte();
                cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt);
                return cryptoDataByte;
            }
            cryptoDataByte.setDid(did);

            cryptoDataByte.checkSum(cryptoDataByte.getAlg());
            return cryptoDataByte;
        }
        return cryptoDataByte;
    }

    public CryptoDataByte decryptStreamBySymkey(@NotNull InputStream inputStream, @NotNull OutputStream outputStream, @NotNull byte[] key, @NotNull byte[] iv, @Nullable CryptoDataByte cryptoDataByte) {
        if(cryptoDataByte==null)cryptoDataByte = new CryptoDataByte();
        if(key!=null)cryptoDataByte.setSymkey(key);
        if(iv!=null)cryptoDataByte.setIv(iv);
        AlgorithmId algorithmId;
        if(cryptoDataByte.getAlg()!= null)algorithmId = cryptoDataByte.getAlg();
        else algorithmId = AlgorithmId.FC_AesCbc256_No1_NrC7;
        switch (algorithmId){
            case FC_AesCbc256_No1_NrC7 -> {
                AesCbc256.decryptStream(inputStream,outputStream,cryptoDataByte);
            }
            default -> AesCbc256.decryptStream(inputStream,outputStream,cryptoDataByte);
        }
        cryptoDataByte.setCodeMessage(1);
        return cryptoDataByte;
    }

    public static void decryptBySymkeyBase(String algo, String transformation, String provider, InputStream inputStream, OutputStream outputStream, @Nullable CryptoDataByte cryptoDataByte) {
        Security.addProvider(new BouncyCastleProvider());
        if(cryptoDataByte==null)return ;
        if(cryptoDataByte.getSymkey()==null){
            cryptoDataByte.setCodeMessage(CodeMessage.Code4006InvalidKey);
            return;
        }
        byte[] key = cryptoDataByte.getSymkey();
        byte[] iv = cryptoDataByte.getIv();

        AlgorithmId alg = null;
        if(cryptoDataByte.getAlg()!=null)
            alg = cryptoDataByte.getAlg();

        if(key.length!=32){
            cryptoDataByte.setCodeMessage(CodeMessage.Code4008WrongKeyLength);
            return;
        }

        SecretKeySpec keySpec = new SecretKeySpec(key, algo);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        try {
            Cipher cipher = Cipher.getInstance(transformation, provider);
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
            cryptoDataByte.setCipherId(sha256(hasherIn.hash().asBytes()));

        } catch (InvalidAlgorithmParameterException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4005InvalidAlgorithmParameter, e.getMessage());
            return;
        } catch (NoSuchPaddingException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4004NoSuchPadding, e.getMessage());
            return;
        } catch (NoSuchAlgorithmException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4002NoSuchAlgorithm, e.getMessage());
            return;
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4001FailedToEncrypt, e.getMessage());
            return;
        } catch (NoSuchProviderException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4003NoSuchProvider, e.getMessage());
            return;
        } catch (InvalidKeyException e) {
            cryptoDataByte.setCodeMessage(CodeMessage.Code4006InvalidKey, e.getMessage());
            return;
        }
        
        if(cryptoDataByte.getType()==null)
            cryptoDataByte.setType(EncryptType.Symkey);
        if(cryptoDataByte.getAlg()==null)
            cryptoDataByte.setAlg(alg);
        if(cryptoDataByte.getCode()==null)
            cryptoDataByte.set0CodeMessage();
    }

    public CryptoDataByte decryptBundleBySymkey(@NotNull byte[]bundle, @NotNull byte[]key) {
        CryptoDataByte cryptoDataByte= CryptoDataByte.fromBundle(bundle);
        cryptoDataByte.setSymkey(key);
        decryptBySymkey(cryptoDataByte);
        return cryptoDataByte;
//        byte[] algBytes = new byte[6];
//        byte[] iv = new byte[16];
//        byte[] sum = new byte[4];
//        System.arraycopy(bundle,0,algBytes,0,6);
//        System.arraycopy(bundle,6,iv,0,16);
//        int cipherLength = bundle.length - 6 - 16 - 4;
//        byte[] cipher = new byte[cipherLength];
//        System.arraycopy(bundle,16+6,cipher,0,cipherLength);
//        System.arraycopy(bundle,16+6+cipher.length,sum,0,4);
//        return decryptBySymkey(cipher,iv,key,sum,algBytes);
    }
    public CryptoDataByte decryptBundleByPassword(@NotNull byte[]bundle, @NotNull char[] password) {
        byte[] iv = new byte[16];
        System.arraycopy(bundle,6,iv,0,16);
        byte[]  symkey = Encryptor.passwordToSymkey(password,iv);
        return decryptBundleBySymkey(bundle,symkey);
    }

    public CryptoDataByte decryptBundleByAsyOneWay(@NotNull byte[] bundle, @NotNull byte[]prikey){
        return decryptBundleByAsy(bundle,prikey,null);
    }
    public CryptoDataByte decryptBundleByAsyTwoWay(@NotNull byte[] bundle, @NotNull byte[]prikeyX, @NotNull byte[]pubKeyY){
        return decryptBundleByAsy(bundle,prikeyX,pubKeyY);
    }
    private CryptoDataByte decryptBundleByAsy(@NotNull byte[] bundle, @NotNull byte[]prikeyX, byte[]pubKeyY){

        CryptoDataByte cryptoDataByte;

        boolean isTwoWay = pubKeyY != null;

        cryptoDataByte = CryptoDataByte.fromBundle(bundle);
        cryptoDataByte.setPrikeyB(prikeyX);
//        cryptoDataByte.setAlg(algorithm);
        if(isTwoWay)cryptoDataByte.setPubkeyA(pubKeyY);

        try(ByteArrayInputStream bis = new ByteArrayInputStream(cryptoDataByte.getCipher());
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            decryptStreamByAsy(bis,bos,cryptoDataByte);

            if(cryptoDataByte.getCode()==0) {
                cryptoDataByte.setData(bos.toByteArray());
            }
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte=new CryptoDataByte();
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }
    }
    public CryptoDataByte decryptJsonByAsyOneWay(@NotNull String cryptoDataJson, @NotNull byte[]prikey){
        return decryptJsonByAsy(cryptoDataJson,prikey,null);
    }
    public CryptoDataByte decryptJsonByAsyTwoWay(@NotNull String cryptoDataJson, @NotNull byte[]prikey, @NotNull byte[] pubKey){
        return decryptJsonByAsy(cryptoDataJson,prikey,pubKey);
    }
    private CryptoDataByte decryptJsonByAsy(@NotNull String cryptoDataJson, @NotNull byte[]prikey, byte[] pubKey){
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson(cryptoDataJson);
        if(cryptoDataByte.getType().equals(EncryptType.AsyTwoWay)&& pubKey==null){
            cryptoDataByte.setCodeMessage(12);
            return cryptoDataByte;
        }
        cryptoDataByte.setPrikeyA(prikey);
        cryptoDataByte.setPubkeyB(pubKey);
        decryptByAsyKey(cryptoDataByte);
        return cryptoDataByte;
    }

    public void decryptByAsyKey(CryptoDataByte cryptoDataByte){
        byte[] cipher = cryptoDataByte.getCipher();
        if(cipher==null){
            cryptoDataByte.setCodeMessage(17);
            return;
        }
        try(ByteArrayInputStream bis = new ByteArrayInputStream(cipher);
            ByteArrayOutputStream bos = new ByteArrayOutputStream()){
            decryptStreamByAsy(bis,bos,cryptoDataByte);
            cryptoDataByte.setData(bos.toByteArray());
            cryptoDataByte.makeDid();
            cryptoDataByte.setCodeMessage(0);
        } catch (IOException e) {
            cryptoDataByte.setCodeMessage(6);
        }
    }

    public static void decryptByAsyKey(CryptoDataByte cryptoDataByte,@NotNull byte[]key){
        cryptoDataByte.setPrikeyB(key);
        new Decryptor().decryptByAsyKey(cryptoDataByte);
    }
    public CryptoDataByte decryptFileByAsyOneWay(@NotNull String cipherFile, String dataFile, @NotNull byte[]prikeyX){
        CryptoDataByte cryptoDataByte = decryptFileByAsyTwoWay(null, cipherFile, null, dataFile, prikeyX, null);
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        return cryptoDataByte;
    }
    public CryptoDataByte decryptFileByAsyTwoWay(@NotNull String cipherFile, String dataFile, @NotNull byte[]prikeyX, @NotNull byte[] pubKeyY){
        return decryptFileByAsyTwoWay(null, cipherFile, null, dataFile, prikeyX,pubKeyY);
    }

    public CryptoDataByte decryptFileToDidByAsyOneWay(String srcPath, String srcFileName, String destPath, @NotNull byte[]prikeyX){
        return decryptFileByAsyTwoWay(srcPath, srcFileName, destPath, null, prikeyX,null);
    }
    public CryptoDataByte decryptFileByAsyOneWay(String srcPath, String srcFileName, String destPath, @Nullable String destFileName, @NotNull byte[]prikeyX){
        return decryptFileByAsyTwoWay(srcPath, srcFileName, destPath, destFileName, prikeyX,null);
    }
    public CryptoDataByte decryptFileByAsyTwoWay(String srcPath, @NotNull String srcFileName, String destPath, @Nullable String destFileName, @NotNull byte[]prikeyX, byte[] pubKeyY){
        CryptoDataByte cryptoDataByte;
        String srcFullName = getFileFullName(srcPath, srcFileName);
        String destFullName = null;
        if(destFileName!=null) destFullName = getFileFullName(destPath, destFileName);

        if(srcFullName==null){
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(21);
            return cryptoDataByte;
        }

        String tempDestFileName;
        String destFileForFos;
        if(destFullName!=null)
            destFileForFos=destFullName;
        else {
            tempDestFileName = FileUtils.getTempFileName();
            destFileForFos = tempDestFileName;
        }
        FileUtils.createFileWithDirectories(destFileForFos);

        try(FileOutputStream fos = new FileOutputStream(destFileForFos);
            FileInputStream fis = new FileInputStream(srcFullName)){
            cryptoDataByte = CryptoDataByte.readFromFileStream(fis);
            if(cryptoDataByte ==null){
                cryptoDataByte = new CryptoDataByte();
                cryptoDataByte.setCodeMessage(8);
                return cryptoDataByte;
            }

            if(cryptoDataByte.getIv()==null){
                cryptoDataByte.setCodeMessage(13);
                return cryptoDataByte;
            }
            if(prikeyX==null){
                cryptoDataByte.setCodeMessage(15);
                return cryptoDataByte;
            }
            if(cryptoDataByte.getPubkeyA()!=null)
                cryptoDataByte.setPrikeyB(prikeyX);
            else {
                cryptoDataByte.setPrikeyB(prikeyX);
                cryptoDataByte.setPubkeyA(pubKeyY);
            }

            decryptStreamByAsy(fis,fos,cryptoDataByte);

            byte[] did = Hash.sha256x2Bytes(new File(destFileForFos));
            cryptoDataByte.setDid(did);
            if(!cryptoDataByte.checkSum())cryptoDataByte.setCodeMessage(20);


            String didHex = Hex.toHex(did);

//            Path destPathForFos;
            if(destFileName==null){
                if(destPath!=null) Files.move(Paths.get(destFileForFos),Paths.get(destPath,didHex), StandardCopyOption.REPLACE_EXISTING);
                else Files.move(Paths.get(destFileForFos),Paths.get(didHex), StandardCopyOption.REPLACE_EXISTING);
            } else {
                if (destPath != null)
                    Files.move(Paths.get(destPath, destFileForFos), Paths.get(destPath, destFileName), StandardCopyOption.REPLACE_EXISTING);
                else Files.move(Paths.get(destFileForFos), Paths.get(destFileName), StandardCopyOption.REPLACE_EXISTING);
            }

            return cryptoDataByte;

        } catch (FileNotFoundException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(11);
            return cryptoDataByte;
        } catch (IOException e) {
            cryptoDataByte = new CryptoDataByte();
            cryptoDataByte.setCodeMessage(6);
            return cryptoDataByte;
        }
    }

    @org.jetbrains.annotations.Nullable
    private String getFileFullName(String path, String fileName) {
        String destFullName;
        if(fileName !=null){
            if(path ==null)destFullName= fileName;
            else destFullName= Paths.get(path, fileName).toString();
        }else destFullName=null;
        return destFullName;
    }
    public void decryptStreamByAsy(InputStream is, OutputStream os, CryptoDataByte cryptoDataByte){
        byte[] prikeyX;
        byte[] pubKeyY;
        byte[] iv = cryptoDataByte.getIv();

        byte[] prikeyA =cryptoDataByte.getPrikeyA();
        byte[] prikeyB = cryptoDataByte.getPrikeyB();
        byte[] pubKeyA = cryptoDataByte.getPubkeyA();
        byte[] pubKeyB = cryptoDataByte.getPubkeyB();

        if(prikeyA!=null && pubKeyB !=null){
            prikeyX = prikeyA;
            pubKeyY = pubKeyB;
        }else if(prikeyB!=null && pubKeyA!=null){
            prikeyX = prikeyB;
            pubKeyY = pubKeyA;
        }else if(prikeyA!=null && pubKeyA!=null){
            byte[] pubKey = KeyTools.prikeyToPubkey(prikeyA);
            if(!Arrays.equals(pubKey,pubKeyA)){
                prikeyX = prikeyA;
                pubKeyY = pubKeyA;
            }else {
                cryptoDataByte.setCodeMessage(19);
                return;
            }
        }
        else if(prikeyB!=null && pubKeyB!=null){
            byte[] pubKey = KeyTools.prikeyToPubkey(prikeyB);
            if(!Arrays.equals(pubKey,pubKeyA)){
                prikeyX = prikeyB;
                pubKeyY = pubKeyB;
            }else {
                cryptoDataByte.setCodeMessage(19);
                return;
            }
        }
        else {
            cryptoDataByte.setCodeMessage(19);
            return;
        }

        if(cryptoDataByte.getIv()==null) {
            cryptoDataByte.setCodeMessage(13);
            return;
        }

        if(cryptoDataByte.getSum()==null) {
            cryptoDataByte.setCodeMessage(14);
            return;
        }

        EncryptType type = cryptoDataByte.getType();
        AlgorithmId algo = cryptoDataByte.getAlg();
        if(algo==null){
            algo = AlgorithmId.EccAes256K1P7_No1_NrC7;
            cryptoDataByte.setAlg(algo);
        }

        byte[] symkey;
        switch (algo){
            case FC_EccK1AesCbc256_No1_NrC7 -> {
                symkey = Ecc256K1.asyKeyToSymkey(prikeyX,pubKeyY, iv);
                cryptoDataByte.setSymkey(symkey);
                cryptoDataByte.setType(EncryptType.Symkey);
                cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
                AesCbc256.decryptStream(is,os,cryptoDataByte);
            }
            default -> {
                symkey = EccAes256K1P7.asyKeyToSymkey(prikeyX,pubKeyY,iv);
                cryptoDataByte.setSymkey(symkey);
                cryptoDataByte.setType(EncryptType.Symkey);
                cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
                AesCbc256.decryptStream(is,os,cryptoDataByte);
            }
        }

        cryptoDataByte.setAlg(algo);
        cryptoDataByte.setType(type);
    }
    public CryptoDataByte decryptStreamByAsy(InputStream is, OutputStream os, byte[]prikeyX, byte[]pubKeyY, byte[] iv, byte[] sum){
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setPrikeyA(prikeyX);
        cryptoDataByte.setPubkeyB(pubKeyY);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setSum(sum);

        decryptStreamByAsy(is,os,cryptoDataByte);
        return cryptoDataByte;
    }
    public static void checkSum(CryptoDataByte cryptoDataByte) {
        byte[] newSum = CryptoDataByte.makeSum4(cryptoDataByte.getSymkey(), cryptoDataByte.getIv(), cryptoDataByte.getDid());
        if(cryptoDataByte.getSum()!=null && !Arrays.equals(newSum, cryptoDataByte.getSum())){
            cryptoDataByte.setCodeMessage(20);
        }else cryptoDataByte.set0CodeMessage();
    }

    public static byte[] sha256(byte[] b) {
        return Hashing.sha256().hashBytes(b).asBytes();
    }

}
