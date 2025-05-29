package com.fc.fc_ajdk.data.apipData;


import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.data.fcData.FcEntity;

public class WebhookRequestBody extends FcEntity {
    private String hookUserId;
    private String userId;
    private String method;
    private String endpoint;
    private Object data;
    private String op;

    public static String makeHookUserId(String sid, String newCashByFidsAPI, String userId) {
        return Hash.sha256x2(sid+newCashByFidsAPI+userId);
    }

    public String makeHookUserId(String sid) {
        this.hookUserId = Hash.sha256x2(sid+method+userId);
        return this.hookUserId;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public String getHookUserId() {
        return hookUserId;
    }

    public void setHookUserId(String hookUserId) {
        this.hookUserId = hookUserId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }
}