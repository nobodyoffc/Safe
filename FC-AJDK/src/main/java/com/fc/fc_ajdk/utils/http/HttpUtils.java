package com.fc.fc_ajdk.utils.http;

import com.fc.fc_ajdk.utils.TimberLogger;
import okhttp3.*;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpUtils {
    public static final String TAG = "HttpUtils";
    public static final String CONTENT_TYPE = "Content-Type";
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public static String getApiNameFromUrl(String url) {
        int lastSlashIndex = url.lastIndexOf('/');
        if (lastSlashIndex == -1 || lastSlashIndex == url.length() - 1)return null;
        String name = url.substring(lastSlashIndex + 1);

        int firstQuestionIndex = name.indexOf('?');
        if(firstQuestionIndex!=-1){
            name = name.substring(0,firstQuestionIndex);
        }
        return name;
    }

    @Nullable
    public static Map<String, String> parseParamsMapFromUrl(String rawStr) {
        Map<String,String >paramMap = new HashMap<>();
        try {
            int questionMarkIndex = rawStr.indexOf('?');
            if (questionMarkIndex == -1) {
                return null;
            }
            String paramString = rawStr.substring(questionMarkIndex + 1);
            String[] pairs = paramString.split("&");

            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                String key = URLDecoder.decode(pair.substring(0, idx));
                String value = URLDecoder.decode(pair.substring(idx + 1));
                paramMap.put(key, value);
            }
        } catch (Exception e) {
            e.printStackTrace(); // Handle the exception appropriately in real code
        }
        return paramMap;
    }

    public enum BodyType {
        STRING,
        BYTES
    }

    public static Response post(String url, Map<String,String>requestHeaderMap, BodyType bodyType, byte[] requestBodyBytes) {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url);

            if (requestHeaderMap != null) {
                for (Map.Entry<String, String> entry : requestHeaderMap.entrySet()) {
                    requestBuilder.addHeader(entry.getKey(), entry.getValue());
                }
            }

            RequestBody requestBody;
            switch (bodyType) {
                case STRING -> requestBody = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        new String(requestBodyBytes)
                );
                case BYTES -> requestBody = RequestBody.create(
                        MediaType.parse("application/octet-stream"),
                        requestBodyBytes
                );
                default -> {
                    TimberLogger.d("Invalid body type");
                    return null;
                }
            }

            Request request = requestBuilder
                    .post(requestBody)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    TimberLogger.d(TAG,"Post response status: %d.%s", response.code(), response.message());
                }
                return response;
            } catch (IOException e) {
                TimberLogger.d(TAG,"Failed to connect " + url + ". Check the URL.", e);
                return null;
            }
        } catch (Exception e) {
            TimberLogger.e("Error during HTTP POST request", e);
            return null;
        }
    }

    public static boolean illegalUrl(String url){
        try {
            URI uri = new URI(url);
            uri.toURL();
            return false;
        }catch (Exception e){
            e.printStackTrace();
            return true;
        }
    }

    public static String makeUrlParamsString(Map<String, String> paramMap) {
        StringBuilder stringBuilder = new StringBuilder();
        if(paramMap !=null&& paramMap.size()>0){
            stringBuilder.append("?");
            for(String key: paramMap.keySet()){
                stringBuilder.append(key).append("=").append(paramMap.get(key)).append("&");
            }
            stringBuilder.deleteCharAt(stringBuilder.lastIndexOf("&"));
        }
        return stringBuilder.toString();
    }

}
