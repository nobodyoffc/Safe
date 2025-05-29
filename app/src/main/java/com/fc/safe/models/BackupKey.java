package com.fc.safe.models;

import com.fc.fc_ajdk.data.fcData.FcObject;

public class BackupKey extends FcObject {
    private String password;
    private String symkey;
    private String time;
    private String keyName;
    private String hint;

    public static BackupKey makeBackupKey(BackupHeader backupHeader, String inputSymKeyStr, String randomPassword) {
        BackupKey backupKey = new BackupKey();
        backupKey.setTime(backupHeader.getTime());
        backupKey.setKeyName(backupHeader.getKeyName());
        if (randomPassword != null) {
            backupKey.setPassword(randomPassword);
        } else if (inputSymKeyStr != null) {
            backupKey.setSymkey(inputSymKeyStr);

        } else {
            backupKey.setHint("App password can not be shown. Please keep it carefully.");
        }
        return backupKey;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSymkey() {
        return symkey;
    }

    public void setSymkey(String symkey) {
        this.symkey = symkey;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }
}
