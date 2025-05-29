package com.fc.fc_ajdk.clients;

import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.data.apipData.WebhookRequestBody;
import com.fc.fc_ajdk.ui.Menu;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.data.apipData.BlockInfo;
import com.fc.fc_ajdk.data.apipData.EncryptIn;
import com.fc.fc_ajdk.data.apipData.Fcdsl;
import com.fc.fc_ajdk.data.apipData.Range;
import com.fc.fc_ajdk.data.apipData.Sort;
import com.fc.fc_ajdk.data.apipData.TxInfo;
import com.fc.fc_ajdk.data.apipData.UnconfirmedInfo;
import com.fc.fc_ajdk.data.apipData.Utxo;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.ReplyBody;
import com.fc.fc_ajdk.data.fcData.FidTxMask;
import com.fc.fc_ajdk.core.fch.Inputer;
import com.fc.fc_ajdk.config.ApiAccount;
import com.fc.fc_ajdk.config.ApiProvider;
import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.constants.FieldNames;
import com.fc.fc_ajdk.data.fchData.Block;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.Cid;
import com.fc.fc_ajdk.data.fchData.FchChainInfo;
import com.fc.fc_ajdk.data.fchData.Nobody;
import com.fc.fc_ajdk.data.fchData.OpReturn;
import com.fc.fc_ajdk.data.fchData.P2SH;
import com.fc.fc_ajdk.data.fchData.SendTo;
import com.fc.fc_ajdk.data.feipData.App;
import com.fc.fc_ajdk.data.feipData.AppHistory;
import com.fc.fc_ajdk.data.feipData.Box;
import com.fc.fc_ajdk.data.feipData.BoxHistory;
import com.fc.fc_ajdk.data.feipData.CidHist;
import com.fc.fc_ajdk.data.feipData.Code;
import com.fc.fc_ajdk.data.feipData.CodeHistory;
import com.fc.fc_ajdk.data.feipData.Contact;
import com.fc.fc_ajdk.data.feipData.Group;
import com.fc.fc_ajdk.data.feipData.GroupHistory;
import com.fc.fc_ajdk.data.feipData.Mail;
import com.fc.fc_ajdk.data.feipData.Nid;
import com.fc.fc_ajdk.data.feipData.Proof;
import com.fc.fc_ajdk.data.feipData.ProofHistory;
import com.fc.fc_ajdk.data.feipData.Protocol;
import com.fc.fc_ajdk.data.feipData.ProtocolHistory;
import com.fc.fc_ajdk.data.feipData.Secret;
import com.fc.fc_ajdk.data.feipData.Service;
import com.fc.fc_ajdk.data.feipData.ServiceHistory;
import com.fc.fc_ajdk.data.feipData.Statement;
import com.fc.fc_ajdk.data.feipData.Team;
import com.fc.fc_ajdk.data.feipData.TeamHistory;
import com.fc.fc_ajdk.data.feipData.Token;
import com.fc.fc_ajdk.data.feipData.TokenHistory;
import com.fc.fc_ajdk.data.feipData.TokenHolder;

import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.ObjectUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.fc_ajdk.utils.http.AuthType;
import com.fc.fc_ajdk.utils.http.RequestMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fc.fc_ajdk.constants.FieldNames.*;

import static com.fc.fc_ajdk.constants.IndicesNames.CONTACT;
import static com.fc.fc_ajdk.constants.IndicesNames.MAIL;
import static com.fc.fc_ajdk.constants.Strings.ACTIVE;
import static com.fc.fc_ajdk.constants.Strings.CHECK;
import static com.fc.fc_ajdk.constants.Strings.CLOSED;
import static com.fc.fc_ajdk.constants.Strings.HOOK_USER_ID;
import static com.fc.fc_ajdk.constants.FieldNames.OP;
import static com.fc.fc_ajdk.constants.Strings.SUBSCRIBE;
import static com.fc.fc_ajdk.constants.Values.ASC;
import static com.fc.fc_ajdk.constants.Values.DESC;
import static com.fc.fc_ajdk.constants.Values.FALSE;
import static com.fc.fc_ajdk.constants.Values.TRUE;
import static com.fc.fc_ajdk.core.crypto.KeyTools.prikeyToFid;
import static com.fc.fc_ajdk.data.apipData.FcQuery.PART;
import static com.fc.fc_ajdk.clients.ApipClient.ApipApiNames.*;

import static com.fc.fc_ajdk.utils.ObjectUtils.objectToList;
import static com.fc.fc_ajdk.utils.ObjectUtils.objectToMap;
import static com.fc.fc_ajdk.utils.ObjectUtils.objectToClass;

import timber.log.Timber;

public class ApipClient extends FcClient {

    public static final Integer DEFAULT_SIZE = 200;

    public static final String[] freeAPIs = new String[]{
            "https://apip.cash/APIP",
            "https://help.cash/APIP",
            "http://127.0.0.1:8080/APIP",
            "http://127.0.0.1:8081/APIP"
    };
    public ApipClient() {
    }
    public ApipClient(ApiProvider apiProvider,ApiAccount apiAccount,byte[] symkey){
        super(apiProvider,apiAccount,symkey);
    }

    public List<P2SH> myP2SHs(String fid) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FIDS).addNewValues(fid);
        return p2shSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
    }

    public void checkMaster(String prikeyCipher,byte[] symkey,BufferedReader br) {
        byte[] prikey = new Decryptor().decryptJsonBySymkey(prikeyCipher,symkey).getData();
        if (prikey == null) {
            Timber.e("Failed to decrypt prikey.");
        }

        String fid = prikeyToFid(prikey);
        Cid cid = cidInfoById(fid);
        if (cid == null) {
            System.out.println("This fid was never seen on chain. Send some fch to it.");
            Menu.anyKeyToContinue(br);
            return;
        }
        if (cid.getMaster() != null) {
            System.out.println("The master of " + fid + " is " + cid.getMaster());
            return;
        }
        if (Inputer.askIfYes(br, "Assign the master for " + fid + "?"))
            FeipClient.setMaster(fid, prikeyCipher, bestHeight, symkey, this, br);
    }

    //OpenAPIs: Ping(Client),GetService(Client),SignIn,SignInEccAPI,Totals

    public Map<String, String> totals(RequestMethod requestMethod, AuthType authType) {
        //Request
        Object data =requestJsonByFcdsl(null, VERSION_1, TOTALS,null,authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMap(data,String.class,String.class);
    }
    public Long bestHeight() {
        //Request
        Block block = bestBlock(RequestMethod.POST,AuthType.FC_SIGN_BODY);
        if(block==null)return null;
        return block.getHeight();
    }

    public Block bestBlock(RequestMethod requestMethod, AuthType authType) {
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data = requestByFcdslOther(SN_2, VERSION_1, BEST_BLOCK, null, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(SN_2, VERSION_1, BEST_BLOCK,null,AuthType.FREE);
        }
        return objectToClass(data,Block.class);
    }
    public ReplyBody general(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType) {
        //Request
        requestJsonByFcdsl(SN_1, VERSION_1, GENERAL,fcdsl,authType,sessionKey, requestMethod);
        return apipClientEvent.getResponseBody();
    }

    public String broadcastTx(String txHex, RequestMethod requestMethod, AuthType authType){
        if(txHex==null)return null;
        if(!Hex.isHexString(txHex))txHex = StringUtils.base64ToHex(txHex);
        if(txHex==null)return null;
        Map<String, String> otherMap = new HashMap<>() ;
        otherMap.put(RAW_TX,txHex);
        Object data = requestByFcdslOther(SN_18, VERSION_1, BROADCAST_TX, otherMap, authType, requestMethod);
        return (String)data;
    }

    public String decodeTx(String txHex, RequestMethod requestMethod, AuthType authType){
        Map<String, String> otherMap = new HashMap<>() ;
        otherMap.put(RAW_TX,txHex);
        Object data = requestByFcdslOther(SN_18, VERSION_1, DECODE_TX, otherMap, authType, requestMethod);
        return data==null ? null: JsonUtils.toNiceJson(data);
    }

    public List<Cash> getCashesFree(String fid, int size, List<String> after) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(fid);
        fcdsl.addNewFilter().addNewTerms().addNewFields(VALID).addNewValues(TRUE);
        fcdsl.addSort(CD,ASC).addSort(ID,ASC);
        if(size>0)fcdsl.addSize(size);
        if(after!=null)fcdsl.addAfter(after);
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, CASH_SEARCH, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        return ObjectUtils.objectToList(data,Cash.class);
    }

    public Map<String, BlockInfo>blockByHeights(RequestMethod requestMethod, AuthType authType, String... heights){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(HEIGHT).addNewValues(heights);

        Object data = requestJsonByFcdsl(SN_2, VERSION_1, BLOCK_BY_HEIGHTS, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToMap(data,String.class,BlockInfo.class);
    }

    public String getPubkey(String fid, RequestMethod requestMethod, AuthType authType) {
        Object data = requestByIds(requestMethod,SN_2, VERSION_1, FID_BY_IDS, authType, fid);
        try {
            return data == null ? null : objectToMap(data, String.class, Cid.class).get(fid).getPubkey();
        }catch (Exception e){
            return null;
        }
    }

    public Map<String, BlockInfo> blockByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, BLOCK_BY_IDS, authType, ids);
        return objectToMap(data,String.class,BlockInfo.class);
    }

    public List<BlockInfo> blockSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        try {
            Object data = requestJsonByFcdsl(SN_2, VERSION_1, BLOCK_SEARCH, fcdsl, authType, sessionKey, requestMethod);
            
            if(data == null) {
                System.out.println("Received null response from server");
                return null;
            }
            return objectToList(data,BlockInfo.class);
        } catch (Exception e) {
            System.out.println("Error in blockSearch: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * If amount !=null, the non-null cd will be ignored.
     */
    public List<Cash> cashValid(String fid, @Nullable Double amount, @Nullable Long cd, @Nullable Integer outputSize, @Nullable Integer msgSize, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Map<String,String> paramMap = new HashMap<>();
        if(amount!=null)paramMap.put(AMOUNT, String.valueOf(amount));
        if(fid!=null)paramMap.put(FID,fid);
        if(amount==null && cd!=null)paramMap.put(CD, String.valueOf(cd));
        if(outputSize!=null)paramMap.put(OUTPUT_SIZE, String.valueOf(outputSize));
        if(msgSize!=null)paramMap.put(MSG_SIZE, String.valueOf(msgSize));  
        fcdsl.addOther(paramMap);
        return cashValid(fcdsl, requestMethod,authType);
    }

    public Map<String, Long> balanceByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_18, VERSION_1, BALANCE_BY_IDS, authType, ids);
        return objectToMap(data,String.class,Long.class);
    }

    public List<Cash> cashValid(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_18, VERSION_1, CASH_VALID, fcdsl,authType, sessionKey, requestMethod);
        return objectToList(data,Cash.class);
    }

    public Map<String, Cash> cashByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, CASH_BY_IDS, authType, ids);
        return objectToMap(data,String.class,Cash.class);
    }

    public List<Cash> cashSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, CASH_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,Cash.class);
    }
    public List<Utxo> getUtxo(String id, double amount, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FID).addNewValues(id);
        Fcdsl.setSingleOtherMap(fcdsl, AMOUNT, String.valueOf(amount));

        Object data = requestJsonByFcdsl(SN_18, VERSION_1, GET_UTXO, fcdsl,authType, sessionKey, requestMethod);
        return objectToList(data,Utxo.class);
    }
    public Map<String, Cid> fidByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, FID_BY_IDS, authType, ids);
        return objectToMap(data,String.class, Cid.class);
    }
    public List<Cid> fidSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, FID_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, Cid.class);
    }
    public Map<String, OpReturn> opReturnByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, OP_RETURN_BY_IDS, authType, ids);
        return objectToMap(data,String.class,OpReturn.class);
    }

    public List<OpReturn> opReturnSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, OP_RETURN_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,OpReturn.class);
    }

    public Map<String, P2SH> p2shByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, P_2_SH_BY_IDS, authType, ids);
        return objectToMap(data,String.class,P2SH.class);
    }
    public List<P2SH> p2shSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, P_2_SH_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,P2SH.class);
    }

    public Map<String, TxInfo> txByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_2, VERSION_1, TX_BY_IDS, authType, ids);
        return objectToMap(data,String.class,TxInfo.class);
    }

    public List<TxInfo> txSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_2, VERSION_1, TX_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,TxInfo.class);
    }

    public List<FidTxMask>  txByFid(String fid, int size, String[] last, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl =txByFidQuery(fid, size, last);
        Object data = requestJsonByFcdsl( SN_2, VERSION_1, TX_BY_FID,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, FidTxMask.class);
    }
    public static Fcdsl txByFidQuery(String fid, int size, @javax.annotation.Nullable String[] last){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery()
                .addNewTerms()
                .addNewFields("inMarks.owner","outMarks.owner")
                .addNewValues(fid);
        if(last!=null){
            fcdsl.addAfter(java.util.List.of(last));
        }
        if(size!=0)
            fcdsl.addSize(size);
        return fcdsl;
    }
    public FchChainInfo chainInfo() {
        return chainInfo(null,RequestMethod.GET,AuthType.FREE);
    }

    public FchChainInfo chainInfo(Long height, RequestMethod requestMethod, AuthType authType) {
        Map<String,String> params=null;
        if(height!=null){
            params = new HashMap<>();
            params.put(FieldNames.HEIGHT, String.valueOf(height));
        }
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data = requestByFcdslOther(SN_2, VERSION_1, CHAIN_INFO, params, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(SN_2, VERSION_1, CHAIN_INFO,params,AuthType.FREE);
        }

        return objectToClass(data,FchChainInfo.class);
    }

    public Map<Long,Long> blockTimeHistory(Long startTime, Long endTime, Integer count, RequestMethod requestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);

        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data =requestByFcdslOther(SN_2, VERSION_1, BLOCK_TIME_HISTORY, params, authType, requestMethod);
        }else {
            data =requestJsonByUrlParams(SN_2, VERSION_1, BLOCK_TIME_HISTORY,params,AuthType.FREE);
        }

        return ObjectUtils.objectToMap(data,Long.class,Long.class);
    }

    public Map<Long,String> difficultyHistory(Long startTime, Long endTime, Integer count, RequestMethod requestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data = requestByFcdslOther(SN_2, VERSION_1, DIFFICULTY_HISTORY, params, authType, requestMethod);
        }else {
            data =requestJsonByUrlParams(SN_2, VERSION_1, DIFFICULTY_HISTORY,params,AuthType.FREE);
        }

        return ObjectUtils.objectToMap(data,Long.class,String.class);
    }

    @NotNull
    private static Map<String, String> makeHistoryParams(Long startTime, Long endTime, Integer count) {
        if(count ==null|| count ==0) count = Constants.DefaultSize;

        Map<String, String> params = new HashMap<>();
        if(startTime !=null)params.put(FieldNames.START_TIME, String.valueOf(startTime));
        if(endTime !=null)params.put(FieldNames.END_TIME, String.valueOf(endTime));
        params.put(FieldNames.COUNT, String.valueOf(count));
        return params;
    }

    public Map<Long,String> hashRateHistory(Long startTime, Long endTime, Integer count, RequestMethod requestMethod, AuthType authType) {
        Map<String, String> params = makeHistoryParams(startTime, endTime, count);
        Object data;
        if(requestMethod.equals(RequestMethod.POST)) {
            data =requestByFcdslOther(SN_2, VERSION_1, HASH_RATE_HISTORY, params, authType, requestMethod);
        }else {
            data = requestJsonByUrlParams(SN_2, VERSION_1, HASH_RATE_HISTORY,params,AuthType.FREE);
        }
        return ObjectUtils.objectToMap(data,Long.class,String.class);
    }

    //Identity APIs
    public Map<String, Cid> cidByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_3, VERSION_1, CID_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class, Cid.class);
    }
    public Cid cidInfoById(String id) {
        Map<String, Cid> map = cidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, id);
        try {
            return map.get(id);
        }catch (Exception e){
            Timber.e("Failed to get Cid info: %s",e.getMessage());
            return null;
        }
    }
    public Cid getFidCid(String id) {
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(ID,id);
        Object data = requestJsonByUrlParams(SN_3, VERSION_1, GET_FID_CID,paramMap,AuthType.FREE);
        return objectToClass(data, Cid.class);
    }

    public Map<String, String> getFidCidMap(List<String> fidList) {
        fidList.remove(null);
        Map<String, Cid> cidInfoMap = this.cidByIds(RequestMethod.POST, AuthType.FC_SIGN_BODY, fidList.toArray(new String[0]));
        if (cidInfoMap == null) return null;
        Map<String, String> fidCidMap = new HashMap<>();
        for (String fid:cidInfoMap.keySet()) {
            Cid cid = cidInfoMap.get(fid);
            if(cid !=null)fidCidMap.put(fid, cid.getCid());
        }
        return fidCidMap;
    }

    //TODO untested
    public List <Cid> searchCidList(BufferedReader br, boolean choose){
        String part = Inputer.inputString(br,"Input the FID, CID, used CIDs or a part of any one of them:");
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(ID,FieldNames.USED_CIDS).addNewValue(part);
        List<Cid> result = cidSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null || result.isEmpty())return null;
        return Cid.showCidList("Chose CIDs",result,20,choose,br);
    }

    public List<Cid> cidSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, VERSION_1, CID_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data, Cid.class);
    }
    public List<CidHist> cidHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "3");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, VERSION_1, CID_HISTORY,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public Map<String, String[]> fidCidSeek(String searchStr, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(ID,CID).addNewValue(searchStr);
        Object data = requestJsonByFcdsl(SN_3, VERSION_1, FID_CID_SEEK,fcdsl, authType,sessionKey, requestMethod);
        return objectToMap(data,String.class,String[].class);
    }

    public Map<String, String[]> fidCidSeek(String fid_or_cid){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewPart().addNewFields(ID,CID).addNewValue(fid_or_cid);
        Map<String,String> map = new HashMap<>();
        map.put(PART,fid_or_cid);
        Object data = requestJsonByUrlParams(SN_3, VERSION_1, FID_CID_SEEK,map, AuthType.FREE);
        return objectToMap(data,String.class,String[].class);
    }

    public Map<String, Nobody> nobodyByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod, SN_3, VERSION_1, NOBODY_BY_IDS, authType, ids);
        return objectToMap(data,String.class,Nobody.class);
    }

    public List<Nobody> nobodySearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, VERSION_1, NOBODY_SEARCH,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,Nobody.class);
    }

    public List<CidHist> homepageHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "9");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, VERSION_1, HOMEPAGE_HISTORY,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public List<CidHist> noticeFeeHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, SN, "10");
        if (fcdsl == null) return null;

        Object data = requestJsonByFcdsl(SN_3, VERSION_1, NOTICE_FEE_HISTORY,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public List<CidHist> reputationHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_3, VERSION_1, REPUTATION_HISTORY,fcdsl, authType,sessionKey, requestMethod);
        return objectToList(data,CidHist.class);
    }

    public Map<String, String> avatars(String[] fids, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(fids);
        Object data = requestJsonByFcdsl(SN_3, VERSION_1, AVATARS,fcdsl, authType,sessionKey, requestMethod);
        return objectToMap(data,String.class,String.class);
    }

    public byte[] getAvatar(String fid){
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(FID,fid);
        Object data = requestBytes(SN_3, VERSION_1, GET_AVATAR, ApipClientEvent.RequestBodyType.NONE,null,paramMap, AuthType.FREE,null, RequestMethod.GET);
        return (byte[])data;
    }


    // Construct
    public Map<String, Protocol> protocolByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_4, VERSION_1, PROTOCOL_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Protocol.class);
    }

    public Protocol protocolById(String id){
        Map<String, Protocol> map = protocolByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Protocol> protocolSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_4, VERSION_1, PROTOCOL_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Protocol.class);
    }


    public List<ProtocolHistory> protocolOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_4, VERSION_1, PROTOCOL_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProtocolHistory.class);
    }


    public List<ProtocolHistory> protocolRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_4, VERSION_1, PROTOCOL_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProtocolHistory.class);
    }

    public Map<String, Code> codeByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_5, VERSION_1, CODE_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Code.class);
    }

    public Code codeById(String id){
        Map<String, Code> map = codeByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<Code> codeSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_5, VERSION_1, CODE_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Code.class);
    }

    public List<CodeHistory> codeOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_5, VERSION_1, CODE_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,CodeHistory.class);
    }

    public List<CodeHistory> codeRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_5, VERSION_1, CODE_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,CodeHistory.class);
    }

    public Map<String, Service> serviceByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_6, VERSION_1, SERVICE_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Service.class);
    }

    public List<Service> serviceSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_6, VERSION_1, SERVICE_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Service.class);
    }

    public List<ServiceHistory> serviceOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_6, VERSION_1, SERVICE_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ServiceHistory.class);
    }

    public List<ServiceHistory> serviceRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_6, VERSION_1, SERVICE_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ServiceHistory.class);
    }

    public List<Service> getServiceListByType(String type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewMatch().addNewFields(FieldNames.TYPES).addNewValue(type);
        fcdsl.addNewExcept().addNewTerms().addNewFields(ACTIVE).addNewValues(FALSE);
        serviceList = serviceSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        return serviceList;
    }

    public List<Service> getServiceListByOwnerAndType(String owner, @Nullable Service.ServiceType type) {
        List<Service> serviceList;
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(OWNER).addNewValues(owner);
        fcdsl.addNewExcept().addNewTerms().addNewFields(CLOSED).addNewValues(TRUE);
        if(type!=null)fcdsl.addNewFilter().addNewMatch().addNewFields(FieldNames.TYPES).setValue(type.name());
        serviceList = serviceSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        return serviceList;
    }

    public Service serviceById(String id){
        Map<String, Service> map = serviceByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public Map<String, App> appByIds(RequestMethod requestMethod, AuthType authType, String... ids) {
        Object data = requestByIds(requestMethod,SN_7, VERSION_1, APP_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,App.class);
    }

    public App appById(String id){
        Map<String, App> map = appByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY , id);
        if(map==null)return null;
        try {
            return map.get(id);
        }catch (Exception ignore){
            return null;
        }
    }

    public List<App> appSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_7, VERSION_1, APP_SEARCH, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToList(data,App.class);
    }


    public List<AppHistory> appOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsExcept(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_7, VERSION_1, APP_OP_HISTORY, fcdsl,authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,AppHistory.class);
    }


    public List<AppHistory> appRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        fcdsl = Fcdsl.makeTermsFilter(fcdsl, OP, RATE);
        if (fcdsl == null) return null;
        Object data = requestJsonByFcdsl(SN_7, VERSION_1, APP_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,AppHistory.class);
    }
//Organize
    public Map<String, Group> groupByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_8, VERSION_1, GROUP_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class, Group.class);
    }
    public List<Group> groupSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_8, VERSION_1, GROUP_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data, Group.class);
    }

    public List<GroupHistory> groupOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_8, VERSION_1, GROUP_OP_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,GroupHistory.class);
    }
    public Map<String, String[]> groupMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_8, VERSION_1, GROUP_MEMBERS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,String[].class);
    }

    public List<Group> myGroups(String fid, Long sinceHeight, Integer size, @NotNull final List<String> last, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        if(sinceHeight!=null)
            fcdsl.getQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(String.valueOf(sinceHeight));
        if(size!=null)fcdsl.addSize(size);
        if(!last.isEmpty())fcdsl.addAfter(last);
        Object data = requestJsonByFcdsl(SN_8, VERSION_1, MY_GROUPS, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        List<String> newLast = this.getFcClientEvent().getResponseBody().getLast();
        last.clear();
        last.addAll(newLast);
        return objectToList(data, Group.class);
    }

    public Map<String, Team> teamByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9, VERSION_1, TEAM_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Team.class);
    }
    public List<Team> teamSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, VERSION_1, TEAM_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Team.class);
    }

    public List<TeamHistory> teamOpHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, VERSION_1, TEAM_OP_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }

    public List<TeamHistory> teamRateHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_9, VERSION_1, TEAM_RATE_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }
    public Map<String, String[]> teamMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9, VERSION_1, TEAM_MEMBERS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,String[].class);
    }

    public Map<String, String[]> teamExMembers(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9, VERSION_1, TEAM_EX_MEMBERS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,String[].class);
    }
    public Map<String,Team> teamOtherPersons(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_9, VERSION_1, TEAM_OTHER_PERSONS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Team.class);
    }
    public List<Team> myTeams(String fid, Long sinceHeight, Integer size, @NotNull final List<String> last, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        if(sinceHeight!=null)
            fcdsl.getQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(String.valueOf(sinceHeight));
        if(size!=null)fcdsl.addSize(size);
        if(!last.isEmpty())fcdsl.addAfter(last);
        
        Object data = requestJsonByFcdsl(SN_9, VERSION_1, MY_TEAMS, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        List<String> newLast = this.getFcClientEvent().getResponseBody().getLast();
        last.clear();
        last.addAll(newLast);
        return objectToList(data,Team.class);
    }

    public Map<String, Box> boxByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_10, VERSION_1, BOX_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Box.class);
    }
    public List<Box> boxSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_10, VERSION_1, BOX_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Box.class);
    }

    public List<BoxHistory> boxHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_10, VERSION_1, BOX_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,BoxHistory.class);
    }

    public Map<String, Contact> contactByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_11, VERSION_1, CONTACT_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Contact.class);
    }
    public List<Contact> contactSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_11, VERSION_1, CONTACT_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Contact.class);
    }

    public List<Contact> contactDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_11, VERSION_1, CONTACTS_DELETED, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Contact.class);
    }

    public List<Contact> freshContactSinceHeight(String myFid, Long lastHeight, Integer size, final List<String> last, Boolean active) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIndex(CONTACT);
        String heightStr = String.valueOf(lastHeight);
        fcdsl.addNewQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(heightStr);

        fcdsl.getQuery().addNewTerms().addNewFields(OWNER).addNewValues(myFid);
        if(active!=null) {
            fcdsl.getQuery().addNewMatch().addNewFields(ACTIVE).addNewValue(String.valueOf(active));
        }
        fcdsl.addSize(size);

        fcdsl.addSort(LAST_HEIGHT, DESC).addSort(ID,ASC);

        if(last!=null && !last.isEmpty())fcdsl.addAfter(last);

        ReplyBody result = this.general(fcdsl,RequestMethod.POST, AuthType.FC_SIGN_BODY);

        Object data = result.getData();
        return objectToList(data, Contact.class);
    }

    public Map<String, Secret> secretByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_12, VERSION_1, SECRET_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Secret.class);
    }
    public List<Secret> secretSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_12, VERSION_1, SECRET_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Secret.class);
    }

    public List<Secret> secretDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_12, VERSION_1, SECRETS_DELETED, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Secret.class);
    }


    public <T> List<T> loadSinceHeight(String index,String idField,String sortField,String termField,String myFid, Long lastHeight, Integer size, List<String> last, Boolean active,Class<T> tClass) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIndex(index);
        String heightStr = String.valueOf(lastHeight);
        fcdsl.addNewQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(heightStr);

        fcdsl.getQuery().addNewTerms().addNewFields(termField).addNewValues(myFid);
        if(active!=null) {
            fcdsl.getQuery().addNewMatch().addNewFields(ACTIVE).addNewValue(String.valueOf(active));
        }
        fcdsl.addSize(size);

        fcdsl.addSort(sortField, ASC).addSort(idField,ASC);

        if(last!=null && !last.isEmpty())fcdsl.addAfter(last);

        ReplyBody result = this.general(fcdsl,RequestMethod.POST, AuthType.FC_SIGN_BODY);

        Object data = result.getData();
        return objectToList(data, tClass);
    }

    public Map<String, Mail> mailByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_13, VERSION_1, MAIL_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Mail.class);
    }
    public List<Mail> mailSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_13, VERSION_1, MAIL_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }

    public List<Mail> mailDeleted(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_13, VERSION_1, MAILS_DELETED, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }
    public List<Mail> mailThread(String fidA, String fidB, Long startTime, Long endTime, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(SENDER,RECIPIENT).addNewValues(fidA);
        if(startTime!=null||endTime!=null){
            Range range = fcdsl.getQuery().addNewRange();
            range.addNewFields(BIRTH_TIME);
            if(startTime!=null)range.addGte(String.valueOf(startTime/1000));
            if(endTime!=null)range.addLt(String.valueOf(endTime/1000));
        }
        fcdsl.addNewFilter().addNewTerms().addNewFields(SENDER,RECIPIENT).addNewValues(fidB);
        Object data = requestJsonByFcdsl(SN_13, VERSION_1, MAIL_THREAD, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Mail.class);
    }

    public List<Mail> freshMailSinceHeight(String myFid, long lastHeight, Integer defaultRequestSize, List<String> last, Boolean active) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIndex(MAIL);
        String heightStr = String.valueOf(lastHeight);
        fcdsl.addNewQuery().addNewRange().addNewFields(LAST_HEIGHT).addGt(heightStr);

        fcdsl.getQuery().addNewTerms().addNewFields(SENDER,RECIPIENT).addNewValues(myFid);
        if(active!=null) {
            fcdsl.getQuery().addNewMatch().addNewFields(ACTIVE).addNewValue(String.valueOf(active));
        }
        fcdsl.addSize(defaultRequestSize);

        fcdsl.addSort(LAST_HEIGHT, DESC).addSort(ID,ASC);

        if(last!=null && !last.isEmpty())fcdsl.addAfter(last);
//        return this.mailSearch(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);


        ReplyBody result = this.general(fcdsl,RequestMethod.POST, AuthType.FC_SIGN_BODY);

        Object data = result.getData();
        return objectToList(data, Mail.class);
    }

    public Map<String, Proof> proofByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_14, VERSION_1, PROOF_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Proof.class);
    }
    public List<Proof> proofSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_14, VERSION_1, PROOF_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Proof.class);
    }

    public List<ProofHistory> proofHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_14, VERSION_1, PROOF_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,ProofHistory.class);
    }


    public Map<String, Statement> statementByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_15, VERSION_1, STATEMENT_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Statement.class);
    }
    public List<Statement> statementSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_15, VERSION_1, STATEMENT_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Statement.class);
    }
    public List<Nid> nidSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_19, VERSION_1, NID_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Nid.class);
    }

    public Map<String, Token> tokenByIds(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_16, VERSION_1, TOKEN_BY_IDS, authType, ids);
        return ObjectUtils.objectToMap(data,String.class,Token.class);
    }
    public List<Token> tokenSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, VERSION_1, TOKEN_SEARCH, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,Token.class);
    }

    public List<TokenHistory> tokenHistory(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, VERSION_1, TOKEN_HISTORY, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data,TokenHistory.class);
    }

    public List<Group> myTokens(String fid, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.FID).addNewValues(fid);
        Object data = requestJsonByFcdsl(SN_16, VERSION_1, MY_TOKENS, fcdsl, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return objectToList(data, Group.class);
    }

    public Map<String, TokenHolder> tokenHoldersByIds(RequestMethod requestMethod, AuthType authType, String... tokenIds){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(Token_Id).addNewValues(tokenIds);
        Object data = requestJsonByFcdsl(SN_16, VERSION_1, TOKEN_HOLDERS_BY_IDS,fcdsl, authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMap(data,String.class,TokenHolder.class);
    }

    public List<TokenHolder> tokenHolderSearch(Fcdsl fcdsl, RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_16, VERSION_1, TOKEN_HOLDER_SEARCH,fcdsl,authType,sessionKey, requestMethod);
        return ObjectUtils.objectToList(data,TokenHolder.class);
    }
    public List<UnconfirmedInfo> unconfirmed(RequestMethod requestMethod, AuthType authType, String... ids){
        Object data = requestByIds(requestMethod,SN_18, VERSION_1, ApipApiNames.UNCONFIRMED, authType, ids);
        return ObjectUtils.objectToList(data,UnconfirmedInfo.class);
    }

    public Map<String, List<Cash>> unconfirmedCaches(RequestMethod requestMethod, AuthType authType,String... ids){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIds(ids);
        Object data = requestJsonByFcdsl(SN_18, VERSION_1, UNCONFIRMED_CASHES,fcdsl, authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMapWithListValues(data, String.class, Cash.class);
    }

    public Double feeRate(RequestMethod requestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_18, VERSION_1, FEE_RATE, null, authType, sessionKey, requestMethod);
        if(data==null)return null;
        return (Double)data;
    }


    public Map<String, String> addresses(String addrOrPubkey, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.ADDR_OR_PUB_KEY, addrOrPubkey);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, ADDRESSES,fcdsl, authType,sessionKey, requestMethod);
        return ObjectUtils.objectToMap(data,String.class,String.class);
    }
    public String encrypt(EncryptType encryptType, String message, String key, String fid, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        EncryptIn encryptIn = new EncryptIn();
        encryptIn.setType(encryptType);
        encryptIn.setMsg(message);
        switch (encryptType){
            case Symkey -> {
                encryptIn.setSymkey(key);
                encryptIn.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
            }
            case Password -> {
                encryptIn.setPassword(key);
                encryptIn.setAlg(AlgorithmId.FC_AesCbc256_No1_NrC7);
            }
            case AsyOneWay -> {
                if(key!=null) {
                    encryptIn.setPubkey(key);
                }else if(fid!=null){
                    encryptIn.setFid(fid);
                }
                encryptIn.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
            }
            default -> {
                return null;
            }
        }
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.ENCRYPT_INPUT, JsonUtils.toJson(encryptIn));
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, ENCRYPT,fcdsl, authType,sessionKey, requestMethod);
        return objectToClass(data,String.class);
    }
    public boolean verify(String signature, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, SIGN, signature);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, VERIFY,fcdsl, authType,sessionKey, requestMethod);
        if(data==null) return false;
        return (boolean) data;
    }

    public String sha256(String text, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.MESSAGE, text);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, SHA_256,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }
    public String sha256x2(String text, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.MESSAGE, text);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, SHA_256_X_2,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }
    public String sha256Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, SHA_256_HEX,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String sha256x2Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, SHA_256_X_2_HEX,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String ripemd160Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, RIPEMD_160_HEX,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String KeccakSha3Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, KECCAK_SHA_3_HEX,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String hexToBase58(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, HEX_TO_BASE_58,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String checkSum4Hex(String hex, RequestMethod requestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.MESSAGE, hex);
        Object data = requestJsonByFcdsl(SN_17, VERSION_1, CHECK_SUM_4_HEX,fcdsl, authType,sessionKey, requestMethod);
        return (String) data;
    }

    public String offLineTx(String fromFid, List<SendTo> sendToList, String msg, Long cd, String ver, RequestMethod requestMethod, AuthType authType){
        if(requestMethod.equals(RequestMethod.POST)) {
            Fcdsl fcdsl = new Fcdsl();
            RawTxInfo rawTxInfo = new RawTxInfo();
            rawTxInfo.setSender(fromFid);
            rawTxInfo.setOpReturn(msg);
            rawTxInfo.setOutputs(sendToList);
            rawTxInfo.setCd(cd);
            rawTxInfo.setVer(ver);
            Fcdsl.setSingleOtherMap(fcdsl, FieldNames.DATA_FOR_OFF_LINE_TX, JsonUtils.toJson(rawTxInfo));
            Object data = requestJsonByFcdsl(SN_18, VERSION_1, OFF_LINE_TX, fcdsl, authType, sessionKey, requestMethod);
            return (String) data;
        }

        Map<String,String> paramMap = new HashMap<>();
        paramMap.put(VER,ver);
        paramMap.put("fromFid",fromFid);
        List<String> toList = new ArrayList<>();
        List<String> amountList = new ArrayList<>();

        if(sendToList!=null && !sendToList.isEmpty()) {
            for (SendTo sendTo : sendToList) {
                toList.add(sendTo.getFid());
                amountList.add(String.valueOf(sendTo.getAmount()));
            }
            paramMap.put("toFids", String.join(",", toList));
            paramMap.put("amounts", String.join(",", amountList));
        }
        if(msg!=null)paramMap.put(MESSAGE,msg);
        if(cd!=0)paramMap.put(CD,String.valueOf(cd));

        Object data = requestJsonByUrlParams(SN_18, VERSION_1, OFF_LINE_TX, paramMap, authType);
        return (String) data;
    }

    public String circulating(){
        Object data = requestBase(CIRCULATING, ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String totalSupply(){
        Object data = requestBase(TOTAL_SUPPLY, ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String richList(){
        Object data = requestBase(RICHLIST, ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    public String freecashInfo(){
        Object data = requestBase(FREECASH_INFO, ApipClientEvent.RequestBodyType.NONE, null, null, null, null, null, ApipClientEvent.ResponseBodyType.STRING, null, null, AuthType.FREE, null, RequestMethod.GET);
        return (String) data;
    }

    //Webhook APIs
    public Map<String, String> checkSubscription(String method, String endpoint) {
        WebhookRequestBody webhookRequestBody = new WebhookRequestBody();
        webhookRequestBody.setEndpoint(endpoint);
        webhookRequestBody.setOp(CHECK);
//    webhookRequestBody.setMethod(method);
//    webhookRequestBody.setUserId(apiAccount.getUserId());
//    String hookUserId = WebhookRequestBody.makeHookUserId(apiAccount.getProviderId(), apiAccount.getUserId(), ApiNames.NewCashByFids);
//    webhookRequestBody.setHookUserId(hookUserId);
        return switch (method){
            case NEW_CASH_BY_FIDS -> newCashListByIds(webhookRequestBody);
            case NEW_OP_RETURN_BY_FIDS -> newOpReturnListByIds(webhookRequestBody);
            default -> null;
        };
    }
    //
    public String subscribeWebhook(String method, Object data, String endpoint) {
        WebhookRequestBody webhookRequestBody = new WebhookRequestBody();
        webhookRequestBody.setEndpoint(endpoint);
        webhookRequestBody.setMethod(method);
//        webhookRequestBody.setUserId(apiAccount.getUserId());
        webhookRequestBody.setOp(SUBSCRIBE);
        webhookRequestBody.setData(data);
        Map<String, String> dataMap=null;
        switch (method) {
            case NEW_CASH_BY_FIDS -> dataMap = newCashListByIds(webhookRequestBody);
            case NEW_OP_RETURN_BY_FIDS -> dataMap=newOpReturnListByIds(webhookRequestBody);
        }
        if(dataMap==null)return null;
        return dataMap.get(HOOK_USER_ID);
    }

    public Map<String, String> newCashListByIds(WebhookRequestBody webhookRequestBody){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.WEBHOOK_REQUEST_BODY, JsonUtils.toJson(webhookRequestBody));
        Object data = requestJsonByFcdsl(SN_20, VERSION_1, ApipApiNames.NEW_CASH_BY_FIDS, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToMap(data,String.class,String.class);
    }

    public Map<String, String> newOpReturnListByIds(WebhookRequestBody webhookRequestBody){
        Fcdsl fcdsl = new Fcdsl();
        Fcdsl.setSingleOtherMap(fcdsl, FieldNames.WEBHOOK_REQUEST_BODY, JsonUtils.toJson(webhookRequestBody));
        Object data = requestJsonByFcdsl(SN_20, VERSION_1, NEW_OP_RETURN_BY_FIDS, fcdsl, AuthType.FC_SIGN_BODY, sessionKey, RequestMethod.POST);
        if(data==null)return null;
        return objectToMap(data,String.class,String.class);
    }
    public Cid searchCidOrFid(BufferedReader br) {
        String choose = chooseFid(br);
        if (choose == null) return null;
        return cidInfoById(choose);
    }

    public String chooseFid(BufferedReader br) {
        while (true) {
            String input = Inputer.inputString(br, "Input CID, FID or a part of them:");
            Map<String, String[]> result = fidCidSeek(input, RequestMethod.POST, AuthType.FC_SIGN_BODY);
            if (result == null || result.isEmpty()) return null;
            Object chosen = Inputer.chooseOneFromMapArray(result, false, false, "Choose one:", br);
            if (chosen == null) {
                if (Inputer.askIfYes(br, "Try again?")) {
                }
                else return null;
            } else {
                return chosen.toString();
            }
        }
    }
    @Nullable
    public static List<?> simpleSearch(ApipClient apipClient, Class<?> dataClass, String indexName, String searchFieldName, String searchValue, String sinceFieldName, long sinceHeight, List<Sort> sortList, int size, final List<String> last) {
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addIndex(indexName);
        if(searchFieldName!=null)fcdsl.addNewQuery().addNewTerms().addNewFields(searchFieldName).addNewValues(searchValue);
        if(sinceFieldName!=null)fcdsl.getQuery().addNewRange().addNewFields(sinceFieldName).addGt(String.valueOf(sinceHeight));
        for(Sort sort: sortList)fcdsl.addSort(sort.getField(),sort.getOrder());
        if(last !=null)fcdsl.addAfter(last).addSize(size);
    
        ReplyBody result = apipClient.general(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null || result.getData()==null) return null;
        if(last!=null){
            last.clear();
            last.addAll(result.getLast());
        }
        return ObjectUtils.objectToList(result.getData(), dataClass);
    }
    public void updateUnconfirmedValidCash(List<Cash> meetList, String fid) {
        if(meetList==null || meetList.isEmpty())return;
        List<Cash> addingList = new ArrayList<>();
        List<String> removingIdList = new ArrayList<>();
        Map<String, List<Cash>> result = unconfirmedCaches(RequestMethod.POST, AuthType.FC_SIGN_BODY,fid);
        if(result!=null){
            List<Cash> unconfirmedCashList = result.get(fid);
            if(unconfirmedCashList!=null){
                for(Cash cash : unconfirmedCashList){
                    if(cash.isValid() && fid!=null){
                        addingList.add(cash);
                    }else{
                        removingIdList.add(cash.getId());
                    }
                }
            }
            if(!addingList.isEmpty()&& fid!=null){
                meetList.addAll(addingList);
            }
            if(!removingIdList.isEmpty()){
                for(String id : removingIdList){
                    meetList.removeIf(cash -> cash.getId().equals(id));
                }
            }
        }
    }
    
    @Nullable
    public <T> Map<String,T> loadOnChainItemByIds(String index, Class<T> tClass, List<String> ids) {
        if (ids == null || ids.isEmpty()) return null;
        
        // Create Fcdsl object
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.setIndex(index);
        fcdsl.addIds(ids);

        // Make request
        ReplyBody result = general(fcdsl, RequestMethod.POST, AuthType.FC_SIGN_BODY);
        if(result==null || result.getData()==null) return null;
        // Convert and return result
        List<T> dataList = objectToList(result.getData(),tClass);
        Map<String,T> resultMap = new HashMap<>();
        for(T data : dataList){
            try {
                java.lang.reflect.Field field = data.getClass().getDeclaredField(ID);
                field.setAccessible(true);
                String id = field.get(data).toString();
                resultMap.put(id, data);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                System.out.println("Error accessing the ID field: " + e.getMessage());
            }
        }
        return resultMap;
    }

    public static class ApipApiNames {
        public static List<String> apiList = new ArrayList<>();
        public static String[] apipAPIs;
        public static ArrayList<String> freeApiList = new ArrayList<>();

        public static final String VERSION_1 ="v1";
        public static final String VERSION_2 ="v2";
        public static final String SN_0 = "sn0";
        public static final String SN_1 = "sn1";
        public static final String SN_2 = "sn2";
        public static final String SN_3 = "sn3";
        public static final String SN_4 = "sn4";
        public static final String SN_5 = "sn5";
        public static final String SN_6 = "sn6";
        public static final String SN_7 = "sn7";
        public static final String SN_8 = "sn8";
        public static final String SN_9 = "sn9";
        public static final String SN_10 = "sn10";

        public static final String SN_11 = "sn11";
        public static final String SN_12 = "sn12";
        public static final String SN_13 = "sn13";
        public static final String SN_14 = "sn14";
        public static final String SN_15 = "sn15";
        public static final String SN_16 = "sn16";
        public static final String SN_17 = "sn17";
        public static final String SN_18 = "sn18";
        public static final String SN_19 = "sn19";
        public static final String SN_20 = "sn20";
        public static final String SN_21 = "sn21";
        public static final String Carve = "carve";

        public static String[] openAPIs;
        public static String[] fcdslAPIs;
        public static String[] freeAPIs;
        public static String[] swapHallAPIs;
        public static String[] blockchainAPIs;
        public static String[] identityAPIs;
        public static String[] organizeAPIs;
        public static String[] constructAPIs;
        public static String[] personalAPIs;
        public static String[] publishAPIs;
        public static String[] walletAPIs;
        public static String[] cryptoAPIs;
        public static String[] endpointAPIs;
        //APIP path
    //    public static final String APIP0V1Path = "/apip0/v1/";
    //    public static final String APIP1V1Path = "/apip1/v1/";
    //    public static final String APIP2V1Path = "/apip2/v1/";
    //    public static final String APIP3V1Path = "/apip3/v1/";
    //    public static final String APIP4V1Path = "/apip4/v1/";
    //    public static final String APIP5V1Path = "/apip5/v1/";
    //    public static final String APIP6V1Path = "/apip6/v1/";
    //    public static final String APIP7V1Path = "/apip7/v1/";
    //    public static final String APIP8V1Path = "/apip8/v1/";
    //    public static final String APIP9V1Path = "/apip9/v1/";
    //    public static final String APIP10V1Path = "/apip10/v1/";
    //    public static final String APIP11V1Path = "/apip11/v1/";
    //    public static final String APIP12V1Path = "/apip12/v1/";
    //    public static final String APIP13V1Path = "/apip13/v1/";
    //    public static final String APIP14V1Path = "/apip14/v1/";
    //    public static final String APIP15V1Path = "/apip15/v1/";
    //    public static final String APIP16V1Path = "/apip16/v1/";
    //    public static final String APIP17V1Path = "/apip17/v1/";
    //    public static final String APIP18V1Path = "/apip18/v1/";
    //    public static final String APIP19V1Path = "/apip19/v1/";
    //    public static final String APIP20V1Path = "/apip20/v1/";
    //    public static final String APIP21V1Path = "/apip21/v1/";
    //    public static final String FreeGetPath = "/freeGet/v1/";
    //    public static final String SwapHallPath = "/swapHall/v1/";
    //    public static final String ToolsPath = "/tools/";
        public static final String SIGN_IN = "signIn";
        public static final String PING = "ping";
        public static final String SIGN_IN_ECC = "signInEcc";
        public static final String GENERAL = "general";
        public static final String TOTALS = "totals";
        public static final String BLOCK_BY_IDS = "blockByIds";
        public static final String BLOCK_SEARCH = "blockSearch";
        public static final String BEST_BLOCK = "bestBlock";
        public static final String BLOCK_BY_HEIGHTS = "blockByHeights";
        public static final String CASH_BY_IDS = "cashByIds";
        public static final String GET_CASHES = "getCashes";
        public static final String CASH_SEARCH = "cashSearch";
        public static final String BALANCE_BY_IDS = "balanceByIds";

        public static final String TX_BY_IDS = "txByIds";
        public static final String TX_BY_FID = "txByFid";

        public static final String TX_SEARCH = "txSearch";
        public static final String OP_RETURN_BY_IDS = "opReturnByIds";
        public static final String OP_RETURN_SEARCH = "opReturnSearch";
        public static final String UNCONFIRMED = "unconfirmed";
        public static final String UNCONFIRMED_CASHES = "unconfirmedCashes";
        public static final String BLOCK_HAS_BY_IDS = "blockHasByIds";
        public static final String TX_HAS_BY_IDS = "TxHasByIds";
        public static final String CASH_VALID = "cashValid";
        public static final String GET_UTXO = "getUtxo";
        public static final String FID_BY_IDS = "fidByIds";
        public static final String FID_SEARCH = "fidSearch";
        public static final String FID_CID_SEEK = "fidCidSeek";
        public static final String NEW_OP_RETURN_BY_FIDS ="newOpReturnByFids";

        public static final String GET_FID_CID = "getFidCid";
        public static final String CID_BY_IDS = "cidByIds";
        public static final String CID_SEARCH = "cidSearch";
        public static final String CID_HISTORY = "cidHistory";
        public static final String HOMEPAGE_HISTORY = "homepageHistory";
        public static final String NOTICE_FEE_HISTORY = "noticeFeeHistory";
        public static final String REPUTATION_HISTORY = "reputationHistory";
        public static final String NOBODY_SEARCH = "nobodySearch";
        public static final String P_2_SH_BY_IDS = "p2shByIds";
        public static final String P_2_SH_SEARCH = "p2shSearch";
        public static final String PROTOCOL_BY_IDS = "protocolByIds";
        public static final String PROTOCOL_SEARCH = "protocolSearch";
        public static final String PROTOCOL_OP_HISTORY = "protocolOpHistory";
        public static final String PROTOCOL_RATE_HISTORY = "protocolRateHistory";
        public static final String CODE_BY_IDS = "codeByIds";
        public static final String CODE_SEARCH = "codeSearch";
        public static final String CODE_OP_HISTORY = "codeOpHistory";
        public static final String CODE_RATE_HISTORY = "codeRateHistory";
        public static final String SERVICE_BY_IDS = "serviceByIds";
        public static final String GET_BEST_BLOCK = "getBestBlock";
        public static final String GET_SERVICES = "getServices";
        public static final String GET_SERVICE = "getService";
        public static final String GET_FREE_SERVICE = "getFreeService";
        public static final String SERVICE_SEARCH = "serviceSearch";
        public static final String SERVICE_OP_HISTORY = "serviceOpHistory";
        public static final String SERVICE_RATE_HISTORY = "serviceRateHistory";
        public static final String APP_BY_IDS = "appByIds";
        public static final String GET_APPS = "getApps";
        public static final String APP_SEARCH = "appSearch";
        public static final String APP_OP_HISTORY = "appOpHistory";
        public static final String APP_RATE_HISTORY = "appRateHistory";
        public static final String GROUP_BY_IDS = "groupByIds";
        public static final String GROUP_SEARCH = "groupSearch";
        public static final String GROUP_MEMBERS = "groupMembers";
        public static final String GROUP_EX_MEMBERS = "groupExMembers";
        public static final String MY_GROUPS = "myGroups";
        public static final String GROUP_OP_HISTORY = "groupOpHistory";
        public static final String TEAM_BY_IDS = "teamByIds";
        public static final String TEAM_SEARCH = "teamSearch";
        public static final String TEAM_MEMBERS = "teamMembers";
        public static final String TEAM_EX_MEMBERS = "teamExMembers";
        public static final String TEAM_OTHER_PERSONS = "teamOtherPersons";
        public static final String MY_TEAMS = "myTeams";
        public static final String TEAM_OP_HISTORY = "teamOpHistory";
        public static final String TEAM_RATE_HISTORY = "teamRateHistory";
        public static final String CONTACT_BY_IDS = "contactByIds";
        public static final String CONTACT_SEARCH = "contactSearch";
        public static final String CONTACTS_DELETED = "contactsDeleted";
        public static final String SECRET_BY_IDS = "secretByIds";
        public static final String SECRET_SEARCH = "secretSearch";
        public static final String SECRETS_DELETED = "secretsDeleted";
        public static final String MAIL_BY_IDS = "mailByIds";
        public static final String MAIL_SEARCH = "mailSearch";
        public static final String MAILS_DELETED = "mailsDeleted";
        public static final String MAIL_THREAD = "mailThread";
        public static final String STATEMENT_BY_IDS = "statementByIds";
        public static final String STATEMENT_SEARCH = "statementSearch";
        public static final String PROOF_BY_IDS = "proofByIds";
        public static final String PROOF_SEARCH = "proofSearch";
        public static final String PROOF_HISTORY = "proofHistory";
        public static final String BOX_BY_IDS = "boxByIds";
        public static final String BOX_SEARCH = "boxSearch";
        public static final String BOX_HISTORY = "boxHistory";
        public static final String AVATARS = "avatars";
        public static final String GET_AVATAR = "getAvatar";
        public static final String DECODE_TX = "decodeTx";
        public static final String BROADCAST_TX = "broadcastTx";
        public static final String FEE_RATE = "feeRate";
        public static final String BROADCAST = "broadcast";
        public static final String GET_TOTALS = "getTotals";
        public static final String GET_PRICES = "getPrices";
        public static final String NID_SEARCH = "nidSearch";
        public static final String ENCRYPT = "encrypt";
        public static final String SHA_256 = "sha256";
        public static final String HEX_TO_BASE_58 = "hexToBase58";
        public static final String CHECK_SUM_4_HEX = "checkSum4Hex";
        public static final String SHA_256_X_2 = "sha256x2";
        public static final String SHA_256_HEX = "sha256Hex";
        public static final String RIPEMD_160_HEX = "ripemd160Hex";
        public static final String KECCAK_SHA_3_HEX = "keccakSha3Hex";
        public static final String SHA_256_X_2_HEX = "sha256x2Hex";
        public static final String VERIFY = "verify";
        public static final String OFF_LINE_TX = "offLineTx";
        public static final String ADDRESSES = "addresses";
        public static final String NEW_CASH_BY_FIDS = "newCashByFids";
        public static final String OP_RETURN_BY_FIDS = "opReturnByFids";
        public static final String NOBODY_BY_IDS ="nobodyByIds";

        public static final String SWAP_REGISTER ="swapRegister";
        public static final String SWAP_UPDATE ="swapUpdate";
        public static final String SWAP_STATE ="swapState";
        public static final String SWAP_LP ="swapLp";
        public static final String SWAP_PENDING ="swapPending";
        public static final String SWAP_FINISHED ="swapFinished";
        public static final String SWAP_PRICE ="swapPrice";
        public static final String SWAP_INFO ="swapInfo";

        public static final String MY_TOKENS = "myTokens";
        public static final String TOKEN_BY_IDS = "tokenByIds";
        public static final String TOKEN_HISTORY = "tokenHistory";
        public static final String TOKEN_HOLDERS_BY_IDS = "tokenHoldersByIds";
        public static final String TOKEN_HOLDER_SEARCH = "tokenHolderSearch";
        public static final String TOKEN_SEARCH = "tokenSearch";

        public static final String CIRCULATING = "circulating";
        public static final String RICHLIST = "richlist";
        public static final String FREECASH_INFO = "freecashInfo";
        public static final String TOTAL_SUPPLY = "totalSupply";

        public static final String CHAIN_INFO ="chainInfo";
        public static final String DIFFICULTY_HISTORY ="difficultyHistory";
        public static final String HASH_RATE_HISTORY ="hashRateHistory";
        public static final String BLOCK_TIME_HISTORY ="blockTimeHistory";
        public static final String NEW_ORDER = "newOrder";


        static {
            openAPIs = new String[]{
                    PING, GET_SERVICE, SIGN_IN, SIGN_IN_ECC, TOTALS
            };
            fcdslAPIs = new String[]{GENERAL};

            blockchainAPIs = new String[]{
                    BLOCK_SEARCH, BEST_BLOCK, BLOCK_BY_IDS, BLOCK_BY_HEIGHTS,
                    CASH_SEARCH, CASH_BY_IDS,
                    FID_SEARCH, FID_BY_IDS,
                    OP_RETURN_SEARCH, OP_RETURN_BY_IDS,
                    P_2_SH_SEARCH, P_2_SH_BY_IDS,
                    TX_SEARCH, TX_BY_IDS, TX_BY_FID,
                    CHAIN_INFO, BLOCK_TIME_HISTORY, DIFFICULTY_HISTORY, HASH_RATE_HISTORY
            };

            identityAPIs = new String[]{
                    CID_SEARCH, CID_BY_IDS, CID_HISTORY,
                    FID_CID_SEEK, GET_FID_CID,
                    NOBODY_SEARCH, NOBODY_BY_IDS,
                    HOMEPAGE_HISTORY, NOTICE_FEE_HISTORY, REPUTATION_HISTORY,
                    GET_AVATAR, AVATARS
            };

            organizeAPIs = new String[]{
                    GROUP_SEARCH, GROUP_BY_IDS, GROUP_MEMBERS, GROUP_OP_HISTORY, MY_GROUPS,
                    TEAM_SEARCH, TEAM_BY_IDS, TEAM_MEMBERS, TEAM_EX_MEMBERS,
                    TEAM_OP_HISTORY, TEAM_RATE_HISTORY, TEAM_OTHER_PERSONS, MY_TEAMS
            };

            constructAPIs = new String[]{
                    PROTOCOL_SEARCH, PROTOCOL_BY_IDS, PROTOCOL_OP_HISTORY, PROTOCOL_RATE_HISTORY,
                    CODE_SEARCH, CODE_BY_IDS, CODE_OP_HISTORY, CODE_RATE_HISTORY,
                    SERVICE_SEARCH, SERVICE_BY_IDS, SERVICE_OP_HISTORY, SERVICE_RATE_HISTORY,
                    APP_SEARCH, APP_BY_IDS, APP_OP_HISTORY, APP_RATE_HISTORY
            };

            personalAPIs = new String[]{
                    BOX_SEARCH, BOX_BY_IDS, BOX_HISTORY,
                    CONTACT_SEARCH, CONTACT_BY_IDS, CONTACTS_DELETED,
                    SECRET_SEARCH, SECRET_BY_IDS, SECRETS_DELETED,
                    MAIL_SEARCH, MAIL_BY_IDS, MAILS_DELETED, MAIL_THREAD
            };

            publishAPIs = new String[]{
                    PROOF_SEARCH, PROOF_BY_IDS, PROOF_HISTORY,
                    STATEMENT_SEARCH, STATEMENT_BY_IDS, NID_SEARCH,
                    TOKEN_SEARCH, TOKEN_BY_IDS, TOKEN_HISTORY,
                    TOKEN_HOLDER_SEARCH, TOKEN_HOLDERS_BY_IDS, MY_TOKENS
            };

            walletAPIs = new String[]{
                    BROADCAST_TX, DECODE_TX,
                    CASH_VALID, BALANCE_BY_IDS,
                    UNCONFIRMED, UNCONFIRMED_CASHES, FEE_RATE,
                    OFF_LINE_TX
            };

            cryptoAPIs = new String[]{
                    ADDRESSES,
                    ENCRYPT, VERIFY,
                    SHA_256, SHA_256_X_2, SHA_256_HEX, SHA_256_X_2_HEX,
                    RIPEMD_160_HEX, KECCAK_SHA_3_HEX,
                    CHECK_SUM_4_HEX, HEX_TO_BASE_58
            };
            endpointAPIs = new String[]{
                    NEW_CASH_BY_FIDS,
                    NEW_OP_RETURN_BY_FIDS
            };

            apiList.addAll(List.of(openAPIs));
            apiList.addAll(List.of(blockchainAPIs));
            apiList.addAll(List.of(identityAPIs));
            apiList.addAll(List.of(organizeAPIs));
            apiList.addAll(List.of(constructAPIs));
            apiList.addAll(List.of(personalAPIs));
            apiList.addAll(List.of(publishAPIs));
            apiList.addAll(List.of(walletAPIs));
            apiList.addAll(List.of(cryptoAPIs));
            apiList.addAll(List.of(endpointAPIs));

            apipAPIs = apiList.toArray(new String[0]);


            endpointAPIs = new String[]{
                    TOTAL_SUPPLY, CIRCULATING, RICHLIST, FREECASH_INFO
            };

            freeAPIs = new String[]{
                    PING, CHAIN_INFO, GET_SERVICE, FID_CID_SEEK, GET_FID_CID, GET_AVATAR, CASH_VALID, BROADCAST
            };

            freeApiList.add(ApipApiNames.GET_BEST_BLOCK);
            freeApiList.add(ApipApiNames.GET_FREE_SERVICE);
            freeApiList.add(ApipApiNames.GET_AVATAR);
            freeApiList.add(ApipApiNames.GET_TOTALS);
            freeApiList.add(ApipApiNames.GET_PRICES);
            freeApiList.add(ApipApiNames.GET_APPS);
            freeApiList.add(ApipApiNames.GET_CASHES);
            freeApiList.add(ApipApiNames.GET_FID_CID);
            freeApiList.add(ApipApiNames.GET_SERVICES);



            swapHallAPIs = new String[]{
                    SWAP_REGISTER, SWAP_UPDATE, SWAP_STATE,
                    SWAP_LP, SWAP_PENDING, SWAP_FINISHED,
                    SWAP_PRICE, SWAP_INFO, SWAP_INFO
            };

        }
    }
}
