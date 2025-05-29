package com.fc.fc_ajdk.core.crypto.old;

import com.fc.fc_ajdk.core.crypto.CryptoDataStr;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.data.fcData.Affair;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.Op;
import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.JsonUtils;

import com.google.gson.Gson;
import com.fc.fc_ajdk.utils.FileUtils;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;
import com.fc.fc_ajdk.utils.TimberLogger;

import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

import static com.fc.fc_ajdk.data.fcData.AlgorithmId.EccAes256K1P7_No1_NrC7;
import static com.fc.fc_ajdk.data.fcData.AlgorithmId.FC_EccK1AesCbc256_No1_NrC7;

/**
 * * ECDH<p>
 * * secp256k1<p>
 * * AES-256-CBC-PKCS7Padding<p>
 * * By No1_NrC7 with the help of chatGPT
 */

public class EccAes256K1P7 {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public void test() throws Exception {

        Gson gson = new Gson();

        System.out.println("----------------------");
        System.out.println("Encode: ");
        System.out.println("    message: UTF-8");
        System.out.println("    key: Hex char[]");
        System.out.println("    ciphertext: Base64");
        System.out.println("----------------------");

        String msg = "hello world!";
        System.out.println("msg: " + msg);

        // ECC Test
        System.out.println("----------------------");
        System.out.println("Basic Test");
        System.out.println("----------------------");
        System.out.println("AsyOneWay:");
        System.out.println("----------");

        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte;
        CryptoDataStr cryptoDataStr = new CryptoDataStr();

        String pubkeyB = "02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67";
        cryptoDataStr.setData(msg);
        cryptoDataStr.setPubkeyB(pubkeyB);
        cryptoDataStr.setType(EncryptType.AsyOneWay);
        cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);

        // Encrypt with new keys
        ecc.encrypt(cryptoDataByte);

        char[] symkey;
        System.out.println("Symkey: " + Hex.toHexString(cryptoDataByte.getSymkey()));
        cryptoDataByte.clearSymkey();
        cryptoDataStr = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
        System.out.println("Encrypted with a new key pair:" + gson.toJson(cryptoDataStr));

        //Decrypt with new key
        String prikeyB = "ee72e6dd4047ef7f4c9886059cbab42eaab08afe7799cbc0539269ee7e2ec30c";
        cryptoDataByte.setData(null);
        cryptoDataByte.setPrikeyB(Hex.decode(prikeyB));

        ecc.decrypt(cryptoDataByte);
        cryptoDataByte.clearSymkey();
        System.out.println("Decrypted from bytes:" + gson.toJson(CryptoDataStr.fromCryptoDataByte(cryptoDataByte)));

        cryptoDataStr = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
        cryptoDataStr.setPrikeyB(prikeyB.toCharArray());
        ecc.decrypt(cryptoDataStr);
        System.out.println("Decrypted from String and char array:" + gson.toJson(cryptoDataStr));

        System.out.println("EccAes JSON without symkey:");
        cryptoDataStr.clearSymkey();
        System.out.println(gson.toJson(cryptoDataStr));


        System.out.println("----------------------");
        System.out.println("AsyTwoWay:");
        System.out.println("----------");

        String pubkeyA = "030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a";
        String prikeyA = "a048f6c843f92bfe036057f7fc2bf2c27353c624cf7ad97e98ed41432f700575";

        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.AsyTwoWay);
        cryptoDataStr.setData(msg);
        cryptoDataStr.setPubkeyB(pubkeyB);
        ecc.encrypt(cryptoDataStr);
        System.out.println("Lack prikeyA: " + gson.toJson(cryptoDataStr));

        cryptoDataStr.setPrikeyA(prikeyA.toCharArray());

        ecc.encrypt(cryptoDataStr);
        System.out.println("Encrypt: " + gson.toJson(cryptoDataStr));

        cryptoDataStr.setPrikeyB(prikeyB.toCharArray());
        cryptoDataStr.setData(null);
        ecc.decrypt(cryptoDataStr);
        System.out.println("Decrypt by private Key B: " + gson.toJson(cryptoDataStr));
        cryptoDataStr.setPrikeyA(prikeyA.toCharArray());
        cryptoDataStr.setData(null);
        ecc.decrypt(cryptoDataStr);
        System.out.println("Decrypt by private Key A: " + gson.toJson(cryptoDataStr));

        System.out.println("----------------------");
        System.out.println("Symkey:");
        System.out.println("----------");
        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.Symkey);

        String symkeyStr = "3b7ca1c4925c597083bb94c8e1582a621e4e72510780aa31ef0a769a406c2870";
        symkey = symkeyStr.toCharArray();
        cryptoDataStr.setSymkey(symkey);
        ecc.encrypt(cryptoDataStr);
        System.out.println("Lack msg: " + gson.toJson(cryptoDataStr));

        cryptoDataStr.setData(msg);
        cryptoDataStr.setSymkey(symkey);
        ecc.encrypt(cryptoDataStr);
        System.out.println("Symkey encrypt: " + gson.toJson(cryptoDataStr));

        cryptoDataStr.setData(null);
        cryptoDataStr.setSymkey(symkey);
        ecc.decrypt(cryptoDataStr);
        System.out.println("Symkey decrypt: " + gson.toJson(cryptoDataStr));

        System.out.println("----------------------");
        System.out.println("Password:");
        System.out.println("----------");

        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.Password);
        cryptoDataStr.setData(msg);
        cryptoDataStr.setSymkey(symkey);
        String passwordStr = "password马云！";
        char[] password = passwordStr.toCharArray();
        cryptoDataStr.setPassword(password);

        System.out.println("password:" + String.valueOf(password));
        ecc.encrypt(cryptoDataStr);
        System.out.println("Password encrypt: \n" + gson.toJson(cryptoDataStr));

        cryptoDataStr.setData(null);
        cryptoDataStr.setSymkey(null);
        password = "password马云！".toCharArray();
        cryptoDataStr.setPassword(password);

        ecc.decrypt(cryptoDataStr);
        System.out.println("Password decrypt: \n" + gson.toJson(cryptoDataStr));
        System.out.println("----------------------");
        System.out.println("----------------------");
        System.out.println("Test Json");
        System.out.println("----------------------");
        System.out.println("AsyOneWay json:");
        System.out.println("----------");

        String encOneWayJson0 = ecc.encrypt(msg, pubkeyB);
        checkResult(cryptoDataStr, "Encrypted: \n" + encOneWayJson0);

        String eccAesData1 = ecc.decrypt(encOneWayJson0, prikeyB.toCharArray());
        checkResult(cryptoDataStr, "Decrypted:\n" + eccAesData1);

        System.out.println("----------");

        System.out.println("AsyTwoWay json:");
        System.out.println("----------");

        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.AsyTwoWay);
        cryptoDataStr.setData(msg);
        cryptoDataStr.setPubkeyB(pubkeyB);
        String twoWayJson1 = gson.toJson(cryptoDataStr);

        System.out.println("TwoWayJson1:" + twoWayJson1);


        String encTwoWayJson1 = ecc.encrypt(msg, pubkeyB, prikeyA.toCharArray());
        checkResult(cryptoDataStr, "Encrypted: \n" + encTwoWayJson1);
        eccAesData1 = ecc.decrypt(encTwoWayJson1, prikeyB.toCharArray());
        checkResult(cryptoDataStr, "Decrypted:\n" + eccAesData1);

        System.out.println("----------");

        System.out.println("Symkey json:");
        System.out.println("----------");
        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.Symkey);
        cryptoDataStr.setData(msg);
        symkey = symkeyStr.toCharArray();
        cryptoDataStr.setSymkey(symkey);

        String symkeyJson1 = gson.toJson(cryptoDataStr);
        System.out.println("SymkeyJson1:" + symkeyJson1);
        System.out.println("Symkey: " + Arrays.toString(symkey));
        String encSymkeyJson1 = ecc.encrypt(msg, symkey);
        checkResult(cryptoDataStr, "Encrypted: \n" + encSymkeyJson1);

        String decSymkeyJson = ecc.decrypt(encSymkeyJson1, symkey);
        checkResult(cryptoDataStr, "Decrypted:\n" + decSymkeyJson);
        System.out.println("----------");

        System.out.println("Password json:");
        System.out.println("----------");
        cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.Password);
        cryptoDataStr.setData(msg);
        cryptoDataStr.setPassword(passwordStr.toCharArray());

        String passwordDataJson1 = gson.toJson(cryptoDataStr);
        System.out.println("PasswordJson1:" + passwordDataJson1);

        String encPasswordJson1 = ecc.encrypt(msg, passwordStr.toCharArray());
        checkResult(cryptoDataStr, "Encrypted: \n" + encPasswordJson1);

        String decPasswordJson = ecc.decrypt(encPasswordJson1, passwordStr.toCharArray());
        checkResult(cryptoDataStr, "Decrypted:\n" + decPasswordJson);
        System.out.println("----------------------");

        System.out.println("----------------------");
        System.out.println("Test Constructor");
        System.out.println("----------------------");

        System.out.println("AsyOneWay encrypt Constructor:");
        System.out.println("----------");
        cryptoDataStr = new CryptoDataStr(EncryptType.AsyOneWay, msg, pubkeyB);
        ecc.encrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));

        System.out.println("----------");
        System.out.println("AsyTwoWay encrypt Constructor:");
        System.out.println("----------");

        cryptoDataStr = new CryptoDataStr(EncryptType.AsyTwoWay, msg, pubkeyB, prikeyA.toCharArray());
        ecc.encrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));
        System.out.println("----------");
        System.out.println("Symkey encrypt Constructor:");
        System.out.println("----------");
        symkey = symkeyStr.toCharArray();
        cryptoDataStr = new CryptoDataStr(EncryptType.Symkey, msg, symkey);
        ecc.encrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));
        System.out.println("----------");
        System.out.println("Password encrypt Constructor:");
        System.out.println("----------");
        cryptoDataStr = new CryptoDataStr(EncryptType.Password, msg, password);
        ecc.encrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));
        System.out.println("----------");
        System.out.println("Asy Decrypt Constructor:");
        System.out.println("----------");
        String cipher = "yu7qzwXoEeKwRsCT/fLxaA==";
        String iv = "988a330ab28e61fa01471bf13ce6cc7d";
        String sum = "346a8033";
        cryptoDataStr = new CryptoDataStr(EncryptType.AsyOneWay, pubkeyA, pubkeyB, iv, cipher, sum, prikeyB.toCharArray());
        ecc.decrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));
        System.out.println("----------");
        System.out.println("Sym Decrypt Constructor:");
        System.out.println("----------");
        cipher = "6f20f3ukM3ol0KRJHACb0w==";
        iv = "862dc48880b515d589851df25827fbcf";
        sum = "befc5792";
        cryptoDataStr = new CryptoDataStr(EncryptType.Symkey, iv, cipher, sum, symkey);
        ecc.decrypt(cryptoDataStr);
        System.out.println(gson.toJson(cryptoDataStr));
        System.out.println("----------------------");
        System.out.println("Bundle test");
        System.out.println("----------------------");
        System.out.println("String");
        System.out.println("----------");
        System.out.println("AsyOneWay bundle test");
        System.out.println("----------");

        System.out.println("msg:" + msg + ",pubkeyB:" + pubkeyB);
        String bundle = ecc.encryptAsyOneWayBundle(msg, pubkeyB);
        System.out.println("Cipher bundle: " + bundle);
        String msgBundle = ecc.decryptAsyOneWayBundle(bundle, prikeyB.toCharArray());
        System.out.println("Msg from bundle:" + msgBundle);

        System.out.println("----------------------");
        System.out.println("AsyTwoWay bundle test");
        System.out.println("----------");

        bundle = ecc.encryptAsyTwoWayBundle(msg, pubkeyB, prikeyA.toCharArray());
        System.out.println("Cipher bundle: " + bundle);
        msgBundle = ecc.decryptAsyTwoWayBundle(bundle, pubkeyA, prikeyB.toCharArray());
        System.out.println("Msg from PrikeyB:" + msgBundle);
        msgBundle = ecc.decryptAsyTwoWayBundle(bundle, pubkeyB, prikeyA.toCharArray());
        System.out.println("Msg from PrikeyA:" + msgBundle);

        System.out.println("----------------------");
        System.out.println("Symkey bundle test");
        System.out.println("----------");

        bundle = ecc.encryptSymkeyBundle(msg, symkey);
        System.out.println("Cipher bundle: " + bundle);
        msgBundle = ecc.decryptSymkeyBundle(bundle, symkey);
        System.out.println("Msg from bundle:" + msgBundle);
        System.out.println("----------------------");

        System.out.println("byte[]");
        System.out.println("----------");
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] pubkeyBBytes = Hex.decode(pubkeyB);
        byte[] prikeyBBytes = Hex.decode(prikeyB);
        byte[] pubkeyABytes = Hex.decode(pubkeyA);
        byte[] prikeyABytes = Hex.decode(prikeyA);


        System.out.println("AsyOneWay bundle test");
        System.out.println("----------");

        byte[] bundleBytes = ecc.encryptAsyOneWayBundle(msgBytes, pubkeyBBytes);
        System.out.println("Cipher bundle: " + Base64.getEncoder().encodeToString(bundleBytes));
        byte[] msgBundleBytes = ecc.decryptAsyOneWayBundle(bundleBytes, prikeyBBytes);
        System.out.println("Msg from bundle:" + new String(msgBundleBytes));

        System.out.println("----------------------");
        System.out.println("AsyTwoWay bundle test");
        System.out.println("----------");

        //Reload sensitive parameters
        prikeyBBytes = Hex.decode(prikeyB);
        prikeyABytes = Hex.decode(prikeyA);

        bundleBytes = ecc.encryptAsyTwoWayBundle(msgBytes, pubkeyBBytes, prikeyABytes);
        System.out.println("Cipher bundle: " + Base64.getEncoder().encodeToString(bundleBytes));
        msgBundleBytes = ecc.decryptAsyTwoWayBundle(bundleBytes, pubkeyABytes, prikeyBBytes);
        System.out.println("Msg from PrikeyB:" + new String(msgBundleBytes));

        //Reload sensitive parameters
        prikeyBBytes = Hex.decode(prikeyB);
        prikeyABytes = Hex.decode(prikeyA);
        msgBundleBytes = ecc.decryptAsyTwoWayBundle(bundleBytes, pubkeyBBytes, prikeyABytes);
        System.out.println("Msg from PrikeyA:" + new String(msgBundleBytes));

        System.out.println("----------------------");
        System.out.println("Symkey bundle test");
        System.out.println("----------");

        byte[] symkeyBytes = Hex.decode(symkeyStr);
        bundleBytes = ecc.encryptSymkeyBundle(msgBytes, symkeyBytes);
        System.out.println("Cipher bundle: " + Base64.getEncoder().encodeToString(bundleBytes));
        //Reload sensitive parameters
        symkeyBytes = Hex.decode(symkeyStr);
        msgBundleBytes = ecc.decryptSymkeyBundle(bundleBytes, symkeyBytes);
        System.out.println("Msg from bundle:" + new String(msgBundleBytes));

        System.out.println("----------------------");
        System.out.println("Char Array as msg:");
        System.out.println("----------------------");

        System.out.println("msg: " + symkeyStr);
        String cipherAsyOne = ecc.encrypt(symkeyStr.toCharArray(), pubkeyB);
        String cipherAsyTwo = ecc.encrypt(symkeyStr.toCharArray(), pubkeyB, prikeyA.toCharArray());
        String cipherSymkey = ecc.encrypt(symkeyStr.toCharArray(), symkeyStr.toCharArray());
        String cipherPassword = ecc.encrypt(symkeyStr.toCharArray(), passwordStr.toCharArray());

        System.out.println("cipherAsyOne:" + cipherAsyOne);
        System.out.println("cipherAsyTwo:" + cipherAsyTwo);
        System.out.println("cipherSymkey:" + cipherSymkey);
        System.out.println("cipherPassword:" + cipherPassword);

        System.out.println("decrypted AsyOne:" + ecc.decrypt(cipherAsyOne, prikeyB.toCharArray()));
        System.out.println("decrypted AsyTwo:" + ecc.decrypt(cipherAsyTwo, prikeyB.toCharArray()));
        System.out.println("decrypted Symkey:" + ecc.decrypt(cipherSymkey, symkeyStr.toCharArray()));
        System.out.println("decrypted Password:" + ecc.decrypt(cipherPassword, passwordStr.toCharArray()));
    }

    private void checkResult(CryptoDataStr cryptoDataStr, String s) {
        if (cryptoDataStr.getMessage() != null) {
            System.out.println(cryptoDataStr.getMessage());
        } else {
            System.out.println(s);
        }
    }
    public void encrypt(CryptoDataByte cryptoDataByte) {
        if (isErrorAlgAndType(cryptoDataByte)) return;
        switch (cryptoDataByte.getType()) {
            case AsyOneWay, AsyTwoWay -> encryptAsy(cryptoDataByte);
            case Symkey -> encryptWithSymkey(cryptoDataByte);
            case Password -> encryptWithPassword(cryptoDataByte);
            default -> cryptoDataByte.setMessage("Wrong type: " + cryptoDataByte.getType());
        }
    }

    public void decrypt(CryptoDataByte cryptoDataByte) {
        if (isErrorAlgAndType(cryptoDataByte)) return;

        switch (cryptoDataByte.getType()) {
            case AsyOneWay, AsyTwoWay -> decryptAsy(cryptoDataByte);
            case Symkey -> decryptWithSymkey(cryptoDataByte);
            case Password -> decryptWithPassword(cryptoDataByte);
        }
    }

    public static String encryptKeyWithPassword(byte[] keyBytes, char[] password) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.Password);
        cryptoDataByte.setData(keyBytes);
        cryptoDataByte.setPassword(BytesUtils.utf8CharArrayToByteArray(password));
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) {
            return "Error:" + cryptoDataByte.getMessage();
        }
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }
    public static String encryptWithSymkey(byte[] msgBytes, byte[] symkey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.Symkey);
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setSymkey(symkey);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() == null)
            return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
        return "Error:" + cryptoDataByte.getMessage();
    }

    public static String encryptWithPubkey(byte[] msgBytes, byte[] pubkey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setPubkeyB(pubkey);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() == null)
            return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
        return "Error:" + cryptoDataByte.getMessage();
    }

    public static String encryptWithPubkeyPrikey(byte[] msgBytes, byte[] pubkeyB,byte[]prikeyA) {
        EccAes256K1P7 ecc = new EccAes256K1P7();

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyTwoWay);
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setPubkeyB(pubkeyB);
        cryptoDataByte.setPrikeyA(prikeyA);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() == null)
            return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
        return "Error:" + cryptoDataByte.getMessage();
    }

    public void encrypt(CryptoDataStr cryptoDataStr) {
        if (cryptoDataStr == null) return;
        cryptoDataStr.setMessage(null);

        if (cryptoDataStr.getType() == null) {
            cryptoDataStr.setMessage("EccAesType is required.");
            //eccAesData.clearAllSensitiveData();
            return;
        }
        if (!isGoodEncryptParams(cryptoDataStr)) {
            //eccAesData.clearAllSensitiveData();
            return;
        }
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        encrypt(cryptoDataByte);
        CryptoDataStr cryptoDataStr1 = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
        copyEccAesData(cryptoDataStr1, cryptoDataStr);
    }

    public String encrypt(String msg, String pubkeyB) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.AsyOneWay, msg, pubkeyB);
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(String msg, String pubkeyB, char[] prikeyA) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.AsyTwoWay, msg, pubkeyB, prikeyA);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(String msg, char[] symkey32OrPassword) {
        CryptoDataStr cryptoDataStr;
        if (symkey32OrPassword.length == 64) {
            boolean isHex = isCharArrayHex(symkey32OrPassword);
            if (isHex) cryptoDataStr = new CryptoDataStr(EncryptType.Symkey, msg, symkey32OrPassword);
            else cryptoDataStr = new CryptoDataStr(EncryptType.Password, msg, symkey32OrPassword);
        } else cryptoDataStr = new CryptoDataStr(EncryptType.Password, msg, symkey32OrPassword);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(char[] msg, String pubkeyB) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        cryptoDataByte.setData(BytesUtils.charArrayToByteArray(msg, StandardCharsets.UTF_8));
        cryptoDataByte.setPubkeyB(BytesUtils.hexToByteArray(pubkeyB));
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(char[] msg, String pubkeyB, char[] prikeyA) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyTwoWay);
        cryptoDataByte.setData(BytesUtils.charArrayToByteArray(msg, StandardCharsets.UTF_8));
        cryptoDataByte.setPubkeyB(BytesUtils.hexToByteArray(pubkeyB));
        cryptoDataByte.setPrikeyA(BytesUtils.hexCharArrayToByteArray(prikeyA));
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }

    public String encrypt(char[] msg, char[] symkey32OrPassword) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        if (symkey32OrPassword.length == 64) {
            boolean isHex = isCharArrayHex(symkey32OrPassword);
            if (isHex) {
                cryptoDataByte.setType(EncryptType.Symkey);
                cryptoDataByte.setSymkey(BytesUtils.hexCharArrayToByteArray(symkey32OrPassword));
            } else {
                cryptoDataByte.setType(EncryptType.Password);
                cryptoDataByte.setPassword(BytesUtils.charArrayToByteArray(symkey32OrPassword, StandardCharsets.UTF_8));
            }
        } else {
            cryptoDataByte.setType(EncryptType.Password);
            cryptoDataByte.setPassword(BytesUtils.charArrayToByteArray(symkey32OrPassword, StandardCharsets.UTF_8));
        }

        cryptoDataByte.setData(BytesUtils.charArrayToByteArray(msg, StandardCharsets.UTF_8));
        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return CryptoDataStr.fromCryptoDataByte(cryptoDataByte).toJson();
    }


    public static byte[] decryptJsonBytes(String keyCipherJson, byte[] keyOrPassword) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
//        System.out.println("Decrypt key...");
        CryptoDataByte result = ecc.decrypt(keyCipherJson, keyOrPassword);
        if (result.getMessage() != null) {
            TimberLogger.d("Decrypting wrong: " + result.getMessage());
            return null;
        }
        return result.getData();
    }

    public static CryptoDataByte encryptWithPassword(byte[] initSymkey, byte[] passwordBytes) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.Password);
        cryptoDataByte.setData(initSymkey);
        cryptoDataByte.setPassword(passwordBytes);
        ecc.encrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) {
            System.out.println("Failed to encrypt key: " + cryptoDataByte.getMessage());
            return null;
        }
        return cryptoDataByte;
    }

    public static CryptoDataByte makeIvCipherToCryptoDataByte(byte[] ivCipherBytes) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
        cryptoDataByte.setType(EncryptType.Symkey);
        byte[] iv = Arrays.copyOfRange(ivCipherBytes, 0, 16);
        byte[] cipher = Arrays.copyOfRange(ivCipherBytes, 16, ivCipherBytes.length);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);
        return cryptoDataByte;
    }

    public static ECPrivateKeyParameters prikeyFromHex(String privateKeyHex) {
        BigInteger privateKeyValue = new BigInteger(privateKeyHex, 16); // Convert hex to BigInteger
        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1"); // Use the same curve name as in key pair generation
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());
        return new ECPrivateKeyParameters(privateKeyValue, domainParameters);
    }

    public static ECPrivateKeyParameters prikeyFromBytes(byte[] privateKey) {
        return prikeyFromHex(Hex.toHexString(privateKey));
    }

    public static ECPublicKeyParameters pubkeyFromPrikey(ECPrivateKeyParameters privateKey) {
        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());

        ECPoint Q = domainParameters.getG().multiply(privateKey.getD()); // Scalar multiplication of base point (G) and private key

        return new ECPublicKeyParameters(Q, domainParameters);
    }

    public static byte[] pubkeyToBytes(ECPublicKeyParameters publicKey) {
        return publicKey.getQ().getEncoded(true);
    }

    public static byte[] getRandomIv() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        return iv;
    }

    public static boolean isTheKeyPair(byte[] pubkeyByte, byte[] prikeyByte) {
        ECPrivateKeyParameters prikey = prikeyFromBytes(prikeyByte);
        byte[] pubkeyFromPrikey = pubkeyToBytes(pubkeyFromPrikey(prikey));
        return Arrays.equals(pubkeyByte, pubkeyFromPrikey);
    }

    public static byte[] decryptWithPrikey(String cipher, byte[] prikey) {
        EccAes256K1P7 ecc = new EccAes256K1P7();
        CryptoDataByte cryptoDataBytes = ecc.decrypt(cipher, prikey);
        if (cryptoDataBytes.getMessage() != null) {
            System.out.println("Decrypt sessionKey wrong: " + cryptoDataBytes.getMessage());
            BytesUtils.clearByteArray(prikey);
            return null;
        }
        return cryptoDataBytes.getData();
    }

    private boolean isCharArrayHex(char[] symkey32OrPassword) {
        boolean isHex = false;
        for (char c : symkey32OrPassword) {
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                isHex = true;
            } else {
                return false;
            }
        }
        return isHex;
    }

    public String encrypt(File originalFile, char[] symkey) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.Symkey);
        cryptoDataStr.setAlg(FC_EccK1AesCbc256_No1_NrC7);
        cryptoDataStr.setSymkey(symkey);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        byte[] symkeyBytes = BytesUtils.hexCharArrayToByteArray(symkey);
        cryptoDataByte.setSymkey(symkeyBytes);
        return encrypt(originalFile, cryptoDataByte);
    }

    public String encrypt(File originalFile, String pubkeyB) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.AsyOneWay);
        cryptoDataStr.setAlg(FC_EccK1AesCbc256_No1_NrC7);
        cryptoDataStr.setPubkeyB(pubkeyB);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        return encrypt(originalFile, cryptoDataByte);
    }

    public String encrypt(File originalFile, String pubkeyB, char[] prikeyA) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr();
        cryptoDataStr.setType(EncryptType.AsyTwoWay);
        cryptoDataStr.setAlg(FC_EccK1AesCbc256_No1_NrC7);
        cryptoDataStr.setPubkeyB(pubkeyB);
        cryptoDataStr.setPrikeyA(prikeyA);

        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        return encrypt(originalFile, cryptoDataByte);
    }

    private String encrypt(File originalFile, CryptoDataByte cryptoDataByte) {
        byte[] msgBytes;
        try (FileInputStream fis = new FileInputStream(originalFile)) {
            msgBytes = BytesUtils.readAllBytes(fis);//fis.readAllBytes();
        } catch (IOException e) {
            return "FileInputStream wrong.";
        }

        if (msgBytes.length != 0)
            cryptoDataByte.setData(msgBytes);

        EccAes256K1P7 ecc = new EccAes256K1P7();
        ecc.encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();

        if (cryptoDataByte.getMessage() != null) {
            return cryptoDataByte.getMessage();
        } else {
            String parentPath = originalFile.getParent();
            String originalFileName = originalFile.getName();
            int endIndex = originalFileName.lastIndexOf('.');
            String suffix = "_" + originalFileName.substring(endIndex + 1);
            String encryptedFileName = originalFileName.substring(0, endIndex) + suffix + Constants.DOT_FV;
            File encryptedFile = FileUtils.getNewFile(parentPath, encryptedFileName, FileUtils.CreateNewFileMode.REWRITE);
            if (encryptedFile == null) return "Error:Creating encrypted file wrong.";

            try (FileOutputStream fos = new FileOutputStream(encryptedFile)) {
                byte[] cipherBytes;
                cipherBytes = cryptoDataByte.getCipher();
                cryptoDataByte.setCipher(null);

                Affair affair = new Affair();
                affair.setOp(Op.ENCRYPT);

                if (cryptoDataByte.getType() == EncryptType.AsyOneWay || cryptoDataByte.getType() == EncryptType.AsyTwoWay)
                    affair.setFid(KeyTools.pubkeyToFchAddr(cryptoDataByte.getPubkeyB()));
                affair.setOid(Hash.sha256x2(originalFile));
                CryptoDataStr cryptoDataStr = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
                affair.setData(cryptoDataStr);
                fos.write(new Gson().toJson(affair).getBytes());
                fos.write(cipherBytes);
            } catch (IOException e) {
                return "Error: Writing encrypted file wrong.";
            }
        }
        return "Done.";
    }

    public void decrypt(CryptoDataStr cryptoDataStr) {
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        decrypt(cryptoDataByte);
        CryptoDataStr cryptoDataStr1 = CryptoDataStr.fromCryptoDataByte(cryptoDataByte);
        copyEccAesData(cryptoDataStr1, cryptoDataStr);
    }
    public String decrypt(String eccAesDataJson, char[] key){
        return decrypt(eccAesDataJson,key,null);
    }
    public String decrypt(String eccAesDataJson, char[] key, @Nullable String pubkeyA) {
        CryptoDataStr cryptoDataStr = CryptoDataStr.fromJson(eccAesDataJson);
        switch (cryptoDataStr.getType()) {
            case AsyOneWay -> cryptoDataStr.setPrikeyB(key);
            case AsyTwoWay -> {
                cryptoDataStr.setPrikeyB(key);
                cryptoDataStr.setPubkeyA(pubkeyA);
            }
            case Symkey -> cryptoDataStr.setSymkey(key);
            case Password -> cryptoDataStr.setPassword(key);
            default -> cryptoDataStr.setMessage("Wrong EccAesType type" + cryptoDataStr.getType());
        }
        if (cryptoDataStr.getMessage() != null) {
            return "Error:" + cryptoDataStr.getMessage();
        }
        decrypt(cryptoDataStr);
        return cryptoDataStr.getData();
    }

    public byte[] decryptForBytes(String eccAesDataJson, char[] key) {
        Gson gson = new Gson();
        CryptoDataStr cryptoDataStr = gson.fromJson(eccAesDataJson, CryptoDataStr.class);

        switch (cryptoDataStr.getType()) {
            case AsyOneWay -> cryptoDataStr.setPrikeyB(key);
            case AsyTwoWay -> cryptoDataStr.setPrikeyB(key);
            case Symkey -> cryptoDataStr.setSymkey(key);
            case Password -> cryptoDataStr.setPassword(key);
            default -> cryptoDataStr.setMessage("Wrong EccAesType type" + cryptoDataStr.getType());
        }
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        if (cryptoDataByte.getMessage() != null) {
            return null;
        }
        decrypt(cryptoDataByte);
        return cryptoDataByte.getData();
    }

    public CryptoDataByte decrypt(String eccAesDataJson, byte[] key) {
        Gson gson = new Gson();
        CryptoDataStr cryptoDataStr = gson.fromJson(eccAesDataJson, CryptoDataStr.class);
        CryptoDataByte cryptoDataBytes = CryptoDataByte.fromCryptoData(cryptoDataStr);
        switch (cryptoDataBytes.getType()) {
            case AsyOneWay, AsyTwoWay -> cryptoDataBytes.setPrikeyB(key);
            case Symkey -> cryptoDataBytes.setSymkey(key);
            case Password -> cryptoDataBytes.setPassword(key);
            default -> cryptoDataBytes.setMessage("Wrong EccAesType type" + cryptoDataBytes.getType());
        }
        if (cryptoDataBytes.getMessage() != null) {
            return cryptoDataBytes;
        }
        decrypt(cryptoDataBytes);
        return cryptoDataBytes;
    }

    private String decrypt(File encryptedFile, CryptoDataByte cryptoDataByte) {

        EccAes256K1P7 ecc = new EccAes256K1P7();
        ecc.decrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();

        if (cryptoDataByte.getMessage() != null) {
            return cryptoDataByte.getMessage();
        } else {
            String parentPath = encryptedFile.getParent();
            String encryptedFileName = encryptedFile.getName();
            int endIndex1 = encryptedFileName.lastIndexOf('_');
            int endIndex2 = encryptedFileName.lastIndexOf('.');
            String oldSuffix;
            String originalFileName;
            if(endIndex1!=-1) {
                oldSuffix = encryptedFileName.substring(endIndex1 + 1, endIndex2);
                originalFileName = encryptedFileName.substring(0, endIndex1) + "." + oldSuffix;
            }else originalFileName = encryptedFileName+ Constants.DOT_DECRYPTED;

            File originalFile = FileUtils.getNewFile(parentPath, originalFileName, FileUtils.CreateNewFileMode.REWRITE);
            if (originalFile == null) return "Create recovered file failed.";
            try (FileOutputStream fos = new FileOutputStream(originalFile)) {
                fos.write(cryptoDataByte.getData());
                return "Done";
            } catch (IOException e) {
                return "Write file wrong";
            }
        }
    }

    public static String getEncryptedFileName(String originalFileFullName){
        File file = new File(originalFileFullName);
        String parentPath = file.getParent();
        String originalFileName = file.getName();
        int endIndex = originalFileName.lastIndexOf('.');
        String suffix = "_" + originalFileName.substring(endIndex + 1);
        String encryptedFileName = originalFileName.substring(0, endIndex) + suffix + Constants.DOT_FV;
        if(parentPath==null)return encryptedFileName;
        else return parentPath+"/"+encryptedFileName;
    }
    public static String getDecryptedFileName(String encryptedFileFullName){
        File encryptedFile=new File(encryptedFileFullName);
        String parentPath = encryptedFile.getParent();
        String encryptedFileName = encryptedFile.getName();
        int endIndex1 = encryptedFileName.lastIndexOf('_');
        int endIndex2 = encryptedFileName.lastIndexOf('.');
        String oldSuffix;
        String originalFileName;
        if(endIndex1!=-1) {
            oldSuffix = encryptedFileName.substring(endIndex1 + 1, endIndex2);
            originalFileName = encryptedFileName.substring(0, endIndex1) + "." + oldSuffix;
        }else originalFileName = encryptedFileName + Constants.DOT_DECRYPTED;
        if(parentPath==null)return originalFileName;
        else return parentPath+"/"+originalFileName;
    }

    public String decrypt(File encryptedFile, byte[] prikeyBBytes) {
        byte[] cipherBytes;
        Affair affair;
        Gson gson = new Gson();
        CryptoDataStr cryptoDataStr;
        CryptoDataByte cryptoDataByte;
        try (FileInputStream fis = new FileInputStream(encryptedFile)) {
            affair = JsonUtils.readObjectFromJsonFile(fis, Affair.class);
            cipherBytes = BytesUtils.readAllBytes(fis);
            if (affair == null) return "Affair is null.";
            if (affair.getData() == null) return "Affair.data is null.";
            cryptoDataStr = gson.fromJson(gson.toJson(affair.getData()), CryptoDataStr.class);
            if (cryptoDataStr == null) return "Got eccAesData null.";
        } catch (IOException e) {
            return "Reading file wrong.";
        }
        cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        cryptoDataByte.setPrikeyB(prikeyBBytes);
        cryptoDataByte.setCipher(cipherBytes);
        return decrypt(encryptedFile, cryptoDataByte);
    }

    public String decrypt(File encryptedFile, char[] symkey) {
        byte[] cipherBytes;
        Affair affair;
        Gson gson = new Gson();
        CryptoDataStr cryptoDataStr;
        CryptoDataByte cryptoDataByte;
        try (FileInputStream fis = new FileInputStream(encryptedFile)) {
            affair = JsonUtils.readObjectFromJsonFile(fis, Affair.class);
            cipherBytes = BytesUtils.readAllBytes(fis);
            if (affair == null) return "Error:affair is null.";
            if (affair.getData() == null) return "Error:affair.data is null.";
            cryptoDataStr = gson.fromJson(gson.toJson(affair.getData()), CryptoDataStr.class);
            if (cryptoDataStr == null) return "Got eccAesData null.";
        } catch (IOException e) {
            return "Read file wrong.";
        }
        cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);
        cryptoDataByte.setSymkey(BytesUtils.hexCharArrayToByteArray(symkey));
        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();
        cryptoDataByte.setCipher(cipherBytes);
        return decrypt(encryptedFile, cryptoDataByte);
    }

    private void decryptAsy(CryptoDataByte cryptoDataByte) {
        if (!isGoodDecryptParams(cryptoDataByte)) {
            return;
        }

        if (cryptoDataByte.getPubkeyB() == null && cryptoDataByte.getPubkeyA() == null) {
            cryptoDataByte.setMessage("No any public key found.");
            return;
        }

        byte[] prikeyBytes = new byte[0];
        byte[] pubkeyBytes = new byte[0];

        Result result = checkPrikeyAndPubkey(cryptoDataByte, prikeyBytes, pubkeyBytes);
        if (result == null) return;

        byte[] symkey =
                asyKeyToSymkey(result.prikeyBytes(), result.pubkeyBytes(),cryptoDataByte.getIv());
        if(symkey==null){
            cryptoDataByte.setMessage("Failed to make symkey from the prikey and the pubkey of another party.");
            return;
        }
        cryptoDataByte.setSymkey(symkey);

        if (cryptoDataByte.getSum() != null) {
            if (!isGoodAesSum(cryptoDataByte)) {
                cryptoDataByte.setCodeMessage(20);
                return;
            }
        }

        byte[] msgBytes = new byte[0];
        try {
            msgBytes = Aes256CbcP7.decrypt(cryptoDataByte.getCipher(), symkey, cryptoDataByte.getIv());
        } catch (NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException |
                 NoSuchAlgorithmException | NoSuchProviderException | IllegalBlockSizeException |
                 BadPaddingException e) {
            cryptoDataByte.setMessage("Decrypt message wrong: " + e.getMessage());
        }
        cryptoDataByte.setData(msgBytes);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.set0CodeMessage();

        cryptoDataByte.clearAllSensitiveDataButSymkey();
    }

    @org.jetbrains.annotations.Nullable
    private static Result checkPrikeyAndPubkey(CryptoDataByte cryptoDataByte, byte[] prikeyBytes, byte[] pubkeyBytes) {
        if (cryptoDataByte.getType() == EncryptType.AsyOneWay) {
            prikeyBytes = cryptoDataByte.getPrikeyB();
            if (prikeyBytes == null || BytesUtils.isFilledKey(prikeyBytes)) {
                cryptoDataByte.setMessage("The private key is null or filled with 0.");
                return null;
            }
            pubkeyBytes = cryptoDataByte.getPubkeyA();
        } else if (cryptoDataByte.getType() == EncryptType.AsyTwoWay) {
            boolean found = false;
            if (cryptoDataByte.getPrikeyB() != null && !BytesUtils.isFilledKey(cryptoDataByte.getPrikeyB())) {
                if (cryptoDataByte.getPubkeyA() != null) {
                    if (isTheKeyPair(cryptoDataByte.getPubkeyA(), cryptoDataByte.getPrikeyB())) {
                        if (isTheKeyPair(cryptoDataByte.getPubkeyB(), cryptoDataByte.getPrikeyB())) {
                            found = false;
                        } else {
                            found = true;
                            prikeyBytes = cryptoDataByte.getPrikeyB();
                            pubkeyBytes = cryptoDataByte.getPubkeyB();
                        }
                    } else {
                        found = true;
                        prikeyBytes = cryptoDataByte.getPrikeyB();
                        pubkeyBytes = cryptoDataByte.getPubkeyA();
                    }
                }
            } else if (cryptoDataByte.getPubkeyA() != null && !BytesUtils.isFilledKey(cryptoDataByte.getPrikeyA())) {
                if (isTheKeyPair(cryptoDataByte.getPubkeyA(), cryptoDataByte.getPrikeyA())) {
                    if (isTheKeyPair(cryptoDataByte.getPubkeyB(), cryptoDataByte.getPrikeyA())) {
                        found = false;
                    } else {
                        found = true;
                        prikeyBytes = cryptoDataByte.getPrikeyA();
                        pubkeyBytes = cryptoDataByte.getPubkeyB();
                    }
                } else {
                    found = true;
                    prikeyBytes = cryptoDataByte.getPrikeyA();
                    pubkeyBytes = cryptoDataByte.getPubkeyA();
                }
            }

            if (!found) {
                cryptoDataByte.setMessage("Private key or public key absent, or the private key and the public key is a pair.");
                return null;
            }
        } else {
            cryptoDataByte.setMessage("Wrong type:" + cryptoDataByte.getType());
            return null;
        }
        return new Result(prikeyBytes, pubkeyBytes);
    }

    private record Result(byte[] prikeyBytes, byte[] pubkeyBytes) {
    }

    public static byte[] asyKeyToSymkey(byte[] prikeyBytes, byte[] pubkeyBytes, byte[] iv) {
        byte[] symkey;
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        byte[] sharedSecret = getSharedSecret(prikeyBytes, pubkeyBytes);

        byte[] sharedSecretHash;

        sharedSecretHash = sha256.digest(sharedSecret);
        byte[] secretHashWithIv = BytesUtils.addByteArray(sharedSecretHash, iv);
        symkey = sha256.digest(sha256.digest(secretHashWithIv));

        clearByteArray(sharedSecret);
        return symkey;
    }

    private void decryptWithPassword(CryptoDataByte cryptoDataByte) {
        if (!isGoodPasswordDecryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] symkeyBytes = makeSymkeyFromPassword(cryptoDataByte, sha256, cryptoDataByte.getIv());
            cryptoDataByte.setSymkey(symkeyBytes);
            if (!isGoodAesSum(cryptoDataByte)) {
                cryptoDataByte.setCodeMessage(20);
                return;
            }
            byte[] msg = Aes256CbcP7.decrypt(cryptoDataByte.getCipher(), cryptoDataByte.getSymkey(), cryptoDataByte.getIv());
            cryptoDataByte.setData(msg);
            cryptoDataByte.set0CodeMessage();
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | NoSuchProviderException |
                 InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            cryptoDataByte.setMessage("Decrypt with password wrong: " + e.getMessage());
        }
    }

    public String encryptAsyOneWayBundle(String msg, String pubkeyB) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.AsyOneWay, msg, pubkeyB);
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymkey();

        byte[] bundleBytes = BytesUtils.addByteArray(BytesUtils.addByteArray(cryptoDataByte.getPubkeyA(), cryptoDataByte.getIv()), cryptoDataByte.getCipher());
        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();
        return Base64.getEncoder().encodeToString(bundleBytes);
    }

    public String decryptAsyOneWayBundle(String bundle, char[] prikeyB) {
        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        byte[] pubkeyABytes = new byte[33];
        byte[] ivBytes = new byte[16];
        byte[] cipherBytes = new byte[bundleBytes.length - pubkeyABytes.length - ivBytes.length];
        byte[] prikeyBBytes = BytesUtils.hexCharArrayToByteArray(prikeyB);

        pubkeyABytes = Arrays.copyOfRange(bundleBytes, 0, 33);
        ivBytes = Arrays.copyOfRange(bundleBytes, 33, 49);
        cipherBytes = Arrays.copyOfRange(bundleBytes, 33 + 16, bundleBytes.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        cryptoDataByte.setIv(ivBytes);
        cryptoDataByte.setPubkeyA(pubkeyABytes);
        cryptoDataByte.setPrikeyB(prikeyBBytes);
        cryptoDataByte.setCipher(cipherBytes);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymkey();

        if (cryptoDataByte.getMessage() != null) {
            return "Error:" + cryptoDataByte.getMessage();
        }
        return new String(cryptoDataByte.getData(), StandardCharsets.UTF_8);
    }

    public String encryptAsyTwoWayBundle(String msg, String pubkeyB, char[] prikeyA) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.AsyTwoWay, msg, pubkeyB, prikeyA);
        CryptoDataByte cryptoDataByte = CryptoDataByte.fromCryptoData(cryptoDataStr);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymkey();

        byte[] bundleBytes = BytesUtils.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();
        return Base64.getEncoder().encodeToString(bundleBytes);
    }

    public String decryptAsyTwoWayBundle(String bundle, String pubkeyA, char[] prikeyB) {
        byte[] prikeyBBytes = BytesUtils.hexCharArrayToByteArray(prikeyB);
        byte[] pubkeyABytes = Hex.decode(pubkeyA);

        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        byte[] ivBytes = Arrays.copyOfRange(bundleBytes, 0, 16);
        byte[] cipherBytes = Arrays.copyOfRange(bundleBytes, 16, bundleBytes.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyTwoWay);
        cryptoDataByte.setIv(ivBytes);
        cryptoDataByte.setPubkeyA(pubkeyABytes);
        cryptoDataByte.setPrikeyB(prikeyBBytes);
        cryptoDataByte.setCipher(cipherBytes);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymkey();

        if (cryptoDataByte.getMessage() != null) {
            return "Error:" + cryptoDataByte.getMessage();
        }
        return new String(cryptoDataByte.getData(), StandardCharsets.UTF_8);
    }

    public String encryptSymkeyBundle(String msg, char[] symkey) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.Symkey, msg, symkey);

        encrypt(cryptoDataStr);
        String iv = cryptoDataStr.getIv();
        String cipher = cryptoDataStr.getCipher();
        byte[] ivBytes = Hex.decode(iv);
        byte[] cipherBytes = Base64.getDecoder().decode(cipher);

        byte[] ivCipherBytes = BytesUtils.addByteArray(ivBytes, cipherBytes);
        String bundle = Base64.getEncoder().encodeToString(ivCipherBytes);
        //eccAesData.clearAllSensitiveData();
        if (cryptoDataStr.getMessage() != null) return "Error:" + cryptoDataStr.getMessage();
        return bundle;
    }

    public String encryptPasswordBundle(String msg, char[] password) {
        CryptoDataStr cryptoDataStr = new CryptoDataStr(EncryptType.Password, msg, password);

        encrypt(cryptoDataStr);
        String iv = cryptoDataStr.getIv();
        String cipher = cryptoDataStr.getCipher();
        byte[] ivBytes = Hex.decode(iv);
        byte[] cipherBytes = Base64.getDecoder().decode(cipher);

        byte[] ivCipherBytes = BytesUtils.addByteArray(ivBytes, cipherBytes);
        String bundle = Base64.getEncoder().encodeToString(ivCipherBytes);
        //eccAesData.clearAllSensitiveData();
        if (cryptoDataStr.getMessage() != null) return "Error:" + cryptoDataStr.getMessage();
        return bundle;
    }

    public String decryptSymkeyBundle(String bundle, char[] symkey) {
        CryptoDataByte cryptoDataByte = makeIvCipherToCryptoDataByte(Base64.getDecoder().decode(bundle));
        cryptoDataByte.setSymkey(BytesUtils.hexCharArrayToByteArray(symkey));
        decrypt(cryptoDataByte);

        if (cryptoDataByte.getMessage() != null) return "Error:" + cryptoDataByte.getMessage();

        return new String(cryptoDataByte.getData(), StandardCharsets.UTF_8);
    }

    public byte[] encryptAsyOneWayBundle(byte[] msg, byte[] pubkeyB) {

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        cryptoDataByte.setData(msg);
        cryptoDataByte.setPubkeyB(pubkeyB);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymkey();

        if (cryptoDataByte.getMessage() != null) return null;
        return BytesUtils.addByteArray(BytesUtils.addByteArray(cryptoDataByte.getPubkeyA(), cryptoDataByte.getIv()), cryptoDataByte.getCipher());
    }

    public byte[] decryptAsyOneWayBundle(byte[] bundle, byte[] prikeyB) {

        byte[] pubkeyA = Arrays.copyOfRange(bundle, 0, 33);
        byte[] ivBytes = Arrays.copyOfRange(bundle, 33, 49);
        byte[] cipherBytes = Arrays.copyOfRange(bundle, 33 + 16, bundle.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        cryptoDataByte.setIv(ivBytes);
        cryptoDataByte.setPubkeyA(pubkeyA);
        cryptoDataByte.setPrikeyB(prikeyB);
        cryptoDataByte.setCipher(cipherBytes);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymkey();

        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    public byte[] encryptAsyTwoWayBundle(byte[] msg, byte[] pubkeyB, byte[] prikeyA) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyTwoWay);
        cryptoDataByte.setData(msg);
        cryptoDataByte.setPubkeyB(pubkeyB);
        cryptoDataByte.setPrikeyA(prikeyA);

        encrypt(cryptoDataByte);
        cryptoDataByte.clearSymkey();

        if (cryptoDataByte.getMessage() != null) return null;
        return BytesUtils.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
    }

    public byte[] decryptAsyTwoWayBundle(byte[] bundle, byte[] pubkeyA, byte[] prikeyB) {

        byte[] iv = Arrays.copyOfRange(bundle, 0, 16);
        byte[] cipher = Arrays.copyOfRange(bundle, 16, bundle.length);

        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setType(EncryptType.AsyTwoWay);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);
        cryptoDataByte.setPubkeyA(pubkeyA);
        cryptoDataByte.setPrikeyB(prikeyB);

        decrypt(cryptoDataByte);
        cryptoDataByte.clearSymkey();

        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    public byte[] encryptSymkeyBundle(byte[] msg, byte[] symkey) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        cryptoDataByte.setType(EncryptType.Symkey);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setData(msg);

        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return BytesUtils.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
    }

    public byte[] decryptSymkeyBundle(byte[] bundle, byte[] symkey) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        byte[] iv = Arrays.copyOfRange(bundle, 0, 16);
        byte[] cipher = Arrays.copyOfRange(bundle, 16, bundle.length);
        cryptoDataByte.setType(EncryptType.Symkey);
        cryptoDataByte.setSymkey(symkey);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);

        decrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    public byte[] encryptPasswordBundle(byte[] msg, byte[] password) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();

        cryptoDataByte.setType(EncryptType.Password);
        cryptoDataByte.setPassword(password);
        cryptoDataByte.setData(msg);

        encrypt(cryptoDataByte);
        //eccAesDataByte.clearAllSensitiveData();
        return BytesUtils.addByteArray(cryptoDataByte.getIv(), cryptoDataByte.getCipher());
    }

    public byte[] decryptPasswordBundle(byte[] bundle, byte[] password) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        byte[] iv = Arrays.copyOfRange(bundle, 0, 16);
        byte[] cipher = Arrays.copyOfRange(bundle, 16, bundle.length);
        cryptoDataByte.setType(EncryptType.Password);
        cryptoDataByte.setPassword(password);
        cryptoDataByte.setIv(iv);
        cryptoDataByte.setCipher(cipher);

        decrypt(cryptoDataByte);
        if (cryptoDataByte.getMessage() != null) return ("Error:" + cryptoDataByte.getMessage()).getBytes();
        return cryptoDataByte.getData();
    }

    private void encryptAsy(CryptoDataByte cryptoDataByte) {
        if (!isGoodEncryptParams(cryptoDataByte)) {
            return;
        }

        // Generate IV
        byte[] iv = getRandomIv();
        cryptoDataByte.setIv(iv);

        //Make sharedSecret
        byte[] prikey;
        byte[] pubkey = cryptoDataByte.getPubkeyB();

        byte[] prikeyABytes = cryptoDataByte.getPrikeyA();
        if (prikeyABytes != null) {
            prikey=prikeyABytes;
        } else {
            prikey = makeRandomKeyPair(cryptoDataByte);
        }

        byte[] symkey =
                asyKeyToSymkey(prikey, pubkey, cryptoDataByte.getIv());
        if(symkey==null){
            cryptoDataByte.setMessage("Failed to make symkey from the prikey and the pubkey of another party.");
            return;
        }

        cryptoDataByte.setSymkey(symkey);

        // Encrypt the original AES key with the shared secret key
        aesEncrypt(cryptoDataByte);
    }

    private byte[] makeRandomKeyPair(CryptoDataByte cryptoDataByte) {
        byte[] prikey;
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(spec.getCurve(), spec.getG(), spec.getN(), spec.getH(), spec.getSeed());

        // Generate EC key pair for sender
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(domainParameters, new SecureRandom()));

        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();

        ECPrivateKeyParameters newPrikey = (ECPrivateKeyParameters) keyPair.getPrivate();
        byte[] newPubkey = pubkeyToBytes(pubkeyFromPrikey(newPrikey));


        prikey = prikeyToBytes(newPrikey);
        cryptoDataByte.setPubkeyA(newPubkey);
        return prikey;
    }

    private void encryptWithPassword(CryptoDataByte cryptoDataByte) {

        cryptoDataByte.setType(EncryptType.Password);
        if (!isGoodEncryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }

        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            cryptoDataByte.setMessage("Create sha256 digester wrong:" + e.getMessage());
            return;
        }
        byte[] iv = getRandomIv();
        cryptoDataByte.setIv(iv);
        byte[] symkeyBytes = makeSymkeyFromPassword(cryptoDataByte, sha256, iv);
        cryptoDataByte.setSymkey(symkeyBytes);
        aesEncrypt(cryptoDataByte);
    }

    private void encryptWithSymkey(CryptoDataByte cryptoDataByte) {

        if (!isGoodEncryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }
        cryptoDataByte.setType(EncryptType.Symkey);
        isGoodEncryptParams(cryptoDataByte);
        cryptoDataByte.setIv(getRandomIv());
        aesEncrypt(cryptoDataByte);
        cryptoDataByte.clearAllSensitiveDataButSymkey();
    }

    private void decryptWithSymkey(CryptoDataByte cryptoDataByte) {
        if (!isGoodSymkeyDecryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }
        try {
            if (!isGoodAesSum(cryptoDataByte)) {
                cryptoDataByte.setCodeMessage(20);
                return;
            }
            byte[] msg = Aes256CbcP7.decrypt(cryptoDataByte.getCipher(), cryptoDataByte.getSymkey(), cryptoDataByte.getIv());
            cryptoDataByte.setData(msg);
            cryptoDataByte.clearAllSensitiveDataButSymkey();
            cryptoDataByte.set0CodeMessage();
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | NoSuchProviderException |
                 InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            cryptoDataByte.setMessage("Decrypt with symkey wrong: " + e.getMessage());
        }
    }

    public void aesEncrypt(CryptoDataByte cryptoDataByte) {

        if (!isGoodEncryptParams(cryptoDataByte)) {
            //eccAesDataByte.clearAllSensitiveData();
            return;
        }

        byte[] iv = cryptoDataByte.getIv();
        byte[] msgBytes = cryptoDataByte.getData();
        byte[] symkeyBytes = cryptoDataByte.getSymkey();
        MessageDigest sha256;
        byte[] cipher;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
            cipher = Aes256CbcP7.encrypt(msgBytes, symkeyBytes, iv);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            cryptoDataByte.setMessage("Aes encrypting wrong: " + e.getMessage());
            return;
        }
        byte[] sum4 = getSum4(sha256, symkeyBytes, iv, cipher);
        cryptoDataByte.setCipher(cipher);
        cryptoDataByte.setSum(sum4);
        cryptoDataByte.setData(null);
        cryptoDataByte.clearAllSensitiveDataButSymkey();
    }
//
//    private boolean aesEncryptFile(String sourceFilePathName,String destFilePathName,byte[] key) {
//
//        EccAesDataByte eccAesDataByte = new EccAesDataByte();
//        eccAesDataByte.setType(EccAesType.Symkey);
//        eccAesDataByte.setAlg(Algorithm.EccAes256K1P7_No1_NrC7.name());
//        byte[] iv = BytesTools.getRandomBytes(16);
//        eccAesDataByte.setIv(iv);
//
//        String tempFilePath = FileTools.getTempFileName();
//        try {
//            Aes256CbcP7.encryptFile(sourceFilePathName,tempFilePath,key);
//        } catch (Exception e) {
//            log.debug("Failed to encrypt file:"+e.getMessage());
//            return false;
//        }
//        byte[] cipherDid = Hash.Sha256x2Bytes(new File(destFilePathName));
//        byte[] sum = getSum4(key, iv, cipherDid);
//        eccAesDataByte.setSum(sum);
//
//        FileTools.createFileWithDirectories(destFilePathName);
//        FileOutputStream fos = new FileOutputStream(destFilePathName);
//        FileInputStream fis = new FileInputStream(tempFilePath);
//        EccAesData eccAesData = EccAesData.fromEccAesDataByte(eccAesDataByte);
//        byte[] headBytes = JsonTools.getString(eccAesData).getBytes();
//        fos.write(headBytes);
//
//        byte[] buffer = new byte[8192];
//        int bytesRead;
//        long bytesLength = 0;
//        while ((bytesRead = fis.read(buffer)) != -1) {
//            // Write the bytes read from the request input stream to the output stream
//            fos.write(buffer, 0, bytesRead);
//            hasher.putBytes(buffer, 0, bytesRead);
//            bytesLength +=bytesRead;
//        }
//
//
//        byte[] msgBytes = eccAesDataByte.getMsg();
//        byte[] symkeyBytes = eccAesDataByte.getSymkey();
//        MessageDigest sha256;
//        byte[] cipher;
//        try {
//            sha256 = MessageDigest.getInstance("SHA-256");
//            cipher = Aes256CbcP7.encrypt(msgBytes, symkeyBytes, iv);
//        } catch (NoSuchAlgorithmException | NoSuchProviderException | NoSuchPaddingException | InvalidKeyException |
//                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
//            eccAesDataByte.setError("Aes encrypting wrong: " + e.getMessage());
//            return;
//        }
//        byte[] sum4 = getSum4(sha256, symkeyBytes, iv, cipher);
//        eccAesDataByte.setCipher(cipher);
//        eccAesDataByte.setSum(sum4);
//        eccAesDataByte.setMsg(null);
//        eccAesDataByte.clearAllSensitiveDataButSymkey();
//    }

    private byte[] makeSymkeyFromPassword(CryptoDataByte cryptoDataByte, MessageDigest sha256, byte[] iv) {
        byte[] symkeyBytes = sha256.digest(BytesUtils.addByteArray(sha256.digest(cryptoDataByte.getPassword()), iv));
        return symkeyBytes;
    }

    private static byte[] getSharedSecret(byte[] prikeyBytes, byte[] pubkeyBytes) {

        ECPrivateKeyParameters prikey = prikeyFromBytes(prikeyBytes);
        ECPublicKeyParameters pubkey = pubkeyFromBytes(pubkeyBytes);
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(prikey);
        return agreement.calculateAgreement(pubkey).toByteArray();
    }

    public static ECPublicKeyParameters pubkeyFromBytes(byte[] publicKeyBytes) {

        X9ECParameters ecParameters = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256k1");
        ECDomainParameters domainParameters = new ECDomainParameters(ecParameters.getCurve(), ecParameters.getG(), ecParameters.getN(), ecParameters.getH());

        ECCurve curve = domainParameters.getCurve();

        ECPoint point = curve.decodePoint(publicKeyBytes);

        return new ECPublicKeyParameters(point, domainParameters);
    }

    public ECPublicKeyParameters pubkeyFromHex(String publicKeyHex) {
        return pubkeyFromBytes(Hex.decode(publicKeyHex));
    }

    public String pubkeyToHex(ECPublicKeyParameters publicKey) {
        return Hex.toHexString(pubkeyToBytes(publicKey));
    }

    public String prikeyToHex(ECPrivateKeyParameters privateKey) {
        BigInteger privateKeyValue = privateKey.getD();
        String hex = privateKeyValue.toString(16);
        while (hex.length() < 64) {  // 64 is for 256-bit key
            hex = "0" + hex;
        }
        return hex;
    }

    public byte[] prikeyToBytes(ECPrivateKeyParameters privateKey) {
        return Hex.decode(prikeyToHex(privateKey));//Hex.decode(prikeyToHex(privateKey));
    }

    public static byte[] getPartOfBytes(byte[] original, int offset, int length) {
        byte[] part = new byte[length];
        System.arraycopy(original, offset, part, 0, part.length);
        return part;
    }

    private boolean isErrorAlgAndType(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getMessage() != null) {
            cryptoDataByte.setMessage("There was an error. Check it at first:" + cryptoDataByte.getMessage() + " .");
            //eccAesDataByte.clearAllSensitiveData();
            return true;
        }

        if (cryptoDataByte.getAlg() == null) {
            cryptoDataByte.setAlg(EccAes256K1P7_No1_NrC7);
        } else if (!cryptoDataByte.getAlg().equals(EccAes256K1P7_No1_NrC7)) {
            cryptoDataByte.setMessage("This method only used by the algorithm " + EccAes256K1P7_No1_NrC7+ " .");
            //eccAesDataByte.clearAllSensitiveData();
            return true;
        }

        if (cryptoDataByte.getType() == null) {
            cryptoDataByte.setMessage("EccAesType is required.");
            //eccAesDataByte.clearAllSensitiveData();
            return true;
        }
        return false;
    }

    private boolean isGoodEncryptParams(CryptoDataByte cryptoDataByte) {
        EncryptType type = cryptoDataByte.getType();
        switch (type) {
            case AsyOneWay -> {
                return isGoodAsyOneWayEncryptParams(cryptoDataByte);
            }
            case AsyTwoWay -> {
                return isGoodAsyTwoWayEncryptParams(cryptoDataByte);
            }
            case Symkey -> {
                return isGoodSymkeyEncryptParams(cryptoDataByte);
            }
            case Password -> {
                return isGoodPasswordEncryptParams(cryptoDataByte);
            }
            default -> cryptoDataByte.setMessage("Wrong type: " + cryptoDataByte.getType());
        }
        return true;
    }

    private boolean isGoodEncryptParams(CryptoDataStr cryptoDataStr) {
        EncryptType type = cryptoDataStr.getType();
        switch (type) {
            case AsyOneWay -> {
                return isGoodAsyOneWayEncryptParams(cryptoDataStr);
            }
            case AsyTwoWay -> {
                return isGoodAsyTwoWayEncryptParams(cryptoDataStr);
            }
            case Symkey -> {
                return isGoodSymkeyEncryptParams(cryptoDataStr);
            }
            case Password -> {
                return isGoodPasswordEncryptParams(cryptoDataStr);
            }
            default -> cryptoDataStr.setMessage("Wrong type: " + cryptoDataStr.getType());
        }
        return true;
    }

    private boolean isGoodPasswordEncryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EncryptType.Password.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getPassword() == null) {
            cryptoDataByte.setMessage(EncryptType.Password.name() + " parameters lack password.");
            return false;
        }

        return true;
    }

    private boolean isGoodSymkeyEncryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getSymkey() == null) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameters lack symkey.");
            return false;
        }

        if (cryptoDataByte.getSymkey().length != Constants.SYMKEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameter symkey should be " + Constants.SYMKEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getSymkey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyTwoWayEncryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getPubkeyB() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack pubkeyB.");
            return false;
        }

        if (cryptoDataByte.getPrikeyA() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack prikeyA.");
            return false;
        }

        if (cryptoDataByte.getPubkeyB().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter pubkeyB should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubkeyB().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPrikeyA().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter prikeyA should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPrikeyA().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyOneWayEncryptParams(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getData() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataByte.getPubkeyB() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack pubkeyB.");
            return false;
        }

        if (cryptoDataByte.getPubkeyB().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameter symkey should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getSymkey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodPasswordEncryptParams(CryptoDataStr cryptoDataStr) {

        if (cryptoDataStr.getData() == null) {
            cryptoDataStr.setMessage(EncryptType.Password.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataStr.getPassword() == null) {
            cryptoDataStr.setMessage(EncryptType.Password.name() + " parameters lack password.");
            return false;
        }

        return true;
    }

    private boolean isGoodSymkeyEncryptParams(CryptoDataStr cryptoDataStr) {

        if (cryptoDataStr.getData() == null) {
            cryptoDataStr.setMessage(EncryptType.Symkey.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataStr.getSymkey() == null) {
            cryptoDataStr.setMessage(EncryptType.Symkey.name() + " parameters lack symkey.");
            return false;
        }

        if (cryptoDataStr.getSymkey().length != Constants.SYMKEY_BYTES_LENGTH * 2) {
            cryptoDataStr.setMessage(EncryptType.Symkey.name() + " parameter symkey should be " + Constants.SYMKEY_BYTES_LENGTH * 2 + " characters. It is " + cryptoDataStr.getSymkey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyTwoWayEncryptParams(CryptoDataStr cryptoDataStr) {

        if (cryptoDataStr.getData() == null) {
            cryptoDataStr.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataStr.getPubkeyB() == null) {
            cryptoDataStr.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack pubkeyB.");
            return false;
        }

        if (cryptoDataStr.getPrikeyA() == null) {
            cryptoDataStr.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack prikeyA.");
            return false;
        }

        if (cryptoDataStr.getPubkeyB().length() != Constants.PUBLIC_KEY_BYTES_LENGTH * 2) {
            cryptoDataStr.setMessage(EncryptType.AsyTwoWay.name() + " parameter pubkeyB should be " + Constants.PUBLIC_KEY_BYTES_LENGTH * 2 + " characters. It is " + cryptoDataStr.getPubkeyB().length() + " now.");
            return false;
        }

        if (cryptoDataStr.getPrikeyA().length != Constants.PRIVATE_KEY_BYTES_LENGTH * 2) {
            cryptoDataStr.setMessage(EncryptType.AsyTwoWay.name() + " parameter prikeyA should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " characters. It is " + cryptoDataStr.getPrikeyA().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyOneWayEncryptParams(CryptoDataStr cryptoDataStr) {
        if (cryptoDataStr.getData() == null) {
            cryptoDataStr.setMessage(EncryptType.AsyOneWay.name() + " parameters lack msg.");
            return false;
        }

        if (cryptoDataStr.getPubkeyB() == null) {
            cryptoDataStr.setMessage(EncryptType.AsyOneWay.name() + " parameters lack pubkeyB.");
            return false;
        }

        if (cryptoDataStr.getPubkeyB().length() != Constants.PUBLIC_KEY_BYTES_LENGTH * 2) {
            cryptoDataStr.setMessage(EncryptType.AsyOneWay.name() + " parameter symkey should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " characters. It is " + cryptoDataStr.getSymkey().length + " now.");
            return false;
        }

        return true;
    }

    public boolean isGoodAesSum(CryptoDataByte cryptoDataByte) {

        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] sum4 = getSum4(sha256, cryptoDataByte.getSymkey(), cryptoDataByte.getIv(), cryptoDataByte.getCipher());
        if (!Arrays.equals(sum4, cryptoDataByte.getSum())) {
            cryptoDataByte.setMessage("The sum  is not equal to the value of sha256(symkey+iv+cipher).");
            return false;
        }
        return true;
    }

    private boolean isGoodDecryptParams(CryptoDataByte cryptoDataByte) {
        EncryptType type = cryptoDataByte.getType();
        switch (type) {
            case AsyOneWay -> {
                return isGoodAsyOneWayDecryptParams(cryptoDataByte);
            }
            case AsyTwoWay -> {
                return isGoodAsyTwoWayDecryptParams(cryptoDataByte);
            }
            case Symkey -> {
                return isGoodSymkeyDecryptParams(cryptoDataByte);
            }
            case Password -> {
                return isGoodPasswordDecryptParams(cryptoDataByte);
            }
            default -> cryptoDataByte.setMessage("Wrong type: " + cryptoDataByte.getType());
        }
        return true;
    }

    private boolean isGoodPasswordDecryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getPassword() == null || isZero(cryptoDataByte.getPassword())) {
            cryptoDataByte.setMessage(EncryptType.Password.name() + " parameters lack password.");
            return false;
        }

        if (cryptoDataByte.getIv().length != Constants.IV_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameter iv should be " + Constants.IV_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getIv().length + " now.");
            return false;
        }
        return true;
    }

    private boolean isGoodSymkeyDecryptParams(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getSymkey() == null || isZero(cryptoDataByte.getSymkey())) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameters lack symkey.");
            return false;
        }

        if (cryptoDataByte.getIv().length != Constants.IV_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameter iv should be " + Constants.IV_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getIv().length + " now.");
            return false;
        }

        if (cryptoDataByte.getSymkey() != null && cryptoDataByte.getSymkey().length != Constants.SYMKEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.Symkey.name() + " parameter symkey should be " + Constants.SYMKEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getSymkey().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyTwoWayDecryptParams(CryptoDataByte cryptoDataByte) {

        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getPubkeyA() == null || cryptoDataByte.getPubkeyA() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack pubkeyA and pubkeyB.");
            return false;
        }

        if ((cryptoDataByte.getPrikeyB() == null || isZero(cryptoDataByte.getPrikeyB()))
                && (cryptoDataByte.getPrikeyA() == null || isZero(cryptoDataByte.getPrikeyA()))) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameters lack both prikeyA and prikeyB.");
            return false;
        }

        if (cryptoDataByte.getPubkeyA() != null && cryptoDataByte.getPubkeyA().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter pubkeyA should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubkeyA().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPubkeyB() != null && cryptoDataByte.getPubkeyB().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter pubkeyB should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubkeyB().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPrikeyA() != null && cryptoDataByte.getPrikeyA().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter prikeyA should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPrikeyA().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPrikeyB() != null && cryptoDataByte.getPrikeyB().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyTwoWay.name() + " parameter prikeyB should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPrikeyB().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isGoodAsyOneWayDecryptParams(CryptoDataByte cryptoDataByte) {
        if (cryptoDataByte.getCipher() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack cipher.");
            return false;
        }

        if (cryptoDataByte.getIv() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack iv.");
            return false;
        }

        if (cryptoDataByte.getPubkeyA() == null) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack pubkeyA.");
            return false;
        }

        if (cryptoDataByte.getPrikeyB() == null || isZero(cryptoDataByte.getPrikeyB())) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameters lack prikeyB.");
            return false;
        }

        if (cryptoDataByte.getPubkeyA().length != Constants.PUBLIC_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameter pubkeyA should be " + Constants.PUBLIC_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPubkeyA().length + " now.");
            return false;
        }

        if (cryptoDataByte.getPrikeyB().length != Constants.PRIVATE_KEY_BYTES_LENGTH) {
            cryptoDataByte.setMessage(EncryptType.AsyOneWay.name() + " parameter prikeyB should be " + Constants.PRIVATE_KEY_BYTES_LENGTH + " bytes. It is " + cryptoDataByte.getPrikeyB().length + " now.");
            return false;
        }

        return true;
    }

    private boolean isZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) return false;
        }
        return true;
    }

    private byte[] getSum4(MessageDigest sha256, byte[] symkey, byte[] iv, byte[] cipher) {
        byte[] sum32 = sha256.digest(BytesUtils.addByteArray(symkey, BytesUtils.addByteArray(iv, cipher)));
        return getPartOfBytes(sum32, 0, 4);
    }

    public static byte[] getSum4(byte[] symkey, byte[] iv, byte[] cipher) {
        byte[] sum32 = Hash.sha256(BytesUtils.addByteArray(symkey, BytesUtils.addByteArray(iv, cipher)));
        return getPartOfBytes(sum32, 0, 4);
    }

    public void copyEccAesData(CryptoDataStr fromCryptoDataStr, CryptoDataStr toCryptoDataStr) {
        toCryptoDataStr.setType(fromCryptoDataStr.getType());
        toCryptoDataStr.setAlg(fromCryptoDataStr.getAlg());
        toCryptoDataStr.setData(fromCryptoDataStr.getData());
        toCryptoDataStr.setCipher(fromCryptoDataStr.getCipher());
        toCryptoDataStr.setSymkey(fromCryptoDataStr.getSymkey());
        toCryptoDataStr.setPassword(fromCryptoDataStr.getPassword());
        toCryptoDataStr.setPubkeyA(fromCryptoDataStr.getPubkeyA());
        toCryptoDataStr.setPubkeyB(fromCryptoDataStr.getPubkeyB());
        toCryptoDataStr.setPrikeyA(fromCryptoDataStr.getPrikeyA());
        toCryptoDataStr.setPrikeyB(fromCryptoDataStr.getPrikeyB());
        toCryptoDataStr.setIv(fromCryptoDataStr.getIv());
        toCryptoDataStr.setSum(fromCryptoDataStr.getSum());
        toCryptoDataStr.setMessage(fromCryptoDataStr.getMessage());
    }

    public static void clearByteArray(byte[] array) {
        Arrays.fill(array, (byte) 0);
    }

}
