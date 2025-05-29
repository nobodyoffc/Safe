package com.fc.fc_ajdk.data.fcData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.core.fch.SchnorrSignature;

import org.bitcoinj.core.ECKey;


import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

public class Signature extends FcObject{
    private String fid;
    private String msg;
    private String sign;
    private AlgorithmId alg;
    private String keyName;


    private String address;
    private String message;
    private String signature;
    private AlgorithmId algorithm;



    private transient byte[] key;
    private transient byte[] fidBytes;
    private transient byte[] msgBytes;
    private transient byte[] signBytes;
    private transient byte[] algBytes;
    private transient byte[] keyNameBytes;

    public Signature() {
    }

    public Signature(String msg, byte[] key, AlgorithmId alg) {
        this.msg = msg;
        this.key = key;
        this.alg = alg;
        makeKeyName(key, alg);
    }

    public Signature(String symSign, String symkeyName) {
        this.sign = symSign;
        this.keyName = symkeyName;
    }

    public Signature(String fid, String msg, String sign, AlgorithmId alg, String symkeyName) {
        if (fid != null) {
            this.fid = fid;
            this.address = fid;
        }

        if (symkeyName != null) {
            this.keyName = symkeyName;
        }

        if (msg != null) {
            this.msg = msg;
            this.message = msg;
        }

        if (sign != null) {
            this.sign = sign;
            this.signature = sign;
        }

        if (alg != null) {
            this.alg = alg;
            this.algorithm = alg;
        }
    }

    public static boolean isGoodSha256Sign(String str, String sign, String symkey) {
        byte[] requestBodyBytes = str.getBytes(StandardCharsets.UTF_8);
        return isGoodSha256Sign(requestBodyBytes, sign, Hex.fromHex(symkey));
    }

    public static boolean isGoodSha256Sign(byte[] bytes, String sign, byte[] symkey) {
        if (sign == null || bytes == null) return false;
        byte[] signBytes = BytesUtils.bytesMerger(bytes, symkey);
        byte[] hash = Hash.sha256x2(signBytes);
        String doubleSha256Hash = Hex.toHex(hash);

        return (sign.equals(doubleSha256Hash));
    }

    public void strToBytes() {
        if(alg!=null)algBytes = switch (alg) {
            case FC_Sha256SymSignMsg_No1_NrC7 -> new byte[]{0, 0, 0, 0, 0, 2};
            case BTC_EcdsaSignMsg_No1_NrC7 -> new byte[]{0, 0, 0, 0, 0, 3};
            default -> algBytes;
        };

        if(keyName!=null)
            keyNameBytes = Hex.fromHex(keyName);

        if(fid!=null)
            fidBytes = KeyTools.addrToHash160(fid);

        if(sign!=null)
            signBytes = Base64.getDecoder().decode(sign);

        if(msg!=null)msgBytes=msg.getBytes();
    }

    public void bytesToStr() {
        if(algBytes!=null)alg = switch (Arrays.toString(algBytes)) {
            case "[0, 0, 0, 0, 0, 3]" ->AlgorithmId.FC_Sha256SymSignMsg_No1_NrC7;
            case "[0, 0, 0, 0, 0, 4]" -> AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7;
            default -> alg;
        };

        if(keyNameBytes!=null)
            keyName = Hex.toHex(keyNameBytes);

        if(fidBytes!=null)
            fid = KeyTools.hash160ToFchAddr(fidBytes);

        if(signBytes!=null)
            sign = Base64.getEncoder().encodeToString(signBytes);

        if(msgBytes!=null)msg= new String(msgBytes);
    }

    public byte[] toBundle() {
        if (msg == null || sign == null) {
            return null; // Handle basic null checks early
        }
        strToBytes();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        // Create algorithm byte array
            switch (alg) {
                case FC_Sha256SymSignMsg_No1_NrC7 ->{
                     // Defaults to all zeroes
                    algBytes = new byte[]{0, 0, 0, 0, 0, 3};
                    // Write algBytes (6 bytes)
                    outputStream.write(algBytes);

                    //Write keyName (6 bytes)
                    if(keyNameBytes==null)return null;
                    outputStream.write(keyNameBytes);
                }
                case BTC_EcdsaSignMsg_No1_NrC7 -> {
                    algBytes = new byte[]{0, 0, 0, 0, 0, 4};
                    outputStream.write(algBytes);

                    if(fidBytes==null)return null;
                    outputStream.write(fidBytes);
                }
                default -> {
                    return null;
                }
            }

            //Write sign length
            if(signBytes==null)return null;
            int signLength = signBytes.length;
            byte[] signLengthBytes = BytesUtils.intTo2ByteArray(signLength);

            outputStream.write(signLengthBytes);
            outputStream.write(signBytes);

            outputStream.write(msgBytes);

            // Convert the output stream to a byte array
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Handle potential IO exceptions (shouldn't happen with ByteArrayOutputStream)
            e.printStackTrace();
            return null;
        }
    }


    public static Signature fromBundle(byte[] bundle) {
        if (bundle == null || bundle.length < 12) {
            return null;
        }

        Signature signature = new Signature();
        int offset = 0;
        // Extract the algorithm bytes (first 6 bytes)
        byte[] algBytes = new byte[6];
        System.arraycopy(bundle, 0, algBytes, 0, 6);
        offset+=6;
        signature.setAlgBytes(algBytes);

        // Map algorithm bytes back to AlgorithmId
        AlgorithmId alg;
        switch (Arrays.toString(algBytes)) {
            case "[0, 0, 0, 0, 0, 3]" -> {
                alg = AlgorithmId.FC_Sha256SymSignMsg_No1_NrC7;
                byte[] keyNameBytes = new byte[6];
                System.arraycopy(bundle, offset, keyNameBytes, 0, 6);
                offset+=6;
                signature.setKeyNameBytes(keyNameBytes);
            }
            case "[0, 0, 0, 0, 0, 4]" -> {
                alg = AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7;
                byte[] hash120 = new byte[20];
                System.arraycopy(bundle, offset, hash120, 0, 20);
                offset+=20;
                signature.setFidBytes(hash120);
                signature.setFid(KeyTools.hash160ToFchAddr(hash120));
            }
            default -> {
                return null;
            }
        }
        signature.setAlg(alg);

        byte[] signLengthBytes = new byte[2];
        System.arraycopy(bundle, offset, signLengthBytes, 0, 2);
        offset+=2;

        int signBytesLength = BytesUtils.bytes2ToIntBE(signLengthBytes);
        byte[] signBytes = new byte[signBytesLength];
        System.arraycopy(bundle, offset, signBytes, 0, signBytesLength);
        offset+=signBytesLength;
        signature.setSignBytes(signBytes);
        signature.setSign(Base64.getEncoder().encodeToString(signBytes));

        int msgBytesLength = bundle.length-offset;
        byte[] msgBytes = new byte[msgBytesLength];
        System.arraycopy(bundle, offset, msgBytes, 0, msgBytesLength);
        signature.setMsgBytes(msgBytes);
        signature.setMsg(new String(msgBytes));

        signature.bytesToStr();
        return signature;
    }


    public static String symSign(String msg, String symkey) {
        if(msg==null || symkey==null)return null;
        byte[] replyJsonBytes = msg.getBytes();
        byte[] keyBytes = Hex.fromHex(symkey);
        byte[] bytes = BytesUtils.bytesMerger(replyJsonBytes,keyBytes);
        byte[] signBytes = Hash.sha256x2(bytes);
        return Hex.toHex(signBytes);
    }

    public static byte[] symSign(byte[] msg, byte[] symkey) {
        if(msg==null || symkey==null)return null;
        byte[] bytes = BytesUtils.bytesMerger(msg, symkey);
        return Hash.sha256x2(bytes);
    }
    public byte[] sign(byte[] msgBytes,byte[] key,AlgorithmId alg){
        Signature signature1 = sign(new String(msgBytes),key,alg);
        if(signature1==null)return null;
        return signature1.toBundle();
    }
    public Signature sign(){
        if(this.msg==null || this.key==null || this.alg==null)return null;
        Signature signature = sign(this.msg,this.key,this.alg);
        this.sign = signature.getSign();
        return this;
    }
    public Signature sign(String msg,byte[] key,AlgorithmId alg){
        if(msg==null || key==null || alg==null)return null;
        this.alg = alg;
        this.msg = msg;
        this.sign=null;

        ECKey ecKey = ECKey.fromPrivate(key);

        switch (alg){
            case BTC_EcdsaSignMsg_No1_NrC7 -> {
                this.fid = KeyTools.prikeyToFid(key);
                this.sign = ecKey.signMessage(msg);
            }
            case FC_Sha256SymSignMsg_No1_NrC7 -> {
                String keyHex = Hex.toHex(key);
                makeKeyName(key, AlgorithmId.FC_Sha256SymSignMsg_No1_NrC7);
                this.sign = symSign(msg, keyHex);
            }
            case FC_SchnorrSignMsg_No1_NrC7 -> {
                this.fid = KeyTools.prikeyToFid(key);
                this.sign = schnorrMsgSign(msg, key);
            }
            default -> {
                System.out.println("Unsupported algorithm");
                return null;
            }
        }
        return this;
    }
    public void makeKeyName(byte[] key, AlgorithmId algorithmId) {
        if(key==null)return;
        switch (algorithmId){
            case FC_Sha256SymSignMsg_No1_NrC7 -> {
                byte[] keyNameBytes = new byte[6];
                byte[] hash = Hash.sha256(key);
                System.arraycopy(hash,0,keyNameBytes,0,6);
                this.keyName = Hex.toHex(keyNameBytes);
            }
            case FC_SchnorrSignMsg_No1_NrC7, BTC_EcdsaSignMsg_No1_NrC7 -> {
                this.keyName = KeyTools.prikeyToFid(key);
            }
            default -> {
                return;
            }
        }
    }

    @Nullable
    public static Signature parseSignature(String rawSignJson) {
        if(rawSignJson==null|| "".equals(rawSignJson))return null;
        Signature signature;
        try {
            if (rawSignJson.contains("----")) {
                signature = parseOldSign(rawSignJson);
                signature.setAlg(AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7);
            } else {
                signature = fromJson(rawSignJson);
                signature.makeSignature();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return signature;
    }

    public static Signature parseOldSign(String oldSign) {
        String[] elm = oldSign.split("----");
        Signature signature = new Signature();
        signature.setMsg(elm[0].replaceAll("\"", ""));
        signature.setFid(elm[1]);
        signature.setSign(elm[2]
                .replaceAll("\"", "")
                .replaceAll("\\u003d", "="));
        return signature;
    }

    public void makeSignature() {
        if (fid == null && address != null) fid = address;
        if (msg == null && message != null) msg = message;
        if (sign == null && signature != null) sign = signature;
        if (alg == null && algorithm != null) alg = algorithm;
    }

    public boolean isAsyPrepared() {
        return (fid != null && msg != null && sign != null && alg != null);
    }
    public boolean isSymPrepared() {
        return (key != null &&msg != null && sign != null && alg != null);
    }

    public boolean verify() {
        if(alg==null)alg = AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7;
        switch (alg){
            case BTC_EcdsaSignMsg_No1_NrC7 ->{
                try {
                    String pubkey = ECKey.signedMessageToKey(msg, sign).getPublicKeyAsHex();
                    String signFid = KeyTools.pubkeyToFchAddr(pubkey);
                    return fid.equals(signFid);
                } catch (SignatureException e) {
                    return false;
                }
            }
            case FC_Sha256SymSignMsg_No1_NrC7 -> {
                return verifySha256SymSign(msg,key,sign);
//                if(sign==null)return false;
//                byte[] signBytes = BytesTools.bytesMerger(msg.getBytes(), key);
//                String doubleSha256Hash = HexFormat.of().formatHex(Hash.sha256x2(signBytes));
//                return sign.equals(doubleSha256Hash);
            }
            case FC_SchnorrSignMsg_No1_NrC7 -> {
                return schnorrMsgVerify(msg, sign, fid);
            }
            default -> {
                System.out.println("Unsupported algorithm");
                return false;
            }
        }
    }

    public static boolean verifySha256SymSign(String msg,byte[] key, String sign){
        byte[] msgBytes = msg.getBytes();
        return verifySha256SymSign(msgBytes,key,sign);
    }

    public static boolean verifySha256SymSign(byte[] msgBytes,byte[] key, String sign){
        if(sign==null)return false;
        byte[] signBytes = BytesUtils.bytesMerger(msgBytes, key);
        String doubleSha256Hash = Hex.toHex(Hash.sha256x2(signBytes));
        return sign.equals(doubleSha256Hash);
    }

    public String toJson() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(AlgorithmId.class, new AlgorithmId.AlgorithmTypeSerializer())
                .disableHtmlEscaping()
                .create();
        return toJson(gson);
    }

    public String toNiceJson() {
        GsonBuilder gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(AlgorithmId.class, new AlgorithmId.AlgorithmTypeSerializer())
                .setPrettyPrinting()
                .disableHtmlEscaping();
        Gson gson = gsonBuilder.create();
        return toJson(gson);
    }

    private String toJson(Gson gson) {
        switch (alg){
            case BTC_EcdsaSignMsg_No1_NrC7, FC_SchnorrSignMsg_No1_NrC7 -> {
                return gson.toJson(new ShortSign(fid, null, msg, sign, alg));
            }
            case FC_Sha256SymSignMsg_No1_NrC7 -> {
                return gson.toJson(new ShortSign(null, keyName, msg, sign, alg));
            }
            default -> {
                System.out.println("Unsupported algorithm:"+alg);
                return new Gson().toJson(this);
            }
        }
    }

    public static Signature fromJson(String signatureJson){
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(AlgorithmId.class, new AlgorithmId.AlgorithmTypeDeserializer())
                .create();
        Signature signature1 = gson.fromJson(signatureJson, Signature.class);
        signature1.makeSignature();
        return signature1;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public AlgorithmId getAlg() {
        return alg;
    }

    public void setAlg(AlgorithmId alg) {
        this.alg = alg;
    }


    public void setAddress(String address) {
        this.address = address;
        this.fid = address;
    }
    public void setMessage(String message) {
        this.message = message;
        this.msg = message;
    }
    public void setSignature(String signature) {
        this.signature = signature;
    }
    public void setAlgorithm(AlgorithmId algorithm) {
        this.algorithm = algorithm;
        this.alg =algorithm;
    }

    public String getKeyName() {
        return keyName;
    }
    public enum Type {
        SymSign, AsySign
    }

    static class ShortSign {
        String fid;
        String keyName;
        String msg;
        String sign;
        AlgorithmId alg;

        ShortSign(String fid, String keyName, String msg, String sign, AlgorithmId alg) {
            this.fid = fid;
            this.keyName = keyName;
            this.msg = msg;
            this.sign = sign;
            this.alg = alg;
        }
    }

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public byte[] getFidBytes() {
        return fidBytes;
    }

    public void setFidBytes(byte[] fidBytes) {
        this.fidBytes = fidBytes;
    }

    public byte[] getMsgBytes() {
        return msgBytes;
    }

    public void setMsgBytes(byte[] msgBytes) {
        this.msgBytes = msgBytes;
    }

    public byte[] getSignBytes() {
        return signBytes;
    }

    public void setSignBytes(byte[] signBytes) {
        this.signBytes = signBytes;
    }

    public byte[] getAlgBytes() {
        return algBytes;
    }

    public void setAlgBytes(byte[] algBytes) {
        this.algBytes = algBytes;
    }

    public byte[] getKeyNameBytes() {
        return keyNameBytes;
    }

    public void setKeyNameBytes(byte[] keyNameBytes) {
        this.keyNameBytes = keyNameBytes;
    }

    public static String schnorrMsgSign(String msg, byte[] prikey) {
        ECKey ecKey = ECKey.fromPrivate(prikey);
        BigInteger prikeyBigInteger = ecKey.getPrivKey();
        byte[] pubkey = ecKey.getPubKey();
        byte[] msgHash = Hash.sha256x2(msg.getBytes());
        byte[] sign = SchnorrSignature.schnorr_sign(msgHash, prikeyBigInteger);
        byte[] pkSign = BytesUtils.bytesMerger(pubkey, sign);
        return Base64.getEncoder().encodeToString(pkSign);
    }

    public static boolean schnorrMsgVerify(String msg, String pubSign, String fid)  {
        byte[] msgHash = Hash.sha256x2(msg.getBytes());
        byte[] pubSignBytes = Base64.getDecoder().decode(pubSign);
        byte[] pubkey = Arrays.copyOf(pubSignBytes, 33);
        if (!fid.equals(KeyTools.pubkeyToFchAddr(Hex.toHex(pubkey)))) return false;
        byte[] sign = Arrays.copyOfRange(pubSignBytes, 33, pubSignBytes.length);
        return SchnorrSignature.schnorr_verify(msgHash, pubkey, sign);
    }
}
