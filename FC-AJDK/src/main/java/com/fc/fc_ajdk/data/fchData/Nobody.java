package com.fc.fc_ajdk.data.fchData;

import com.fc.fc_ajdk.data.fcData.FcObject;

public class Nobody extends FcObject {
    private String priKey;
    private Long deathTime;
    private Long deathHeight;
    private String deathTxId;
    private Integer deathTxIndex;

    public String getPriKey() {
        return priKey;
    }

    public void setPriKey(String priKey) {
        this.priKey = priKey;
    }

    public Long getDeathTime() {
        return deathTime;
    }

    public void setDeathTime(Long deathTime) {
        this.deathTime = deathTime;
    }

    public Long getDeathHeight() {
        return deathHeight;
    }

    public void setDeathHeight(Long deathHeight) {
        this.deathHeight = deathHeight;
    }

    public String getDeathTxId() {
        return deathTxId;
    }

    public void setDeathTxId(String deathTxId) {
        this.deathTxId = deathTxId;
    }

    public Integer getDeathTxIndex() {
        return deathTxIndex;
    }

    public void setDeathTxIndex(Integer deathTxIndex) {
        this.deathTxIndex = deathTxIndex;
    }
}
