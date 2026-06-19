package com.fc.fc_ajdk.data.feipData;

import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.utils.DateUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Algorithm.Bitcore;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static com.fc.fc_ajdk.constants.FieldNames.*;

public class Secret extends FcObject {
    private String alg;
    private String cipher;

    private String owner;
    private Long birthTime;
    private Long birthHeight;
    private Long lastHeight;
    private Boolean active;

    private String type;
    private String title;
    private String content;
    private String memo;
    private Long updateHeight;
    private String saveTime;
    private String contentCipher;
    private String detailCipher;
    private Boolean onChain;

    public Secret(){}

    public Secret(String title, String content, String memo, Type secretType) {
        super();
        this.title=title;
        this.content=content;
        this.memo=memo;
        this.type = secretType.name();
    }


    public enum Type{
        PRIKEY("prikey"),
        SYMKEY("symkey"),
        PASSWORD("password"),
        TOTP("TOTP"),
        SECRET("secret"),
        TEXT("text");
        public final String displayName;
        Type(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName(Type type){
            return type.displayName;
        }
        
        public static Type fromString(String typeString) {
            for (Type type : Type.values()) {
                if (type.toString().equalsIgnoreCase(typeString)) {
                    return type;
                }
            }
            return null;
        }
    }
    public static List<String> getSatoshiFieldList(){
        return new ArrayList<>();
    }
    public static Map<String, String> getHeightToTimeFieldMap() {
        return new HashMap<>();
    }

    public String checkIdWithCreate(){
        if(this.id==null || this.id.isEmpty())
            id = Hex.toHex(Hash.sha256x2(this.toBytes()));
        return id;
    }

    public static LinkedHashMap<String,Integer> getFieldWidthMap(){
        LinkedHashMap<String,Integer> fieldWidthMap = new LinkedHashMap<>();
        fieldWidthMap.put(TITLE, TEXT_DEFAULT_SHOW_SIZE);
        fieldWidthMap.put(TYPE, TEXT_MEDIUM_DEFAULT_SHOW_SIZE);
        fieldWidthMap.put(SAVE_TIME, TIME_DEFAULT_SHOW_SIZE);
        fieldWidthMap.put(MEMO, TEXT_DEFAULT_SHOW_SIZE);
        fieldWidthMap.put(ID, ID_DEFAULT_SHOW_SIZE);
        return fieldWidthMap;
    }

    public static List<String> getTimestampFieldList(){
        List<String> timestampFieldList = new ArrayList<>();
        timestampFieldList.add(UPDATE_HEIGHT);
        return timestampFieldList;
    }

    public static Map<String,String> getShowFieldNameAs(){
        Map<String,String> showFieldNameAs = new HashMap<>();
        showFieldNameAs.put(UPDATE_HEIGHT, "Update Time");
        return showFieldNameAs;
    }


    public byte[] toBytes() {
        return JsonUtils.toJson(this).getBytes();
    }

    public static Secret fromBytes(byte[] bytes) {
        return JsonUtils.fromJson(new String(bytes), Secret.class);
    }

    public static Secret parseDetail(Secret secret, byte[] priKey) {
        if (secret.getCipher() != null && !secret.getCipher().isEmpty()) {
            Decryptor decryptor = new Decryptor();
            String cipher = secret.getCipher();
            byte[] cipherBytes = null;
            CryptoDataByte cryptoDataByte = null;

            if (cipher.startsWith("A")) {
                try {
                    cipherBytes = Base64.getDecoder().decode(cipher);
                } catch (IllegalArgumentException e) {
                    return null; // Not Base64
                }

                try {
                    cryptoDataByte = CryptoDataByte.fromBundle(cipherBytes);

                    // Not FC algorithm
                    if (cryptoDataByte == null || cryptoDataByte.getCode() != 0) {
                        try {
                            byte[] dataBytes = Bitcore.decrypt(cipherBytes, priKey);
                            Secret secretDetail = JsonUtils.fromJson(new String(dataBytes), Secret.class);
                            if (secretDetail != null) {
                                secretDetail.setId(secret.getId());
                                secretDetail.setUpdateHeight(secret.getLastHeight());
                                return secretDetail;
                            }
                        } catch (Exception e) {
                            return null;
                        }
                        return null;
                    }
                } catch (Exception ignore) {
                }
            }

            // FC algorithm
            if (cryptoDataByte == null) {
                try {
                    cryptoDataByte = CryptoDataByte.fromJson(cipher);
                } catch (Exception e) {
                    return null;
                }
            }

            cryptoDataByte.setPrikeyB(priKey);
            cryptoDataByte = decryptor.decrypt(cryptoDataByte);

            if (cryptoDataByte.getCode() == 0) {
                // Successfully decrypted
                String decryptedContent = new String(cryptoDataByte.getData());
                Secret decryptedDetail = JsonUtils.fromJson(decryptedContent, Secret.class);

                if (decryptedDetail != null) {
                    decryptedDetail.setId(secret.getId());
                    decryptedDetail.setUpdateHeight(secret.getLastHeight());
                    return decryptedDetail;
                }
            }
        }
        return null;
    }


    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public Long getUpdateHeight() {
        return updateHeight;
    }

    public void setUpdateHeight(Long updateHeight) {
        this.updateHeight = updateHeight;
    }

    public String getContentCipher() {
        return contentCipher;
    }

    public void setContentCipher(String contentCipher) {
        this.contentCipher = contentCipher;
    }

    public String getSaveTime() {
        return saveTime;
    }

    public void setSaveTime(Long saveTime) {
        this.saveTime = DateUtils.longToTime(saveTime,DateUtils.TO_MINUTE);
    }
    public void setSaveTime(String saveTime) {
        this.saveTime = saveTime;
    }

    public static String getShowFieldName(String fieldName, String language) {
        switch (language.toLowerCase()) {
            case "zh":
            case "zh-cn":
                return getShowFieldNameZh(fieldName);
            case "en":
            default:
                return getShowFieldNameEn(fieldName);
        }
    }

    private static String getShowFieldNameEn(String fieldName) {
        switch (fieldName) {
            case "id": return "Secret ID";
            case "type": return "Type";
            case "title": return "Title";
            case "content": return "Content";
            case "memo": return "Memo";
            case "updateHeight": return "Update Height";
            case "saveTime": return "Save Time";
            case "contentCipher": return "Content Cipher";
            default: return fieldName;
        }
    }

    private static String getShowFieldNameZh(String fieldName) {
        return switch (fieldName) {
            case "id" -> "ID";
            case "type" -> "类型";
            case "title" -> "标题";
            case "content" -> "内容";
            case "memo" -> "备注";
            case "updateHeight" -> "更新高度";
            case "saveTime" -> "保存时间";
            case "contentCipher" -> "内容密文";
            default -> fieldName;
        };
    }

    public static LinkedHashMap<String, Map<String, String>> getFieldNameMap() {
        LinkedHashMap<String, Map<String, String>> fieldMap = new LinkedHashMap<>();

        // Add Secret fields (lines 32-38)
        addFieldToMap(fieldMap, "type", "Type", "类型");
        addFieldToMap(fieldMap, "title", "Title", "标题");
        addFieldToMap(fieldMap, "content", "Content", "内容");
        addFieldToMap(fieldMap, "memo", "Memo", "备注");
        addFieldToMap(fieldMap, "updateHeight", "Update Height", "更新高度");
        addFieldToMap(fieldMap, "saveTime", "Save Time", "保存时间");
        addFieldToMap(fieldMap, "contentCipher", "Content Cipher", "内容密文");

        return fieldMap;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getDetailCipher() {
        return detailCipher;
    }

    public void setDetailCipher(String detailCipher) {
        this.detailCipher = detailCipher;
    }

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }
    public String getCipher() {
        return cipher;
    }
    public void setCipher(String cipher) {
        this.cipher = cipher;
    }
    public Long getBirthTime() {
        return birthTime;
    }
    public void setBirthTime(Long birthTime) {
        this.birthTime = birthTime;
    }
    public Long getBirthHeight() {
        return birthHeight;
    }
    public void setBirthHeight(Long birthHeight) {
        this.birthHeight = birthHeight;
    }
    public Long getLastHeight() {
        return lastHeight;
    }
    public void setLastHeight(Long lastHeight) {
        this.lastHeight = lastHeight;
    }
    public void setActive(Boolean active) {
        this.active = active;
    }
    public Boolean getActive() {
        return active;
    }

    public Boolean getOnChain() {
        return onChain;
    }

    public void setOnChain(Boolean onChain) {
        this.onChain = onChain;
    }
}
