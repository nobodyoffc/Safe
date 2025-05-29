package com.fc.fc_ajdk.data.feipData.serviceParams;

import com.fc.fc_ajdk.clients.ApipClient;
import com.fc.fc_ajdk.data.feipData.Service;
import com.fc.fc_ajdk.ui.Inputer;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DiskParams extends Params {
    private String dataLifeDays;
    private String pricePerKBytesCarve;
    private transient ApipClient apipClient;

    public DiskParams(){
        super();
    };

    public static DiskParams fromObject(Object data) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(data), DiskParams.class);
    }
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        toMap(map);
        if (this.dataLifeDays != null) map.put("dataLifeDays", this.dataLifeDays);
        if (this.pricePerKBytesCarve != null) map.put("pricePerKBytesPermanent", this.pricePerKBytesCarve);
        return map;
    }

    // Method to create DiskParams from Map
    public static DiskParams fromMap(Map<String, String> map) {
        DiskParams params = new DiskParams();
        params.fromMap(map, params);
        params.dataLifeDays = map.get("dataLifeDays");
        params.pricePerKBytesCarve = map.get("pricePerKBytesPermanent");
        return params;
    }

    public void inputParams(BufferedReader br, byte[]symKey){
        inputParams(br,symKey,apipClient);
        this.dataLifeDays = Inputer.inputString(br,"Input the dataLifeDays:");
        this.pricePerKBytesCarve = Inputer.inputDoubleAsString(br,"Input the pricePerKBytesPermanent:");
    }

    public Params updateParams(BufferedReader br, byte[] symKey) {
        try {
            updateParams(br, symKey,apipClient );
            this.dataLifeDays = Inputer.promptAndUpdate(br,"dataLifeDays",this.dataLifeDays);
            this.pricePerKBytesCarve = Inputer.promptAndUpdate(br,"pricePerKBytesPermanent",this.pricePerKBytesCarve);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public static DiskParams getParamsFromService(Service service) {
        DiskParams params;
        Gson gson = new Gson();
        try {
            params = gson.fromJson(gson.toJson(service.getParams()), DiskParams.class);
        }catch (Exception e){
            System.out.println("Parse maker parameters from Service wrong："+e.getMessage());
            return null;
        }
        service.setParams(params);
        return params;
    }


    public String getDataLifeDays() {
        return dataLifeDays;
    }

    public void setDataLifeDays(String dataLifeDays) {
        this.dataLifeDays = dataLifeDays;
    }

    public String getPricePerKBytesCarve() {
        return pricePerKBytesCarve;
    }

    public void setPricePerKBytesCarve(String pricePerKBytesCarve) {
        this.pricePerKBytesCarve = pricePerKBytesCarve;
    }
    public ApipClient getApipClient() {
        return apipClient;
    }
}
