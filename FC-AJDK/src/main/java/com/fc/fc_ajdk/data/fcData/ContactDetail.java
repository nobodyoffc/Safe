package com.fc.fc_ajdk.data.fcData;

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

import com.fc.fc_ajdk.data.fchData.Cid;
import com.fc.fc_ajdk.clients.ApipClient;
import com.fc.fc_ajdk.utils.FcUtils;
import com.fc.fc_ajdk.data.feipData.Contact;
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
import com.fc.fc_ajdk.ui.Shower;

public class ContactDetail extends CidInfo{

    private String fid;


    private List<String> titles;

    private String memo;
    private Boolean seeStatement;
    private Boolean seeWritings;

    private Long updateHeight;
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

    public static ContactDetail fromCidInfo(Cid cid) {
        ContactDetail contactDetail = new ContactDetail();
        contactDetail.setFid(cid.getId());
        contactDetail.setCid(cid.getCid());
        String fee = cid.getNoticeFee();
        if(fee!=null) {
            contactDetail.setNoticeFee(fee);
            contactDetail.setNoticeFeeSat(FchUtils.coinToSatoshi(Double.parseDouble(fee)));
        }
        contactDetail.setPubkey(cid.getPubkey());
        contactDetail.setUpdateHeight(cid.getLastHeight());
        return contactDetail;
    }

    public byte[] toBytes(){
        return JsonUtils.toJson(this).getBytes();
    }

    public static ContactDetail fromBytes(byte[] bytes){
        return JsonUtils.fromJson(new String(bytes), ContactDetail.class);
    }

    public static ContactDetail fromContact(Contact contact,byte[] priKey, ApipClient apipClient) {
        ContactDetail contactDetail = fromContact(contact,priKey);
        if (contactDetail == null) return null;
        return makeContactDetail(contactDetail, apipClient);    
    }

    private static ContactDetail fromContact(Contact contact,byte[] priKey) {
        
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
                        ContactDetail contactDetail = JsonUtils.fromJson(new String(dataBytes), ContactDetail.class);
                        if(contactDetail!=null){
                            contactDetail.setId(contact.getId());
                            contactDetail.setUpdateHeight(contact.getLastHeight());
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
                ContactDetail decryptedDetail = JsonUtils.fromJson(decryptedContent, ContactDetail.class);
                
                if (decryptedDetail != null) {
                    decryptedDetail.setId(contact.getId());
                    decryptedDetail.setUpdateHeight(contact.getLastHeight());
                    return decryptedDetail;
                }
            } 
        }  
        return null;
    }

    private static ContactDetail makeContactDetail(ContactDetail contactDetail, ApipClient apipClient) {
        if (contactDetail == null) return null;
        Cid cid = apipClient.getFidCid(contactDetail.getFid());
        if (cid == null) return null;
        contactDetail.setFid(cid.getId());
        contactDetail.setPubkey(cid.getPubkey());
        String fee = cid.getNoticeFee();
        if(fee!=null) {
            contactDetail.setNoticeFee(fee);
            contactDetail.setNoticeFeeSat(FchUtils.coinToSatoshi(Double.parseDouble(fee)));
        }
        contactDetail.setCid(cid.getCid());
        return contactDetail;
    }

    public static void showContactDetailList(List<ContactDetail> contactList, String title, int totalDisplayed) {
        String[] fields = new String[]{"CID", "FID", "Title","Memo", "Notice Fee", "Update Time"};
        int[] widths = new int[]{10, 10, 10,30, 25, 20};
        List<List<Object>> valueListList = new ArrayList<>();

        for (ContactDetail contact : contactList) {
            List<Object> showList = new ArrayList<>();
            showList.add(contact.getCid());
            showList.add(contact.getFid());
            showList.add(contact.getTitles());
            showList.add(contact.getMemo());
            showList.add(contact.getNoticeFee());
            showList.add(FcUtils.heightToLongDate(contact.getUpdateHeight()));
            valueListList.add(showList);
        }
        Shower.showOrChooseList(title, fields, widths, valueListList, null);
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public Long getUpdateHeight() {
        return updateHeight;
    }

    public void setUpdateHeight(Long updateHeight) {
        this.updateHeight = updateHeight;
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
}
