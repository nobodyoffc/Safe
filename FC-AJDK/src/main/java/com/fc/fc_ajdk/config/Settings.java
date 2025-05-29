package com.fc.fc_ajdk.config;

import com.fc.fc_ajdk.constants.OpNames;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.feipData.Feip;
import com.fc.fc_ajdk.data.feipData.ServiceOpData;
import com.fc.fc_ajdk.ui.Inputer;
import com.fc.fc_ajdk.ui.Menu;
import com.fc.fc_ajdk.clients.ApipClient;
import com.fc.fc_ajdk.clients.FcClient;
import com.fc.fc_ajdk.clients.ClientGroup;
import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.constants.IndicesNames;

import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.AutoTask;
import com.fc.fc_ajdk.data.fchData.Block;
import com.fc.fc_ajdk.data.fchData.Cid;
import com.fc.fc_ajdk.data.feipData.Service;
import com.fc.fc_ajdk.data.feipData.ServiceMask;
import com.fc.fc_ajdk.clients.NaSaClient.NaSaRpcClient;
import com.fc.fc_ajdk.core.crypto.Base58;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.feipData.serviceParams.ApipParams;
import com.fc.fc_ajdk.data.feipData.serviceParams.DiskParams;
import com.fc.fc_ajdk.data.feipData.serviceParams.Params;
import com.fc.fc_ajdk.data.feipData.serviceParams.SwapHallParams;
import com.fc.fc_ajdk.data.feipData.serviceParams.TalkParams;
import com.fc.fc_ajdk.handlers.AccountHandler;
import com.fc.fc_ajdk.handlers.CashHandler;
import com.fc.fc_ajdk.handlers.CidHandler;
import com.fc.fc_ajdk.handlers.ContactHandler;
import com.fc.fc_ajdk.handlers.DiskHandler;
import com.fc.fc_ajdk.handlers.GroupHandler;
import com.fc.fc_ajdk.handlers.Handler;
import com.fc.fc_ajdk.handlers.HatHandler;
import com.fc.fc_ajdk.handlers.MailHandler;
import com.fc.fc_ajdk.handlers.MempoolHandler;
import com.fc.fc_ajdk.handlers.NonceHandler;
import com.fc.fc_ajdk.handlers.SecretHandler;
import com.fc.fc_ajdk.handlers.SessionHandler;
import com.fc.fc_ajdk.handlers.TalkIdHandler;
import com.fc.fc_ajdk.handlers.TalkUnitHandler;
import com.fc.fc_ajdk.handlers.TeamHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fc.fc_ajdk.ui.Shower;
import com.fc.fc_ajdk.utils.TimberLogger;

import com.fc.fc_ajdk.constants.FreeApi;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.FileUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.QRCodeUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.fc_ajdk.utils.http.AuthType;
import com.fc.fc_ajdk.utils.http.RequestMethod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.fc.fc_ajdk.constants.FieldNames.SETTINGS;
import static com.fc.fc_ajdk.constants.Strings.DATA;
import static com.fc.fc_ajdk.constants.Strings.DOT_JSON;
import static com.fc.fc_ajdk.data.feipData.Service.ServiceType.APIP;
import static com.fc.fc_ajdk.data.feipData.Service.ServiceType.NASA_RPC;
import static com.fc.fc_ajdk.ui.Inputer.askIfYes;
import static com.fc.fc_ajdk.config.Configure.*;
import static com.fc.fc_ajdk.constants.Constants.UserDir;
import static com.fc.fc_ajdk.clients.ApipClient.ApipApiNames.VERSION_1;

import android.content.Context;
import android.content.SharedPreferences;

public class Settings {
    public final static String TAG = "Settings";
    public static final String DEFAULT_AVATAR_BASE_PATH = System.getProperty("user.dir") + "/avatar/elements/";
    public static final String DEFAULT_AVATAR_FILE_PATH = System.getProperty("user.dir") + "/avatar/png/";

    public static final String LISTEN_PATH = "listenPath";
    public static final String DB_DIR = "dbDir";
    public static final String OP_RETURN_PATH = "opReturnPath";
    public static final String FORBID_FREE_API = "forbidFreeApi";
    public static final String FROM_WEBHOOK = "fromWebhook";
    public static final String WINDOW_TIME = "windowTime";
    public static final String DEALER_MIN_BALANCE = "dealerMinBalance";
    public static final String MIN_DISTRIBUTE_BALANCE = "minDistributeBalance";
    public static final String AVATAR_ELEMENTS_PATH = "avatarElementsPath";
    public static final String AVATAR_PNG_PATH = "avatarPngPath";
    public static final Long DEFAULT_WINDOW_TIME = 300000L;
    public static final String DISK_DATA_PATH = Constants.UserHome+".diskData";

    public static Map<Service.ServiceType,List<FreeApi>> freeApiListMap;
    private static String fileName;
    private transient Configure config;
    private transient BufferedReader br;
    private transient String clientDataFileName;

    private transient List<ApiAccount> paidAccountList;
    private transient byte[] symkey;
    private transient Map<Handler.HandlerType, Handler<?>> handlers;

    private Map<Service.ServiceType, ClientGroup> clientGroups;
    private Service.ServiceType serverType;
    private Map<String,Long> bestHeightMap;
    private Service service;
    private String clientName;

    private String sid; //For server
    private String mainFid; //For Client
    private String myPubkey;
    private String myPrikeyCipher;

    //Settings
    private Map<String,Object> settingMap;

    private Object[] modules;
//    private Object[] runningModules;
    private List<String> apiList;
    private String dbDir;
    private LocalDB.DbType localDBType;
    private List<AutoTask> autoTaskList;

    private Context context;

    public Settings(Configure configure, String clientName, Object[] modules, Map<String, Object> settingMap, List<AutoTask> autoTaskList) {
        if(configure!=null) {
            this.config = configure;
            this.br = configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            checkDbDir(null);
            this.clientName = clientName;
            this.modules = modules;
            this.autoTaskList = autoTaskList;
            this.settingMap = settingMap;
            checkSetting(br);
            addShutdownHook();
        }
    }
    public Settings(Configure configure, Service.ServiceType serverType, Map<String,Object> settingMap, Object[] modules, List<AutoTask> autoTaskList) {
        if(configure!=null) {
            this.config = configure;
            this.br =configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            this.settingMap = settingMap;
            this.serverType = serverType;
            this.modules = modules;
            this.autoTaskList = autoTaskList;
            checkSetting(br);
            checkDbDir(settingMap);
            addShutdownHook();
        }
    }

    public Settings(Configure configure, Map<String,Object> settingMap, Object[] modules) {
        if(configure!=null) {
            this.config = configure;
            this.br = configure.getBr();
            freeApiListMap = configure.getFreeApiListMap();
            clientGroups = new HashMap<>();
            this.settingMap = settingMap;
            this.modules = modules;
            checkDbDir(settingMap);
            addShutdownHook();
        }
    }

    @Nullable
    public static Settings loadSettingsForServer(String configFileName) {
        WebServerConfig webServerConfig;
        Configure configure;
        Settings settings;
        Map<String, Configure> configureMap;
        try {
            webServerConfig = JsonUtils.readJsonFromFile(configFileName,WebServerConfig.class);
            configureMap = JsonUtils.readMapFromJsonFile(null,webServerConfig.getConfigPath(),String.class,Configure.class);
            if(configureMap==null){
                TimberLogger.e(TAG,"Failed to read the config file of "+ configFileName +".");
                return null;
            }
            configure = configureMap.get(webServerConfig.getPasswordName());
            settings = JsonUtils.readJsonFromFile(webServerConfig.getSettingPath(), Settings.class);
            settings.setConfig(configure);

        } catch (IOException e) {
            TimberLogger.e(TAG,"Failed to read the config file of "+ configFileName +".");
            return null;
        }
        return settings;
    }

    @Nullable
    public static Settings loadSettings(String fid, String name, Context context) {
        Settings settings = null;
        String fileName = FileUtils.makeFileName(fid, name, SETTINGS, DOT_JSON);
        
        if (context != null) {
            // Android environment - use SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(SETTINGS + "_" + fileName, Context.MODE_PRIVATE);
            String settingsJson = prefs.getString(SETTINGS, null);
            if (settingsJson != null) {
                try {
                    settings = JsonUtils.fromJson(settingsJson, Settings.class);
                } catch (Exception e) {
                    TimberLogger.e(TAG,"Failed to parse settings from SharedPreferences: " + e.getMessage());
                }
            }
        } else {
            // Command line environment - use file system
            try {
                settings = JsonUtils.readObjectFromJsonFile(getConfDir(), fileName, Settings.class);
            } catch (IOException e) {
                TimberLogger.e(TAG,"Failed to load settings json from file.");
            }
        }
        
        return settings;
    }

    public Block getBestBlock(){
        ApipClient apipClient = null;
        if(getClient(APIP)!=null)
            apipClient = (ApipClient)getClient(APIP);
        return getBestBlock(apipClient,null);
    }


    public static Block getBestBlock(ApipClient apipClient,NaSaRpcClient naSaRpcClient) {
        
        if(naSaRpcClient!=null){
            Block block = new Block();
            naSaRpcClient.freshBestBlock();
            block.setHeight(naSaRpcClient.getBestHeight());
            block.setId(naSaRpcClient.getBestBlockId());
            return block;
        }

        if(apipClient!=null){
            return apipClient.bestBlock(RequestMethod.POST, AuthType.FC_SIGN_BODY);
        }
        return null;
    }

    public Long getBestHeight() {
        if(getClient(APIP)!=null){
            return ((ApipClient)getClient(APIP)).getBestHeight();
        }

        if(getClient(NASA_RPC)!=null){
            NaSaRpcClient client = (NaSaRpcClient) getClient(NASA_RPC);
            client.freshBestBlock();
            return client.getBestHeight();
        }
        
        return null;
    }

    public static Long getBestHeight(ApipClient apipClient,NaSaRpcClient naSaRpcClient){

        if(naSaRpcClient!=null){
            naSaRpcClient.freshBestBlock();
            return naSaRpcClient.getBestHeight();
        }

        if(apipClient!=null){
            return apipClient.bestHeight();
        }
        return null;
    }

    private void checkDbDir(Map<String, Object> settingMap) {
        if (settingMap == null || settingMap.isEmpty() || settingMap.get(DB_DIR) == null) {
            if (context != null) {
                this.dbDir = context.getFilesDir().getAbsolutePath() + "/db/";
            } else {
                this.dbDir = FileUtils.getUserDir() + "/com/fc/fc_ajdk/db/";
            }
        }
        FileUtils.checkDirOrMakeIt(this.dbDir);
    }

    public static String makeSettingsFileName(@Nullable String fid,@Nullable String sid){
        return FileUtils.makeFileName(fid, sid, SETTINGS, DOT_JSON);
    }

    public void initiateServer(String sid, byte[] symkey, Configure config, List<String> apiList){
        if(clientGroups==null)clientGroups = new HashMap<>();
        if(this.config==null)this.config = config;
        this.apiList = apiList;
        this.symkey = symkey;

        System.out.println("Initiating server settings...");

        br = config.getBr();

//        if(sid==null)checkSetting(br);

        mainFid = config.getServiceDealer(sid,symkey);
        myPrikeyCipher = config.getMainCidInfoMap().get(mainFid).getPrikeyCipher();

        initModels();

        if(service==null){
            System.out.println("Failed to load service information");
            return;
        }
        setMyKeys(symkey, config);

        saveServerSettings(service.getId());

        runAutoTasks();

        Configure.saveConfig();
    }

    private void setMyKeys(byte[] symkey, Configure config) {
        if(mainFid==null)return;

        Map<String, KeyInfo> mainCidInfoMap = config.getMainCidInfoMap();
        if(mainCidInfoMap==null)mainCidInfoMap = new HashMap<>();

        KeyInfo keyInfo = mainCidInfoMap.get(mainFid);

        if(keyInfo ==null){
            System.out.println("The mainFid is new.");
            ApipClient apipClient = (ApipClient)getClient(APIP);
            if(apipClient!=null) {
                Cid cid = apipClient.cidInfoById(mainFid);
                if(cid!=null){
                    if(askIfYes(br,"Import the priKey of "+mainFid+". Enter to set it watch-only:")){
                        byte[] priKey = com.fc.fc_ajdk.core.fch.Inputer.importOrCreatePrikey(br);
                        if(priKey!=null)
                            myPrikeyCipher = Encryptor.encryptBySymkeyToJson(priKey,symkey);
                        this.myPubkey = cid.getPubkey();
                        keyInfo = KeyInfo.fromCid(cid,myPrikeyCipher);
                        mainCidInfoMap.put(mainFid, keyInfo);
                        saveConfig();
                        return;
                    }
                }

                keyInfo = new KeyInfo();
                keyInfo.setId(mainFid);
                keyInfo.setWatchOnly(true);
                if(askIfYes(br,"Input the pubkey?")){
                    String pubkey = KeyTools.inputPubkey(br);
                    keyInfo.setPubkey(pubkey);
                    myPubkey = pubkey;
                }
                mainCidInfoMap.put(mainFid, keyInfo);
                return;
            }
        }else {
            myPrikeyCipher = keyInfo.getPrikeyCipher();
            myPubkey = keyInfo.getPubkey();
        }
    }

    public void initiateMuteServer(String serverName, byte[] symkey, Configure config){
        if(clientGroups==null)clientGroups = new HashMap<>();
        if(this.config==null)this.config = config;
        this.symkey = symkey;

        System.out.println("Initiating mute server settings...");

        initModels();
        saveServerSettings(serverName);
        runAutoTasks();
        Configure.saveConfig();
    }

    public void initiateClient(String fid, String clientName, byte[] symkey, Configure config, BufferedReader br){
        System.out.println("Initiating Client settings...");
        this.br= br;
        this.symkey = symkey;
        this.mainFid = fid;
        this.myPrikeyCipher = config.getMainCidInfoMap().get(fid).getPrikeyCipher();
        if(this.config==null)this.config = config;

        if(clientGroups==null)clientGroups = new HashMap<>();

        freeApiListMap = config.getFreeApiListMap();
        if(bestHeightMap==null)
            bestHeightMap=new HashMap<>();

        initModels();

        clientDataFileName = FileUtils.makeFileName(mainFid,clientName,DATA,DOT_JSON);

        setMyKeys(symkey, config);

        saveClientSettings(mainFid,clientName);
        runAutoTasks();
        Configure.saveConfig();
    }

    public void initiateTool(String toolName, byte[] symkey, Configure config, BufferedReader br){
        System.out.println("Initiating Client settings...");

        this.config = config;
        this.symkey = symkey;

        if(clientGroups==null) clientGroups = new HashMap<>();
        this.br= br;
        freeApiListMap = config.getFreeApiListMap();

        initModels();

        Configure.saveConfig();

        saveToolSettings(toolName);
    }

    public void closeMenu() {
        return;
    }

    public void close() {
        try {
            // Close all handlers first to ensure LevelDB instances are properly closed
            if (handlers != null) {
                TimberLogger.i(TAG,"Closing {} handlers...", handlers.size());
                for (Handler<?> handler : handlers.values()) {
                    if (handler != null) {
                        try {
                            TimberLogger.d(TAG,"Closing handler: {}", handler.getHandlerType());
                            handler.close();
                        } catch (Exception e) {
                            TimberLogger.e(TAG,"Error closing handler {}: {}", handler.getHandlerType(), e.getMessage(), e);
                        }
                    }
                }
                handlers.clear();
            }
            
            // Stop all auto tasks
            if (autoTaskList != null && !autoTaskList.isEmpty()) {
                TimberLogger.i(TAG,"Stopping {} auto tasks...", autoTaskList.size());
                AutoTask.stopAllTasks();
            }
            
            // Close all clients in all groups
            if (clientGroups != null) {
                TimberLogger.i(TAG,"Closing client groups...");
                for (ClientGroup group : clientGroups.values()) {
                    for (Object client : group.getClientMap().values()) {
                        try {
                            switch (group.getGroupType()) {
                                case NASA_RPC -> {
                                    // Nothing to close
                                }
                                default -> {
                                    if(client instanceof FcClient fcClient1){
                                        TimberLogger.d(TAG,"Closing client: {}", fcClient1.getClass().getSimpleName());
                                        fcClient1.close();
                                    }
                                }
                            }
                        } catch (Exception e) {
                            TimberLogger.e(TAG,"Error closing client in group {}: {}", group.getGroupType(), e.getMessage(), e);
                        }
                    }
                }
            }
            
            // Clear sensitive data
            BytesUtils.clearByteArray(symkey);
            
            TimberLogger.i(TAG,"Settings closed successfully");
        } catch (Exception e) {
            TimberLogger.e(TAG,"Error during settings cleanup: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to close settings", e);
        }
    }

    @Nullable
    public Service loadMyService(String sid, byte[] symkey, Configure config) {
        Class<? extends Params> paramClass =
                switch (serverType){
                    case APIP -> ApipParams.class;
                    case DISK -> DiskParams.class;
                    case TALK -> TalkParams.class;
                    case OTHER, MAP, SWAP_HALL -> Params.class;
                    default -> null;
                };

        ApipClient apipClient = (ApipClient) getClient(APIP);
        if(apipClient!=null) {
            service = getMyService(sid, symkey, config, br, apipClient, paramClass, this.serverType);
        }

        if(service==null){
            System.out.println("Failed to get service.");
            return null;
        }

        if (isWrongServiceType(service, serverType.name())) return null;

        System.out.println("\nYour service:\n"+ JsonUtils.toNiceJson(service));
        Menu.anyKeyToContinue(br);

        this.sid = service.getId();
        return service;
    }

    public Object getClient(Service.ServiceType serviceType) {
        ClientGroup group = clientGroups.get(Service.ServiceType.valueOf(serviceType.name()));
        return group != null ? group.getClient() : null;
    }

    public ApiAccount getApiAccount(Service.ServiceType serviceType) {
        ClientGroup group = clientGroups.get(Service.ServiceType.valueOf(serviceType.name()));
        if (group != null && !group.getAccountIds().isEmpty()) {
            String accountId = group.getAccountIds().get(0);
            return config.getApiAccountMap().get(accountId);
        }
        return null;
    }

    public String getApiAccountId(Service.ServiceType serviceType) {
        ClientGroup group = clientGroups.get(Service.ServiceType.valueOf(serviceType.name()));
        if (group != null && !group.getAccountIds().isEmpty()) {
            return group.getAccountIds().get(0); // Returns first account ID by default
        }
        return null;
    }


    public static ApipClient getFreeApipClient(){
        return getFreeApipClient(null);
    }
    public static ApipClient getFreeApipClient(BufferedReader br){
        ApipClient apipClient = new ApipClient();
        ApiAccount apipAccount = new ApiAccount();

        List<FreeApi> freeApiList = freeApiListMap.get(APIP);

        for(FreeApi freeApi : freeApiList){
            apipAccount.setApiUrl(freeApi.getUrlHead());
            apipClient.setApiAccount(apipAccount);
            apipClient.setUrlHead(freeApi.getUrlHead());
            try {
                if ((boolean) apipClient.ping(VERSION_1, RequestMethod.GET, AuthType.FREE, APIP))
                    return apipClient;
            }catch (Exception ignore){};
        }
        if(br !=null) {
            if (askIfYes(br, "Failed to get free APIP service. Add new?")) {
                do {
                    String url = com.fc.fc_ajdk.core.fch.Inputer.inputString(br, "Input the urlHead of the APIP service:");
                    apipAccount.setApiUrl(url);
                    apipClient.setApiAccount(apipAccount);
                    if ((boolean) apipClient.ping(VERSION_1, RequestMethod.GET,AuthType.FREE, APIP)) {
                        FreeApi freeApi = new FreeApi(url,true, APIP);
                        freeApiList.add(freeApi);
                        return apipClient;
                    }
                } while (askIfYes(br, "Failed to ping this APIP Service. Try more?"));
            }
        }
        return null;
    }


    public static String getLocalDataDir(String sid, Context context) {
        if (context != null) {
            return context.getFilesDir().getAbsolutePath() + "/" + addSidBriefToName(sid, DATA) + "/";
        }
        return System.getProperty("user.dir") + "/" + addSidBriefToName(sid, DATA) + "/";
    }

    public static String addSidBriefToName(String sid, String name) {
        return IdNameUtils.makeKeyName(null,sid,name,true);
    }

    public static byte[] getMainFidPrikey(byte[] symkey, Settings settings) {
        Decryptor decryptor = new Decryptor();
        String mainFid = settings.getMainFid();
        String cipher = settings.getConfig().getMainCidInfoMap().get(mainFid).getPrikeyCipher();
        if(cipher==null)return null;
        CryptoDataByte result = decryptor.decryptJsonBySymkey(cipher, symkey);
        if(result.getCode()!=0){
            System.out.println("Failed to decrypt the private key of "+mainFid+".");
            return null;
        }
        return result.getData();
    }

    public Cid checkFidInfo(ApipClient apipClient, BufferedReader br) {
        Cid fidInfo = apipClient.cidInfoById(mainFid);

        if(fidInfo!=null) {
            long bestHeight = apipClient.getFcClientEvent().getResponseBody().getBestHeight();

            bestHeightMap.put(IndicesNames.CID,bestHeight);
            System.out.println("My information:\n" + JsonUtils.toNiceJson(fidInfo));
            Menu.anyKeyToContinue(br);
            if(fidInfo.getBalance()==0){
                System.out.println("No fch yet. Send some fch to "+mainFid);
                Menu.anyKeyToContinue(br);
            }
        }else{
            System.out.println("New FID. Send some fch to it: "+mainFid);
            Menu.anyKeyToContinue(br);
        }
        return fidInfo;
    }

    public void initClientGroup(Service.ServiceType groupType) {
        System.out.println("Initiate "+ groupType +" accounts and clients...");

        // Preserve existing group if it exists
        ClientGroup group;
        if(clientGroups==null) clientGroups = new HashMap<>();
        group = clientGroups.get(groupType);
        if(group!=null){
            group.connectAllClients(config, this, symkey, br);
            return;
        }

        group = new ClientGroup(groupType);
        while(true){
            ApipClient freeApipClient;
            switch (groupType){
                case NASA_RPC ->freeApipClient = null;
                default -> freeApipClient = getFreeApipClient();
            }
            ApiAccount apiAccount = config.getApiAccount(symkey, mainFid, groupType, freeApipClient);
            if(apiAccount==null){
                System.out.println(groupType+" module is ignored.");
                return;
            }
            group.addApiAccount(apiAccount);
            group.getAccountIds().add(apiAccount.getId());
            if(apiAccount.getClient()!=null)group.getClientMap().put(apiAccount.getId(),apiAccount.getClient());
            if(br !=null && !askIfYes(br,"Add more "+groupType + " account?"))break;
        }
        if(group.getAccountIds().size()>1){
            ClientGroup.GroupStrategy strategy = Inputer.chooseOne(ClientGroup.GroupStrategy.values(),null,"Chose the strategy",br);
            group.setStrategy(strategy);
        }
        clientGroups.put(groupType, group);
        if(sid==null && serverType!=null) {
            if(groupType==APIP)
                loadMyService(null, symkey, config);
        }
    }

    public Service getMyService(String sid, byte[] symkey, Configure config, BufferedReader br, ApipClient apipClient, Class<?> paramsClass, Service.ServiceType serviceType) {
        System.out.println("Get my service...");
        Service service = null;
        if(sid ==null) {
            service = askIfPublishNewService(sid, symkey, br, serviceType, apipClient);
            if(service==null) {
                String owner = Inputer.chooseOneFromList(config.getOwnerList(), null, "Choose the owner:", br);

                if (owner == null)
                    owner = config.addOwner(br);
                service = config.chooseOwnerService(owner, symkey, serviceType, apipClient);
            }
        }else {
            service = getServiceBySid(sid, apipClient, service);
        }

        if(service==null){
            service = askIfPublishNewService(sid, symkey, br, serviceType, apipClient);
            if(service==null)return null;
        }

        Params params;
        switch (serviceType) {
            case APIP -> params = (ApipParams) Params.getParamsFromService(service, paramsClass);
            case DISK -> params = (DiskParams) Params.getParamsFromService(service, paramsClass);
            case TALK -> params = (TalkParams) Params.getParamsFromService(service, paramsClass);
            case SWAP_HALL -> params = (SwapHallParams) Params.getParamsFromService(service, paramsClass);
            default -> params = (Params) Params.getParamsFromService(service, paramsClass);
        }
        if (params == null) return service;
        service.setParams(params);
        this.sid = service.getId();
        this.mainFid = params.getDealer();
        if(config.getMainCidInfoMap().get(mainFid)==null) {
            System.out.println("The dealer of "+mainFid+" is new...");
            config.addUser(mainFid, symkey);
        }

        config.getMyServiceMaskMap().put(service.getId(),ServiceMask.ServiceToMask(service,this.mainFid));
        saveConfig();
        return service;
    }

    public static void publishService(ApipClient apipClient, byte[] symkey, BufferedReader br) {
        System.out.println("Publish service services...");

        if (Menu.askIfToDo("Get the OpReturn text to publish a new service service?", br)) return;

        Feip dataOnChain = setFcInfoForService();

        ServiceOpData data = new ServiceOpData();

        data.setOp(OpNames.PUBLISH);

        data.inputTypes(br);

        if(symkey!=null) {
            data.inputServiceHead(br, symkey,apipClient );
        }
        else data.inputServiceHead(br);

        System.out.println("Set the service parameters...");
        Service.ServiceType type = Inputer.chooseOne(Service.ServiceType.values(), null, "Choose the type of your service:", br);
        Params serviceParams = inputParams(symkey, br,type);

        data.setParams(serviceParams);

        dataOnChain.setData(data);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Shower.printUnderline(10);
        String opReturnJson = gson.toJson(dataOnChain);
        System.out.println(opReturnJson);
        Shower.printUnderline(10);
        System.out.println("Check, and edit if you want, the JSON text above. Send it in a TX by the owner of the service to freecash blockchain:");
        Menu.anyKeyToContinue(br);
    }

    protected static Params inputParams(byte[] symkey, BufferedReader br,Service.ServiceType type) {
        switch (type) {
            case APIP :
                ApipParams apipParams = new ApipParams();
                apipParams.inputParams(br, symkey, null);
                return apipParams;
            case DISK:
                DiskParams diskParams = new DiskParams();
                diskParams.inputParams(br, symkey, null);
                return diskParams;
            default: return null;
        }
    }
    private static Feip setFcInfoForService() {
        Feip dataOnChain = new Feip();
        dataOnChain.setType("FEIP");
        dataOnChain.setSn("5");
        dataOnChain.setVer("2");
        dataOnChain.setName("Service");
        return dataOnChain;
    }

    private static Service askIfPublishNewService(String sid, byte[] symkey, BufferedReader br, Service.ServiceType serviceType, ApipClient apipClient) {
        Service service = null;
        if(askIfYes(br,"Publish a new service?")) {
            publishService(apipClient,symkey,br);
            while (true){
                sid = Inputer.inputString(br,"Input the SID of the service you published:");
                if(!Hex.isHex32(sid))continue;
                service = getServiceBySid(sid, apipClient, service);
                if(service!=null)return service;
                else System.out.println("Failed to get the service with SID: "+sid);
            }
        }
        return null;
    }

    @Nullable
    private static Service getServiceBySid(String sid, ApipClient apipClient, Service service) {
        if(apipClient !=null){
            service = apipClient.serviceById(sid);
        }
        return service;
    }

    public static boolean isWrongServiceType(Service service, String type) {
        if(!StringUtils.isContainCaseInsensitive(service.getTypes(), type)) {
            System.out.println("\nWrong service type:"+ Arrays.toString(service.getTypes()));
            return true;
        }
        return false;
    }

    public String chooseFid(Configure config, BufferedReader br, byte[] symkey) {
        String fid = com.fc.fc_ajdk.core.fch.Inputer.chooseOne(config.getMainCidInfoMap().keySet().toArray(new String[0]), null, "Choose fid:",br);
        if(fid==null)fid =config.addUser(symkey);
        return fid;
    }

    public void writeToFile(String fid, String oid){
        fileName = FileUtils.makeFileName(fid, oid, SETTINGS, DOT_JSON);
        JsonUtils.writeObjectToJsonFile(this,Configure.getConfDir(),fileName,false);
    }

    public void saveServerSettings(String sid) {
        if (context != null) {
            // Android environment - use SharedPreferences
            String fileName = FileUtils.makeFileName(null, sid, SETTINGS, DOT_JSON);
            SharedPreferences prefs = context.getSharedPreferences(SETTINGS + "_" + fileName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(SETTINGS, JsonUtils.toJson(this));
            editor.apply();
        } else {
            // Command line environment - use file system
            writeToFile(null, sid);
        }
    
    }

    public void saveToolSettings(String clientName){
        writeToFile(null, clientName);
    }

    public void saveClientSettings(String fid, String clientName) {
        if (context != null) {
            // Android environment - use SharedPreferences
            String fileName = FileUtils.makeFileName(fid, clientName, SETTINGS, DOT_JSON);
            SharedPreferences prefs = context.getSharedPreferences(SETTINGS + "_" + fileName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(SETTINGS, JsonUtils.toJson(this));
            editor.apply();
        } else {
            // Command line environment - use file system
            writeToFile(fid, clientName);
        }

    }

    public void saveSimpleSettings(String clientName){
        writeToFile(null, clientName);
    }

    public void setting(BufferedReader br, @Nullable Service.ServiceType serviceTypeOnlyForServer) {
        System.out.println("Setting...");
        Menu menu = new Menu("Setting",this::closeMenu);
        menu.setTitle("Settings");
        ApipClient apipClient = (ApipClient) getClient(APIP);
        menu.add("Reset password", () -> resetPassword(serviceTypeOnlyForServer));
        menu.add("Add API provider", () -> config.addApiProviderAndConnect(symkey, null, apipClient));
        menu.add("Add API account", () -> config.addApiAccount(null, symkey, apipClient));
        menu.add("Update API provider", () -> config.updateApiProvider(apipClient));
        menu.add("Update API account", () -> config.updateApiAccount(config.chooseApiProviderOrAdd(config.getApiProviderMap(), apipClient), symkey, apipClient));
        menu.add("Delete API provider", () -> config.deleteApiProvider(symkey, apipClient));
        menu.add("Delete API account", () -> config.deleteApiAccount(symkey, apipClient));
        menu.add("Reset default APIs", () -> resetApi(symkey, apipClient));
        menu.add("Check settings", () -> checkSetting(br));
        menu.add("Show my priKey", () -> dumpPrikey(br));
        menu.add("Remove me", () -> removeMe(br));

        menu.showAndSelect(br);
    }

    private void dumpPrikey(BufferedReader br) {
        if(askIfYes(br,"Never leak your priKey. Continue dumping priKey?")){
            if(askIfYes(br,"Do you ensure you circumstance is safe?")){
                byte[] priKey = Decryptor.decryptPrikey(getMyPrikeyCipher(), symkey);
                if (priKey == null) {
                    System.out.println("Failed to decrypt your priKey.");
                    return;
                }
                String hex = Hex.toHex(priKey);
                String base58 = KeyTools.prikey32To38WifCompressed(hex);
                if(!askIfYes(br,"Encrypt priKey with random password?")) {
                    System.out.println("Prikey cipher of "+mainFid+":");
                    System.out.println("Hex:\n" + hex);
                    QRCodeUtils.generateQRCode(hex);

                    System.out.println("Base58check:\n" + base58);
                    QRCodeUtils.generateQRCode(base58);
                    Menu.anyKeyToContinue(br);
                }else{
                    System.out.println("Prikey of "+mainFid+":");
                    byte[] random = BytesUtils.getRandomBytes(6);
                    CryptoDataByte cryptoDataByte = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptByPassword(priKey, Base58.encode(random).toCharArray());
                    if(cryptoDataByte.getCode()==0 && cryptoDataByte.getCipher()!=null) {
                        String cipher = cryptoDataByte.toNiceJson();
                        String password = Base58.encode(random);
                        System.out.println("Prikey Cipher:\n"+cipher);
                        QRCodeUtils.generateQRCode(cipher);
                        System.out.println("password: "+password);
                        QRCodeUtils.generateQRCode(password);
                        System.out.println("""
                                IMPORTANT:\s
                                \t1) Both cipher and password are required to recover your priKey!!!
                                \t2) The password is still weak for professional hacking.\s""");
                        Menu.anyKeyToContinue(br);
                    }
                }
            }
        }
    }


    public void resetPassword(@Nullable Service.ServiceType serviceType,BufferedReader br) {
        byte[] newSymkey = resetPassword(serviceType);
        if (newSymkey != null) {
            this.symkey = newSymkey;
        }
    }

    public void removeMe(BufferedReader br) {
        System.out.println("Removing user...");
        
        // Get the password to confirm deletion
        if (!checkPassword(br, symkey, config)) {
            System.out.println("Wrong password. Removal cancelled.");
            return;
        }

        // Remove from configureMap
        if(!Inputer.askIfYes(br, "Are you sure you want to remove "+mainFid+"? \nThis action cannot be undone."))
            return;

        config.getMainCidInfoMap().remove(mainFid);
        
        if(Inputer.askIfYes(br, "Remove from the owner list?")) {
            config.getOwnerList().remove(mainFid);
        }

        for(ApiAccount apiAccount : config.getApiAccountMap().values()) {
            if(apiAccount.getUserId()!=null && apiAccount.getUserId().equals(mainFid)) {
                config.getApiAccountMap().remove(apiAccount.getId());
            }
        }

        for(ServiceMask serviceMask : config.getMyServiceMaskMap().values()) {
            if(serviceMask.getDealer().equals(mainFid)) {
                config.getMyServiceMaskMap().remove(serviceMask.getId());
            }
        }
        
        saveConfig();

        // Delete settings file
        String midName=null;
        if((clientName!=null) && (!clientName.isEmpty())) midName = clientName;
        else midName = sid;
        String fileName = FileUtils.makeFileName(mainFid,midName , SETTINGS, DOT_JSON);

        String settingsPath = getConfDir() + File.separator + fileName;
        File settingsFile = new File(settingsPath);
        if (settingsFile.exists()) {
            if (settingsFile.delete()) {
                System.out.println("Settings file deleted successfully.");
            } else {
                System.out.println("Failed to delete settings file.");
            }
        }

        System.out.println("User is deleted successfully.");
    }


    /**
     * Adds a shutdown hook to ensure resources are cleaned up even in unexpected shutdowns.
     * This includes stopping auto tasks, closing clients, and clearing sensitive data.
     */
    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                // Stop all auto tasks
                if (this.autoTaskList != null && !this.autoTaskList.isEmpty()) {
                    AutoTask.stopAllTasks();
                }

                // Close all clients in all groups
                if (clientGroups != null) {
                    for (ClientGroup group : clientGroups.values()) {
                        for (Object client : group.getClientMap().values()) {
                            try {
                                switch (group.getGroupType()) {
                                    case NASA_RPC -> {
                                        // Nothing to close
                                    }
                                    default -> {
                                        if (client instanceof FcClient fcClient1) {
                                            fcClient1.close();
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                TimberLogger.e(TAG,"Error closing client in shutdown hook: {}", e.getMessage());
                            }
                        }
                    }
                }

                // Clear sensitive data
                if (symkey != null) {
                    BytesUtils.clearByteArray(symkey);
                }

                TimberLogger.i(TAG,"Shutdown hook completed");
            } catch (Exception e) {
                TimberLogger.e(TAG,"Error in shutdown hook: {}", e.getMessage());
            }
        }));
    }

    public byte[] resetPassword(@Nullable Service.ServiceType serviceType){
        System.out.println("Reset password...");
        byte[] oldSymkey;
        byte[] oldNonceBytes;
        byte[] oldPasswordBytes;
        String oldNonce;
        String oldPasswordName;

        while(true) {
            oldPasswordBytes = Inputer.getPasswordBytes(br);
            oldPasswordName = IdNameUtils.makePasswordHashName(oldPasswordBytes);
            if(oldPasswordName.equals(config.getPasswordName()))break;
            System.out.println("Password wrong. Try again.");
        }

        oldNonce =config.getNonce();
        oldNonceBytes = Hex.fromHex(oldNonce);
        oldSymkey = getSymkeyFromPasswordAndNonce(oldPasswordBytes, oldNonceBytes);//Hash.sha256x2(BytesTools.bytesMerger(oldPasswordBytes, oldNonceBytes));

        byte[] newPasswordBytes = Inputer.resetNewPassword(br);
        if(newPasswordBytes==null)return null;
        String newPasswordName = IdNameUtils.makePasswordHashName(newPasswordBytes);
        config.setPasswordName(newPasswordName);
        byte[] newNonce = BytesUtils.getRandomBytes(16);
        config.setNonce(Hex.toHex(newNonce));
        byte[] newSymkey =  getSymkeyFromPasswordAndNonce(newPasswordBytes, newNonce);


        if(config.getApiAccountMap()==null||config.getApiAccountMap().isEmpty())return newSymkey;
        for(ApiAccount apiAccount : config.getApiAccountMap().values()){
            if(apiAccount.getPasswordCipher()!=null){
                String cipher = apiAccount.getPasswordCipher();
                String newCipher = replaceCipher(cipher,oldSymkey,newSymkey);
                apiAccount.setPasswordCipher(newCipher);
            }
            if(apiAccount.getUserPrikeyCipher()!=null){
                String cipher = apiAccount.getUserPrikeyCipher();
                String newCipher = replaceCipher(cipher,oldSymkey,newSymkey);
                apiAccount.setUserPrikeyCipher(newCipher);
                apiAccount.setUserPubkey(ApiAccount.makePubkey(newCipher,newSymkey));
            }
            if(apiAccount.getSession()!=null && apiAccount.getSession().getKeyCipher()!=null){
                String cipher = apiAccount.getSession().getKeyCipher();
                String newCipher = replaceCipher(cipher,oldSymkey,newSymkey);
                apiAccount.getSession().setKeyCipher(newCipher);
            }
        }

        configureMap.put(config.getPasswordName(),config);
        configureMap.remove(oldPasswordName);

        Configure.saveConfig();

        BytesUtils.clearByteArray(oldPasswordBytes);
        BytesUtils.clearByteArray(newPasswordBytes);
        BytesUtils.clearByteArray(oldSymkey);
        Menu.anyKeyToContinue(br);
        return newSymkey;
    }

    private String replaceCipher(String oldCipher, byte[] oldSymkey, byte[] newSymkey) {
        byte[] data = new Decryptor().decryptJsonBySymkey(oldCipher, oldSymkey).getData();
        return new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptBySymkey(data,newSymkey).toJson();
    }

    public void resetApi(byte[] symkey, ApipClient apipClient) {
        System.out.println("Reset default API service...");
        List<Service.ServiceType> requiredBasicServices = new ArrayList<>();
        for(Object model: modules){
            if(model instanceof String str){
                try{
                    requiredBasicServices.add(Service.ServiceType.fromString(str));
                }catch (Exception ignore){}
            }else if(model instanceof Service.ServiceType type){
                requiredBasicServices.add(type);
            }
        }
        Service.ServiceType type = Inputer.chooseOne(requiredBasicServices.toArray(new Service.ServiceType[0]),null,"Choose the Service:",br);

        ApiProvider apiProvider = config.chooseApiProviderOrAdd(config.getApiProviderMap(), type, apipClient);
        if(apiProvider==null)return;
        ApiAccount apiAccount = config.findAccountForTheProvider(apiProvider, mainFid, symkey, apipClient);
        if (apiAccount != null) {
            Object client = apiAccount.getClient();//connectApi(config.getApiProviderMap().get(apiAccount.getProviderId()), symkey, br, null, config.getMainCidInfoMap());
            if (client != null) {
                System.out.println("Done.");
            } else System.out.println("Failed to connect the apiAccount: " + apiAccount.getApiUrl());
        } else {
            System.out.println("Failed to get the apiAccount.");
            return;
        }
        addAccountToGroup(type,apiAccount);
        saveConfig();
        saveSettings();
    }

    private void saveSettings() {
        if(this.sid!=null)saveServerSettings(sid);
        else if(this.mainFid!=null)saveClientSettings(mainFid,clientName);
        else saveSimpleSettings(clientName);
    }

//    private void freshAliasMaps(String alias, ApiAccount apiAccount) {
//        aliasAccountMap.put(alias, apiAccount);
//        aliasAccountIdMap.put(alias, apiAccount.getId());
//        aliasClientMap.put(alias, apiAccount.getClient());
//    }

    private void addAccountToGroup(Service.ServiceType type, ApiAccount apiAccount){
        ClientGroup clientGroup = clientGroups.get(type);
        if(clientGroup==null)clientGroup= new ClientGroup(type);
        ClientGroup.GroupStrategy strategy = clientGroup.getStrategy();
        switch (strategy) {
            case USE_FIRST:
                clientGroup.addToFirstClient(apiAccount.getId(),apiAccount.getClient());
                break;
            case USE_ANY_VALID:

            case USE_ALL:

            case USE_ONE_RANDOM:

            case USE_ONE_ROUND_ROBIN:

            default:
                clientGroup.addClient(apiAccount.getId(),apiAccount.getClient());
                break;
        }
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public Configure getConfig() {
        return config;
    }

    public void setConfig(Configure config) {
        this.config = config;
    }

    public BufferedReader getBr() {
        return br;
    }

    public void setBr(BufferedReader br) {
        this.br = br;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName1) {
        fileName = fileName1;
    }


    public String getMainFid() {
        return mainFid;
    }

    public void setMainFid(String mainFid) {
        this.mainFid = mainFid;
    }


    @NotNull
    public static String makeWebhookListenPath(String sid, String methodName) {
        return System.getProperty(UserDir) + "/" + Settings.addSidBriefToName(sid, methodName);
    }

    public void checkSetting(BufferedReader br) {
        if(settingMap==null){
            settingMap = new HashMap<>();
        }

        for(String key: settingMap.keySet()) {
            Class<?> valueClass = settingMap.get(key).getClass();
            if (valueClass.equals(String.class)) {
                settingMap.put(key,Inputer.inputString(br, key,(String) settingMap.get(key)));
            } else if (valueClass.equals(Long.class)||valueClass.equals(long.class)) {
                settingMap.put(key,Inputer.inputLong(br, key,(Long) settingMap.get(key)));
            } else if(valueClass.equals(Double.class)||valueClass.equals(double.class)){
                settingMap.put(key,Inputer.inputDouble(br, key,(Double) settingMap.get(key)));
            } else if(valueClass.equals(float.class)||valueClass.equals(Float.class)){
                settingMap.put(key,Inputer.inputFloat(br, key,(Float) settingMap.get(key)));
            }else if(valueClass.equals(boolean.class)||valueClass.equals(Boolean.class)){
                settingMap.put(key,Inputer.inputBoolean(br, key,(Boolean) settingMap.get(key)));
            }
        }

        if(autoTaskList!=null){
            for(AutoTask autoTask : autoTaskList){
                autoTask.checkTrigger(br);
            }
        }
    }

    public Map<String, Long> getBestHeightMap() {
        return bestHeightMap;
    }

    public void setBestHeightMap(Map<String, Long> bestHeightMap) {
        this.bestHeightMap = bestHeightMap;
    }

    public List<ApiAccount> getPaidAccountList() {
        return paidAccountList;
    }

    public void setPaidAccountList(List<ApiAccount> paidAccountList) {
        this.paidAccountList = paidAccountList;
    }



    public byte[] getSymkey() {
        return symkey;
    }

    public void setSymkey(byte[] symkey) {
        this.symkey = symkey;
    }

//    public Service.ServiceType[] getRequiredBasicServices() {
//        return requiredBasicServices;
//    }
//
//    public void setRequiredBasicServices(Service.ServiceType[] requiredBasicServices) {
//        this.requiredBasicServices = requiredBasicServices;
//    }


    public Service.ServiceType getServerType() {
        return serverType;
    }

    public void setServerType(Service.ServiceType serverType) {
        this.serverType = serverType;
    }

    public static Map<Service.ServiceType, List<FreeApi>> getFreeApiListMap() {
        return freeApiListMap;
    }

    public static void setFreeApiListMap(Map<Service.ServiceType, List<FreeApi>> freeApiListMap) {
        Settings.freeApiListMap = freeApiListMap;
    }

    public Map<String, Object> getSettingMap() {
        return settingMap;
    }

    public void setSettingMap(Map<String, Object> settingMap) {
        this.settingMap = settingMap;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public String getClientDataFileName() {
        return clientDataFileName;
    }

    public void setClientDataFileName(String clientDataFileName) {
        this.clientDataFileName = clientDataFileName;
    }

    public String getMyPubkey() {
        return myPubkey;
    }

    public void setMyPubkey(String myPubkey) {
        this.myPubkey = myPubkey;
    }

    public String getMyPrikeyCipher() {
        return myPrikeyCipher;
    }

    public void setMyPrikeyCipher(String myPrikeyCipher) {
        this.myPrikeyCipher = myPrikeyCipher;
    }
//
//    public Map<String, KeyInfo> getWatchFidMap() {
//        return watchFidMap;
//    }
//
//    public void setWatchFidMap(Map<String, KeyInfo> watchFidMap) {
//        this.watchFidMap = watchFidMap;
//    }
//
//    public String addWatchingFids(BufferedReader br2, ApipClient apipClient, String clientName) {
//        if(watchFidMap == null) watchFidMap = new HashMap<>();
//        String fid = Inputer.inputFid(br2, "Input the watching FID:");
//        if(fid == null) return null;
//        String pubkey = null;
//        if(apipClient != null) {
//            pubkey = apipClient.getPubkey(fid, RequestMethod.POST, AuthType.FC_SIGN_BODY);
//        }
//        if(pubkey == null) pubkey = Inputer.inputString(br2, "Input the watching FID's public key:", null);
//        KeyInfo keyInfo = new KeyInfo(null,pubkey);
//        watchFidMap.put(fid, keyInfo);
//        saveClientSettings(this.mainFid,clientName);
//        return fid;
//    }

//    private void initializeClientGroups() {
//        if (serviceTypeList != null) {
//            for (Service.ServiceType groupType : serviceTypeList) {
//                clientGroups.put(groupType, new ClientGroup(groupType));
//            }
//        }
//    }
//
//    public void setClientGroupTypeList(List<Service.ServiceType> types) {
//        serviceTypeList = types;
//    }

    public ClientGroup getClientGroup(Service.ServiceType type) {
        return clientGroups.get(type);
    }

    public void addClientToGroup(Service.ServiceType type, String apiAccountId, Object client) {
        ClientGroup group = clientGroups.get(type);
        if (group != null) {
            group.addClient(apiAccountId, client);
        }
    }


    public void initModulesMute() {
        if(clientGroups==null)clientGroups = new HashMap<>();
        if(handlers==null)handlers = new HashMap<>();
        int total = modules.length;
        System.out.println("\nThere are "+ total +" modules to be loaded.");
        int count = 1;
        for (Object model : modules) {
            System.out.println("Loading module "+(count++) + "/"+ total +"...");
            if (model instanceof String strModel) {
                try {
                    // Try to parse as ServiceType
                    Service.ServiceType serviceType = Service.ServiceType.valueOf(strModel);
                    initClientGroupMute(serviceType);
                } catch (IllegalArgumentException e) {
                    // Not a ServiceType, try HandlerType
                    Handler.HandlerType handlerType = Handler.HandlerType.valueOf(strModel);
                    try {
                        initHandler(handlerType);
                    }catch (Exception e1){
                        e1.printStackTrace();
                    }
                }
            } else if (model instanceof Service.ServiceType type) {
                initClientGroupMute(type);
            } else if (model instanceof Handler.HandlerType type) {
                initHandler(type);
            }
        }
        System.out.println("Nice! All the "+ total +" modules are loaded.\n");
    }

    public void initModels() {
        if(modules==null)return;
        if(clientGroups==null)clientGroups = new HashMap<>();
        handlers = new HashMap<>();
        int total = modules.length;
        System.out.println("\nThere are "+ total +" modules to be loaded.");
        int count = 1;
        for (Object model : modules) {
            System.out.println("Load module "+(count++) + "/"+ total +"...");
            if (model instanceof String strModel) {
                try {
                    // Try to parse as ServiceType
                    try {
                        Service.ServiceType type = Service.ServiceType.valueOf(strModel);
                        initClientGroup(type);
                        continue;
                    } catch (IllegalArgumentException ignored) {
                        // Not a ServiceType, try HandlerType
                    }
                    
                    // Try to parse as HandlerType
                    Handler.HandlerType type = Handler.HandlerType.valueOf(strModel);
                    initHandler(type);
                } catch (IllegalArgumentException e) {
                    TimberLogger.w(TAG,"Unknown module type: " + strModel);
                }
            } else if (model instanceof Service.ServiceType type) {
                initClientGroup(type);
            } else if (model instanceof Handler.HandlerType type) {
                initHandler(type);
            }
        }
        System.out.println("All the "+ total +" modules are loaded.\n");
    }


    public Handler<?> initHandler(Handler.HandlerType type) {
        if(handlers==null)handlers = new HashMap<>();
        Handler<?> handler = handlers.get(type);
        if(handler!=null)return handler;

        switch (type) {
            case CID -> handlers.put(type, new CidHandler(this));
            case CASH -> handlers.put(type, new CashHandler(this));
            case SESSION -> handlers.put(type, new SessionHandler(this));
            case NONCE -> handlers.put(type, new NonceHandler(this));
            case MAIL -> handlers.put(type, new MailHandler(this));
            case CONTACT -> handlers.put(type, new ContactHandler(this));
            case GROUP -> handlers.put(type, new GroupHandler(this));
            case TEAM -> handlers.put(type, new TeamHandler(this));
            case HAT -> handlers.put(type, new HatHandler(this));
            case DISK -> handlers.put(type, new DiskHandler(this));
            case TALK_ID -> handlers.put(type, new TalkIdHandler(this));
            case TALK_UNIT -> handlers.put(type, new TalkUnitHandler(this));
            case ACCOUNT -> handlers.put(type, new AccountHandler(this));
            case SECRET -> handlers.put(type,new SecretHandler(this));
            case MEMPOOL -> handlers.put(type,new MempoolHandler(this));
            default -> throw new IllegalArgumentException("Unexpected handler type: " + type);
        }
        System.out.println(type + " handler initiated.\n");
        return handlers.get(type);
    }

    public Handler<?> getHandler(Handler.HandlerType type) {
        return handlers != null ? handlers.get(type) : null;
    }


    public Map<Service.ServiceType, ClientGroup> getClientGroups() {
        return clientGroups;
    }

    public void setClientGroups(Map<Service.ServiceType, ClientGroup> clientGroups) {
        this.clientGroups = clientGroups;
    }

    public Map<Handler.HandlerType, Handler<?>> getHandlers() {
        return handlers;
    }

    public void addHandler(Handler<?> handler){
        if(this.getHandlers()==null)this.handlers = new HashMap<>();
        this.handlers.put(handler.getHandlerType(),handler);
    }

    public void setHandlers(Map<Handler.HandlerType, Handler<?>> handlers) {
        this.handlers = handlers;
    }

    public List<String> getApiList() {
        return apiList;
    }

    public void setApiList(List<String> apiList) {
        this.apiList = apiList;
    }

    public String getDbDir() {
        return dbDir;
    }

    public void setDbDir(String dbDir) {
        this.dbDir = dbDir;
    }

    public Object[] getModules() {
        return modules;
    }

    public void setModules(Object[] modules) {
        this.modules = modules;
    }

    public void runAutoTasks() {
        if(autoTaskList==null)return;
        AutoTask.runAutoTask(autoTaskList, this);
    }

    /**
     * Initialize client group for web server without user interaction
     */
    public Object initClientGroupMute(Service.ServiceType type) {
        System.out.println("Initiate "+ type +" accounts and clients for server...");

        // Preserve existing group if it exists
        ClientGroup group;
        if(clientGroups==null) {
            TimberLogger.w(TAG,"Client groups are not initialized");
            return null;
        };
        group = clientGroups.get(type);
        if(group==null){
            TimberLogger.w(TAG,"Client group for "+ type +" is not initialized");
            return null;
        }

        group.connectAllClients(config, this, symkey, br);


        if(sid==null && serverType!=null) {
            if(type==APIP ) {
                loadMyService(null, symkey, config);
            }
        }
        return getClient(type);
    }

    public LocalDB.DbType getLocalDBType() {
        return localDBType;
    }

    public void setLocalDBType(LocalDB.DbType localDBType) {
        this.localDBType = localDBType;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public List<AutoTask> getAutoTaskList() {
        return autoTaskList;
    }

    public void setAutoTaskList(List<AutoTask> autoTaskList) {
        this.autoTaskList = autoTaskList;
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }
}
