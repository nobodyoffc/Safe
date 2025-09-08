package com.fc.fc_ajdk.data.fchData;

import java.io.BufferedReader;
import java.util.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;

import com.fc.fc_ajdk.ui.Shower;
import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.data.nasa.UTXO;
import com.fc.fc_ajdk.utils.FchUtils;

import static com.fc.fc_ajdk.constants.Constants.COINBASE;
import static com.fc.fc_ajdk.constants.Constants.OneDayInterval;
import static com.fc.fc_ajdk.constants.FieldNames.BIRTH_TIME;
import static com.fc.fc_ajdk.constants.FieldNames.CASH_ID;
import static com.fc.fc_ajdk.constants.FieldNames.CD;
import static com.fc.fc_ajdk.constants.FieldNames.CDD;
import static com.fc.fc_ajdk.constants.FieldNames.ID;
import static com.fc.fc_ajdk.constants.FieldNames.ISSUER;
import static com.fc.fc_ajdk.constants.FieldNames.OWNER;
import static com.fc.fc_ajdk.constants.FieldNames.VALID;
import static com.fc.fc_ajdk.constants.FieldNames.VALUE;

public class Cash extends FcObject {

	//calculated
	private String owner; 	//address
	private Long value;		//in satoshi
	private String issuer; //first input fid when this cash was born.

	//from utxo
	private Integer birthIndex;		//index of cash. Order in cashs of the tx when created.
	private String type;	//type of the script. P2PKH,Multisign,OP_RETURN,Unknown,MultiSig
	private Long cd;		//CoinDays

	private String lockScript;	//LockScript
	private String birthTxId;		//txid, hash in which this cash was created.
	private Integer birthTxIndex;		//Order in the block of the tx in which this cash was created.
	private String birthBlockId;		//block ID, hash of block head
	private Long birthTime;		//Block time when this cash is created.
	private Long birthHeight;		//Block height.

	//from input
	private Long spendTime;	//Block time when spent.
	private String spendTxId;	//Tx hash when spent.
	private Long spendHeight; 	//Block height when spent.
	private Integer spendTxIndex;		//Order in the block of the tx in which this cash was spent.
	private String spendBlockId;		//block ID, hash of block head
	private Integer spendIndex;		//Order in inputs of the tx when spent.
	private String unlockScript;	//unlock script.
	private String sigHash;	//sigHash.
	private String sequence;	//nSequence
	private Long cdd;		//CoinDays Destroyed
	private Boolean valid;	//Is this cash valid (utxo), or spent (stxo);
	private Long lastTime;
	private Long lastHeight;

	public Cash() {
		// default constructor
	}

	public Cash(String txId, int index, double amount) {
		super();
		this.birthTxId = txId;
		this.birthIndex = index;
		this.value = FchUtils.coinToSatoshi(amount);
	}


	public static LinkedHashMap<String,Integer>getFieldWidthMap(){
		LinkedHashMap<String,Integer> map = new LinkedHashMap<>();
		map.put(OWNER,ID_DEFAULT_SHOW_SIZE);
		map.put(VALUE,AMOUNT_DEFAULT_SHOW_SIZE);
		map.put(CD,CD_DEFAULT_SHOW_SIZE);
		map.put(ID,ID_DEFAULT_SHOW_SIZE);
		map.put(BIRTH_TIME,TIME_DEFAULT_SHOW_SIZE);
		map.put(VALID,BOOLEAN_DEFAULT_SHOW_SIZE);
		map.put(ISSUER,ID_DEFAULT_SHOW_SIZE);
		map.put(CDD,CD_DEFAULT_SHOW_SIZE);

		return map;
	}
	public static List<String> getTimestampFieldList(){
		return List.of(BIRTH_TIME);
	}

	public static List<String> getSatoshiFieldList(){
		return List.of(VALUE);
	}
	public static Map<String, String> getHeightToTimeFieldMap() {
		return new HashMap<>();
	}

	public static Map<String, String> getShowFieldNameAsMap() {
		Map<String,String> map = new HashMap<>();
		map.put(ID,CASH_ID);
		return map;
	}
	public static List<String> getReplaceWithMeFieldList() {
		return List.of(OWNER,ISSUER);
	}

	//For create with user input
	public static Map<String, Object> getInputFieldDefaultValueMap() {
		return new HashMap<>();
	}

	public static String makeCashId(byte[] b36PreTxIdAndIndex) {
		return BytesUtils.bytesToHexStringLE(Hash.sha256x2(b36PreTxIdAndIndex));
	}

	public static String makeCashId(String txId, Integer j) {
		if(txId==null || j ==null)return null;

		byte[] txIdBytes = BytesUtils.invertArray(BytesUtils.hexToByteArray(txId));
		byte[] b4OutIndex = new byte[4];
		b4OutIndex = BytesUtils.invertArray(BytesUtils.intToByteArray(j));

		return BytesUtils.bytesToHexStringLE(
				Hash.sha256x2(
						BytesUtils.bytesMerger(txIdBytes, b4OutIndex)
				));
	}

	public static List<Cash> makeCashListForPay(List<Cash> cashList) {
		List<Cash> resultCashList = new ArrayList<>();
		if(cashList==null || cashList.isEmpty())return resultCashList;

		for(Cash cash:cashList){
			Cash newCash = new Cash();
			newCash.setBirthTxId(cash.getBirthTxId());
			newCash.setBirthIndex(cash.getBirthIndex());
			newCash.setValue(cash.getValue());
//			newCash.setOwner(cash.getOwner());
			resultCashList.add(newCash);
		}
		return resultCashList;
	}

	public String makeId(){
		if(this.birthTxId==null || this.birthIndex==null)return null;
		this.id = makeCashId(this.getBirthTxId(),this.getBirthIndex());
		return this.id;
	}
	public String makeId(String txId, Integer index){
		this.id = makeCashId(txId,index);
		return this.id;
	}


	public Cash(int outIndex, String type, String addr, long value, String lockScript, String txId, int txIndex,
				String blockId, long birthTime, long birthHeight) {
		this.birthIndex = outIndex;
		this.type = type;
		this.owner = addr;
		this.value = value;
		this.lockScript = lockScript;
		this.birthTxId = txId;
		this.birthTxIndex = txIndex;
		this.birthBlockId = blockId;
		this.birthTime = birthTime;
		this.birthHeight = birthHeight;
	}

	public String toJson() {
		return new Gson().toJson(this);
	}

	public static Cash fromJson(String json) {
		return new Gson().fromJson(json, Cash.class);
	}

	public byte[] toBytes() {
		return toJson().getBytes();
	}

	public static Cash fromBytes(byte[] bytes) {
		return fromJson(new String(bytes));
	}

	public static Cash fromUtxo(UTXO utxo) {
		Cash cash = new Cash();
		cash.setBirthTxId(utxo.getTxid());
		cash.setBirthIndex(utxo.getVout());
		cash.setOwner(utxo.getAddress());
		cash.setLockScript(utxo.getScriptPubKey());
		cash.setValue(FchUtils.coinToSatoshi(utxo.getAmount()));
		cash.setValid(true);
		return cash;
	}

	public static List<Cash> fromUtxoList(List<UTXO> utxoList) {
		if (utxoList == null || utxoList.isEmpty()) {
			return new ArrayList<>();
		}

		return utxoList.stream()
			.map(Cash::fromUtxo)
			.collect(Collectors.toList());
	}

	public static long sumCashValue(List<Cash> cashList) {
		if(cashList==null||cashList.isEmpty())return 0;
		long sum = 0;
		for(Cash cash :cashList){
			sum+=cash.getValue();
		}
		return sum;
	}

	public static double sumCashAmount(List<Cash> cashList) {
		if(cashList==null||cashList.isEmpty())return 0;
		long sum = 0;
		for(Cash cash :cashList){
			sum+=cash.getValue();
		}
		return FchUtils.satoshiToCoin(sum);
	}

	public static long sumCashCd(List<Cash> cashList) {
		if(cashList==null||cashList.isEmpty())return 0;
		long sum = 0;
		for(Cash cash :cashList){
			if(cash.makeCd()==null)continue;
			if(cash.getCd()!=null)sum+=cash.getCd();
		}
		return sum;
	}

	public static void checkImmatureCoinbase(List<Cash> cashList, long bestHeight) {
		cashList.removeIf(cash -> COINBASE.equals(cash.getIssuer()) && bestHeight != 0 && (bestHeight - cash.getBirthHeight()) < OneDayInterval * 10);
	}

    public static List<Cash> showOrChooseCashList(List<Cash> cashList, String title, String myFid, boolean choose, BufferedReader br) {
		return Shower.showOrChooseList(
				title,
				cashList,
				myFid, choose,  // choose
				Cash.class, br
		);
    }


	public static List<Cash> showAndChooseCashListInPages(List<Cash> cashList, String title, String myFid, boolean choose,java.io.BufferedReader br) {
        if(cashList==null || cashList.isEmpty())return null;
		return Shower.showOrChooseListInPages(title,cashList,Shower.DEFAULT_PAGE_SIZE, myFid, choose,Cash.class,br);
    }

    public String getBirthBlockId() {
		return birthBlockId;
	}

	public void setBirthBlockId(String birthBlockId) {
		this.birthBlockId = birthBlockId;
	}

	public String getSpendBlockId() {
		return spendBlockId;
	}

	public void setSpendBlockId(String spendBlockId) {
		this.spendBlockId = spendBlockId;
	}
	public Integer getSpendTxIndex() {
		return spendTxIndex;
	}

	public void setSpendTxIndex(Integer spendTxIndex) {
		this.spendTxIndex = spendTxIndex;
	}

	public Integer getBirthIndex() {
		return birthIndex;
	}
	public void setBirthIndex(Integer birthIndex) {
		this.birthIndex = birthIndex;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getOwner() {
		return owner;
	}
	public void setOwner(String owner) {
		this.owner = owner;
	}
	public Long getValue() {
		return value;
	}
	public void setValue(Long value) {
		this.value = value;
	}
	public String getLockScript() {
		return lockScript;
	}
	public void setLockScript(String lockScript) {
		this.lockScript = lockScript;
	}
	public String getBirthTxId() {
		return birthTxId;
	}
	public void setBirthTxId(String birthTxId) {
		this.birthTxId = birthTxId;
	}
	public Integer getBirthTxIndex() {
		return birthTxIndex;
	}
	public void setBirthTxIndex(Integer birthTxIndex) {
		this.birthTxIndex = birthTxIndex;
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
	public Long getSpendTime() {
		return spendTime;
	}
	public void setSpendTime(Long spendTime) {
		this.spendTime = spendTime;
	}
	public String getSpendTxId() {
		return spendTxId;
	}
	public void setSpendTxId(String spendTxId) {
		this.spendTxId = spendTxId;
	}
	public Long getSpendHeight() {
		return spendHeight;
	}
	public void setSpendHeight(Long spendHeight) {
		this.spendHeight = spendHeight;
	}
	public Integer getSpendIndex() {
		return spendIndex;
	}
	public void setSpendIndex(Integer spendIndex) {
		this.spendIndex = spendIndex;
	}
	public String getUnlockScript() {
		return unlockScript;
	}
	public void setUnlockScript(String unlockScript) {
		this.unlockScript = unlockScript;
	}
	public String getSigHash() {
		return sigHash;
	}
	public void setSigHash(String sigHash) {
		this.sigHash = sigHash;
	}
	public String getSequence() {
		return sequence;
	}
	public void setSequence(String sequence) {
		this.sequence = sequence;
	}
	public Long getCdd() {
		return cdd;
	}
	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}
	public Long getCd() {
		return cd;
	}
	public Long makeCd(){
		if(value==null || birthTime==null)return null;
		this.cd = FchUtils.cdd(getValue(),getBirthTime(),System.currentTimeMillis()/1000);
		return this.cd;
	}
	public void setCd(Long cd) {
		this.cd = cd;
	}
	public Boolean isValid() {
		return valid;
	}
	public void setValid(Boolean valid) {
		this.valid = valid;
	}

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}

	public Long getLastTime() {
		return lastTime;
	}

	public void setLastTime(Long lastTime) {
		this.lastTime = lastTime;
	}

	public Long getLastHeight() {
		return lastHeight;
	}

	public void setLastHeight(Long lastHeight) {
		this.lastHeight = lastHeight;
	}

	public static String getShowFieldName(String fieldName, String language) {
        return switch (language.toLowerCase()) {
            case "zh", "zh-cn" -> getShowFieldNameZh(fieldName);
            default -> getShowFieldNameEn(fieldName);
        };
	}

	private static String getShowFieldNameEn(String fieldName) {
		switch (fieldName) {
			case "id": return "Cash ID";
			case "owner": return "Owner";
			case "value": return "Value";
			case "issuer": return "Issuer";
			case "birthIndex": return "Birth Index";
			case "type": return "Type";
			case "cd": return "Coin Days";
			case "lockScript": return "Lock Script";
			case "birthTxId": return "Birth Transaction";
			case "birthTxIndex": return "Birth TX Index";
			case "birthBlockId": return "Birth Block";
			case "birthTime": return "Birth Time";
			case "birthHeight": return "Birth Height";
			case "spendTime": return "Spend Time";
			case "spendTxId": return "Spend Transaction";
			case "spendHeight": return "Spend Height";
			case "spendTxIndex": return "Spend TX Index";
			case "spendBlockId": return "Spend Block";
			case "spendIndex": return "Spend Index";
			case "unlockScript": return "Unlock Script";
			case "sigHash": return "Signature Hash";
			case "sequence": return "Sequence";
			case "cdd": return "Coin Days Destroyed";
			case "valid": return "Valid";
			case "lastTime": return "Last Time";
			case "lastHeight": return "Last Height";
			default: return fieldName;
		}
	}

	private static String getShowFieldNameZh(String fieldName) {
		LinkedHashMap<String,String> map = new LinkedHashMap<>();

        return switch (fieldName) {
            case "id" -> "ID";
            case "owner" -> "拥有者";
            case "value" -> "金额";
            case "issuer" -> "发行者";
            case "birthIndex" -> "出生索引";
            case "type" -> "类型";
            case "cd" -> "币天";
            case "lockScript" -> "锁定脚本";
            case "birthTxId" -> "出生交易";
            case "birthTxIndex" -> "出生交易索引";
            case "birthBlockId" -> "出生区块";
            case "birthTime" -> "出生时间";
            case "birthHeight" -> "出生高度";
            case "spendTime" -> "花费时间";
            case "spendTxId" -> "花费交易";
            case "spendHeight" -> "花费高度";
            case "spendTxIndex" -> "花费交易索引";
            case "spendBlockId" -> "花费区块";
            case "spendIndex" -> "花费索引";
            case "unlockScript" -> "解锁脚本";
            case "sigHash" -> "签名哈希";
            case "sequence" -> "序列号";
            case "cdd" -> "币天销毁";
            case "valid" -> "有效";
            case "lastTime" -> "最后时间";
            case "lastHeight" -> "最后高度";
            default -> fieldName;
        };
	}


	/**
	 * Returns a LinkedHashMap with field names in their declaration order and their display names in multiple languages
	 * @return LinkedHashMap<fieldName, Map<language, displayName>>
	 */
	public static LinkedHashMap<String, Map<String, String>> getFieldNameMap() {
		LinkedHashMap<String, Map<String, String>> fieldMap = new LinkedHashMap<>();

		// Add fields in their declaration order from source code (lines 31-61)
		addFieldToMap(fieldMap, "id", "Cash ID", "钞票ID");
		addFieldToMap(fieldMap, "owner", "Owner", "拥有者");
		addFieldToMap(fieldMap, "value", "Value", "金额");
		addFieldToMap(fieldMap, "issuer", "Issuer", "发行者");
		addFieldToMap(fieldMap, "birthIndex", "Birth Index", "出生索引");
		addFieldToMap(fieldMap, "type", "Type", "类型");
		addFieldToMap(fieldMap, "cd", "Coin Days", "币天");
		addFieldToMap(fieldMap, "lockScript", "Lock Script", "锁定脚本");
		addFieldToMap(fieldMap, "birthTxId", "Birth Transaction", "出生交易");
		addFieldToMap(fieldMap, "birthTxIndex", "Birth TX Index", "出生交易索引");
		addFieldToMap(fieldMap, "birthBlockId", "Birth Block", "出生区块");
		addFieldToMap(fieldMap, "birthTime", "Birth Time", "出生时间");
		addFieldToMap(fieldMap, "birthHeight", "Birth Height", "出生高度");
		addFieldToMap(fieldMap, "spendTime", "Spend Time", "花费时间");
		addFieldToMap(fieldMap, "spendTxId", "Spend Transaction", "花费交易");
		addFieldToMap(fieldMap, "spendHeight", "Spend Height", "花费高度");
		addFieldToMap(fieldMap, "spendTxIndex", "Spend TX Index", "花费交易索引");
		addFieldToMap(fieldMap, "spendBlockId", "Spend Block", "花费区块");
		addFieldToMap(fieldMap, "spendIndex", "Spend Index", "花费索引");
		addFieldToMap(fieldMap, "unlockScript", "Unlock Script", "解锁脚本");
		addFieldToMap(fieldMap, "sigHash", "Signature Hash", "签名哈希");
		addFieldToMap(fieldMap, "sequence", "Sequence", "序列号");
		addFieldToMap(fieldMap, "cdd", "Coin Days Destroyed", "币天销毁");
		addFieldToMap(fieldMap, "valid", "Valid", "有效");
		addFieldToMap(fieldMap, "lastTime", "Last Time", "最后时间");
		addFieldToMap(fieldMap, "lastHeight", "Last Height", "最后高度");

		return fieldMap;
	}

}
