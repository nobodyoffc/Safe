package com.fc.fc_ajdk.data.fcData;


import com.fc.fc_ajdk.constants.FieldNames;
import com.fc.fc_ajdk.core.crypto.Hash;

import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.JsonUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import static com.fc.fc_ajdk.constants.FieldNames.PUB_KEY;
import static com.fc.fc_ajdk.constants.FieldNames.SESSION_KEY;
import static com.fc.fc_ajdk.constants.Values.TRUE;

public class FcSession extends FcObject {
    private String key;
    private String pubkey;
    private String keyCipher;
    private String userId;
    private transient byte[] keyBytes;


    // public static void deleteSession(String sessionName, String sid, Jedis jedis) {
    //     jedis.select(1);
    //     String key = Settings.addSidBriefToName(sid, sessionName);
    //     jedis.del(key);
    // }
    public byte[] makeKeyBytes(){
        if(this.key!=null)
            this.keyBytes = Hex.fromHex(this.key);
        return this.keyBytes;
    }
    private static FcSession fromMap(Map<String, String> sessionMap) {
        FcSession session = new FcSession();
        session.setUserId(sessionMap.get(FieldNames.ID));
        String sessionKey = sessionMap.get(SESSION_KEY);
        if(sessionKey!=null) {
            byte[] keyBytes = Hex.fromHex(sessionKey);
            session.setKeyBytes(keyBytes);
            session.setKey(sessionKey);
            session.setId(IdNameUtils.makeKeyName(keyBytes));
        }
        session.setPubkey(sessionMap.get(PUB_KEY));
        session.makeKeyBytes();
        return session;
    }



    public String sign(byte[] dataBytes) {
        return sign(keyBytes,dataBytes);
    }
    public static String sign(byte[] sessionKeyBytes, byte[] dataBytes) {
        byte[] signBytes = Hash.sha256x2(BytesUtils.bytesMerger(dataBytes, sessionKeyBytes));
        return Hex.toHex(signBytes);
    }

    public String verifySign(String sign, byte[] requestBodyBytes) {
        if(sign==null)return "The sign is null.";
        if(requestBodyBytes==null)return "The byte array is null.";
        byte[] signBytes = BytesUtils.bytesMerger(requestBodyBytes, keyBytes);
        String doubleSha256Hash = Hex.toHex(Hash.sha256x2(signBytes));

        if(!sign.equals(doubleSha256Hash)){
            return "The sign of the request body should be: "+doubleSha256Hash;
        }
        return TRUE;
    }


    public static String genKey(Integer length) {
        if(length==null)return null;
        SecureRandom random = new SecureRandom();
        byte[] keyBytes = new byte[length];
        random.nextBytes(keyBytes);
        return BytesUtils.bytesToHexStringBE(keyBytes);
    }

    public static String makeSessionName(byte[] sessionKey) {
        return IdNameUtils.makeKeyName(sessionKey);
    }
    public String makeId() {
        if(keyBytes==null & key!=null)keyBytes=Hex.fromHex(key);
        return IdNameUtils.makeKeyName(keyBytes);
    }

    public byte[] getKeyBytes() {
        if(keyBytes == null) {
            keyBytes = Hex.fromHex(key);
        }
        return keyBytes;
    }

    public void setKeyBytes(byte[] keyBytes) {
        this.keyBytes = keyBytes;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public String getKeyCipher() {
        return keyCipher;
    }

    public void setKeyCipher(String keyCipher) {
        this.keyCipher = keyCipher;
    }
    public static String toJsonList(List<FcSession> value) {
        return JsonUtils.toJson(value);
    }

    public static List<FcSession> fromJsonList(String json) {
        return JsonUtils.listFromJson(json, FcSession.class);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
