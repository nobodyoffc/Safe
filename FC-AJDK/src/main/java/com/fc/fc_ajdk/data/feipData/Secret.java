package com.fc.fc_ajdk.data.feipData;

import com.fc.fc_ajdk.data.fcData.FcObject;


public class Secret extends FcObject {
    private String alg;
	private String cipher;
	
	private String owner;
	private Long birthTime;
	private Long birthHeight;
	private Long lastHeight;
	private Boolean active;

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
	public Long getLastHeight() {
		return lastHeight;
	}
	public void setLastHeight(Long lastHeight) {
		this.lastHeight = lastHeight;
	}
	public Boolean isActive() {
		return active;
	}
	public void setActive(Boolean active) {
		this.active = active;
	}
	public Boolean getActive() {
		return active;
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
			case "id": return "ID";
			case "alg": return "Algorithm";
			case "cipher": return "Cipher";
			case "owner": return "Owner";
			case "birthTime": return "Birth Time";
			case "birthHeight": return "Birth Height";
			case "lastHeight": return "Last Height";
			case "active": return "Active";
			default: return fieldName;
		}
	}

	private static String getShowFieldNameZh(String fieldName) {
        return switch (fieldName) {
            case "id" -> "ID";
            case "alg" -> "算法";
            case "cipher" -> "密文";
            case "owner" -> "拥有者";
            case "birthTime" -> "创建时间";
            case "birthHeight" -> "创建高度";
            case "lastHeight" -> "最后高度";
            case "active" -> "活跃中";
            default -> fieldName;
        };
	}
	
	
}
