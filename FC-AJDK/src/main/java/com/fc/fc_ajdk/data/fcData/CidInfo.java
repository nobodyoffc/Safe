package com.fc.fc_ajdk.data.fcData;

import static com.fc.fc_ajdk.constants.FieldNames.ID;
import static com.fc.fc_ajdk.constants.FieldNames.IS_NOBODY;
import static com.fc.fc_ajdk.constants.FieldNames.LABEL;
import static com.fc.fc_ajdk.constants.FieldNames.PRI_KEY;
import static com.fc.fc_ajdk.constants.FieldNames.SAVE_TIME;
import static com.fc.fc_ajdk.constants.FieldNames.WATCH_ONLY;
import static com.fc.fc_ajdk.constants.Values.ASC;
import static com.fc.fc_ajdk.constants.Values.DESC;
import static com.fc.fc_ajdk.ui.Shower.DEFAULT_PAGE_SIZE;

import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fchData.Cid;
import com.fc.fc_ajdk.ui.Shower;
import com.fc.fc_ajdk.utils.Hex;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CidInfo extends Cid {
    public final static String TAG = "CidInfo";

    private String prikeyCipher;
    private String label;
    private Boolean watchOnly;


    public static LinkedHashMap<String,Integer> getFieldWidthMap(){
        LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
        map.put(ID,ID_DEFAULT_SHOW_SIZE);
        map.put(LABEL,ID_DEFAULT_SHOW_SIZE);
        map.put(SAVE_TIME,TIME_DEFAULT_SHOW_SIZE);
        map.put(IS_NOBODY,10);
        map.put(WATCH_ONLY,12);
        return map;
    }
    public static List<String> getTimestampFieldList(){
        return new ArrayList<>();
    }

    public static List<String> getSatoshiFieldList(){
        return new ArrayList<>();
    }
    public static Map<String, String> getHeightToTimeFieldMap() {
        return new HashMap<>();
    }

    public static Map<String, String> getShowFieldNameAsMap() {
        Map<String,String> map = new HashMap<>();
        map.put(ID,"FID");
        map.put(LABEL,"Label");
        map.put(SAVE_TIME,"Save Time");
        map.put(IS_NOBODY,"Nobody");
        map.put(WATCH_ONLY,"Watch Only");
        return map;
    }
    public static Map<String, String> getFieldOrderMap() {
        Map<String, String> map = new HashMap<>();
        map.put(SAVE_TIME,DESC);
        map.put(LABEL,ASC);
        return map;
    }

    public static List<String> getReplaceWithMeFieldList() {
        return new ArrayList<>();
    }

    //For create with user input
    public static LinkedHashMap<String, Object> getInputFieldDefaultValueMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put(PRI_KEY,"");
        map.put(LABEL,"");
        map.put(IS_NOBODY,false);
        return map;
    }


    public CidInfo(){}
    public CidInfo(byte[] prikey, byte[] symkey) {
        super();
//        this.prikeyBytes=prikey;
        this.prikeyCipher = Encryptor.encryptBySymkeyToJson(prikey, symkey);
        this.pubkey = Hex.toHex(KeyTools.prikeyToPubkey(prikey));
        this.id = KeyTools.prikeyToFid(prikey);
        makeAddresses();
    }

    public CidInfo(String label, byte[] prikey, byte[] symkey) {
        super();
        this.prikeyBytes =prikey;
        this.prikeyCipher = Encryptor.encryptBySymkeyToJson(prikey, symkey);
        this.label = label;
        this.pubkey = Hex.toHex(KeyTools.prikeyToPubkey(prikey));
        this.id = KeyTools.prikeyToFid(prikey);
        makeAddresses();
    }

    public CidInfo(String label, String pubkey) {
        super();
        this.label = label;
        this.pubkey = pubkey;
        this.id = KeyTools.pubkeyToFchAddr(pubkey);
        this.watchOnly = true;
        makeAddresses();
    }


    public static List<CidInfo> showList(List<CidInfo> keyInfoList, BufferedReader br) {
        return Shower.showOrChooseListInPages("FID Info", keyInfoList, DEFAULT_PAGE_SIZE, null, true, CidInfo.class, br);
    }

    public void makeAddresses() {
        btcAddr = KeyTools.pubkeyToBtcAddr(pubkey);
        ethAddr = KeyTools.pubkeyToEthAddr(pubkey);
        bchAddr = KeyTools.pubkeyToBchBesh32Addr(pubkey);
        ltcAddr = KeyTools.pubkeyToLtcAddr(pubkey);
        dogeAddr = KeyTools.pubkeyToDogeAddr(pubkey);
        trxAddr = KeyTools.pubkeyToTrxAddr(pubkey);
    }

    public static CidInfo newKeyInfo(String label, byte[] prikey, byte[] symkey) {
        return new CidInfo(label, prikey, symkey);
    }

    public static CidInfo newKeyInfo(String label, String pubkey) {
        return new CidInfo(label, pubkey);
    }

    /**
     * Converts a Cid object to a CidInfo object
     * @param cid The Cid object to convert
     * @return A new CidInfo object with all properties from the Cid object
     */
    public static CidInfo fromCid(Cid cid) {
        if (cid == null) return null;
        
        CidInfo keyInfo = new CidInfo();
        keyInfo.setId(cid.getId());
        keyInfo.setCid(cid.getCid());
        keyInfo.setPubkey(cid.getPubkey());
        keyInfo.setMaster(cid.getMaster());
        keyInfo.setBalance(cid.getBalance());
        keyInfo.setCash(cid.getCash());
        keyInfo.setIncome(cid.getIncome());
        keyInfo.setExpend(cid.getExpend());
        keyInfo.setCd(cid.getCd());
        keyInfo.setCdd(cid.getCdd());
        keyInfo.setReputation(cid.getReputation());
        keyInfo.setHot(cid.getHot());
        keyInfo.setWeight(cid.getWeight());
        keyInfo.setGuide(cid.getGuide());
        keyInfo.setNoticeFee(cid.getNoticeFee());
        keyInfo.setHomepages(cid.getHomepages());
        keyInfo.setBtcAddr(cid.getBtcAddr());
        keyInfo.setEthAddr(cid.getEthAddr());
        keyInfo.setLtcAddr(cid.getLtcAddr());
        keyInfo.setDogeAddr(cid.getDogeAddr());
        keyInfo.setTrxAddr(cid.getTrxAddr());
        keyInfo.setBchAddr(cid.getBchAddr());
        keyInfo.setBirthHeight(cid.getBirthHeight());
        keyInfo.setNameTime(cid.getNameTime());
        keyInfo.setLastHeight(cid.getLastHeight());
        
        return keyInfo;
    }
    
    /**
     * Converts a Cid object to a CidInfo object and sets the prikeyCipher
     * @param cid The Cid object to convert
     * @param prikeyCipher The encrypted private key
     * @return A new CidInfo object with all properties from the Cid object and the provided prikeyCipher
     */
    public static CidInfo fromCid(Cid cid, String prikeyCipher) {
        CidInfo keyInfo = fromCid(cid);
        if (keyInfo != null) {
            keyInfo.setPrikeyCipher(prikeyCipher);
        }
        return keyInfo;
    }
    public String getPrikeyCipher() {
        return prikeyCipher;
    }

    public void setPrikeyCipher(String prikeyCipher) {
        this.prikeyCipher = prikeyCipher;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Boolean getWatchOnly() {
        return watchOnly;
    }

    public void setWatchOnly(Boolean watchOnly) {
        this.watchOnly = watchOnly;
    }

    public byte[] decryptPrikey(byte[] symkey) {
        return Decryptor.decryptPrikey(prikeyCipher,symkey);
    }

    public String encryptPrikey(byte[] symkey) {
        if(prikeyBytes ==null || prikeyBytes.length==0)return null;
        this.prikeyCipher = Encryptor.encryptBySymkeyToJson(prikeyBytes, symkey);
        return this.prikeyCipher;
    }
    /**
     * Gets the isNobody flag
     * @return The isNobody flag value
     */
    public Boolean getIsNobody() {
        return isNobody;
    }

}
