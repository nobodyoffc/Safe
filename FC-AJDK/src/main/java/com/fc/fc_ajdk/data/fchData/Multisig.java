package com.fc.fc_ajdk.data.fchData;

import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;

import org.bitcoinj.script.Script;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Multisig extends FcObject {
	private String redeemScript;
	private Integer m;
	private Integer n;
	private List<String> pubKeys;
	private List<String> fids;

	private Long birthHeight;
	private Long birthTime;
	private String birthTxId;
	private String saveTime;
	private String label;

	public Multisig() {}

	/**
	 * Create Multisig from P2SH object.
	 * Only works for P2SH objects with type MULTISIG or MULTISIG_CLTV.
	 */
	public Multisig(P2SH p2sh) {
		if (p2sh == null) {
			throw new IllegalArgumentException("P2SH object cannot be null");
		}
		if (p2sh.getType() != P2SH.P2shType.MULTISIG && p2sh.getType() != P2SH.P2shType.MULTISIG_CLTV) {
			throw new IllegalArgumentException("P2SH type must be MULTISIG or MULTISIG_CLTV, got: " + p2sh.getType());
		}
		if (p2sh.getM() == null || p2sh.getN() == null || p2sh.getPubkeys() == null || p2sh.getPubkeys().isEmpty()) {
			throw new IllegalArgumentException("P2SH object lacks required multisig data (m, n, or pubkeys)");
		}

		this.m = p2sh.getM();
		this.n = p2sh.getN();
		this.pubKeys = new ArrayList<>(p2sh.getPubkeys());

		if (p2sh.getType() == P2SH.P2shType.MULTISIG) {
			this.redeemScript = p2sh.getRedeemScript();
		} else {
			Script multisigScript = P2SH.makeMultisigRedeemScript(this.pubKeys, this.m, this.n);
			this.redeemScript = Hex.toHex(multisigScript.getProgram());
		}

		this.fids = new ArrayList<>();
		for (String pubkey : this.pubKeys) {
			this.fids.add(KeyTools.pubkeyToFchAddr(pubkey));
		}

		this.setId(p2sh.getFid());
		this.birthHeight = p2sh.getBirthHeight();
		this.birthTime = p2sh.getBirthTime();
		this.birthTxId = p2sh.getBirthTxId();
	}

	public void parseMultisign(Cash input) throws IOException {
        /* Example of multiSig input unlocking script:
				"00" +
				"41" +
				"8ec1f75f4368e650f6cf0c8a80c009094748845c9d354f593359bd971370d94b" +
				"a48db3b925dd0869d1610b1c0a4d27f7ac25f35d46b034dbcbd30a6e78110764" +
				"41" +
				"41" +
				"e226dbc949b2f2bfb2fd8cfb7a4851c43700e9febf16873b679e412714ad3235" +
				"b528053dc46990e3bdcc40656764694f403fbce5f59a4e5424140e09e09e87b4" +
				"41" +
				"4c" +
				"69" +
				"52" +
				"21" +
				"030be1d7e633feb2338a74a860e76d893bac525f35a5813cb7b21e27ba1bc8312a" +
				"21" +
				"02536e4f3a6871831fa91089a5d5a950b96a31c861956f01459c0cd4f4374b2f67" +
				"21" +
				"03f0145ddf5debc7169952b17b5c6a8a566b38742b6aa7b33b667c0a7fa73762e2" +
				"53" +
				"ae";
		*/
		String script = input.getUnlockScript();

		if(! script.substring(script.length()-2).equals("ae"))return;

		try {
			TimeUnit.SECONDS.sleep(2);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}


		InputStream scriptIs = new ByteArrayInputStream(BytesUtils.hexToByteArray(script));

		byte[] b = new byte[1];
		scriptIs.read(b);

		if(b[0]!=0x00) return;
		scriptIs.read(b);

		while(b[0]==65) {
			for (int i = 0; i < 65; i++) {
				scriptIs.read();
			}
			scriptIs.read(b);
		}

		if(b[0]>75)scriptIs.read();

		ArrayList<byte[]> redeemScriptBytesList = new ArrayList<byte[]>();
		scriptIs.read(b);
		redeemScriptBytesList.add(b.clone());
		int m = b[0]-80;

		if(m>16 || m<0) return;

		ArrayList<String> pukList = new ArrayList<String>();
		ArrayList<String> addrList = new ArrayList<String>();

		while(true) {
			scriptIs.read(b);
			redeemScriptBytesList.add(b.clone());
			int pkLen = b[0];
			if(pkLen!=33 && pkLen!=65)break;

			byte[] pkBytes = new byte[pkLen];
			scriptIs.read(pkBytes);
			redeemScriptBytesList.add(pkBytes.clone());
			String pubKey = BytesUtils.bytesToHexStringBE(pkBytes);
			String addr = KeyTools.pubkeyToFchAddr(pubKey);
			pukList.add(pubKey);
			addrList.add(addr);
		}

		if(pukList.size()==0) return;

		int n = b[0]-80;
		scriptIs.read(b);
		redeemScriptBytesList.add(b.clone());


		this.setRedeemScript(BytesUtils.bytesToHexStringBE(BytesUtils.bytesMerger(redeemScriptBytesList)));
		this.setM(m);
		this.setN(n);

		this.setPubKeys(pukList);
		this.setFids(addrList);

		this.setId(input.getOwner());
		this.setBirthHeight(input.getSpendHeight());
		this.setBirthTime(input.getSpendTime());
		this.setBirthTxId(input.getBirthTxId());
	}

	public static Multisig parseMultisignRedeemScript(String script)  {
		Multisig multisig = new Multisig();

		multisig.setId(KeyTools.scriptToMultiAddr(script));
		InputStream scriptIs = new ByteArrayInputStream(BytesUtils.hexToByteArray(script));

		byte[] b = new byte[1];
		try{
			ArrayList<byte[]> redeemScriptBytesList = new ArrayList<>();
			scriptIs.read(b);
			redeemScriptBytesList.add(b.clone());
			int m = b[0]-80;

			if(m>16 || m<0) return null;

			ArrayList<String> pukList = new ArrayList<>();
			ArrayList<String> addrList = new ArrayList<>();

			while(true) {
				scriptIs.read(b);
				redeemScriptBytesList.add(b.clone());
				int pkLen = b[0];
				if(pkLen!=33 && pkLen!=65)break;

				byte[] pkBytes = new byte[pkLen];
				scriptIs.read(pkBytes);
				redeemScriptBytesList.add(pkBytes.clone());
				String pubKey = BytesUtils.bytesToHexStringBE(pkBytes);
				String addr = KeyTools.pubkeyToFchAddr(pubKey);
				pukList.add(pubKey);
				addrList.add(addr);
				if(scriptIs.available()==0)break;
			}

			if(pukList.size()==0) return null;

			int n = b[0]-80;

			scriptIs.read(b);

			redeemScriptBytesList.add(b.clone());
			multisig.setRedeemScript(BytesUtils.bytesToHexStringBE(BytesUtils.bytesMerger(redeemScriptBytesList)));
			multisig.setM(m);
			multisig.setN(n);

			multisig.setPubKeys(pukList);
			multisig.setFids(addrList);
			return multisig;
		} catch (IOException e) {

			return null;
		}
	}

	public static String makeMultisignFid(List<String> pubKeys, int m, int n) {
		Script multisigScript = P2SH.makeMultisigRedeemScript(pubKeys, m, n);
		byte[] multisigHash160 = Hash.sha256hash160(multisigScript.getProgram());
		return KeyTools.hash160ToMultiAddr(multisigHash160);
	}

	public String getRedeemScript() {
		return redeemScript;
	}

	public void setRedeemScript(String redeemScript) {
		this.redeemScript = redeemScript;
	}

	public Integer getM() {
		return m;
	}

	public void setM(Integer m) {
		this.m = m;
	}

	public Integer getN() {
		return n;
	}

	public void setN(Integer n) {
		this.n = n;
	}

	public List<String> getPubKeys() {
		return pubKeys;
	}

	public void setPubKeys(List<String> pubKeys) {
		this.pubKeys = pubKeys;
	}

	public Long getBirthHeight() {
		return birthHeight;
	}

	public void setBirthHeight(Long birthHeight) {
		this.birthHeight = birthHeight;
	}

	public Long getBirthTime() {
		return birthTime;
	}

	public void setBirthTime(Long birthTime) {
		this.birthTime = birthTime;
	}

	public String getBirthTxId() {
		return birthTxId;
	}

	public void setBirthTxId(String birthTxId) {
		this.birthTxId = birthTxId;
	}

	public List<String> getFids() {
		return fids;
	}

	public void setFids(List<String> fids) {
		this.fids = fids;
	}

	public String getSaveTime() {
		return saveTime;
	}

	public void setSaveTime(String saveTime) {
		this.saveTime = saveTime;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}
}
