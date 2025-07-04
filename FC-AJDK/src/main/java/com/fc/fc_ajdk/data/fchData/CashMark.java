package com.fc.fc_ajdk.data.fchData;

import com.fc.fc_ajdk.data.fcData.FcObject;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;

public class CashMark extends FcObject {
	private String owner;
	private Long value;
	private Long cdd;

	public CashMark(String fid, Long value,String id) {
		super();
		owner = fid;
		this.id = id;
		this.value = value;
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
	public Long getCdd() {
		return cdd;
	}
	public void setCdd(Long cdd) {
		this.cdd = cdd;
	}
}
