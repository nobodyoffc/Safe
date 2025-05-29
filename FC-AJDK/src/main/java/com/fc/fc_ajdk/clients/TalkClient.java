package com.fc.fc_ajdk.clients;


import static com.fc.fc_ajdk.data.fcData.TalkUnit.makeTalkUnit;
import static com.fc.fc_ajdk.data.fcData.TalkUnit.parseTalkUnitData;

import android.os.Looper;

import com.fc.fc_ajdk.config.ApiAccount;
import com.fc.fc_ajdk.config.ApiProvider;
import com.fc.fc_ajdk.config.Settings;
import com.fc.fc_ajdk.constants.FieldNames;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.apipData.RequestBody;
import com.fc.fc_ajdk.data.fcData.ContactDetail;
import com.fc.fc_ajdk.data.fcData.FcSession;
import com.fc.fc_ajdk.data.fcData.Op;
import com.fc.fc_ajdk.data.fcData.TalkIdInfo;
import com.fc.fc_ajdk.data.fcData.TalkUnit;
import com.fc.fc_ajdk.data.fchData.Cid;
import com.fc.fc_ajdk.data.feipData.Group;
import com.fc.fc_ajdk.data.feipData.Service;
import com.fc.fc_ajdk.data.feipData.Team;
import com.fc.fc_ajdk.handlers.*;
import com.fc.fc_ajdk.ui.Inputer;
import com.fc.fc_ajdk.ui.Menu;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.TalkUnitExecutor;
import com.fc.fc_ajdk.utils.TcpUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.fc_ajdk.utils.http.AuthType;
import com.fc.fc_ajdk.utils.http.RequestMethod;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
public class TalkClient {
    private static final String TAG = "TalkClient";
    private static final int MAX_RECONNECT_ATTEMPTS = 3;
    private static final int RECONNECT_DELAY_MS = 2000;

    private final ConcurrentLinkedQueue<TalkUnit> receivedQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<TalkUnit> sendQueue = new ConcurrentLinkedQueue<>();
    private final Map<String, Handler> handlers = new HashMap<>();
    private final Settings settings;
    private final ApiAccount apiAccount;
    private final ApiProvider apiProvider;
    private TalkUnitHandler talkUnitHandler;
    private final TalkUnitSender talkUnitSender;
    private TalkIdHandler talkIdHandler;
    private SessionHandler sessionHandler;
    private ContactHandler contactHandler;
    private GroupHandler groupHandler;
    private TeamHandler teamHandler;
    private HatHandler hatHandler;
    private CashHandler cashHandler;
    private CidHandler cidHandler;
    private MailHandler mailHandler;
    private DiskHandler diskHandler;

    private ApipClient apipClient;

    private final android.os.Handler mainHandler;
    private final ExecutorService executorService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    private FcSession serverSession;
    private byte[] sessionKey;
    private byte[] symkey;
    private String dealer;
    private String dealerPubkey;
    private String myFid;
    private byte[] myPrikey;
    public TalkClient(ApiProvider apiProvider, ApiAccount apiAccount, byte[] symkey, ApipClient apipClient, BufferedReader br) {
        this.apiAccount = apiAccount;
        this.apiProvider = apiProvider;
        this.symkey = symkey;
        this.settings = null;
        this.mainHandler = null;
        this.executorService = null;
        this.talkUnitHandler = null;
        this.talkUnitSender = null;
        this.talkIdHandler = null;
        this.sessionHandler = null;
        this.contactHandler = null;
        this.groupHandler = null;
        this.teamHandler = null;
        this.hatHandler = null;
        this.cashHandler = null;
        this.cidHandler = null;
        this.mailHandler = null;
        this.diskHandler = null;
    }
    public TalkClient(Settings settings, ApiAccount apiAccount, ApiProvider apiProvider) {
        this.settings = settings;
        this.apiAccount = apiAccount;
        this.apiProvider = apiProvider;
        this.mainHandler = new android.os.Handler(Looper.getMainLooper());
        this.executorService = Executors.newSingleThreadExecutor();

        // Initialize handlers
        this.talkUnitHandler = new TalkUnitHandler(settings);
        this.talkUnitSender = new TalkUnitSender(this);
        this.talkIdHandler = new TalkIdHandler(settings);
        this.sessionHandler = new SessionHandler(settings);
        this.contactHandler = new ContactHandler(settings);
        this.groupHandler = new GroupHandler(settings);
        this.teamHandler = new TeamHandler(settings);
        this.hatHandler = new HatHandler(settings);
        this.cashHandler = new CashHandler(settings);
        this.cidHandler = new CidHandler(settings);
        this.mailHandler = new MailHandler(settings);
        this.diskHandler = new DiskHandler(settings);

        this.apipClient = (ApipClient) settings.getClient(Service.ServiceType.APIP);

        // Add handlers to map
        handlers.put("talkUnit", talkUnitHandler);
        handlers.put("talkId", talkIdHandler);
        handlers.put("session", sessionHandler);
        handlers.put("contact", contactHandler);
        handlers.put("group", groupHandler);
        handlers.put("team", teamHandler);
        handlers.put("hat", hatHandler);
        handlers.put("cash", cashHandler);
        handlers.put("cid", cidHandler);
        handlers.put("mail", mailHandler);
        handlers.put("disk", diskHandler);
    }

    public void start() throws Exception {
        checkHandlers();
        if (running.get()) {
            throw new IllegalStateException("Client is already running");
        }

        running.set(true);
        executorService.execute(this::connectAndListen);
    }

    private void checkHandlers() {
        if(this.cidHandler == null) this.cidHandler = (CidHandler) settings.getHandler(Handler.HandlerType.CID);
        if(this.cashHandler == null) this.cashHandler = (CashHandler) settings.getHandler(Handler.HandlerType.CASH);
        if(this.sessionHandler == null) this.sessionHandler = (SessionHandler) settings.getHandler(Handler.HandlerType.SESSION);
        if(this.mailHandler == null) this.mailHandler = (MailHandler) settings.getHandler(Handler.HandlerType.MAIL);
        if(this.contactHandler == null) this.contactHandler = (ContactHandler) settings.getHandler(Handler.HandlerType.CONTACT);
        if(this.groupHandler == null) this.groupHandler = (GroupHandler) settings.getHandler(Handler.HandlerType.GROUP);
        if(this.teamHandler == null) this.teamHandler = (TeamHandler) settings.getHandler(Handler.HandlerType.TEAM);
        if(this.hatHandler == null) this.hatHandler = (HatHandler) settings.getHandler(Handler.HandlerType.HAT);
        if(this.diskHandler == null) this.diskHandler = (DiskHandler) settings.getHandler(Handler.HandlerType.DISK);
        if(this.talkIdHandler == null) this.talkIdHandler = (TalkIdHandler) settings.getHandler(Handler.HandlerType.TALK_ID);
        if(this.talkUnitHandler == null) this.talkUnitHandler = (TalkUnitHandler) settings.getHandler(Handler.HandlerType.TALK_UNIT);
    }
    public TalkIdInfo getTalkIdInfoById(String talkId) {
        if(talkId==null) return null;
        TalkIdInfo talkIdInfo = null;

        // 1. Check talkIdHandler
        talkIdInfo = talkIdHandler.get(talkId);
        if (talkIdInfo != null) return talkIdInfo;

        // 2. Check contactHandler
        ContactDetail contactDetail = contactHandler.getContact(talkId);
        if (contactDetail != null) {
            talkIdInfo = TalkIdInfo.fromContact(contactDetail);
            talkIdHandler.put(talkId, talkIdInfo);
            return talkIdInfo;
        }

        // 4. Check groupHandler
        Group group = groupHandler.getGroupInfo(talkId, apipClient);
        if (group != null) {
            talkIdInfo = TalkIdInfo.fromGroup(group);
            talkIdHandler.put(talkId, talkIdInfo);
            return talkIdInfo;
        }

        // 5. Check teamHandler
        Team team = teamHandler.getTeamInfo(talkId, apipClient);
        if (team != null) {
            talkIdInfo = TalkIdInfo.fromTeam(team);
            talkIdHandler.put(talkId, talkIdInfo);
            return talkIdInfo;
        }

        // 6. Check apipClient.cidInfoByIds as last resort
        Map<String, Cid> cidInfoMap = apipClient.cidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, talkId);
        if (cidInfoMap != null && !cidInfoMap.isEmpty()) {
            Cid cid = cidInfoMap.get(talkId);
            if (cid != null) {
                talkIdInfo = TalkIdInfo.fromCidInfo(cid);
                talkIdHandler.put(talkId, talkIdInfo);
                return talkIdInfo;
            }
        }

        return null;
    }

    private void connectAndListen() {
        try {
            connectToServer();
            listenForMessages();
        } catch (Exception e) {
            TimberLogger.e("Error in connectAndListen");
            handleConnectionError();
        }
    }

    private void connectToServer() throws IOException {
        URL url = new URL(apiProvider.getApiUrl());
        String host = url.getHost();
        int port = url.getPort() > 0 ? url.getPort() : 80;

        TimberLogger.d("Connecting to %s:%d", host, port);
        java.net.Socket socket = new java.net.Socket(host, port);
        inputStream = new DataInputStream(socket.getInputStream());
        outputStream = new DataOutputStream(socket.getOutputStream());
        TimberLogger.d("Connected to server");

        if(serverSession==null){
            serverSession = sessionHandler.getSessionByUserId(dealer);
            if(serverSession==null)
                askKey(dealer, dealer, TalkUnit.IdType.FID, (ApipClient) settings.getClient(Service.ServiceType.APIP), null);
        }
    }


    public void askKey(String whoSKey, String askWhom, TalkUnit.IdType type, ApipClient apipClient, BufferedReader br) {
        if (askWhom != null) {
            if (!KeyTools.isGoodFid(askWhom)) {
                System.out.println("Invalid FID: " + askWhom);
                return;
            }
            type = TalkUnit.IdType.FID;
        }

        if (type == null) {
            while (true) {
                String choice = Inputer.chooseOne(new String[]{TalkUnit.IdType.FID.name(), TalkUnit.IdType.TEAM.name(), TalkUnit.IdType.GROUP.name()}, null, "Which type ID you are asking session key from? ", br);
                if (choice == null) {
                    System.out.println("Canceled");
                    return;
                }
                type = TalkUnit.IdType.valueOf(choice);
                switch (type) {
                    case FID -> {
                        if (KeyTools.isGoodFid(whoSKey)) break;
                        System.out.println("It is not a FID. Try again.");
                    }
                    case TEAM, GROUP -> {
                        if (Hex.isHex32(whoSKey)) break;
                        System.out.println("It is not a team Id or group Id. Try again.");
                    }
                }
            }
        }

        System.out.println("[APP] Asking the session key of " + whoSKey);

        if (type.equals(TalkUnit.IdType.FID)) {
            // For FID type, directly ask key from the FID itself
            TalkIdInfo talkIdInfo;
            if (askWhom == null) {
                talkIdInfo = talkIdHandler.get(whoSKey);
            } else {
                talkIdInfo = talkIdHandler.get(askWhom);
            }

            if (talkIdInfo == null) {
                System.out.println("No such talk ID: " + whoSKey);
                return;
            }

            TalkUnit rawTalkUnit = new TalkUnit(myFid, new RequestBody(Op.ASK_KEY,whoSKey), TalkUnit.DataType.ENCRYPTED_REQUEST, talkIdInfo.getId(),talkIdInfo.getIdType());
            rawTalkUnit.setDataEncryptType(EncryptType.AsyTwoWay);
            TalkUnit talkUnit = makeTalkUnit(rawTalkUnit,null, myPrikey, talkIdInfo.getPubkey());
            if(talkUnit!=null){
                talkUnit.setUnitEncryptType(EncryptType.AsyTwoWay);
                boolean done = sendTalkUnit(talkUnit, sessionKey, myPrikey, dealerPubkey);
                talkUnitHandler.saveTalkUnit(rawTalkUnit, done);
                System.out.println("[APP] Asked the key of "+whoSKey+" from "+talkUnit.getTo());
            }
            return;
        }
        // Rest of the code for TEAM and GROUP types
        String targetFid = askWhom;
        TalkIdInfo talkIdInfo = null;
        while (true) {
            if (targetFid != null) {
                talkIdInfo = talkIdHandler.get(targetFid);
                if (talkIdInfo == null) {
                    System.out.println("You have not connected with " + targetFid + ". Try other.");
                    targetFid = null;
                    continue;
                }else break;
            }
            String ownerFid = null;
            if (type.equals(TalkUnit.IdType.TEAM)) {
                Team team = teamHandler.getTeamInfo(whoSKey, apipClient);
                if (team != null) {
                    ownerFid = team.getOwner();
                }
            } else if (type.equals(TalkUnit.IdType.GROUP)) {
                Group group = groupHandler.getGroupInfo(whoSKey, apipClient);
                if (group != null) {
                    ownerFid = group.getNamers()[group.getNamers().length - 1];
                }
            }

            // Show options to user
            Menu menu = new Menu();
            menu.setTitle("Ask session key from:");
            if (ownerFid != null) {
                menu.add("Owner/Namer (" + ownerFid + ")");
            }
            menu.add("Search another FID");
            menu.add("Cancel");

            int choice = menu.choose(br);

            if (choice == 1 && ownerFid != null) {
                targetFid = ownerFid;
            } else if (choice == 1 || choice == 2) {
                // Search for FID
                System.out.println("Search for FID to ask key from:");
                talkIdInfo = searchCidOrFid(apipClient, br);
                if (talkIdInfo != null) break;
            }
        }
        // If we have a target FID, create and send the ask key request
        Map<String, String> askMap = new HashMap<>();
        if (type.equals(TalkUnit.IdType.TEAM)) {
            askMap.put(FieldNames.TID, whoSKey);
        } else if (type.equals(TalkUnit.IdType.GROUP)) {
            askMap.put(FieldNames.GID, whoSKey);
        }

        RequestBody requestBody = new RequestBody();
        requestBody.setOp(Op.ASK_KEY);
        requestBody.setData(askMap);
        TalkUnit rawTalkUnit = new TalkUnit(myFid, requestBody, TalkUnit.DataType.ENCRYPTED_REQUEST, talkIdInfo.getId(),talkIdInfo.getIdType());
        TalkUnit talkUnit = makeTalkUnit(rawTalkUnit, null, myPrikey, talkIdInfo.getPubkey());
        if (talkUnit == null) return;
        boolean done = sendTalkUnit(talkUnit, sessionKey, myPrikey, dealerPubkey);
        talkUnitHandler.saveTalkUnit(rawTalkUnit, done);
    }

    public TalkIdInfo searchCidOrFid(ApipClient apipClient, BufferedReader br) {
        Cid cid = apipClient.searchCidOrFid(br);
        if(cid ==null)return null;
        return TalkIdInfo.fromCidInfo(cid);
    }

    private void listenForMessages() {
        while (running.get()) {
            try {
                byte[] messageBytes = TcpUtils.readBytes(inputStream);
                if (messageBytes == null) {
                    // Connection closed by server
                    TimberLogger.d("Connection closed by server");
                    break;
                }

                // Process the message
                processMessage(messageBytes);
            } catch (IOException e) {
                TimberLogger.e("Error reading from socket");
                break;
            }
        }

        // If we exit the loop and we're still supposed to be running, try to reconnect
        if (running.get() && !reconnecting.get()) {
            handleConnectionError();
        }
    }

    private void processMessage(byte[] messageBytes) {
        // Process the message using existing logic
        byte[] myPrikey = getMyPrikey();
        TalkUnit decryptedTalkUnit = TalkUnit.decryptUnit(messageBytes, myPrikey, serverSession, null);

        if(decryptedTalkUnit==null){
            TimberLogger.e("Failed to decrypt talkUnit: %s", new String(messageBytes));
            return;
        }

        TalkUnit parsedTalkUnit = null;
        try {
            parsedTalkUnit = parseTalkUnitData(decryptedTalkUnit, myPrikey, sessionHandler);
        } catch (Exception e) {
            TimberLogger.e( "Error parsing talk unit data");
        }

        if(parsedTalkUnit==null){
            TimberLogger.e(TAG,"Failed to parse talkUnit: %s", decryptedTalkUnit);
            return;
        }

        Boolean gotOrRelay = TalkUnitExecutor.checkGotOrRelay(parsedTalkUnit, talkUnitHandler);
        if(!Boolean.FALSE.equals(gotOrRelay)) return;

        TalkUnitSender.sayGot(outputStream, myPrikey, parsedTalkUnit);

        TalkUnitExecutor talkUnitExecutor = new TalkUnitExecutor(this);
        talkUnitExecutor.executeTalkUnit(parsedTalkUnit);
        talkUnitSender.sendTalkUnitListByTcp(talkUnitExecutor.getSendTalkUnitList(), outputStream);

        talkUnitHandler.put(parsedTalkUnit);

//        if(talkUnitExecutor.getPayToForBytes()!=null)
//            pay(talkUnitExecutor.getPayToForBytes());

        if(talkUnitExecutor.getDisplayText()!=null)
            TimberLogger.d("Display text: %s", talkUnitExecutor.getDisplayText());

        if(parsedTalkUnit.getDataType()!= TalkUnit.DataType.ENCRYPTED_GOT && 
           parsedTalkUnit.getDataType()!= TalkUnit.DataType.ENCRYPTED_RELAYED)
            talkUnitHandler.put(parsedTalkUnit);
    }

    private void handleConnectionError() {
        if (reconnecting.get()) {
            return;
        }

        reconnecting.set(true);
        executorService.execute(this::tryReconnect);
    }

    public boolean tryReconnect() {
        for (int attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS; attempt++) {
            try {
                TimberLogger.d(TAG,"Attempting to reconnect... (Attempt %d/%d)", attempt, MAX_RECONNECT_ATTEMPTS);
                
                // Close existing socket if any
                closeSocket();
                
                // Create new connection
                connectToServer();
                
                // If we get here, connection was successful
                TimberLogger.d("Successfully reconnected to server");
                reconnecting.set(false);
                return true;
            } catch (Exception e) {
                TimberLogger.e(TAG,"Reconnection attempt %d failed", attempt);
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // If we get here, all reconnection attempts failed
        TimberLogger.e(TAG,"Failed to reconnect after %d attempts", MAX_RECONNECT_ATTEMPTS);
        stop();
        return false;
    }

    private void closeSocket() {
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
        } catch (IOException e) {
            TimberLogger.e("Error closing socket");
        }
    }

    public void stop() {
        running.set(false);
        reconnecting.set(false);
        closeSocket();
        executorService.shutdown();
    }

    public boolean sendTalkUnit(TalkUnit talkUnit, byte[] sessionKey, byte[] myPrikey, String pubkey) {
        TalkUnit.checkSelfUnitEncrypt(talkUnit);
        CryptoDataByte cryptoDataByte = talkUnit.encryptUnit(sessionKey, myPrikey, pubkey, talkUnit.getUnitEncryptType());
        TimberLogger.d("Encrypted talkUnit for send: %s", cryptoDataByte.toNiceJson());
        if(cryptoDataByte.getCode()!=0){
            TimberLogger.d("Failed to encrypt talk unit: %s", cryptoDataByte.getMessage());
            return false;
        }
        return sendBytes(cryptoDataByte.toBundle());
    }

    public boolean sendMessage(String message) {
        return TalkUnitSender.sendBytesByTcp(outputStream, message.getBytes());
    }

    public boolean sendBytes(byte[] bytes) {
        return TalkUnitSender.sendBytesByTcp(outputStream, bytes);
    }

    // ... rest of the existing methods ...

    public boolean isRunning() {
        return running.get();
    }

    public void setRunning(boolean running) {
        this.running.set(running);
    }

    // Getters and setters
    public TalkUnitHandler getTalkUnitHandler() {
        return talkUnitHandler;
    }

    public TalkUnitSender getTalkUnitSender() {
        return talkUnitSender;
    }

    public TalkIdHandler getTalkIdHandler() {
        return talkIdHandler;
    }

    public SessionHandler getSessionHandler() {
        return sessionHandler;
    }

    public ContactHandler getContactHandler() {
        return contactHandler;
    }

    public GroupHandler getGroupHandler() {
        return groupHandler;
    }

    public TeamHandler getTeamHandler() {
        return teamHandler;
    }

    public HatHandler getHatHandler() {
        return hatHandler;
    }

    public CashHandler getCashHandler() {
        return cashHandler;
    }

    public CidHandler getCidHandler() {
        return cidHandler;
    }

    public MailHandler getMailHandler() {
        return mailHandler;
    }

    public DiskHandler getDiskHandler() {
        return diskHandler;
    }

    public String getMyFid() {
        return myFid;
    }

    public void setMyFid(String myFid) {
        this.myFid = myFid;
    }

    public byte[] getMyPrikey() {
        return myPrikey;
    }

    public void setMyPrikey(byte[] myPrikey) {
        this.myPrikey = myPrikey;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(byte[] sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getDealer() {
        return dealer;
    }

    public void setDealer(String dealer) {
        this.dealer = dealer;
    }

    public String getDealerPubkey() {
        return dealerPubkey;
    }

    public void setDealerPubkey(String dealerPubkey) {
        this.dealerPubkey = dealerPubkey;
    }

    public FcSession getServerSession() {
        return serverSession;
    }

    public void setServerSession(FcSession serverSession) {
        this.serverSession = serverSession;
    }

    public ConcurrentLinkedQueue<TalkUnit> getReceivedQueue() {
        return receivedQueue;
    }

    public ConcurrentLinkedQueue<TalkUnit> getSendQueue() {
        return sendQueue;
    }

    public Settings getSettings() {
        return settings;
    }

    public ApiAccount getApiAccount() {
        return apiAccount;
    }

    public ApiProvider getApiProvider() {
        return apiProvider;
    }

    public ApipClient getApipClient() {
        return apipClient;
    }

    public void setApipClient(ApipClient apipClient) {
        this.apipClient = apipClient;
    }
}

