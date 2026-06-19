package com.fc.fc_ajdk.data.feipData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.utils.StringUtils;

import java.util.Map;

import static com.fc.fc_ajdk.constants.Strings.ACTIVE;
import static com.fc.fc_ajdk.constants.Strings.CLOSED;
import static com.fc.fc_ajdk.constants.Values.DESC;
import static com.fc.fc_ajdk.constants.FieldNames.*;

public class Service extends FcObject {
//sid
	protected String stdName;
	protected String[] localNames;
	protected String desc;
	protected String[] types;
	protected String ver;
	protected String[] urls;
	protected String[] waiters;
	protected String[] protocols;
	protected String[] codes;
	protected String[] services;
	private Object params;
	protected String owner;
	
	protected Long birthTime;
	protected Long birthHeight;
	protected String lastTxId;
	protected Long lastTime;
	protected Long lastHeight;
	protected Long tCdd;
	protected Float tRate;
	protected Boolean active;
	protected Boolean closed;
	protected String closeStatement;


	public String toJson() {
		Gson gson = new Gson();
		return gson.toJson(this);
	}
	public String toNiceJson() {
		Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
		return gson.toJson(this);
	}


    public String[] getServices() {
		return services;
	}

	public void setServices(String[] services) {
		this.services = services;
	}

	public String getStdName() {
		return stdName;
	}
	public void setStdName(String stdName) {
		this.stdName = stdName;
	}
	public String[] getLocalNames() {
		return localNames;
	}
	public void setLocalNames(String[] localNames) {
		this.localNames = localNames;
	}
	public String getDesc() {
		return desc;
	}
	public void setDesc(String desc) {
		this.desc = desc;
	}
	public String[] getTypes() {
		return types;
	}
	public void setTypes(String[] types) {
		this.types = types;
	}
	public String[] getUrls() {
		return urls;
	}
	public void setUrls(String[] urls) {
		this.urls = urls;
	}
	public String[] getWaiters() {
		return waiters;
	}
	public void setWaiters(String[] waiters) {
		this.waiters = waiters;
	}
	public String getOwner() {
		return owner;
	}
	public void setOwner(String signer) {
		this.owner = signer;
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
	public String getLastTxId() {
		return lastTxId;
	}
	public void setLastTxId(String lastTxId) {
		this.lastTxId = lastTxId;
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
	public Long gettCdd() {
		return tCdd;
	}
	public void settCdd(Long tCdd) {
		this.tCdd = tCdd;
	}
	public Float gettRate() {
		return tRate;
	}
	public void settRate(Float tRate) {
		this.tRate = tRate;
	}
	public Boolean isActive() {
		return active;
	}
	public void setActive(Boolean active) {
		this.active = active;
	}
	public String[] getProtocols() {
		return protocols;
	}
	public void setProtocols(String[] protocols) {
		this.protocols = protocols;
	}
	public Object getParams() {
		return params;
	}
	public void setParams(Object params) {
		this.params = params;
	}
	public String[] getCodes() {
		return codes;
	}
	public void setCodes(String[] codes) {
		this.codes = codes;
	}
	public Boolean isClosed() {
		return closed;
	}
	public void setClosed(Boolean closed) {
		this.closed = closed;
	}
	public String getCloseStatement() {
		return closeStatement;
	}
	public void setCloseStatement(String closeStatement) {
		this.closeStatement = closeStatement;
	}

	public String getVer() {
		return ver;
	}

	public void setVer(String ver) {
		this.ver = ver;
	}

	public Boolean getActive() {
		return active;
	}

	public Boolean getClosed() {
		return closed;
	}

    public enum ServiceType {
        NASA_RPC,
        APIP,
        FEIP,
        DISK,
        OTHER,
        TALK,
        MAP,
        SWAP_HALL;

        @Override
        public String toString() {
            return this.name();
        }

        // New method to check if a string matches a ServiceType
        public static ServiceType fromString(String input) {
            if (input == null) {
                return null;
            }
            for (ServiceType type : ServiceType.values()) {
                if (type.name().equalsIgnoreCase(input)) {
                    return type;
                }
            }
            return null;
        }
    }
}
