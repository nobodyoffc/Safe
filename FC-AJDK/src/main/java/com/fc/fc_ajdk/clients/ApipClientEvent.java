package com.fc.fc_ajdk.clients;

import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.FcSession;
import com.fc.fc_ajdk.data.fcData.ReplyBody;
import com.fc.fc_ajdk.data.fcData.Signature;
import com.fc.fc_ajdk.data.apipData.Fcdsl;
import com.fc.fc_ajdk.data.apipData.RequestBody;
import com.fc.fc_ajdk.core.fch.FchMainNetwork;
import com.fc.fc_ajdk.utils.http.ContentType;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.constants.CodeMessage;
import com.fc.fc_ajdk.constants.UpStrings;
import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.fc_ajdk.utils.http.AuthType;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

import org.bitcoinj.core.ECKey;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static com.fc.fc_ajdk.constants.UpStrings.CODE;
import static com.fc.fc_ajdk.constants.UpStrings.SESSION_NAME;
import static com.fc.fc_ajdk.constants.UpStrings.SIGN;
import static com.fc.fc_ajdk.data.fcData.Signature.isGoodSha256Sign;
import static com.fc.fc_ajdk.clients.ApipClientEvent.RequestBodyType.*;
import static com.fc.fc_ajdk.utils.http.HttpUtils.CONTENT_TYPE;

public class ApipClientEvent {

    protected ApiUrl apiUrl;
    protected Fcdsl fcdsl;
    protected Map<String, String> requestHeaderMap;
    protected Signature signatureOfRequest;
    protected Map<String,String>requestParamMap;
    protected RequestBody requestBody;
    protected RequestBodyType requestBodyType;
    protected String requestBodyStr;
    protected byte[] requestBodyBytes;
    protected Map<String, String> responseHeaderMap;
    protected ReplyBody responseBody;
    protected String responseBodyStr;
    protected String requestFileName;
    protected ResponseBodyType responseBodyType;
    protected String responseFileName;
    protected String responseFilePath;
    protected byte[] responseBodyBytes;
    protected Signature signatureOfResponse;
    protected Response httpResponse;
    protected AuthType authType;
    protected String via;
    protected Integer code;
    protected String message;

    public enum RequestBodyType {
        NONE,STRING,BYTES,FILE,FCDSL
    }
    public enum ResponseBodyType {
        FC_REPLY,BYTES,FILE,STRING
    }

    public ApipClientEvent() {
    }
    public ApipClientEvent(String urlHead, String sn, String ver, String apiName,
                           RequestBodyType requestBodyType,
                           @Nullable Fcdsl fcdsl,
                           @Nullable String requestBodyStr,
                           @Nullable byte[] requestBodyBytes,
                           @Nullable Map<String,String>paramMap,
                           @Nullable String requestFileName,
                           ResponseBodyType responseBodyType,
                           @Nullable String responseFileName,
                           @Nullable String responseFilePath,
                           AuthType authType,
                           @Nullable byte[] authKey,
                           @Nullable String via) {
        String urlTail = ApiUrl.makeUrlTailPath(sn,ver)+apiName;
        initiate(urlHead, urlTail, requestBodyType, fcdsl, requestBodyStr, requestBodyBytes, paramMap,requestFileName, responseBodyType,responseFileName,responseFilePath,authType, authKey, via);
    }
    public ApipClientEvent(String urlHead, String urlTail,
                           RequestBodyType requestBodyType,
                           @Nullable Fcdsl fcdsl,
                           @Nullable String requestBodyStr,
                           @Nullable byte[] requestBodyBytes,
                           @Nullable Map<String,String>paramMap,
                           @Nullable String requestFileName,
                           ResponseBodyType responseBodyType,
                           @Nullable String responseFileName,
                           @Nullable String responseFilePath,
                           AuthType authType,
                           @Nullable byte[] authKey,
                           @Nullable String via) {
        initiate(urlHead, urlTail, requestBodyType, fcdsl, requestBodyStr, requestBodyBytes, paramMap,requestFileName,responseBodyType,responseFileName,responseFilePath, authType, authKey, via);
    }

    private void initiate(String urlHead, String urlTail, RequestBodyType requestBodyType, @Nullable Fcdsl fcdsl, @Nullable String requestBodyStr,@Nullable byte[] requestBodyBytes, @Nullable Map<String, String> paramMap,String requestFileName,
                          @Nullable ResponseBodyType responseBodyType, @Nullable String responseFileName, @Nullable String responseFilePath,
                          AuthType authType, @Nullable byte [] authKey, @Nullable String via) {
        boolean signUrl= AuthType.FC_SIGN_URL.equals(authType);
        apiUrl=new ApiUrl(urlHead, urlTail, paramMap,signUrl, via);
        this.requestBodyType = requestBodyType;
        this.responseBodyType = responseBodyType;
        this.requestFileName = requestFileName;
        this.responseFileName = responseFileName;
        this.responseFilePath = responseFilePath;
        this.authType = authType;
        this.via = via;
        if(this.requestHeaderMap==null)requestHeaderMap = new HashMap<>();
        switch (requestBodyType){
            case FCDSL -> {
                this.fcdsl = fcdsl;
                makeFcdslRequest(urlHead,apiUrl.getUrlTail(), via, fcdsl);
            }
            case STRING -> {
                this.requestBodyStr = requestBodyStr;
                if(requestBodyStr !=null)
                    this.requestBodyBytes = requestBodyStr.getBytes();
                else if (paramMap != null) {
                    requestBody = new RequestBody(apiUrl.getUrl(), via);
                    requestBody.setData(paramMap);
                    if(requestBody!=null)
                        this.requestBodyBytes = JsonUtils.toJson(requestBody).getBytes();
                    requestHeaderMap.put(CONTENT_TYPE, ContentType.APPLICATION_JSON.getType());
                }
                requestHeaderMap.put("Content-Type", ContentType.TEXT_PLAIN.toString());
            }
            case BYTES -> {
                this.requestBodyBytes = requestBodyBytes;
                requestHeaderMap.put("Content-Type", ContentType.APPLICATION_OCTET_STREAM.toString());
            }
            case FILE -> {
                this.requestFileName = requestFileName;
                requestHeaderMap.put("Content-Type", ContentType.APPLICATION_OCTET_STREAM.toString());
            }
            default -> {}
        }

        if(authType.equals(AuthType.FC_SIGN_BODY)){
            makeHeaderSession(authKey, this.requestBodyBytes);
        }else if (AuthType.FC_SIGN_URL.equals(authType))
            makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
    }

    public ApipClientEvent(String urlHead, String sn, String ver, String apiName, Map<String,String>paramMap, AuthType authType, byte[] authKey, String via) {
        this.requestParamMap=paramMap;
        this.via=via;
        this.authType=authType;
        switch (authType){
            case FC_SIGN_URL -> {
                apiUrl=new ApiUrl(urlHead, sn,ver,apiName,paramMap, true,via);
                makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
            }
            case FC_SIGN_BODY -> {
                apiUrl=new ApiUrl(urlHead, sn,ver,apiName,null, null,null);
                requestBody=new RequestBody(apiUrl.getUrl(),via);
                requestBody.setData(paramMap);
                requestHeaderMap = new HashMap<>();
                makeRequestBodyBytes();
                makeHeaderSession(authKey,requestBodyBytes);
            }
            default -> apiUrl=new ApiUrl(urlHead, sn,ver,apiName,null, null,via);
        }
    }
    public ApipClientEvent(String url) {
        apiUrl=new ApiUrl(url);
    }
    public ApipClientEvent(String urlHead, String urlTail) {
        apiUrl=new ApiUrl(urlHead,urlTail,null,null,null);
    }
    public ApipClientEvent(String urlHead, String urlTailPath, String apiName) {
        apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,null,null,null);
    }
    public ApipClientEvent(String urlHead, String urlTailPath, String apiName, Map<String,String>paramMap) {
        apiUrl=new ApiUrl(urlHead,urlTailPath, apiName,paramMap,false,null);
    }
    public ApipClientEvent(String urlHead, String sn, String version, String apiName) {
        apiUrl=new ApiUrl(urlHead, sn, version, apiName,null,null,null);
    }
    public ApipClientEvent(String urlHead, String sn, String version, String apiName, Map<String,String>paramMap) {
        apiUrl=new ApiUrl(urlHead, sn, version, apiName,paramMap,false,null);
    }
    public ApipClientEvent(String urlHead, String sn, String ver, String apiName, Fcdsl fcdsl, AuthType authType, byte[] authKey, String via) {
        boolean signUrl= AuthType.FC_SIGN_URL.equals(authType);
        apiUrl=new ApiUrl(urlHead, sn, ver, apiName,null,signUrl,via);
        this.fcdsl =fcdsl;
        makeFcdslRequest(urlHead,apiUrl.getUrlTail(),via,fcdsl);
        if(authType.equals(AuthType.FC_SIGN_BODY)){
            makeHeaderSession(authKey,requestBodyBytes);
        }else if (AuthType.FC_SIGN_URL.equals(authType))
            makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
    }

    public ApipClientEvent(byte[] authKey, String urlHead, String urlTailPath, String apiName, Fcdsl fcdsl, AuthType authType, String via) {
        urlTailPath=ApiUrl.formatUrlPath(urlTailPath);
        apiUrl=new ApiUrl(urlHead,urlTailPath+apiName);
        this.fcdsl =fcdsl;
        makeFcdslRequest(urlHead,apiUrl.getUrlTail(),via,fcdsl);
        if(authType.equals(AuthType.FC_SIGN_BODY)){
            makeHeaderSession(authKey,requestBodyBytes);
        }else if (AuthType.FC_SIGN_URL.equals(authType))makeHeaderSession(authKey,apiUrl.getUrl().getBytes());
    }



    public int checkResponse() {
        if (responseBody == null) {
            code = CodeMessage.Code3001ResponseIsNull;
            message = CodeMessage.Msg3001ResponseIsNull;
            return code;
        }

        if (responseBody.getCode() != 0) {
            code = responseBody.getCode();
            message = responseBody.getMessage();
            return code;
        }

        if (responseBody.getData() == null) {
            code = CodeMessage.Code3005ResponseDataIsNull;
            message = CodeMessage.Msg3005ResponseDataIsNull;
            return code;
        }
        return 0;
    }

    public void signInPost(@Nullable String via, byte[] priKey, @Nullable RequestBody.SignInMode mode){
        makeSignInRequest(via, mode);
        makeHeaderAsySign(priKey);
        post();
        if(responseBody!=null){
            code = responseBody.getCode();
            message = responseBody.getMessage();
        }else{
            code = 1020;
            message = "Failed to sign in.";
        }
    }

    public boolean get() {
        return get(null);
    }

    public boolean get(@Nullable byte[] sessionKey){
        if(responseBodyType==null)responseBodyType=ResponseBodyType.FC_REPLY;

        if(authType == AuthType.FC_SIGN_URL || authType == AuthType.FC_SIGN_BODY ){
            if(sessionKey==null){
                code = CodeMessage.Code1023MissSessionKey;
                message = CodeMessage.Msg1023MissSessionKey;
                return false;
            }
        }

        if (apiUrl.getUrl() == null) {
            code = CodeMessage.Code3004RequestUrlIsAbsent;
            message = CodeMessage.Msg3004RequestUrlIsAbsent;
            System.out.println(message);
            return false;
        }

        OkHttpClient client = new OkHttpClient();
        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl.getUrl());

        // add request headers
        if(requestHeaderMap!=null) {
            for (String head : requestHeaderMap.keySet()) {
                requestBuilder.addHeader(head, requestHeaderMap.get(head));
            }
        }

        Request request = requestBuilder.build();

        try {
            httpResponse = client.newCall(request).execute();
        } catch (IOException e) {
            Timber.d("Failed to connect %s. Check the URL.", apiUrl.getUrl());
            code = CodeMessage.Code3001ResponseIsNull;
            message = CodeMessage.Msg3001ResponseIsNull;
            return false;
        }

        //TODO
        System.out.println("Request Url GET: "+apiUrl.getUrl());

        if(httpResponse==null){
            code= CodeMessage.Code3002GetRequestFailed;
            message = CodeMessage.Msg3002GetRequestFailed;
            return false;
        }

        parseResponseHeader();

        try {
            return makeReply(sessionKey);
        } catch (IOException e) {
            code = CodeMessage.Code3007ErrorWhenRequestingPost;
            message = CodeMessage.Msg3007ErrorWhenRequestingPost+":"+e.getMessage();
            return false;
        }
    }

    private boolean makeReply(byte[] sessionKey) throws IOException {
        switch (responseBodyType){
            case STRING ->{
                return makeStringReply(sessionKey);
            }
            case FC_REPLY ->{
                return makeFcReply(sessionKey);
            }
            case BYTES -> {
                return makeBytesReply(sessionKey);
            }
            case FILE -> {
                return makeFileReply(sessionKey);
            }
            default -> {
                return makeDefaultReply();
            }
        }
    }

    private boolean makeDefaultReply() {
        code = CodeMessage.Code1020OtherError;
        message = "ResponseBodyType is "+responseBodyType+".";
        return false;
    }

    private boolean makeStringReply(byte[] sessionKey) throws IOException {
        ResponseBody responseBody = httpResponse.body();
        if (responseBody == null) {
            code = CodeMessage.Code3001ResponseIsNull;
            message = CodeMessage.Msg3001ResponseIsNull;
            return false;
        }
        responseBodyBytes = responseBody.bytes();
        responseBodyStr = new String(responseBodyBytes);
        code=0;
        return checkReplySign(sessionKey);
    }

    private boolean checkReplySign(byte[] sessionKey) {
        if(authType==AuthType.FREE)return true;
        if (responseHeaderMap != null && responseHeaderMap.get(SIGN) != null) {
            if (sessionKey ==null || !checkResponseSign(sessionKey)) {
                code = CodeMessage.Code1008BadSign;
                message = CodeMessage.Msg1008BadSign;
                return false;
            }
        }
        return true;
    }

    private boolean makeBytesReply(byte[] sessionKey) throws IOException {
        ResponseBody responseBody = httpResponse.body();
        if (responseBody == null) {
            code = CodeMessage.Code3001ResponseIsNull;
            message = CodeMessage.Msg3001ResponseIsNull;
            return false;
        }
        responseBodyBytes = responseBody.bytes();
        if(responseBodyBytes==null){
            code= CodeMessage.Code1020OtherError;
            message = "The response body is null.";
            return false;
        }
        code= CodeMessage.Code0Success;
        message= CodeMessage.Msg0Success;
        return checkReplySign(sessionKey);
    }

    private boolean makeFileReply(byte[] sessionKey) throws IOException {
        String code = responseHeaderMap.get(CODE);
        if(!"0".equals(code)) {
            return makeFcReply(sessionKey);
        }
        String fileName;
        if(responseFileName==null)fileName= StringUtils.getTempName();
        else fileName=responseFileName;
        String gotDid = downloadFileFromHttpResponse(fileName, responseFilePath);
        if(gotDid==null){
            this.code = CodeMessage.Code1020OtherError;
            message = "Failed to download file from HttpResponse.";
            return false;
        }
        if(responseFileName==null)
            Files.move(Paths.get(fileName),Paths.get(gotDid), StandardCopyOption.REPLACE_EXISTING);

        if(responseBody==null)responseBody=new ReplyBody();
        responseBody.setCode(CodeMessage.Code0Success);
        responseBody.setMessage(CodeMessage.Msg0Success);
        responseBody.setData(gotDid);

        return checkReplySign(sessionKey);
    }

    private boolean makeFcReply(byte[] sessionKey) throws IOException {
        ResponseBody responseBody = httpResponse.body();
        if (responseBody == null) {
            code = CodeMessage.Code3001ResponseIsNull;
            message = CodeMessage.Msg3001ResponseIsNull;
            return false;
        }
        responseBodyBytes = responseBody.bytes();
        responseBodyStr = new String(responseBodyBytes);
        parseFcResponse(httpResponse);
        try {
            this.responseBody = new Gson().fromJson(responseBodyStr, ReplyBody.class);
        } catch (JsonSyntaxException ignore) {
            Timber.d("Failed to parse responseBody json.");
            code= CodeMessage.Code1020OtherError;
            message = "Failed to parse responseBody from HttpResponse.";
            return false;
        }
        if(!checkReplySign(sessionKey))return false;
        return checkResponseCode();
    }

    private boolean checkResponseCode() {
        if(responseBodyType.equals(ResponseBodyType.FC_REPLY) && responseBody!=null){
            if(responseBody.getMessage()!=null)
                message = responseBody.getMessage();
            if(responseBody.getCode()!=null) {
                code = responseBody.getCode();
            }
        }
        return code != null && code == 0;
    }


    public String downloadFileFromHttpResponse(String did, String responseFilePath) {
        if(responseFilePath==null)responseFilePath=System.getProperty(Constants.UserDir);
        if(httpResponse==null)
            return null;
        String finalFileName=did;
        InputStream inputStream = null;
        try {
            ResponseBody responseBody = httpResponse.body();
            if (responseBody == null) {
                code = CodeMessage.Code3001ResponseIsNull;
                message = CodeMessage.Msg3001ResponseIsNull;
                return null;
            }
            inputStream = responseBody.byteStream();
        } catch (Exception e) {
            code= CodeMessage.Code1020OtherError;
            message="Failed to get inputStream from http response.";
            return null;
        }

        while(true) {
            File file = new File(responseFilePath,finalFileName);
            if (!file.exists()) {
                try {
                    boolean done = file.createNewFile();
                    if (!done) {
                        code = CodeMessage.Code1020OtherError;
                        message = "Failed to create file " + finalFileName;
                        return null;
                    }
                    break;
                } catch (IOException e) {
                    code = CodeMessage.Code1020OtherError;
                    message = "Failed to create file " + finalFileName;
                    return null;
                }
            }else{
                if(finalFileName.contains("_")){
                    try {
                        int order = Integer.parseInt(finalFileName.substring(finalFileName.indexOf("_")+1));
                        order++;
                        finalFileName = did.substring(0,6)+"_"+order;

                    }catch (Exception ignore){};
                }else{
                    finalFileName = did.substring(0,6)+"_"+1;
                }
            }
        }

        HashFunction hashFunction = Hashing.sha256();
        Hasher hasher = hashFunction.newHasher();

        if(!responseFilePath.endsWith("/"))responseFilePath=responseFilePath+"/";
        try(FileOutputStream outputStream = new FileOutputStream(responseFilePath+finalFileName)){
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                hasher.putBytes(buffer, 0, bytesRead);
            }
            inputStream.close();
        } catch (IOException e) {
            code= CodeMessage.Code1020OtherError;
            message="Failed to read buffer.";
            return null;
        }

        String didFromResponse = Hex.toHex(Hash.sha256(hasher.hash().asBytes()));

        if(!did.equals(didFromResponse)){
            code= CodeMessage.Code1020OtherError;
            message="The DID of the file from response is not equal to the requested DID.";
            return null;
        }

        if(!finalFileName.equals(did)){
            try {
                Files.move(Paths.get(responseFilePath,finalFileName), Paths.get(responseFilePath,did), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                code= CodeMessage.Code1020OtherError;
                message="Failed to replace the old file.";
                return null;
            }
        }

        return didFromResponse;
    }
    public boolean post() {
        return post(null);
    }

    public boolean post(@Nullable byte[] sessionKey) {
        if(this.requestBodyType==null)requestBodyType= STRING;
        if(responseBodyType==null)responseBodyType=ResponseBodyType.FC_REPLY;

        if (apiUrl.getUrl() == null) {
            code = CodeMessage.Code3004RequestUrlIsAbsent;
            message = CodeMessage.Msg3004RequestUrlIsAbsent;
            System.out.println(message);
            return false;
        }


        //TODO for test
        if(fcdsl!=null) {
            String params = Fcdsl.fcdslToUrlParams(fcdsl);
            if(params!=null && !params.isBlank())System.out.println("The FCDSL can be converted to GET URL:\n"+apiUrl.getUrl()+"?"+ params);
            else System.out.println("The FCDSL can be converted to GET URL:\n"+apiUrl.getUrl());
        }

        OkHttpClient client = new OkHttpClient();
        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl.getUrl());

        if (requestHeaderMap != null) {
            for (String key : requestHeaderMap.keySet()) {
                requestBuilder.addHeader(key, requestHeaderMap.get(key));
            }
        }

        okhttp3.RequestBody requestBody;
        switch (requestBodyType){
            case STRING ->{
                requestBody = okhttp3.RequestBody.create(
                        new String(requestBodyBytes),
                        MediaType.parse(ContentType.TEXT_PLAIN.toString())
                );
            }
            case FCDSL -> {
                requestBody = okhttp3.RequestBody.create(
                        new String(requestBodyBytes),
                        MediaType.parse(ContentType.APPLICATION_JSON.toString())
                );
            }
            case BYTES -> {
                requestBody = okhttp3.RequestBody.create(
                        requestBodyBytes,
                        MediaType.parse(ContentType.APPLICATION_OCTET_STREAM.toString())
                );
            }
            case FILE -> {
                File file = new File(requestFileName);
                if(!file.exists()){
                    this.code = CodeMessage.Code1020OtherError;
                    message = "File "+requestFileName+" doesn't exist.";
                    return false;
                }
                requestBody = okhttp3.RequestBody.create(
                        file,
                        MediaType.parse(ContentType.APPLICATION_OCTET_STREAM.toString())
                );
            }
            default -> {
                return false;
            }
        }

        Request request = requestBuilder.post(requestBody).build();

        try {
            httpResponse = client.newCall(request).execute();
        } catch (IOException e) {
            Timber.d("Failed to connect %s. Check the URL.", apiUrl.getUrl());
            code = CodeMessage.Code3001ResponseIsNull;
            message = CodeMessage.Msg3001ResponseIsNull;
            return false;
        }

        if (httpResponse == null) {
            Timber.d("httpResponse == null.");
            code = CodeMessage.Code3001ResponseIsNull;
            message = CodeMessage.Msg3001ResponseIsNull;
            return false;
        }

        if (!httpResponse.isSuccessful()) {
            Timber.d("Post response status: %d.%s", httpResponse.code(), httpResponse.message());
            ResponseBody responseBody = httpResponse.body();
            if (responseBody != null) {
                Timber.d("Post response error: %s", responseBody.toString());
            }
            
            String codeHeader = httpResponse.header(CODE);
            if (codeHeader != null) {
                code = Integer.valueOf(codeHeader);
                message = CodeMessage.getMsg(code);
                Timber.d("Code:%d. Message:%s", code, message);
            } else {
                code = CodeMessage.Code3006ResponseStatusWrong;
                message = CodeMessage.Msg3006ResponseStatusWrong + ": " + httpResponse.code();
                Timber.d("Code:%d. Message:%s", code, message);
            }
            return false;
        }

        parseResponseHeader();
        try {
            return makeReply(sessionKey);
        } catch (IOException e) {
            Timber.e(e, "Error when processing response.");
            code = CodeMessage.Code3007ErrorWhenRequestingPost;
            message = CodeMessage.Msg3007ErrorWhenRequestingPost+":"+e.getMessage();
            Timber.d("Code:%d. Message:%s", code, message);
            return false;
        }
    }

    private void parseResponseHeader() {
        this.responseHeaderMap = getHeaders(httpResponse);
        String sign = this.responseHeaderMap.get(SIGN);
        String sessionName = this.responseHeaderMap.get(SESSION_NAME);
        this.signatureOfResponse = new Signature(sign, sessionName);
    }

    protected void makeFcdslRequest(String urlHead, String urlTail, @Nullable String via, Fcdsl fcdsl) {
        if(apiUrl==null){
            apiUrl=new ApiUrl(urlHead,urlTail);
        }
        String url = ApiUrl.makeUrl(urlHead, urlTail);
        requestBody = new RequestBody(url, via);
        requestBody.setFcdsl(fcdsl);

        Gson gson = new Gson();
        requestBodyStr = gson.toJson(requestBody);
        requestBodyBytes = requestBodyStr.getBytes(StandardCharsets.UTF_8);

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put(CONTENT_TYPE, ContentType.APPLICATION_JSON.getType());
    }

    protected void makeHeaderAsySign(byte[] priKey) {
        if (priKey == null) return;

        ECKey ecKey = ECKey.fromPrivate(priKey);

        requestBodyStr = new String(requestBodyBytes, StandardCharsets.UTF_8);
        String sign = ecKey.signMessage(requestBodyStr);
        String fid = ecKey.toAddress(FchMainNetwork.MAINNETWORK).toBase58();

        signatureOfRequest = new Signature(fid, requestBodyStr, sign, AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7, null);
        requestHeaderMap.put(UpStrings.FID, fid);
        requestHeaderMap.put(SIGN, signatureOfRequest.getSign());
    }
    protected void makeFcdslRequest(@Nullable String via, @Nullable Fcdsl fcdsl) {
        requestBody = new RequestBody(apiUrl.getUrl(), via);
        if(fcdsl!=null)requestBody.setFcdsl(fcdsl);

        makeRequestBodyBytes();

        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    public void makeRequestBodyBytes() {
        this.requestBodyStr = JsonUtils.toJson(requestBody);
        requestBodyBytes = this.requestBodyStr.getBytes(StandardCharsets.UTF_8);
    }

    protected void makeSignInRequest(@Nullable String via, @Nullable RequestBody.SignInMode mode) {
        requestBody = new RequestBody(apiUrl.getUrl(), via, mode);
        makeRequestBodyBytes();
        requestHeaderMap = new HashMap<>();
        requestHeaderMap.put("Content-Type", "application/json");
    }

    public void makeHeaderSession(byte[] sessionKey,byte[] dataBytes) {
        String sign = FcSession.sign(sessionKey, dataBytes);
        signatureOfRequest = new Signature(sign, IdNameUtils.makeKeyName(sessionKey));
        if(requestHeaderMap==null)requestHeaderMap=new HashMap<>();
        requestHeaderMap.put(SESSION_NAME, signatureOfRequest.getKeyName());
        requestHeaderMap.put(SIGN, signatureOfRequest.getSign());
    }

    protected boolean parseFcResponse(Response response) {
        if (response == null) return false;
        Gson gson = new Gson();
        String sign;
        try {
            this.responseBody = gson.fromJson(responseBodyStr, ReplyBody.class);
        } catch (JsonSyntaxException ignore) {
            return false;
        }

        String signHeader = response.header(SIGN);
        if (signHeader != null) {
            this.responseHeaderMap.put(SIGN, signHeader);
            String symKeyName = response.header(SESSION_NAME);
            if (symKeyName != null) {
                this.responseHeaderMap.put(SESSION_NAME, symKeyName);
            }
            this.signatureOfResponse = new Signature(signHeader, symKeyName);
        }
        return true;
    }

    public static Map<String, String> getHeaders(Response response) {
        Map<String, String> headersMap = new HashMap<>();
        Headers headers = response.headers();

        for (int i = 0; i < headers.size(); i++) {
            headersMap.put(headers.name(i), headers.value(i));
        }

        return headersMap;
    }

    public boolean checkResponseSign(byte[] symKey) {
        return isGoodSha256Sign(responseBodyBytes, signatureOfResponse.getSign(), symKey);
    }

    public boolean checkRequestSign(byte[] symKey) {
        return isGoodSha256Sign(requestBodyBytes, signatureOfRequest.getSign(), symKey);
    }

    public String getType() {
        return Constants.APIP;
    }

    public Map<String, String> getRequestHeaderMap() {
        return requestHeaderMap;
    }

    public void setRequestHeaderMap(Map<String, String> requestHeaderMap) {
        this.requestHeaderMap = requestHeaderMap;
    }

    public Signature getSignatureOfRequest() {
        return signatureOfRequest;
    }

    public void setSignatureOfRequest(Signature signatureOfRequest) {
        this.signatureOfRequest = signatureOfRequest;
    }

    public RequestBody getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(RequestBody requestBody) {
        this.requestBody = requestBody;
    }

    public String getRequestBodyStr() {
        return requestBodyStr;
    }

    public void setRequestBodyStr(String requestBodyStr) {
        this.requestBodyStr = requestBodyStr;
    }

    public byte[] getRequestBodyBytes() {
        return requestBodyBytes;
    }

    public void setRequestBodyBytes(byte[] requestBodyBytes) {
        this.requestBodyBytes = requestBodyBytes;
    }

    public Map<String, String> getResponseHeaderMap() {
        return responseHeaderMap;
    }

    public void setResponseHeaderMap(Map<String, String> responseHeaderMap) {
        this.responseHeaderMap = responseHeaderMap;
    }

    public ReplyBody getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(ReplyBody responseBody) {
        this.responseBody = responseBody;
    }

    public String getResponseBodyStr() {
        return responseBodyStr;
    }

    public void setResponseBodyStr(String responseBodyStr) {
        this.responseBodyStr = responseBodyStr;
    }

    public byte[] getResponseBodyBytes() {
        return responseBodyBytes;
    }

    public void setResponseBodyBytes(byte[] responseBodyBytes) {
        this.responseBodyBytes = responseBodyBytes;
    }

    public Signature getSignatureOfResponse() {
        return signatureOfResponse;
    }

    public void setSignatureOfResponse(Signature signatureOfResponse) {
        this.signatureOfResponse = signatureOfResponse;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Fcdsl getFcdsl() {
        return fcdsl;
    }

    public void setFcdsl(Fcdsl fcdsl) {
        this.fcdsl = fcdsl;
    }

    public Response getHttpResponse() {
        return httpResponse;
    }

    public void setHttpResponse(Response httpResponse) {
        this.httpResponse = httpResponse;
    }

    public ApiUrl getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(ApiUrl apiUrl) {
        this.apiUrl = apiUrl;
    }

    public AuthType getAuthType() {
        return authType;
    }

    public void setAuthType(AuthType authType) {
        this.authType = authType;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Map<String, String> getRequestParamMap() {
        return requestParamMap;
    }

    public void setRequestParamMap(Map<String, String> requestParamMap) {
        this.requestParamMap = requestParamMap;
    }

    public String getRequestFileName() {
        return requestFileName;
    }

    public void setRequestFileName(String requestFileName) {
        this.requestFileName = requestFileName;
    }

    public String getResponseFileName() {
        return responseFileName;
    }

    public void setResponseFileName(String responseFileName) {
        this.responseFileName = responseFileName;
    }

    public String getResponseFilePath() {
        return responseFilePath;
    }

    public void setResponseFilePath(String responseFilePath) {
        this.responseFilePath = responseFilePath;
    }

    public ResponseBodyType getResponseBodyType() {
        return responseBodyType;
    }

    public void setResponseBodyType(ResponseBodyType responseBodyType) {
        this.responseBodyType = responseBodyType;
    }

    public RequestBodyType getRequestBodyType() {
        return requestBodyType;
    }

    public void setRequestBodyType(RequestBodyType requestBodyType) {
        this.requestBodyType = requestBodyType;
    }
}
