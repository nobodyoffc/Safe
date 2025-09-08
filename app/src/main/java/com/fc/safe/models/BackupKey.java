package com.fc.safe.models;

import com.fc.fc_ajdk.data.fcData.FcObject;

public class BackupKey extends FcObject {
    private String password;
    private String symkey;
    private String time;
    private String keyName;
    private String hint;
    private String hintZh; // 中文提示
    private String hintLang; // 提示语言代码

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
            backupKey.setHintZh("应用密码无法显示，请妥善保管。");
            backupKey.setHintLang("en"); // 默认英文
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

    public String getHintZh() {
        return hintZh;
    }

    public void setHintZh(String hintZh) {
        this.hintZh = hintZh;
    }

    public String getHintLang() {
        return hintLang;
    }

    public void setHintLang(String hintLang) {
        this.hintLang = hintLang;
    }

    /**
     * 根据当前语言获取提示文本
     * @param isChinese 是否为中文环境
     * @return 对应语言的提示文本
     */
    public String getLocalizedHint(boolean isChinese) {
        if (isChinese && hintZh != null && !hintZh.isEmpty()) {
            return hintZh;
        }
        return hint;
    }
}
