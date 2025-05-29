package com.fc.fc_ajdk.handlers;
    
import com.fc.fc_ajdk.clients.ApipClient;
import com.fc.fc_ajdk.clients.TalkClient;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.FcSession;
import com.fc.fc_ajdk.data.fcData.TalkUnit;

import com.fc.fc_ajdk.data.feipData.Service;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.TcpUtils;

import java.io.DataOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.fc.fc_ajdk.data.fcData.TalkUnit.IdType.FID;

import timber.log.Timber;

public class TalkUnitSender extends Handler {
    private static final String TAG = "TalkUnitSender";
    private byte[] myPrikey;

    private AccountHandler accountHandler=null;
    private SessionHandler sessionHandler;
    private TalkIdHandler talkIdHandler;
    private ContactHandler contactHandler;
    private ApipClient apipClient;
    private TalkUnitHandler talkUnitHandler;

    public TalkUnitSender(TalkClient talkClient) {
        this.myPrikey = talkClient.getMyPrikey();
        this.sessionHandler = talkClient.getSessionHandler();
        this.apipClient = (ApipClient) talkClient.getSettings().getClient(Service.ServiceType.APIP);
        this.talkUnitHandler = talkClient.getTalkUnitHandler();
        
        this.contactHandler = talkClient.getContactHandler();
        this.talkIdHandler = talkClient.getTalkIdHandler();
    }

    public static boolean sendToFidChannels(Map<String, Set<DataOutputStream>> fidChannelsMap, byte[] bytes, String fid) {
        Set<DataOutputStream> outputStreamSet = fidChannelsMap.get(fid);
        if(outputStreamSet==null || outputStreamSet.isEmpty()) return true;
        for (DataOutputStream outputStream : outputStreamSet)
            sendBytesByTcp(outputStream, bytes);
        return false;
    }

    public boolean sendTalkUnitWithTcp(TalkUnit talkUnit, byte[] sessionKey, byte[] myPrikey, String recipientPubkey, Set<DataOutputStream> outputStreamSet) {
        if (talkUnit != null) {
            CryptoDataByte cryptoDataByte = talkUnit.encryptUnit(sessionKey, myPrikey, recipientPubkey, talkUnit.getUnitEncryptType());
            if (cryptoDataByte != null) {
                byte[] bytes = cryptoDataByte.toBundle();
                int outputStreamCount = 0;
                for(DataOutputStream outputStream : outputStreamSet){
                    if(sendBytesByTcp(outputStream, bytes))
                        outputStreamCount++;
                }
                updateSenderBalance(talkUnit.getFrom(), accountHandler, talkUnit.getDataType().name(), bytes.length);
                return outputStreamCount>0;
            }
        }
        return false;
    }

    public static boolean writeTalkUnitByTcp(DataOutputStream outputStream, TalkUnit talkUnitRequest) {
        return writeTalkUnitByTcp(outputStream,talkUnitRequest,null,null,null);
    }

    public static boolean writeTalkUnitByTcp(DataOutputStream outputStream, TalkUnit talkUnitRequest, EncryptType encryptType, byte[] key, byte[] pubkey) {
        byte[] data = talkUnitRequest.toBytes();
        return writeBytesByTcp(outputStream, encryptType, key, pubkey, data);
    }

    public static boolean writeBytesByTcp(DataOutputStream outputStream, EncryptType encryptType, byte[] key, byte[] pubkey, byte[] data) {
        Encryptor encryptor;
        if(encryptType ==null)
            return TcpUtils.writeBytes(outputStream, data);

        CryptoDataByte cryptoDataByte=null;
        switch (encryptType){
            case Symkey -> {
                encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptBySymkey(data, key);
            }
            case Password -> {
                encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByPassword(data, BytesUtils.bytesToChars(key));
            }
            case AsyTwoWay -> {
                encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByAsyTwoWay(data, key, pubkey);
            }
            case AsyOneWay -> {
                encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
                cryptoDataByte = encryptor.encryptByAsyOneWay(data, key);
            }
        }

        if(cryptoDataByte==null || cryptoDataByte.getCode()!=0)
            return false;

        return TcpUtils.writeBytes(outputStream, cryptoDataByte.toBundle());
    }

    public static boolean sendBytesByTcp(DataOutputStream outputStream, byte[] messageBytes) {
        if (outputStream != null) {
            try {
                return TcpUtils.writeBytes(outputStream, messageBytes);
            } catch (Exception e) {
                Timber.e(e, "Error sending message bytes");
                return false;
            }
        } else {
            Timber.e("Cannot send message - output stream is null");
        }
        return false;
    }

    public void sendTalkUnitListByTcp(List<TalkUnit> sendTalkUnitList, DataOutputStream outputStream) {
        try {
            if(outputStream!=null && sendTalkUnitList!=null){
                for(TalkUnit talkUnit : sendTalkUnitList){
                    sendTalkUnit(talkUnit, outputStream);
                }
            }
        }catch (Exception e){
            Timber.e(e, "Error sending talk unit list");
        }
    }

    public static void sayGot(DataOutputStream outputStream, byte[] myPrikey, TalkUnit parsedTalkUnit) {
        if(parsedTalkUnit.getToType().equals(TalkUnit.IdType.FID)) { //Don't say got to team or group.
            byte[] gotBytes = TalkUnit.notifyGot(parsedTalkUnit, myPrikey, parsedTalkUnit.getFromSession(), parsedTalkUnit.getBySession());
            sendBytesByTcp(outputStream, gotBytes);
        }
    }

    public static Long updateSenderBalance(String userFid,
                                           AccountHandler accountHandler, String chargeType, int length) {
        Long newBalance = null;
        long cost;
        Long price = accountHandler.getPriceBase();
        Long nPrice = accountHandler.getnPriceMap().get(chargeType);
        if(price!=null && nPrice!=null)
            cost = length * nPrice * price;
        else if(price!=null)
            cost = length * price;
        else cost =0;
        newBalance = accountHandler.updateUserBalance(userFid, -cost);
        if(newBalance!= null && newBalance >= 0) accountHandler.updateViaBalance(userFid, cost, null);
        return newBalance;
    }

    public void sendTalkUnit(TalkUnit talkUnit, DataOutputStream outputStream) {
        Set<DataOutputStream> outputStreamSet = new HashSet<>();
        outputStreamSet.add(outputStream);
        sendTalkUnit(talkUnit, outputStreamSet);
    }

    public void sendTalkUnit(TalkUnit talkUnit, Set<DataOutputStream> outputStreamSet) {
        String toId = talkUnit.getTo();
        byte[] sessionKey = null;
        String pubkey = null;
        byte[] priKey = myPrikey;

        FcSession session = sessionHandler.getSessionByUserId(toId);
        if(session!=null){
            sessionKey = session.getKeyBytes();
            pubkey = session.getPubkey();
        }else if(!talkUnit.getToType().equals(FID))
            return;

        if(sessionKey==null && pubkey==null){
            pubkey = KeyTools.getPubkey(toId, sessionHandler, talkIdHandler, contactHandler, apipClient);
            if(pubkey==null) return;
        }
        boolean done = talkUnit.makeTalkUnit(sessionKey, priKey, pubkey);
        if(!done)return;
        if(talkUnit.getUnitEncryptType()!=null) {
            switch (talkUnit.getUnitEncryptType()){
                case Symkey -> {
                    if(sessionKey==null)return;
                    pubkey=null;
                    priKey=null;
                }
                case AsyOneWay -> {
                    if(pubkey==null)return;
                    sessionKey=null;
                }
                case AsyTwoWay -> {
                    if(pubkey==null||priKey==null)return;
                    sessionKey=null;
                }
                default -> {
                    return;
                }
            }
        }
        done = sendTalkUnitWithTcp(talkUnit, sessionKey, priKey, pubkey, outputStreamSet);
        if(done){
            talkUnit.setStata(TalkUnit.State.SENT);
        }
        talkUnitHandler.put(talkUnit);
    }
}
