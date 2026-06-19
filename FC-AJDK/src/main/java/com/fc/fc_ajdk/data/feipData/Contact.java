package com.fc.fc_ajdk.data.feipData;

import static com.fc.fc_ajdk.constants.FieldNames.CID;
import static com.fc.fc_ajdk.constants.FieldNames.FID;
import static com.fc.fc_ajdk.constants.FieldNames.MEMO;
import static com.fc.fc_ajdk.constants.FieldNames.NOTICE_FEE;
import static com.fc.fc_ajdk.constants.FieldNames.SEE_STATEMENT;
import static com.fc.fc_ajdk.constants.FieldNames.SEE_WRITINGS;
import static com.fc.fc_ajdk.constants.FieldNames.TITLES;
import static com.fc.fc_ajdk.constants.FieldNames.UPDATE_HEIGHT;
import static com.fc.fc_ajdk.constants.FieldNames.UPDATE_TIME;

import com.fc.fc_ajdk.core.crypto.Algorithm.Bitcore;

import com.fc.fc_ajdk.data.fcData.CidInfo;
import com.fc.fc_ajdk.data.fchData.Cid;
import com.fc.fc_ajdk.utils.FcUtils;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.JsonUtils;

import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class Contact extends CidInfo {

    private String alg;
    private String cipher;

    private String owner;
    private Long birthTime;
    private Long birthHeight;
    private Long lastHeight;
    private Boolean active;


    private String fid;

//On chain details
    private List<String> titles;

    private String memo;
    private Boolean seeStatement;
    private Boolean seeWritings;

    private Long noticeFeeSat;


	public static LinkedHashMap<String,Integer>getFieldWidthMap(){
		LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
        map.put(CID,ID_DEFAULT_SHOW_SIZE);
        map.put(FID,ID_DEFAULT_SHOW_SIZE);
        map.put(TITLES,TEXT_DEFAULT_SHOW_SIZE);
        map.put(MEMO,TEXT_DEFAULT_SHOW_SIZE);
        map.put(NOTICE_FEE,AMOUNT_DEFAULT_SHOW_SIZE);
        map.put(UPDATE_HEIGHT,TIME_DEFAULT_SHOW_SIZE);
		return map;
	}
	public static List<String> getTimestampFieldList(){
		return new ArrayList<>();
	}

	public static List<String> getSatoshiFieldList(){
		return new ArrayList<>();
	}
	public static Map<String, String> getHeightToTimeFieldMap() {
		Map<String, String> map = new HashMap<>();
		map.put(UPDATE_HEIGHT, UPDATE_TIME);
		return map;
	}

	public static Map<String, String> getShowFieldNameAsMap() {
		return new HashMap<>();
	}
    public static LinkedHashMap<String, Object> getInputFieldDefaultValueMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put(FID, "");
        map.put(TITLES, new ArrayList<>().add(""));
        map.put(MEMO, "");
        map.put(SEE_STATEMENT, true);

        map.put(SEE_WRITINGS, true);
        return map;
    }

    public static Contact fromCidInfo(Cid cid) {
        Contact contact = new Contact();
        contact.setFid(cid.getId());
        contact.setCid(cid.getCid());
        String fee = cid.getNoticeFee();
        if(fee!=null) {
            contact.setNoticeFee(fee);
            contact.setNoticeFeeSat(FchUtils.coinToSatoshi(Double.parseDouble(fee)));
        }
        contact.setPubkey(cid.getPubkey());
        contact.setLastHeight(cid.getLastHeight());
        return contact;
    }

    public byte[] toBytes(){
        return JsonUtils.toJson(this).getBytes();
    }

    public static Contact fromBytes(byte[] bytes){
        return JsonUtils.fromJson(new String(bytes), Contact.class);
    }


    private static Contact parseDetail(Contact contact, byte[] priKey) {
        
        if (contact.getCipher() != null && !contact.getCipher().isEmpty()) {
            Decryptor decryptor = new Decryptor();
            String cipher = contact.getCipher();
            byte[] cipherBytes=null;

            String decryptedContent=null;
            byte[] dataBytes;
            CryptoDataByte cryptoDataByte=null;


            if(cipher.startsWith("A")){
                try {
                    cipherBytes = Base64.getDecoder().decode(cipher);
                }catch (IllegalArgumentException e){
                    return null; //Not Base64
                } 
                
                try{
                    cryptoDataByte = CryptoDataByte.fromBundle(cipherBytes);

                    //Not FC algorithm
                    if(cryptoDataByte==null ||cryptoDataByte.getCode() != 0){
                        try {
                            dataBytes = Bitcore.decrypt(cipherBytes, priKey);
                            if(dataBytes == null)return null;
                        }catch (Exception e){
                            return null;
                        }
                        Contact contactDetail = JsonUtils.fromJson(new String(dataBytes), Contact.class);
                        if(contactDetail!=null){
                            contactDetail.setId(contact.getId());
                            contactDetail.setLastHeight(contact.getLastHeight());
                            return contactDetail;
                        }
                    return null;
                    }
                }catch (Exception ignore){
                }
            }

            //FC algorithm
            if(cryptoDataByte==null) {
                try {
                    cryptoDataByte = CryptoDataByte.fromJson(cipher);
                }catch (Exception e){
                    return null;
                }
            }

            cryptoDataByte.setPrikeyB(priKey);
            cryptoDataByte = decryptor.decrypt(cryptoDataByte);

            if (cryptoDataByte.getCode() == 0) {
                // Successfully decrypted
                decryptedContent = new String(cryptoDataByte.getData());
                Contact decryptedDetail = JsonUtils.fromJson(decryptedContent, Contact.class);
                
                if (decryptedDetail != null) {
                    decryptedDetail.setId(contact.getId());
                    decryptedDetail.setLastHeight(contact.getLastHeight());
                    return decryptedDetail;
                }
            } 
        }  
        return null;
    }



    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public Long getLastHeight() {
        return lastHeight;
    }

    public void setLastHeight(Long lastHeight) {
        this.lastHeight = lastHeight;
    }

    public List<String> getTitles() {
        return titles;
    }

    public void setTitles(List<String> titles) {
        this.titles = titles;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public Boolean getSeeStatement() {
        return seeStatement;
    }

    public void setSeeStatement(Boolean seeStatement) {
        this.seeStatement = seeStatement;
    }

    public Boolean getSeeWritings() {
        return seeWritings;
    }

    public void setSeeWritings(Boolean seeWritings) {
        this.seeWritings = seeWritings;
    }

    public Long getNoticeFeeSat() {
        return noticeFeeSat;
    }

    public void setNoticeFeeSat(Long noticeFeeSat) {
        this.noticeFeeSat = noticeFeeSat;
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
    public String getOwner() {
        return owner;
    }
    public void setOwner(String owner) {
        this.owner = owner;
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

    public Boolean isActive() {
        return active;
    }
    public void setActive(Boolean active) {
        this.active = active;
    }
}
