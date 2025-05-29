package com.fc.fc_ajdk.clients.NaSaClient;

import com.google.gson.Gson;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.http.HttpUtils;
import com.fc.fc_ajdk.constants.NetNames;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class RpcRequest {

    private String jsonrpc = "2.0";


    private String id;


    private String method;


    private Object[] params;

    // Constructor


    public RpcRequest(
            String method,
            Object[] params) {
        this.method = method;
        this.params = params;
        byte[] rd = BytesUtils.getRandomBytes(4);
        this.id = String.valueOf(BytesUtils.byte4ArrayToUnsignedInt(rd));
    }

    public static Object requestRpc(String url, String username, String password, String method, RpcRequest jsonRPC2Request) {

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put(NetNames.HeaderKey_Accept, NetNames.HeaderValue_Application_Json);
        headerMap.put(NetNames.HeaderKey_Content_type, NetNames.HeaderValue_Application_Json);
        if (username != null) {
            String cred = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            headerMap.put(NetNames.HeaderKey_Authorization, "Basic " + cred);
        }
        
        // Replace ApacheHttp with HttpUtils
        String requestBody = jsonRPC2Request.toJson();
        okhttp3.Response response = HttpUtils.post(url, headerMap, HttpUtils.BodyType.STRING, requestBody.getBytes());

        boolean badResponse = false;
        if (response == null || !response.isSuccessful()) {
            badResponse = true;
        }

        String bodyStr = null;
        RpcResponse rpcResponse = null;
        if (response != null && response.body() != null) {
            try {
                bodyStr = response.body().string();
                rpcResponse = new Gson().fromJson(bodyStr, RpcResponse.class);
                if (rpcResponse.isBadResult(method)) badResponse = true;
            } catch (Exception e) {
                e.printStackTrace();
                badResponse = true;
            }
        }

        String error;
        if(rpcResponse==null){
            error = "Unknown error from NasaRPC.";
            return error;
        } else if(rpcResponse.getError()!=null)error= rpcResponse.getError().getCode() + ":" + rpcResponse.getError().getMessage();
        else error = "Unknown error from NasaRPC.";
        return badResponse ? error : rpcResponse.getResult();
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Object[] getParams() {
        return params;
    }

    public void setParams(Object[] params) {
        this.params = params;
    }
}
