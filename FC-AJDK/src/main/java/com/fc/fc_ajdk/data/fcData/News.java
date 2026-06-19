package com.fc.fc_ajdk.data.fcData;

public class News extends FcObject{
    private String doer;    //signer
    private String act;     //op
    private String objectType;  //protocol sn of Feip
    private String objectId;    //id
    private String objectName;  //name, stdName or title
    private String objectBrief; //a brief of the content or desc
    private Long height;    //height
    private Long time;      //time


    public String getDoer() {
        return doer;
    }

    public void setDoer(String doer) {
        this.doer = doer;
    }

    public String getAct() {
        return act;
    }

    public void setAct(String act) {
        this.act = act;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getObjectBrief() {
        return objectBrief;
    }

    public void setObjectBrief(String objectBrief) {
        this.objectBrief = objectBrief;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }

    public Long getTime() {
        return time;
    }

    public void setTime(Long time) {
        this.time = time;
    }
}
