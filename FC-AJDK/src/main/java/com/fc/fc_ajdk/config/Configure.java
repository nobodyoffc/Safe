package com.fc.fc_ajdk.config;

import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.feipData.ServiceMask;
import com.fc.fc_ajdk.data.feipData.Service;

import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.google.gson.Gson;

import com.fc.fc_ajdk.constants.FreeApi;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import android.content.Context;
import android.content.SharedPreferences;

public class Configure extends FcObject {
    public static final String TAG = "Configure";
    protected String nonce;
    protected String passwordName;
    protected String passwordHash;
    protected List<String> ownerList;  //Owners for servers.
    protected Map<String, KeyInfo> mainCidInfoMap; //Users for clients or accounts for servers.
    private String esAccountId;

    private Map<String, ServiceMask> myServiceMaskMap;

    private transient byte[] symkey;

    public static String CONFIG_DOT_JSON = "config.json";

    private static BufferedReader br;
//    private List<FreeApi> freeApipUrlList;
    private Map<Service.ServiceType,List<FreeApi>> freeApiListMap;
    public static Map<String,Configure> configureMap;
//    public static List<String> freeApipUrls;

    private static Context context;
    private static final String SHARED_PREFS_NAME = "fc_ajdk_prefs";
    private static final String CONFIG_KEY = "config_json";

    public Configure(BufferedReader br) {
        Configure.br =br;
    }

    public Configure() {
    }


    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }


    public static void saveConfig() {
        if (context != null) {
            // Android environment - use SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            String configJson = JsonUtils.toJson(configureMap);
            editor.putString(CONFIG_KEY, configJson);
            editor.apply();
        } else {
            // Command line environment - use file system
            JsonUtils.writeObjectToJsonFile(configureMap, Configure.getConfDir()+ Configure.CONFIG_DOT_JSON, false);
        }
    }

    public List<String> getOwnerList() {
        return ownerList;
    }

    public void setOwnerList(List<String> ownerList) {
        this.ownerList = ownerList;
    }

    public static byte[] getSymkeyFromPasswordAndNonce(byte[] passwordBytes, byte[] nonce) {
        return Hash.sha256(BytesUtils.bytesMerger(passwordBytes, nonce));
    }

    public static byte[] getSymkeyFromPassword(byte[] passwordBytes) {
        return Hash.sha256(passwordBytes);
    }

    public byte[] makeSymkeyFromPassword(byte[] passwordBytes) {
        this.symkey = Hash.sha256(passwordBytes);
        return this.symkey;
    }

    public byte[] getSymkeyFromPasswordAndNonce(byte[] passwordBytes) {
        setSymkey(getSymkeyFromPasswordAndNonce(passwordBytes,Hex.fromHex(nonce)));
        return this.symkey;
    }

    public static  <T> T parseMyServiceParams(Service myService, Class<T> tClass){
        Gson gson = new Gson();
        T params = gson.fromJson(gson.toJson(myService.getParams()), tClass);
        myService.setParams(params);
        return params;
    }

    public static String getConfDir(){
        if (context != null) {
            return context.getFilesDir().getAbsolutePath() + "/config/";
        }
        return System.getProperty("user.dir") + "/config/";
    }

    public Map<String, ServiceMask> getMyServiceMaskMap() {
        return myServiceMaskMap;
    }

    public void setMyServiceMaskMap(Map<String, ServiceMask> myServiceMaskMap) {
        this.myServiceMaskMap = myServiceMaskMap;
    }

    public static String getConfigDotJson() {
        return CONFIG_DOT_JSON;
    }

    public static void setConfigDotJson(String configDotJson) {
        CONFIG_DOT_JSON = configDotJson;
    }
    public String getEsAccountId() {
        return esAccountId;
    }

    public void setEsAccountId(String esAccountId) {
        this.esAccountId = esAccountId;
    }

    public Map<String, KeyInfo> getMainCidInfoMap() {
        return mainCidInfoMap;
    }

    public Map<Service.ServiceType, List<FreeApi>> getFreeApiListMap() {
        return freeApiListMap;
    }

    public byte[] getSymkey() {
        return symkey;
    }

    public void setSymkey(byte[] symkey) {
        this.symkey = symkey;
    }

    public String getPasswordName() {
        return passwordName;
    }

    public void setPasswordName(String passwordName) {
        this.passwordName = passwordName;
    }


    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public static void setContext(Context androidContext) {
        context = androidContext;
    }
}
