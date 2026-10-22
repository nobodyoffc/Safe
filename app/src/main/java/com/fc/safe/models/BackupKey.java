package com.fc.safe.models;

import android.content.Context;

import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.utils.Base32;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.safe.R;

public class BackupKey extends FcObject {
    private String password;
    private String symkey;
    private String time;
    private String keyName;
    private String hint;

    /** @return a 128-bit random password, 26 Base32 characters. */
    public static String makeRandomPassword() {
        return Base32.toBase32(BytesUtils.getRandomBytes(16));
    }

    public static BackupKey makeBackupKey(BackupHeader backupHeader, String inputSymKeyStr, String randomPassword, Context context) {
        BackupKey backupKey = new BackupKey();
        backupKey.setTime(backupHeader.getTime());
        backupKey.setKeyName(backupHeader.getKeyName());
        if (randomPassword != null) {
            // The random password is handed over apart from the export, never inside it.
            backupKey.setHint(context.getString(R.string.random_password_kept_apart));
        } else if (inputSymKeyStr != null) {
            backupKey.setSymkey(inputSymKeyStr);

        } else {
            backupKey.setHint(context.getString(R.string.app_password_can_not_be_shown_please_keep_it_carefully));
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
    /**
     * 根据当前语言获取提示文本
     * @param isChinese 是否为中文环境
     * @return 对应语言的提示文本
     */
    public String getLocalizedHint(boolean isChinese) {
        if (isChinese && hint != null && !hint.isEmpty()) {
            return hint;
        }
        return hint;
    }
}
