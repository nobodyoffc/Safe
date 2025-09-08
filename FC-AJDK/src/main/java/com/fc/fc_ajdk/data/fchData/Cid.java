package com.fc.fc_ajdk.data.fchData;

import com.fc.fc_ajdk.ui.Shower;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fc.fc_ajdk.data.fcData.FcSubject;
import com.fc.fc_ajdk.core.fch.Weight;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.fc.fc_ajdk.constants.FieldNames.BALANCE;
import static com.fc.fc_ajdk.constants.FieldNames.CASH;
import static com.fc.fc_ajdk.constants.FieldNames.CDD;
import static com.fc.fc_ajdk.constants.FieldNames.CID;
import static com.fc.fc_ajdk.constants.FieldNames.ID;
import static com.fc.fc_ajdk.constants.FieldNames.LAST_HEIGHT;
import static com.fc.fc_ajdk.constants.FieldNames.LAST_TIME;
import static com.fc.fc_ajdk.ui.Shower.DEFAULT_PAGE_SIZE;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Cid extends FcSubject {
    private String prikey;

    protected Long balance;        //value of fch in satoshi
    protected Long cash;        //Count of UTXO
    protected Long income;        //total amount of fch received in satoshi
    protected Long expend;        //total amount of fch pay in satoshi

    protected Long cd;        //CoinDays
    protected Long cdd;        //the total amount of coindays destroyed
    protected Long reputation;
    protected Long hot;
    protected Long weight;

    protected String master;
    protected String guide;    //the address of the address which sent the first fch to this address
    protected String noticeFee;
    protected List<String> homepages;

    protected String btcAddr;    //the btc address
    protected String ethAddr;    //the eth address
    protected String ltcAddr;    //the ltc address
    protected String dogeAddr;    //the doge address
    protected String trxAddr;    //the trx address
    protected String bchAddr;    //the bch address

    protected Long birthHeight;    //the height where this address got its first fch
    protected Long nameTime;
    protected Long lastHeight;     //the height where this address info changed latest. If roll back happened, lastHei point to the lastHeight before fork.


    public static LinkedHashMap<String,Integer> getFieldWidthMap(){
        LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
        map.put(ID,ID_DEFAULT_SHOW_SIZE);
        map.put(CID,ID_DEFAULT_SHOW_SIZE);
        map.put(CASH,AMOUNT_DEFAULT_SHOW_SIZE);
        map.put(BALANCE,AMOUNT_DEFAULT_SHOW_SIZE);
        map.put(CDD,CD_DEFAULT_SHOW_SIZE);
        map.put(LAST_HEIGHT,TIME_DEFAULT_SHOW_SIZE);
        return map;
    }
    public static List<String> getTimestampFieldList(){
        return new ArrayList<>();
    }

    public static List<String> getSatoshiFieldList(){
        return List.of(BALANCE);
    }
    public static Map<String, String> getHeightToTimeFieldMap() {
        Map<String, String> map = new HashMap<>();
        map.put(LAST_HEIGHT,LAST_TIME);
        return map;
    }

    public static Map<String, String> getShowFieldNameAsMap() {
        return new HashMap<>();
    }

    public static LinkedHashMap<String, Object> getInputFieldDefaultValueMap() {
        return new LinkedHashMap<>();
    }


    public static List<Cid> showCidList(String title, List<Cid> list, Integer maxFieldWidth, boolean choose, BufferedReader br) {
        return Shower.showOrChooseListInPages("FID Info", list, DEFAULT_PAGE_SIZE, null, choose, Cid.class, br);
    }
        public void reCalcWeight() {
        if(reputation==null)reputation=0L;
        if(cdd == null)cdd =0L;
        if(cd == null)cd = 0L;
        this.weight = Weight.calcWeight(this.cd, this.cdd, this.reputation);
    }

    public static LinkedHashMap<String, Map<String, String>> getFieldNameMap() {
        LinkedHashMap<String, Map<String, String>> fieldMap = new LinkedHashMap<>();

        // Add Cid fields (lines 32-58)
        addFieldToMap(fieldMap, "prikey", "Private Key", "私钥");
        addFieldToMap(fieldMap, "balance", "Balance", "余额");
        addFieldToMap(fieldMap, "cash", "Cash", "钞票");
        addFieldToMap(fieldMap, "income", "Income", "收入");
        addFieldToMap(fieldMap, "expend", "Expend", "支出");
        addFieldToMap(fieldMap, "cd", "CoinDays", "币天");
        addFieldToMap(fieldMap, "cdd", "CoinDays Destroyed", "币天销毁");
        addFieldToMap(fieldMap, "reputation", "Reputation", "声誉");
        addFieldToMap(fieldMap, "hot", "Hot", "热度");
        addFieldToMap(fieldMap, "weight", "Weight", "权重");
        addFieldToMap(fieldMap, "master", "Master", "主控");
        addFieldToMap(fieldMap, "guide", "Guide", "引导");
        addFieldToMap(fieldMap, "noticeFee", "Notice Fee", "通知费");
        addFieldToMap(fieldMap, "homepages", "Homepages", "主页");
        addFieldToMap(fieldMap, "btcAddr", "BTC Address", "BTC地址");
        addFieldToMap(fieldMap, "ethAddr", "ETH Address", "ETH地址");
        addFieldToMap(fieldMap, "ltcAddr", "LTC Address", "LTC地址");
        addFieldToMap(fieldMap, "dogeAddr", "DOGE Address", "DOGE地址");
        addFieldToMap(fieldMap, "trxAddr", "TRX Address", "TRX地址");
        addFieldToMap(fieldMap, "bchAddr", "BCH Address", "BCH地址");
        addFieldToMap(fieldMap, "birthHeight", "Birth Height", "创建高度");
        addFieldToMap(fieldMap, "nameTime", "Name Time", "命名时间");
        addFieldToMap(fieldMap, "lastHeight", "Last Height", "最后高度");
        addFieldToMap(fieldMap, "multisign", "Multisign", "多重签名");

        return fieldMap;
    }

    public Long getWeight() {
        return weight;
    }

    public void setWeight(Long weight) {
        this.weight = weight;
    }

    public String getPrikey() {
        return prikey;
    }

    public void setPrikey(String prikey) {
        this.prikey = prikey;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public Long getCash() {
        return cash;
    }

    public void setCash(Long cash) {
        this.cash = cash;
    }

    public Long getIncome() {
        return income;
    }

    public void setIncome(Long income) {
        this.income = income;
    }

    public Long getExpend() {
        return expend;
    }

    public void setExpend(Long expend) {
        this.expend = expend;
    }

    public Long getCd() {
        return cd;
    }

    public void setCd(Long cd) {
        this.cd = cd;
    }

    public Long getCdd() {
        return cdd;
    }

    public void setCdd(Long cdd) {
        this.cdd = cdd;
    }

    public Long getReputation() {
        return reputation;
    }

    public void setReputation(Long reputation) {
        this.reputation = reputation;
    }

    public Long getHot() {
        return hot;
    }

    public void setHot(Long hot) {
        this.hot = hot;
    }

    public String getMaster() {
        return master;
    }

    public void setMaster(String master) {
        this.master = master;
    }

    public String getGuide() {
        return guide;
    }

    public void setGuide(String guide) {
        this.guide = guide;
    }

    public String getNoticeFee() {
        return noticeFee;
    }

    public void setNoticeFee(String noticeFee) {
        this.noticeFee = noticeFee;
    }

    public List<String> getHomepages() {
        return homepages;
    }

    public void setHomepages(List<String> homepages) {
        this.homepages = homepages;
    }

    public String getBtcAddr() {
        return btcAddr;
    }

    public void setBtcAddr(String btcAddr) {
        this.btcAddr = btcAddr;
    }

    public String getEthAddr() {
        return ethAddr;
    }

    public void setEthAddr(String ethAddr) {
        this.ethAddr = ethAddr;
    }

    public String getLtcAddr() {
        return ltcAddr;
    }

    public void setLtcAddr(String ltcAddr) {
        this.ltcAddr = ltcAddr;
    }

    public String getDogeAddr() {
        return dogeAddr;
    }

    public void setDogeAddr(String dogeAddr) {
        this.dogeAddr = dogeAddr;
    }

    public String getTrxAddr() {
        return trxAddr;
    }

    public void setTrxAddr(String trxAddr) {
        this.trxAddr = trxAddr;
    }

    public Long getBirthHeight() {
        return birthHeight;
    }

    public void setBirthHeight(Long birthHeight) {
        this.birthHeight = birthHeight;
    }

    public Long getNameTime() {
        return nameTime;
    }

    public void setNameTime(Long nameTime) {
        this.nameTime = nameTime;
    }

    public Long getLastHeight() {
        return lastHeight;
    }

    public void setLastHeight(Long lastHeight) {
        this.lastHeight = lastHeight;
    }

    public String getBchAddr() {
        return bchAddr;
    }

    public void setBchAddr(String bchAddr) {
        this.bchAddr = bchAddr;
    }
}