package com.fc.fc_ajdk.data.feipData.serviceParams;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class ApipParams extends Params{

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        toMap(map);
        return map;
    }

    public static ApipParams fromObject(Object data) {
        Gson gson = new Gson();
        return gson.fromJson(gson.toJson(data), ApipParams.class);
    }
}