package com.fc.safe.models;

import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.utils.DateUtils;

public class BackupHeader extends FcObject {
    private String time;
    private Integer items;
    private Integer qrCodes;
    private String keyName;
    private String alg;
    private String tClass;

    public String getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = DateUtils.longToTime(time, "yyyy-MM-dd HH:mm:ss");
    }

    public void setDateTime(String dateTime) {
        this.time = dateTime;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public Integer getItems() {
        return items;
    }

    public void setItems(Integer items) {
        this.items = items;
    }

    public void setQrCodes(Integer qrCodes) {
        this.qrCodes = qrCodes;
    }

    public int getQrCodes() {
        return qrCodes;
    }

    public void setQrCodes(int qrCodes) {
        this.qrCodes = qrCodes;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getAlg() {
        return alg;
    }

    public void setAlg(String alg) {
        this.alg = alg;
    }

    public String gettClass() {
        return tClass;
    }

    public void settClass(String tClass) {
        this.tClass = tClass;
    }
}
