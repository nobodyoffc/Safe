package com.fc.fc_ajdk.config;

import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.ui.Menu;
import com.fc.fc_ajdk.data.feipData.ServiceMask;
import com.fc.fc_ajdk.ui.Inputer;
import com.fc.fc_ajdk.clients.ApipClient;
import com.fc.fc_ajdk.data.feipData.Service;
import com.fc.fc_ajdk.ui.Shower;

import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.google.gson.Gson;
import com.fc.fc_ajdk.constants.FieldNames;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fc.fc_ajdk.constants.FreeApi;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

import static com.fc.fc_ajdk.constants.FieldNames.STD_NAME;
import static com.fc.fc_ajdk.constants.Strings.CONFIG;
import static com.fc.fc_ajdk.constants.Strings.DOT_JSON;
import static com.fc.fc_ajdk.ui.Inputer.askIfYes;
import static com.fc.fc_ajdk.ui.Inputer.confirmDefault;
import static com.fc.fc_ajdk.constants.FieldNames.TYPES;
import static com.fc.fc_ajdk.core.fch.Inputer.importOrCreatePrikey;

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
    private Map<String, ApiProvider> apiProviderMap;
    private Map<String, ApiAccount> apiAccountMap;
    private transient byte[] symkey;

    private transient ApipClient apipClient;
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
        initFreeApiListMap();
    }

    public Configure() {
    }
    
    @NotNull
    public static String makeConfigFileName(String type) {
        return type + "_" + CONFIG + DOT_JSON;
    }

    public void initFreeApiListMap(){
        if (freeApiListMap == null) {
            freeApiListMap = new HashMap<>();
        }
        if(freeApiListMap.get(Service.ServiceType.APIP)==null){
            ArrayList<FreeApi> freeApipList = new ArrayList<>();
            for(String url:ApipClient.freeAPIs){
                freeApipList.add(new FreeApi(url,true, Service.ServiceType.APIP));
            }
            freeApiListMap.put(Service.ServiceType.APIP,freeApipList);
        }

        if(freeApiListMap.get(Service.ServiceType.DISK)==null){
            ArrayList<FreeApi> freeApipList = new ArrayList<>();
            for(String url:ApipClient.freeAPIs){
                freeApipList.add(new FreeApi(url,true, Service.ServiceType.DISK));
            }
            freeApiListMap.put(Service.ServiceType.APIP,freeApipList);
        }
    }

//    public String initiateClient(byte[] symkey) {
//        System.out.println("Initiating config...");
//        String fid;
//        if (apiProviderMap == null) apiProviderMap = new HashMap<>();
//        if (apiAccountMap == null) apiAccountMap = new HashMap<>();
//        if (fidCipherMap == null) {
//            fidCipherMap = new HashMap<>();
//            addUser(symkey);
//        }
//
//        if(fidCipherMap ==null || fidCipherMap.isEmpty())
//            return null;
//        fid = (String) Inputer.chooseOne(fidCipherMap.keySet().toArray(), null, "Choose a user:", br);
//        if(fid==null)fid = addUser(symkey);
//        saveConfig();
//        return fid;
//    }

    public String addUser(byte[] symkey) {
        return addUser(null,symkey);

    }

    public String addUser(String fid,byte[] symkey) {
        KeyInfo keyInfo;
        if(mainCidInfoMap.get(fid)!=null){
            if(!Inputer.askIfYes(br,fid +" exists. Replace it?"))
                return fid;
        }

        if(fid==null)System.out.println("Add new user...");
        else System.out.println("Add "+fid+" to users...");
        byte[] prikeyBytes;
        String pubkey=null;

        while(true) {
            try {
                prikeyBytes = importOrCreatePrikey(br);
                if(prikeyBytes==null){
                    if(askIfYes(br, "Add a watch-only FID?")) {
                        if(askIfYes(br,"Input the pubkey? ")){
                            pubkey = KeyTools.inputPubkey(br);
                            keyInfo = new KeyInfo(null,pubkey);
                        }else {
                            keyInfo = new KeyInfo();
                            String newFid = Inputer.inputFid(br, "Input the watch-only FID:");
                            if(newFid!=null){
                                keyInfo.setId(newFid);
                                keyInfo.setWatchOnly(true);
                            }
                            else return null;
                        }
                    } else continue;
                }else{
                    keyInfo = new KeyInfo(prikeyBytes,symkey);
                }
            }catch (Exception e){
                System.out.println("Something wrong. Try again.");
                continue;
            }
            if(fid == null) {
                    fid = keyInfo.getId();
                    break;
            }
            if(!fid.equals(keyInfo.getId())) {
                System.out.println("The cipher is of " + keyInfo.getId() + " instead of " + fid + ". \nTry again.");
            }
        }

        mainCidInfoMap.put(fid, keyInfo);
        saveConfig();
        return fid;
    }

    @Nullable
    public Service chooseOwnerService(String owner, byte[] symkey, Service.ServiceType type, ApipClient apipClient) {
        List<Service> serviceList;

        serviceList = apipClient.getServiceListByOwnerAndType(owner,type);

        if(serviceList==null || serviceList.isEmpty()){
            System.out.println("No any service on chain of the owner.");
            return null;
        }

        Service service;
        if(symkey!=null)service = selectService(serviceList, symkey, apipClient.getApiAccount());
        else service = selectService(serviceList);
        if(service==null) System.out.println("Failed to get the service.");

        return service;
    }

    public static Service selectService(List<Service> serviceList,byte[] symkey,ApiAccount apipAccount){
        if(serviceList==null||serviceList.isEmpty())return null;

        showServices(serviceList);

        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0){
            if(Inputer.askIfYes(br,"Publish a new service?")){
                Settings.publishService((ApipClient) apipAccount.getClient(),symkey,br);
                System.out.println("Wait for a few minutes and try to start again.");
                System.exit(0);
            }
        }
        return serviceList.get(choice-1);
    }

    public static Service selectService(List<Service> serviceList){
        if(serviceList==null||serviceList.isEmpty())return null;
        showServices(serviceList);
        int choice = Shower.choose(br,0,serviceList.size());
        if(choice==0)return null;
        return serviceList.get(choice-1);
    }
    public static void showServices(List<Service> serviceList) {
        String title = "Services";
        String[] fields = new String[]{STD_NAME, TYPES,FieldNames.ID};
        int[] widths = new int[]{24,24,64};
        List<List<Object>> valueListList = new ArrayList<>();
        for(Service service : serviceList){
            List<Object> valueList = new ArrayList<>();
            valueList.add(service.getStdName());
            StringBuilder sb = new StringBuilder();
            for(String type:service.getTypes()){
                sb.append(type);
                sb.append(",");
            }
            if(sb.length()>1)sb.deleteCharAt(sb.lastIndexOf(","));
            valueList.add(sb.toString());
            valueList.add(service.getId());
            valueListList.add(valueList);
        }
        Shower.showOrChooseList(title,fields,widths,valueListList, null);
    }
//    private ApiAccount chooseApi(byte[] symkey, ServiceType type, ApipClient apipClient) {
//        System.out.println("The " + type.name() + " is not ready. Set it...");
//        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap,type,apipClient);
//        ApiAccount apiAccount = findAccountForTheProvider(apiProvider, null, symkey,apipClient);
//        if(apiAccount.getClient()==null) {
//            System.err.println("Failed to create " + type.name() + ".");
//            return null;
//        }
//        return apiAccount;
//    }
    public ApiAccount addApiAccount(@NotNull ApiProvider apiProvider, String userFid, byte[] symkey, ApipClient initApipClient) {
        System.out.println("Adding new account...");
        if(askIfYes(br,"Stop adding API account for provider "+ apiProvider.getId()+"?"))return null;
        if(apiAccountMap==null)apiAccountMap = new HashMap<>();
        ApiAccount apiAccount;
        while(true) {
            apiAccount = new ApiAccount();
            apiAccount.inputAll(symkey,apiProvider, userFid, mainCidInfoMap, br);
            saveConfig();
            try {
                Object client = apiAccount.connectApi(apiProvider, symkey, br, initApipClient, mainCidInfoMap);
                if(client==null) {
                    if(askIfYes(br,"Failed to connect "+apiAccount.getApiUrl()+". Reset?")) continue;
                    else return null;
                }
            }catch (Exception e){
                e.printStackTrace();
                System.out.println("Can't connect the API provider of "+apiProvider.getId());
                if(Inputer.askIfYes(br,"Do you want to revise the API provider?")){
                    apiProvider.updateAll(br);
                    saveConfig();
                    continue;
                }else return null;
            }
            apiAccountMap.put(apiAccount.getId(), apiAccount);
            saveConfig();
            break;
        }
        return apiAccount;
    }

    public void showApiProviders(Map<String, ApiProvider> apiProviderMap) {
        if(apiProviderMap==null || apiProviderMap.size()==0)return;
        String[] fields = {"sid", "type","url", "ticks"};
        int[] widths = {16,10, 32, 24};
        List<List<Object>> valueListList = new ArrayList<>();
        for (ApiProvider apiProvider : apiProviderMap.values()) {
            List<Object> valueList = new ArrayList<>();
            valueList.add(apiProvider.getId());
            valueList.add(apiProvider.getType());
            valueList.add(apiProvider.getApiUrl());
            valueList.add(Arrays.toString(apiProvider.getTicks()));
            valueListList.add(valueList);
        }
        Shower.showOrChooseList("API providers", fields, widths, valueListList, null);
    }

    public void showAccounts(Map<String, ApiAccount> apiAccountMap) {
        if(apiAccountMap==null || apiAccountMap.size()==0)return;
        String[] fields = {"id","userName","userId", "url", "sid"};
        int[] widths = {16,16,16, 32, 16};
        List<List<Object>> valueListList = new ArrayList<>();
        for (ApiAccount apiAccount : apiAccountMap.values()) {
            List<Object> valueList = new ArrayList<>();
            valueList.add(apiAccount.getId());
            valueList.add(apiAccount.getUserName());
            valueList.add(apiAccount.getUserId());
            valueList.add(apiAccount.getApiUrl());
            valueList.add(apiAccount.getProviderId());
            valueListList.add(valueList);
        }
        Shower.showOrChooseList("API accounts", fields, widths, valueListList, null);
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public Map<String, ApiProvider> getApiProviderMap() {
        return apiProviderMap;
    }

    public void setApiProviderMap(Map<String, ApiProvider> apiProviderMap) {
        this.apiProviderMap = apiProviderMap;
    }

    public Map<String, ApiAccount> getApiAccountMap() {
        return apiAccountMap;
    }

    public void setApiAccountMap(Map<String, ApiAccount> apiAccountMap) {
        this.apiAccountMap = apiAccountMap;
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



    public void addApiAccount(String userFid, byte[] symkey, ApipClient initApipClient){
        System.out.println("Add API accounts...");
        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap,  initApipClient);
        if(apiProvider!=null) {
            ApiAccount apiAccount = addApiAccount(apiProvider, userFid, symkey, initApipClient);
            saveConfig();
            if(apiAccount==null) System.out.println("Failed to add API account for "+apiProvider.getApiUrl());
            else System.out.println("Add API account "+apiAccount.getId()+" is added.");
        } else System.out.println("Failed to add API account.");

        Menu.anyKeyToContinue(br);
    }
    public void addApiProviderAndConnect(byte[] symkey, Service.ServiceType serviceType, ApipClient initApipClient){
        System.out.println("Add API providers...");
        ApiProvider apiProvider = addApiProvider(serviceType,initApipClient);
        if(apiProvider!=null) {
            ApiAccount apiAccount = addApiAccount(apiProvider, null, symkey, initApipClient);
            if(apiAccount!=null) {
                apiAccount.connectApi(apiProvider, symkey, br, null, mainCidInfoMap);
                saveConfig();
            }else return;
        }
       if(apiProvider!=null)System.out.println("Add API provider "+apiProvider.getId()+" is added.");
       else System.out.println("Failed to add API provider.");
       Menu.anyKeyToContinue(br);
    }

    public ApiProvider addApiProvider(Service.ServiceType serviceType, ApipClient apipClient) {
        String ask;
        System.out.println("Adding a new API provider...");
        if(serviceType==null)
            ask = "Stop to add new provider?";
        else ask = "Stop to add new "+ serviceType +" provider?";

        if(askIfYes(br,ask))return null;

        ApiProvider apiProvider = new ApiProvider();
        if(!apiProvider.makeApiProvider(br, serviceType,apipClient))return null;

        if(apiProviderMap==null)apiProviderMap= new HashMap<>();
        apiProviderMap.put(apiProvider.getId(),apiProvider);
        System.out.println(apiProvider.getId()+" on "+apiProvider.getApiUrl() + " added.");
        saveConfig();
        return apiProvider;
    }


    public void updateApiAccount(ApiProvider apiProvider, byte[] symkey, ApipClient initApipClient){
        System.out.println("Update API accounts...");
        ApiAccount apiAccount;
        if(apiProvider==null)apiAccount = chooseApiAccount(symkey,initApipClient);
        else apiAccount = findAccountForTheProvider(apiProvider, null, symkey,initApipClient);
        if(apiAccount!=null) {
            System.out.println("Update API account: "+apiAccount.getProviderId()+"...");
            apiAccount.updateAll(symkey, apiProvider,br);
            getApiAccountMap().put(apiAccount.getId(), apiAccount);
            saveConfig();
        }
        if(apiAccount!=null) System.out.println("Api account "+apiAccount.getId()+" is updated.");
        else System.out.println("Failed to update API account.");
        Menu.anyKeyToContinue(br);
    }

    public void updateApiProvider(ApipClient apipClient){
        System.out.println("Update API providers...");
        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, apipClient);
        if(apiProvider!=null) {
            apiProvider.updateAll(br);
            getApiProviderMap().put(apiProvider.getId(), apiProvider);
            saveConfig();
            System.out.println("Api provider "+apiProvider.getId()+" is updated.");
        }
        System.out.println("Failed to update API provider.");
        Menu.anyKeyToContinue(br);
    }

    public void deleteApiProvider(byte[] symkey,ApipClient apipClient){
        System.out.println("Deleting API provider...");
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap);
        if(apiProvider==null) return;
        for(ApiAccount apiAccount: getApiAccountMap().values()){
            if(apiAccount.getProviderId().equals(apiProvider.getId())){
                if(Inputer.askIfYes(br,"There is the API account "+apiAccount.getId()+" of "+apiProvider.getId()+". \nDelete it?")){
                    getApiAccountMap().remove(apiAccount.getId());
                    System.out.println("Api account "+apiAccount.getId()+" is deleted.");
                    saveConfig();
                }
            }
        }
        if(Inputer.askIfYes(br,"Delete API provider "+apiProvider.getId()+"?")){
            getApiProviderMap().remove(apiProvider.getId());
            System.out.println("Api provider " + apiProvider.getId() + " is deleted.");
            saveConfig();
        }
        Menu.anyKeyToContinue(br);
    }

    public void deleteApiAccount(byte[] symkey,ApipClient initApipClient){
        System.out.println("Deleting API Account...");
        ApiAccount apiAccount = chooseApiAccount(symkey,initApipClient);
        if(apiAccount==null) return;
        if(Inputer.askIfYes(br,"Delete API account "+apiAccount.getId()+"?")) {
            getApiAccountMap().remove(apiAccount.getId());
            System.out.println("Api account " + apiAccount.getId() + " is deleted.");
            saveConfig();
        }
        Menu.anyKeyToContinue(br);
    }

    public ApiAccount chooseApiAccount(byte[] symkey,ApipClient initApipClient){
        ApiAccount apiAccount = null;
        showAccounts(getApiAccountMap());
        int input = Inputer.inputInt(br, "Input the number of the account you want. Enter to add a new one:", getApiAccountMap().size());
        if (input == 0) {
            if(Inputer.askIfYes(br,"Add a new API account?")) {
                ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, initApipClient);
                if(apiProvider==null)return null;
                apiAccount = addApiAccount(apiProvider, null, symkey, initApipClient);
            }
        } else {
            apiAccount = (ApiAccount) getApiAccountMap().values().toArray()[input - 1];
        }
        return apiAccount;
    }

public ApiProvider chooseApiProviderOrAdd(Map<String, ApiProvider> apiProviderMap, ApipClient apipClient){
        return chooseApiProviderOrAdd(apiProviderMap,null,apipClient);
}
    public ApiProvider chooseApiProviderOrAdd(Map<String, ApiProvider> apiProviderMap, Service.ServiceType serviceType, ApipClient apipClient){
        if(serviceType ==null)
            serviceType = com.fc.fc_ajdk.core.fch.Inputer.chooseOne(Service.ServiceType.values(), null, "Choose the API type:",br);
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap, serviceType);
        if(apiProvider==null){
            if(askIfYes(br,"Add new provider?"))
                apiProvider = addApiProvider(serviceType,apipClient);
            else return null;
        }
        return apiProvider;
    }
    public ApiProvider chooseApiProvider(Map<String, ApiProvider> apiProviderMap, Service.ServiceType serviceType){
        Map<String, ApiProvider> map = new HashMap<>();
        for(String id : apiProviderMap.keySet()){
            ApiProvider apiProvider = apiProviderMap.get(id);
            if(apiProvider.getType().equals(serviceType))
                map.put(id,apiProvider);
        }
        return chooseApiProvider(map);
    }

    public ApiProvider chooseApiProvider(Map<String, ApiProvider> apiProviderMap){
        System.out.println("Choose API provider...");
        ApiProvider apiProvider;
        if (apiProviderMap == null) {
            apiProviderMap = new HashMap<>();
            setApiProviderMap(apiProviderMap);
        }
        if (apiProviderMap.size() == 0) {
            System.out.println("No API provider yet.");
            return null;
        } else {
            if(apiProviderMap.size()==1){
                String key = (String)apiProviderMap.keySet().toArray()[0];
                ApiProvider apiProvider1 = apiProviderMap.get(key);
                if(confirmDefault(br,apiProvider1.getName())) {
                    return apiProvider1;
                } else return null;
            }

            showApiProviders(apiProviderMap);
            int input = Inputer.inputInt( br,"Input the number of the API provider you want. Enter to add new one:", apiProviderMap.size());
            if (input == 0) {
                return null;
            } else apiProvider = (ApiProvider) apiProviderMap.values().toArray()[input - 1];
        }
        return apiProvider;
    }

    public ApiProvider selectFcApiProvider(ApipClient initApipClient, Service.ServiceType serviceType) {
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap, serviceType);
        if(apiProvider==null) apiProvider= ApiProvider.searchFcApiProvider(initApipClient, serviceType);
        return apiProvider;
    }


    public ApiAccount getAccountForTheProvider(ApiProvider apiProvider, String userFid, byte[] symkey, ApipClient initApipClient) {
        System.out.println("Get account for "+apiProvider.getName()+"...");
        ApiAccount apiAccount;
        if (apiAccountMap == null) setApiAccountMap(new HashMap<>());

        if(apiAccountMap.size()!=0) {
            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
                if (apiAccount1.getProviderId().equals(apiProvider.getId())) {
                    String account1UserId = apiAccount1.getUserId();
                    if (account1UserId != null && account1UserId.equals(userFid) && KeyTools.isGoodFid(account1UserId)) {
                        apiAccount1.setApipClient(initApipClient);
                        if (apiAccount1.getClient() == null) apiAccount1.connectApi(apiProvider, symkey, br);
                        return apiAccount1;
                    }
                }
            }
        }
        apiAccount = addApiAccount(apiProvider, userFid, symkey,initApipClient );
        if(apiAccount==null)return null;
        apiAccount.setApipClient(initApipClient);
        if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symkey, br);
        return apiAccount;
    }

    public ApiAccount findAccountForTheProvider(ApiProvider apiProvider, String userFid, byte[] symkey, ApipClient initApipClient) {
        System.out.println("Get account for "+apiProvider.getName()+"...");
        ApiAccount apiAccount;
        Map<String, ApiAccount> hitApiAccountMap = new HashMap<>();
        if (apiAccountMap == null) setApiAccountMap(new HashMap<>());

        if(apiAccountMap.size()!=0){
            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
                if (apiAccount1.getProviderId().equals(apiProvider.getId())) {
                    String account1UserId = apiAccount1.getUserId();
                    if(account1UserId !=null && account1UserId.equals(userFid) && KeyTools.isGoodFid(account1UserId)) {
                        apiAccount1.setApipClient(initApipClient);
                        if (apiAccount1.getClient() == null) apiAccount1.connectApi(apiProvider, symkey, br);
                        return apiAccount1;
                    }else hitApiAccountMap.put(apiAccount1.getId(), apiAccount1);
                }
            }

            if(hitApiAccountMap.size()==0) {
                apiAccount = addApiAccount(apiProvider, userFid, symkey,initApipClient );
            }else if(hitApiAccountMap.size()==1){
                String key = (String)hitApiAccountMap.keySet().toArray()[0];
                apiAccount = hitApiAccountMap.get(key);
                if(confirmDefault(br,apiAccount.getUserName())) {
                    apiAccount.setApipClient(initApipClient);
                    if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symkey, br);
                    return apiAccount;
                } else apiAccount = addApiAccount(apiProvider, userFid, symkey,initApipClient );
            }else {
                showAccounts(hitApiAccountMap);

                int input = Inputer.inputInt(br, "Input the number of the account you want. Enter to add new one:", hitApiAccountMap.size());
                if (input == 0) {
                    apiAccount = addApiAccount(apiProvider, userFid, symkey, initApipClient);
                } else {
                    apiAccount = (ApiAccount) hitApiAccountMap.values().toArray()[input - 1];
                }
            }
        }else apiAccount = addApiAccount(apiProvider, userFid, symkey,initApipClient );

        apiAccount.setApipClient(initApipClient);
        if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symkey, br);
        return apiAccount;
    }

//        System.out.println("No API accounts yet. Add new one...");
//        return addApiAccount(apiProvider, userFid, symkey, initApipClient);


//        if (apiAccountMap.size() == 0) {
//            System.out.println("No API accounts yet. Add new one...");
//            apiAccount = addApiAccount(apiProvider, userFid, symkey, initApipClient);
//        } else {
//            for (ApiAccount apiAccount1 : getApiAccountMap().values()) {
//                if (apiAccount1.getProviderId().equals(apiProvider.getId())) {
//                    hitApiAccountMap.put(apiAccount1.getId(), apiAccount1);
//                }
//            }
//            if (hitApiAccountMap.size() == 0) {
//                apiAccount = addApiAccount(apiProvider, userFid, symkey, initApipClient);
//            }
//            else {
//
//                if(hitApiAccountMap.size()==1){
//                    String key = (String)hitApiAccountMap.keySet().toArray()[0];
//                    ApiAccount apiAccount1 = hitApiAccountMap.get(key);
//                    if(confirmDefault(br,apiAccount1.getUserName())) {
//                        return apiAccount1;
//                    } else return null;
//                }
//
//                showAccounts(hitApiAccountMap);
//                int input = Inputer.inputInteger( br,"Input the number of the account you want. Enter to add new one:", hitApiAccountMap.size());
//                if (input == 0) {
//                    apiAccount = addApiAccount(apiProvider, userFid, symkey,initApipClient );
//                } else {
//                    apiAccount = (ApiAccount) hitApiAccountMap.values().toArray()[input - 1];
//                    apiAccount.setApipClient(initApipClient);
//                    if(apiAccount.getClient()==null)apiAccount.connectApi(apiProvider,symkey);
//                }
//            }
//        }
//        return apiAccount;

    public ApiAccount checkAPI(@Nullable String apiAccountId, String userFid, Service.ServiceType serviceType, byte[] symkey) {
        if(serviceType !=null) System.out.println("\nCheck "+ serviceType +" API...");
        apiAccountMap.remove("null");
        ApiAccount apiAccount = null;
        while (true) {
            if (apiAccountId == null) {
                System.out.println("No " + serviceType + " account set yet. ");
                    apiAccount = getApiAccount(symkey, userFid, serviceType, apipClient);
            }else {
                apiAccount = apiAccountMap.get(apiAccountId);
                if(apiAccount ==null || askIfYes(br,"Current API is from "+apiAccount.getApiUrl()+" Change it?"))
                    apiAccount = getApiAccount(symkey, userFid, serviceType, apipClient);
            }

            if (apiAccount == null) {
                if(askIfYes(br,"Failed to get API account. Try again?")) continue;
                return null;
            }

            if (apiAccount.getClient() == null) {
                Object apiClient;
                try {
                    apiClient = apiAccount.connectApi(getApiProviderMap().get(apiAccount.getProviderId()), symkey, br, apipClient, mainCidInfoMap);
                }catch (Exception e){
                    System.out.println("Failed to connect " + apiAccount.getApiUrl() + ". Try again.");
                    continue;
                }
                if (apiClient == null) {
                    System.out.println("Failed to connect " + apiAccount.getApiUrl() + ". Try again.");
                    continue;
                }
            }
            return apiAccount;
        }
    }

//    public ApiAccount setApiService(byte[] symkey,ApipClient apipClient) {
//        ApiProvider apiProvider = chooseApiProviderOrAdd(apiProviderMap, apipClient);
//        ApiAccount apiAccount = chooseApiProvidersAccount(apiProvider,symkey,apipClient);
//        if(apiAccount.getClient()!=null) saveConfig();
//        return apiAccount;
//    }

    public ApiAccount getApiAccount(byte[] symkey, String userFid, Service.ServiceType serviceType, ApipClient apipClient) {
        ApiProvider apiProvider = chooseApiProvider(apiProviderMap, serviceType);

        while(apiProvider==null) {
            apiProvider = addApiProvider(serviceType,apipClient);
            if(apiProvider!=null)break;

            if(!Inputer.askIfYes(br,"Failed to add API provider. Try again?"))return null;
        }

        if(apiProvider.getId()==null){
            System.out.println("The ID of the API provider is null. Update it...");
            apiProvider.updateAll(br);
            saveConfig();
        }

        ApiAccount apiAccount;
//        if(shareApiAccount)apiAccount = findAccountForTheProvider(apiProvider, userFid, symkey,apipClient);
//        else
        apiAccount = getAccountForTheProvider(apiProvider, userFid, symkey,apipClient);

        if(apiAccount!=null && apiAccount.getClient()!=null) saveConfig();
        return apiAccount;
    }



    public static Configure checkPassword(BufferedReader br){
        Configure configure;
        byte[] passwordBytes;
        if(configureMap.isEmpty()){
            while(true) {
                passwordBytes = Inputer.resetNewPassword(br);
                if(passwordBytes!=null) break;
                System.out.println("A password is required. Try again.");
            }
            configure = createNewConfigure(passwordBytes);
        }else {
            configure = verifyPassword(br);
            if(configure==null)return null;
        }
        initConfigure(br, configure);
        return configure;
    }

    public static Configure verifyPassword(BufferedReader br) {
        byte[] symkey;
        byte[] nonceBytes;
        byte[] passwordBytes;
        String passwordName;
        Configure configure = null;
        char[] password = Inputer.inputPassword(br, "Input your password:");
        while (true) {
            if(password==null)continue;
            passwordBytes = BytesUtils.utf8CharArrayToByteArray(password);
            passwordName = IdNameUtils.makePasswordHashName(passwordBytes);
            configure = configureMap.get(passwordName);
            if (configure != null) {
                nonceBytes = Hex.fromHex(configure.getNonce());
                symkey = getSymkeyFromPassword(passwordBytes);
                configure.setSymkey(symkey);
                configure.setPasswordName(passwordName);
                break;
            }
            String input = Inputer.inputString(br, "Password wrong. Try again. 'c' to create new one. 'q' to quit:");
            if (input.equals("c")) {
                passwordBytes = Inputer.resetNewPassword(br);
                configure = createNewConfigure(passwordBytes);
                break;
            } else if (input.equals("q")) {
                System.exit(0);
                return null;
            }else {
                password = input.toCharArray();
            }
        }
        return configure;
    }

    public static boolean checkPassword(BufferedReader br, byte[] symkey,Configure configure) {
        while(true){    
            char[] password = Inputer.inputPassword(br, "Input your password:");
            if(password==null)return false;
            byte[] passwordBytes = BytesUtils.utf8CharArrayToByteArray(password);
            byte[] nonceBytes = Hex.fromHex(configure.getNonce());
            byte[] symkey1 = getSymkeyFromPassword(passwordBytes);
            if(Arrays.equals(symkey, symkey1))return true;
            System.out.println("Wrong password. Try again.");
        }
    }

    public static boolean checkPassword(byte[] passwordBytes, byte[] symkey, Configure configure) {
        while(true){
            byte[] nonceBytes = Hex.fromHex(configure.getNonce());
            byte[] symkey1 = getSymkeyFromPassword(passwordBytes);
            if(Arrays.equals(symkey, symkey1))return true;
            System.out.println("Wrong password. Try again.");
        }
    }


    public boolean checkSimplePassword(byte[] passwordBytes){
        if(passwordBytes==null)return false;
        String passwordHash;

        if(getPasswordHash()==null){
                passwordHash = Hex.toHex(passwordBytes);
                setPasswordHash(passwordHash);
                saveConfig();
                this.symkey = Hash.sha256(passwordBytes);
                return true;
        }else return verifyPassword(getPasswordHash(), br);
    }

    public boolean verifyPassword(String passwordHash,BufferedReader br) {
        byte[] passwordBytes;
        char[] password = Inputer.inputPassword(br, "Input your password:");
        while (true) {
            if(password==null)continue;
            passwordBytes = BytesUtils.utf8CharArrayToByteArray(password);
            byte[] newHash = Hash.sha256x2(passwordBytes);
            String newHashHex = Hex.toHex(newHash);
            if(passwordHash.equals(newHashHex))return true;

            String input = Inputer.inputString(br, "Password wrong. Try again. 'q' to quit:");
            if (input.equals("q")) {
                System.exit(0);
                return false;
            }
        }
    }

    private static Configure createNewConfigure(byte[] passwordBytes) {
        Configure configure = new Configure();
        byte[] symkey;
        byte[] nonceBytes;
        nonceBytes = BytesUtils.getRandomBytes(16);
        symkey = getSymkeyFromPassword(passwordBytes);
        configure.nonce = Hex.toHex(nonceBytes);
        configure.setSymkey(symkey);
        String name = IdNameUtils.makePasswordHashName(passwordBytes);
        configure.setPasswordName(name);
        configureMap.put(name,configure);
        saveConfig();
        BytesUtils.clearByteArray(passwordBytes);
        return configure;
    }

    private static void initConfigure(BufferedReader br, Configure configure) {
        configure.initFreeApiListMap();
        configure.setBr(br);
        if(configure.getApiProviderMap()==null)
                configure.setApiProviderMap(new HashMap<>());
        if(configure.getApiAccountMap() == null)
                configure.setApiAccountMap(new HashMap<>());
        if(configure.getMainCidInfoMap()==null)
                configure.setMainCidInfoMap(new HashMap<>());
        if(configure.getMyServiceMaskMap()==null)
                configure.setMyServiceMaskMap(new HashMap<>());
        if(configure.getOwnerList()==null)
                configure.setOwnerList(new ArrayList<>());
        if(configure.mainCidInfoMap ==null)
            configure.initFreeApiListMap();
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

    public static void loadConfig(String path, BufferedReader br){
        if (context != null) {
            // Android environment - use SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            String configJson = prefs.getString(CONFIG_KEY, null);
            if (configJson != null) {
                try {
                    configureMap = JsonUtils.jsonToMap(configJson, String.class, Configure.class);
                } catch (Exception e) {
                    TimberLogger.e("Failed to parse config from SharedPreferences: " + e.getMessage());
                    configureMap = new HashMap<>();
                }
            } else {
                configureMap = new HashMap<>();
            }
        } else {
            // Command line environment - use file system
            if(path == null) path = getConfDir();
            try {
                configureMap = JsonUtils.readMapFromJsonFile(path, CONFIG_DOT_JSON, String.class, Configure.class);
            } catch (IOException e) {
                TimberLogger.e("Failed to load configMap from " + CONFIG_DOT_JSON + ". It will be created.");
                configureMap = new HashMap<>();
            }
        }
        
        if(configureMap == null) {
            TimberLogger.d("Failed to load configMap from " + CONFIG_DOT_JSON + ". It will be created.");
            configureMap = new HashMap<>();
        }
    }

    public static void loadConfig(BufferedReader br){
        loadConfig(null, br);
    }


    public BufferedReader getBr() {
        return br;
    }

    public void setBr(BufferedReader br1) {
        br = br1;
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

    public void setMainCidInfoMap(Map<String, KeyInfo> mainCidInfoMap) {
        this.mainCidInfoMap = mainCidInfoMap;
    }

    public String addOwner(BufferedReader br) {
        String owner = com.fc.fc_ajdk.core.fch.Inputer.inputGoodFid(br,"Input the FID or pubkey of the owner:");

        if(ownerList ==null) ownerList = new ArrayList<>();

        ownerList.add(owner);

        saveConfig();

        return owner;
    }


    public String chooseMainFid(byte[] symkey) {
        while(true) {
            String fid = Inputer.chooseOne(mainCidInfoMap.keySet().toArray(new String[0]), null, "Choose the FID", br);
            if (fid == null) {
                System.out.println("No FID chosen.");
                if (askIfYes(br, "Add a new FID?"))
                    return addUser(symkey);
                else continue;
            }
            String fidCipher = mainCidInfoMap.get(fid).getPrikeyCipher();
            if(fidCipher==null || fidCipher.equals("")){
                System.out.println("This is a watch-only FID.");
                Menu.anyKeyToContinue(br);
                return fid;
            }     
            return fid;
        }
    }

    public String getServiceDealer(String sid, byte[] symkey) {

        ServiceMask serviceMask = myServiceMaskMap.get(sid);
        if(serviceMask!=null && serviceMask.getDealer()!=null)
            return serviceMask.getDealer();

        System.out.println("Set the dealer of your service which was published on-chain...");
        return chooseMainFid(symkey);
    }

    public String chooseSid(Service.ServiceType serviceType) {
        Map<String, ServiceMask> map = new HashMap<>();
        if(serviceType!=null){
            for(String key: myServiceMaskMap.keySet()){
                ServiceMask serviceSummary = myServiceMaskMap.get(key);
                if(StringUtils.isContainCaseInsensitive(serviceSummary.getTypes(),serviceType.name()))
                    map.put(key,serviceSummary);
            }
        }else map= myServiceMaskMap;
        return Inputer.chooseOneKeyFromMap(map, true, STD_NAME, "Choose your service:", br);
    }

    public static boolean containsIgnoreCase(String str, String searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        return str.toLowerCase().contains(searchStr.toLowerCase());
    }

    public Map<Service.ServiceType, List<FreeApi>> getFreeApiListMap() {
        return freeApiListMap;
    }

    public void setFreeApiListMap(Map<Service.ServiceType, List<FreeApi>> freeApiListMap) {
        this.freeApiListMap = freeApiListMap;
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

    public static Map<String, Configure> getConfigureMap() {
        return configureMap;
    }

    public static void setConfigureMap(Map<String, Configure> configureMap) {
        Configure.configureMap = configureMap;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public void setApipClient(ApipClient apipClient) {
        this.apipClient = apipClient;
    }

    // Add method to get all account IDs for a service type
    public List<String> getAccountIdsForServiceType(Service.ServiceType type) {
        List<String> accountIds = new ArrayList<>();
        if (apiAccountMap != null) {
            for (Map.Entry<String, ApiAccount> entry : apiAccountMap.entrySet()) {
                ApiProvider provider = apiProviderMap.get(entry.getValue().getProviderId());
                if (provider != null && provider.getType() == type) {
                    accountIds.add(entry.getKey());
                }
            }
        }
        return accountIds;
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
