package com.fc.safe.secret;

import static com.fc.safe.utils.BackupUtils.readBackup;

import android.content.Context;
import android.content.Intent;
import android.view.inputmethod.InputMethodManager;
import android.view.View;

import androidx.annotation.Nullable;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.safe.R;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.ui.SingleInputActivity;
import com.fc.safe.models.BackupHeader;
import com.fc.safe.models.BackupKey;
import com.fc.safe.utils.ToastUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FcEntityImporter<T extends FcEntity> {
    private final Context context;
    private final Class<T> typeClass;
    private final OnImportListener<T> listener;
    private CryptoDataByte pendingCryptoDataByte = null;
    private EncryptType pendingEncryptType = null;
    private String type;
    private String password;
    private List<T> finalTList;
    private List<CryptoDataByte> pendingCryptoDataByteList;

    public interface OnImportListener<T extends FcEntity> {
        void onImportSuccess(List<T> result);
        void onImportError(String error);
        void onPasswordRequired(Intent intent);
    }

    public FcEntityImporter(Context context, Class<T> typeClass, OnImportListener<T> listener) {
        this.context = context;
        this.typeClass = typeClass;
        this.listener = listener;
        this.finalTList = new ArrayList<>();
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<T> importEntity(String jsonText) {
        if (jsonText.isEmpty()) {
            listener.onImportError("Please input JSON text");
            return null;
        }
        byte[] jsonBytes = jsonText.getBytes(StandardCharsets.UTF_8);

        if(jsonBytes==null || jsonBytes.length==0)return null;

        try(InputStream is = new ByteArrayInputStream(jsonBytes)){
            return importEntity(is);
        } catch (Exception e) {
            ToastUtils.showError(context, context.getString(R.string.failed_to_parse_json));
            return null;
        }
    }

    @Nullable
    public List<T> importEntity(InputStream is) throws Exception {
        List<Object> objectList= readBackup(is,typeClass);
        if(objectList==null || objectList.isEmpty()){
            return null;
        }
        BackupKey backupKey= null;
        BackupHeader backupHeader= null;
        List<CryptoDataByte> cryptoDataByteList = new ArrayList<>();
        List<T> tList = new ArrayList<>();

        for(Object object : objectList) {
            if(object instanceof BackupKey){
                backupKey = (BackupKey) object;
                if(backupKey.getPassword()!=null)
                    password = backupKey.getPassword();
            }else if(object instanceof BackupHeader){
                backupHeader = (BackupHeader) object;
            }else if(object instanceof CryptoDataByte){
                cryptoDataByteList.add((CryptoDataByte) object);
            }else if(object.getClass().equals(typeClass)){
                tList.add((T)object);
            }
        }

        if(backupKey!=null && backupHeader!=null){
            if(!backupKey.getKeyName().equals(backupHeader.getKeyName())){
                ToastUtils.showError(context, context.getString(R.string.keyname_is_inconsistent));
                return null;
            }
        }

        finalTList.addAll(tList);

        for(CryptoDataByte cryptoDataByte:cryptoDataByteList){
            if(cryptoDataByte.getType().equals(EncryptType.Password)){
                if(password!=null) {
                    Decryptor.decryptByPassword(cryptoDataByte, password.toCharArray());
                    if(cryptoDataByte.getCode()==0) {
                        T t = T.fromJson(new String(cryptoDataByte.getData()), typeClass);
                        finalTList.add(t);
                    }
                }else{
                    Intent intent = new Intent(context, SingleInputActivity.class);
                    intent.putExtra(SingleInputActivity.EXTRA_PROMOTE, context.getString(R.string.input_the_password));
                    intent.putExtra(SingleInputActivity.EXTRA_INPUT_TYPE, "password");
                    pendingEncryptType = EncryptType.Password;
                    pendingCryptoDataByte = cryptoDataByte;
                    pendingCryptoDataByteList = new ArrayList<>(cryptoDataByteList.subList(cryptoDataByteList.indexOf(cryptoDataByte) + 1, cryptoDataByteList.size()));
                    listener.onPasswordRequired(intent);
                    return new ArrayList<>();
                }
            }
        }
        for(T t:finalTList){
            if(t instanceof KeyInfo keyInfo){
                if(keyInfo.getPrikeyCipher()!=null){
                    CryptoDataByte cryptoDataByte = CryptoDataByte.fromBase64(keyInfo.getPrikeyCipher());

                    if (cryptoDataByte.getType().equals(EncryptType.Password) && password==null){
                        Intent intent = new Intent(context, SingleInputActivity.class);
                        intent.putExtra(SingleInputActivity.EXTRA_PROMOTE, getString(R.string.input_the_password));
                        intent.putExtra(SingleInputActivity.EXTRA_INPUT_TYPE, "password");
                        pendingEncryptType = EncryptType.Password;
                        pendingCryptoDataByte = cryptoDataByte;
                        listener.onPasswordRequired(intent);
                        return new ArrayList<>();
                    }

                    if(password!=null){
                        Decryptor.decryptByPassword(cryptoDataByte,password.toCharArray());
                        if(cryptoDataByte.getCode()==0) {
                            keyInfo.setPrikey(Hex.toHex(cryptoDataByte.getData()));
                        }
                    }
                }
            }
        }

        listener.onImportSuccess(finalTList);
        return finalTList;
    }

    public void handleInputResult(Intent data) {
        if (data == null) {
            listener.onImportError("Input cancelled");
            return;
        }

        String input = data.getStringExtra(SingleInputActivity.EXTRA_RESULT);
        if (pendingEncryptType != null && input != null && pendingCryptoDataByte != null) {
            try {
                if (pendingEncryptType.equals(EncryptType.Password)) {
                    password = input;
                    Decryptor.decryptByPassword(pendingCryptoDataByte, input.toCharArray());
                }

                if (pendingCryptoDataByte.getCode() == 0) {
                    try {
                        String json = new String(pendingCryptoDataByte.getData());
                        T t = T.fromJson(json, typeClass);
                        finalTList.add(t);
                    }catch (Exception ignore){}
                }

                if (pendingCryptoDataByteList != null) {
                    for (CryptoDataByte remainingCryptoDataByte : pendingCryptoDataByteList) {
                        if (remainingCryptoDataByte.getType().equals(EncryptType.Password)) {
                            Decryptor.decryptByPassword(remainingCryptoDataByte, password.toCharArray());
                            if (remainingCryptoDataByte.getCode() == 0) {
                                T t = T.fromJson(new String(remainingCryptoDataByte.getData()), typeClass);
                                finalTList.add(t);
                            }
                        }
                    }
                }

                // Continue processing KeyInfo items after getting password
                for(T t:finalTList){
                    if(t instanceof KeyInfo keyInfo){
                        if(keyInfo.getPrikeyCipher()!=null){
                            CryptoDataByte cryptoDataByte = CryptoDataByte.fromBase64(keyInfo.getPrikeyCipher());

                            if(password!=null){
                                Decryptor.decryptByPassword(cryptoDataByte,password.toCharArray());
                                if(cryptoDataByte.getCode()==0) {
                                    keyInfo.setPrikey(Hex.toHex(cryptoDataByte.getData()));
                                }
                            }
                        }
                    }
                }

                listener.onImportSuccess(finalTList);
            } catch (Exception e) {
                listener.onImportError("Invalid password or key");
            } finally {
                pendingCryptoDataByte = null;
                pendingEncryptType = null;
                pendingCryptoDataByteList = null;
            }
        }
    }

    private void getPasswordString(String promote) {
        Intent intent = new Intent(context, SingleInputActivity.class);
        intent.putExtra(SingleInputActivity.EXTRA_PROMOTE, promote);
        intent.putExtra(SingleInputActivity.EXTRA_INPUT_TYPE, "password");
        listener.onPasswordRequired(intent);
    }

    private String getString(int resId) {
        return context.getString(resId);
    }

    public static void hideKeyboard(View view) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public List<T> getFinalTList() {
        return finalTList;
    }

    public void setFinalTList(List<T> finalTList) {
        this.finalTList = finalTList;
    }
}