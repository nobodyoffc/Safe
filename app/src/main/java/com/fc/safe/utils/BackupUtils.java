package com.fc.safe.utils;

import static com.fc.fc_ajdk.utils.JsonUtils.readOneJsonFromInputStream;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.fcData.SecretDetail;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.safe.models.BackupHeader;
import com.fc.safe.models.BackupKey;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BackupUtils {

    public static <T> List<Object> readBackup(File file, Class<T> tClass) {
        try(FileInputStream fis = new FileInputStream(file)){
            return readBackup(fis,tClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read backup data from "+file.getAbsolutePath(),e);
        }
    }

    public static <T> List<Object> readBackup(byte[] bytes, Class<T> tClass) {
        if(bytes==null || bytes.length==0)return null;
        try(InputStream is = new ByteArrayInputStream(bytes)){
            return readBackup(is,tClass);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> List<Object> readBackup(InputStream is, Class<T> tClass) throws Exception {
        List<Object> objectList = new ArrayList<>();
        while(true) {
            byte[] jsonBytes = readOneJsonFromInputStream(is);
            if (jsonBytes == null) return objectList;
            String json = new String(jsonBytes, StandardCharsets.UTF_8);

            CryptoDataByte cryptoDataByte = CryptoDataByte.fromJson(json);
            if ( cryptoDataByte.getCipher()!=null && cryptoDataByte.getIv()!=null) {
                objectList.add(cryptoDataByte);
                continue;
            }

            BackupKey backupKey = BackupKey.fromJson (json, BackupKey.class);
            if (backupKey != null &&  (backupKey.getPassword()!=null || backupKey.getSymkey()!=null)){
                objectList.add(backupKey);
                continue;
            }

            BackupHeader backupHeader = JsonUtils.fromJson(json, BackupHeader.class);
            if (backupHeader != null && backupHeader.getItems()!=null && backupHeader.getTime()!=null) {
                objectList.add(backupHeader);
                continue;
            }

            T t = JsonUtils.fromJson(json, tClass);
            if (t != null) {
                if(t instanceof SecretDetail secretDetail){
                    if(secretDetail.getContent()==null && secretDetail.getContentCipher()==null)continue;
                }

                if(t instanceof KeyInfo keyInfo){
                    if(keyInfo.getId()==null)continue;
                }

                objectList.add(t);
            }
        }
    }
}
