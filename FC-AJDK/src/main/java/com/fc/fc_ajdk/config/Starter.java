package com.fc.fc_ajdk.config;

import static com.fc.fc_ajdk.clients.FeipClient.setCid;
import static com.fc.fc_ajdk.clients.FeipClient.setMaster;

import android.content.Context;
import java.io.BufferedReader;
import java.util.*;

import com.fc.fc_ajdk.core.fch.Inputer;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.fcData.AutoTask;
import com.fc.fc_ajdk.data.fchData.Cid;
import com.fc.fc_ajdk.clients.ApipClient;
import com.fc.fc_ajdk.data.feipData.Service;
import com.fc.fc_ajdk.clients.NaSaClient.NaSaRpcClient;
import com.fc.fc_ajdk.utils.TimberLogger;

public class Starter {

    public static Settings startClient(Context context, String clientName,
                                       Map<String, Object> settingMap, BufferedReader br, Object[] modules, List<AutoTask> autoTaskList) {
        // Load config info from the file of config.json
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure == null) return null;
        byte[] symKey = configure.getSymkey();
    
        String fid = configure.chooseMainFid(symKey);
        Settings settings;

        settings = Settings.loadSettings(fid, clientName, context);

        if(settings == null)
            settings = new Settings(configure, clientName, modules, settingMap, autoTaskList);

        // Set Android context
        settings.setContext(context);

        // Initialize clients and handlers
        settings.initiateClient(fid, clientName, symKey, configure, br);
        NaSaRpcClient naSaRpcClient;
        ApipClient apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);

        if(apipClient==null){

            naSaRpcClient = (NaSaRpcClient) settings.getClient(Service.ServiceType.NASA_RPC);
            if(naSaRpcClient==null) {
                TimberLogger.i("Failed to fresh bestHeight due to the absence of apipClient, nasaClient, and esClient.");
                return settings;
            }
            
        }
        long bestHeight = settings.getBestHeight();
        if(apipClient!=null) {
            Cid cid = settings.checkFidInfo(apipClient, br);
            
            if(cid!=null){
                String userPriKeyCipher = configure.getMainCidInfoMap().get(fid).getPrikeyCipher();
                KeyInfo keyInfo = KeyInfo.fromCid(cid, userPriKeyCipher);
                settings.getConfig().getMainCidInfoMap().put(keyInfo.getId(), keyInfo);
                Configure.saveConfig();
                if (cid.getCid() == null) {
                    if (Inputer.askIfYes(br, "No CID yet. Set CID?")) {
                        setCid(fid, userPriKeyCipher, bestHeight, symKey, apipClient, br);
                    }
                }

                if (cid.getMaster() == null) {
                    if (Inputer.askIfYes(br, "No master yet. Set master for this FID?")) {
                        setMaster(fid, userPriKeyCipher, bestHeight, symKey, apipClient, br);
                    }
                }
            }
        }

        return settings;
    }


    public static Settings startTool(Context context, String toolName,
                                     Map<String, Object> settingMap, BufferedReader br, Object[] modules, List<AutoTask> autoTaskList) {
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure == null) return null;
        byte[] symKey = configure.getSymkey();

        Settings settings = Settings.loadSettings(null, toolName, context);

        if(settings == null)
            settings = new Settings(configure,toolName, modules, settingMap, autoTaskList);

        // Set Android context
        settings.setContext(context);

        settings.initiateTool(toolName, symKey, configure, br);
        return settings;
    }

    public static Settings startServer(Context context, Service.ServiceType serverType,
                                       Map<String, Object> settingMap, List<String> apiList, Object[] modules, BufferedReader br, List<AutoTask> autoTaskList) {
        // Load config info from the file of config.json
        Configure.loadConfig(br);
        Configure configure = Configure.checkPassword(br);
        if(configure == null) return null;
        byte[] symKey = configure.getSymkey();
        while(true) {
            String sid = configure.chooseSid(serverType);

            Settings settings = null;
            if(sid!=null)settings = Settings.loadSettings(null, sid, context);
            if (settings == null) {
                settings = new Settings(configure, serverType, settingMap, modules, autoTaskList);
            }

            // Set Android context
            settings.setContext(context);

            settings.initiateServer(sid, symKey, configure,apiList);
            if(settings.getService()!=null){
                return settings;
            }
            System.out.println("Try again.");
        }
    }
    public static Settings startMuteServer(Context context, String serverName, Map<String,Object> settingMap, BufferedReader br, Object[] modules, List<AutoTask> autoTaskList) {
        Configure.loadConfig(br);

        Configure configure = Configure.checkPassword(br);
        if(configure==null)return null;
        byte[] symKey = configure.getSymkey();
        Settings settings = Settings.loadSettings(null, serverName, context);

        if (settings == null) settings = new Settings(configure, null, settingMap, modules, autoTaskList);

        // Set Android context
        settings.setContext(context);

        settings.initiateMuteServer(serverName, symKey, configure);
        return settings;
    }

}
