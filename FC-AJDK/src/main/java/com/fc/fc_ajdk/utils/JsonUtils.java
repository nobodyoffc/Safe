package com.fc.fc_ajdk.utils;

import com.fc.fc_ajdk.ui.Inputer;
import com.fc.fc_ajdk.ui.Menu;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.fc.fc_ajdk.constants.Strings.DOT_JSON;

public class JsonUtils {



    @SuppressWarnings("unused")
    private static void sort(JsonElement e) {
        if (e.isJsonNull() || e.isJsonPrimitive()) {
            return;
        }

        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            Iterator<JsonElement> it = a.iterator();
            it.forEachRemaining(i -> sort(i));
            return;
        }

        if (e.isJsonObject()) {
            Map<String, JsonElement> tm = new TreeMap<>(getComparator());
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                tm.put(en.getKey(), en.getValue());
            }

            String key;
            JsonElement val;
            for (Map.Entry<String, JsonElement> en : tm.entrySet()) {
                key = en.getKey();
                val = en.getValue();
                e.getAsJsonObject().remove(key);
                e.getAsJsonObject().add(key, val);
                sort(val);
            }
        }
    }

    public static <T, E> Type getMapType(Class<T> t, Class<E> e) {
        return new TypeToken<Map<T, E>>() {
        }.getType();
    }

    public static String toNiceJson(Object ob) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        gsonBuilder.disableHtmlEscaping();
        Gson gson = gsonBuilder.create();
        return gson.toJson(ob);
    }

    public static Map<String, String> getStringStringMap(String json) {
        Gson gson = new Gson();
        // Define the type of the map
        Type mapType = new TypeToken<Map<String, String>>() {
        }.getType();
        // Parse the JSON string back into a map
        Map<String, String> map = gson.fromJson(json, mapType);
        return map;
    }

    public static String toJson(Object ob) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        return gson.toJson(ob);
    }


    public static void printJson(Object ob) {
        if(ob==null) {
            System.out.println("printJson: The object is null.");
            return;
        }
        System.out.println("----------\n" + ob.getClass().toString() + ": " + toNiceJson(ob) + "\n----------");
    }

    public static String strToJson(String rawStr) {
        if (rawStr == null) return null;
        if ((!rawStr.contains("{") && !rawStr.contains("[")) || 
            (!rawStr.contains("}") && !rawStr.contains("]"))) return null;
        
        try {
            // First try to parse and validate the JSON structure as-is
            JsonParser.parseString(rawStr);
            return rawStr.replaceAll("[\r\n\t]", "");
        } catch (JsonSyntaxException e) {
            try {
                // If parsing fails, try removing escapes first
                String unescaped = removeEscapes(rawStr);
                // Validate the unescaped JSON
                JsonParser.parseString(unescaped);
                return unescaped.replaceAll("[\r\n\t]", "");
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public static String jsonToNiceJson(String jsonString) {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        JsonElement jsonElement = JsonParser.parseString(jsonString);
        return gson.toJson(jsonElement);
    }

    public static String niceJsonToJson(String prettyJsonString) {
        Gson gson = new Gson(); // Gson without pretty printing
        JsonElement jsonElement = JsonParser.parseString(prettyJsonString);
        return gson.toJson(jsonElement);
    }
    public static String strToNiceJson(String rawStr){
        rawStr = strToJson(rawStr);
        if (rawStr == null) return null;
        
        // Create a lenient Gson builder to handle malformed JSON
        Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .setLenient()  // Add lenient parsing
            .disableHtmlEscaping()  // Prevent additional escaping
            .create();
            
        try {
            JsonElement jsonElement = JsonParser.parseString(rawStr);
            return gson.toJson(jsonElement);
        } catch (JsonSyntaxException e) {
            try {
                // If parsing fails, try removing escapes first
                String unescaped = removeEscapes(rawStr);
                JsonElement jsonElement = JsonParser.parseString(unescaped);
                return gson.toJson(jsonElement);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private static Comparator<String> getComparator() {
        return String::compareTo;
    }

    public static <T> T readObjectFromJsonFile(String filePath, String fileName, Class<T> tClass) throws IOException {
        File file = new File(filePath, fileName);
        if (!file.exists() || file.length() == 0) return null;
        
        // Create a custom Gson builder with type adapters
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.enableComplexMapKeySerialization(); // Enable complex map key serialization
        Gson gson = gsonBuilder.create();
        
        FileInputStream fis = new FileInputStream(file);
        byte[] configJsonBytes = new byte[fis.available()];
        fis.read(configJsonBytes);

        String configJson = new String(configJsonBytes);
        T t = gson.fromJson(configJson, tClass);
        fis.close();
        return t;
    }

    public static <K,T> Map<K, T> readMapFromJsonFile(String filePath, String fileName, Class<K> kClass,Class<T> tClass) throws IOException {
        File file = new File(filePath, fileName);
        if (!file.exists() || file.length() == 0) return null;
        FileInputStream fis = new FileInputStream(file);
        byte[] configJsonBytes = new byte[fis.available()];
        fis.read(configJsonBytes);

        String configJson = new String(configJsonBytes);

        Map<K, T> map = jsonToMap(configJson, kClass, tClass);

        fis.close();
        return map;
    }

    public static <T> void writeObjectToJsonFile(T obj, String fileName, boolean append) {
        FileUtils.createFileDirectories(fileName);
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        Gson gson = gsonBuilder.create();
        try (Writer writer = new FileWriter(fileName, append)) {
            gson.toJson(obj, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> void writeObjectToJsonFile(T obj,String path, String fileName, boolean append) {
        writeObjectToJsonFile(obj,path+fileName,append);
    }

    public static <T> void writeListToJsonFile(List<T> objList, String fileName, boolean append) {
        FileUtils.createFileDirectories(fileName);
        Gson gson = new Gson();
        try (Writer writer = new FileWriter(fileName, append)) {
            objList.forEach(t -> gson.toJson(t, writer));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <K, V> void writeMapToJsonFile(Map<K, V> map, String fileName) {
        FileUtils.createFileDirectories(fileName);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (Writer writer = new FileWriter(fileName)) {
            gson.toJson(map, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> T readObjectFromJsonFile(FileInputStream fis, Class<T> tClass) throws IOException {

        T t;
        byte[] jsonBytes = readOneJsonFromInputStream(fis);
        if (jsonBytes == null) return null;

        Gson gson = new Gson();
        try {
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            t = gson.fromJson(json, tClass);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return t;
    }

    public static <T> T readOneJsonFromFile(String path,String fileName,Class<T> tClass) throws IOException {
        File file = new File(path,fileName);
        if(!file.exists())return null;
        FileInputStream fis = new FileInputStream(file);
        byte[] jsonBytes = readOneJsonFromInputStream(fis);
        if(jsonBytes==null) return null;
        String json = new String(jsonBytes,StandardCharsets.UTF_8);
        Gson gson = new Gson();
        T t;
        try {
            t = gson.fromJson(json, tClass);
        }catch (Exception e){
            return null;
        }
        return t;
    }
    public static byte[] readOneJsonFromInputStream(InputStream fis) {
        byte[] jsonBytes=null;
        ArrayList<Integer> jsonByteList = new ArrayList<>();

        int tip = 0;

        boolean counting = false;
        boolean ignore;
        int b;
        try {
            while (true) {
                b = fis.read();
                if (b < 0) return null;

                ignore = (char) b == '\\';

                if (ignore) {
                    jsonByteList.add(b);
                    continue;
                }

                if ((char) b == '{') {
                    counting = true;
                    tip++;
                } else {
                    if ((char) b == '}' && counting) tip--;
                }

                jsonByteList.add(b);

                if (counting && tip == 0) {
                    jsonBytes = new byte[jsonByteList.size()];
                    int i = 0;
                    for (int b1 : jsonByteList) {
                        jsonBytes[i] = (byte) b1;
                        i++;
                    }
                    counting = false;
                    break;
                }
            }
        }catch (Exception e){
            return null;
        }
        return jsonBytes;
    }

    public static String removeEscapes(String input) {
        StringBuilder result = new StringBuilder();
        boolean escape = false;

        for (char c : input.toCharArray()) {
            if (escape) {
                switch (c) {
                    case 'n' -> result.append('\n');
                    case 't' -> result.append('\t');
                    case 'r' -> result.append('\r');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case '\\' -> result.append('\\');
                    default -> result.append(c);
                }
                escape = false;
            } else {
                if (c == '\\') {
                    escape = true;
                } else {
                    result.append(c);
                }
            }
        }

        return result.toString();
    }

    public static <T> T readJsonFromFile(String filePath, Class<T> classOfT) throws IOException {
        // Read file content
        String fileContent = new String(Files.readAllBytes(Paths.get(filePath)));

        // Create a Gson instance
        Gson gson = new Gson();

        // Deserialize the JSON string into an instance of class T
        return gson.fromJson(fileContent, classOfT);
    }

    public static <T> List<T> readJsonObjectListFromFile(String fileName, Class<T> clazz) {
        List<T> objects = new ArrayList<>();
        Gson gson = new Gson();

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            StringBuilder jsonBuilder = new StringBuilder();

            int braceCount = 0;
            int ch;

            while ((ch = reader.read()) != -1) {
                char c = (char) ch;
                jsonBuilder.append(c);

                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;

                    if (braceCount == 0) {
                        T obj = gson.fromJson(jsonBuilder.toString(), clazz);
                        objects.add(obj);
                        jsonBuilder = new StringBuilder();
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return null;
        }
        return objects;
    }

    public static <T> List<T> readJsonObjectListFromFile(FileInputStream fis, int count, Class<T> clazz) {
        List<T> objectList = new ArrayList<>();
        Gson gson = new Gson();
        int i = 0;

        StringBuilder jsonBuilder = new StringBuilder();

        int braceCount = 0;
        int ch;

        while (true) {
            try {
                ch = fis.read();
            } catch (IOException e) {
                System.out.println("FileInputStream is wrong.");
                return null;
            }
            if (ch == -1) break;
            char c = (char) ch;
            jsonBuilder.append(c);

            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;

                if (braceCount == 0) {
                    T obj = gson.fromJson(jsonBuilder.toString(), clazz);
                    objectList.add(obj);
                    i++;
                    if (i == count) break;
                    jsonBuilder = new StringBuilder();
                }
            }
        }
        return objectList;
    }

    public static <K, T> Map<K, T> jsonToMap(String json, Class<K> kClass, Class<T> tClass) {
        Type type = TypeToken.getParameterized(Map.class, kClass, tClass).getType();
        try{
            return new HashMap<>(new Gson().fromJson(json, type));
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public static void saveToJsonFile(Object obj, String fid, String sid, String name, Boolean append) {
        String fileName = FileUtils.makeFileName(fid, sid, name, DOT_JSON);
        writeObjectToJsonFile(obj,fileName,append);
        System.out.println("Successfully saved " + fileName + ".");
    }

    public static <T> Set<T> loadSetFromJsonFile(String fid, String sid, String name, Class<T> clazz) {
        String fileName = FileUtils.makeFileName(fid, sid, name, DOT_JSON);
        Gson gson = new Gson();
        Set<T> set = null;
        try (FileReader fileReader = new FileReader(fileName)) {
            Type setType = TypeToken.getParameterized(Set.class, clazz).getType();
            set = gson.fromJson(fileReader, setType);
        } catch (IOException e) {
            System.out.println("Failed to load "+fileName+".");
            return null;
        }

        return set;
    }

    public static <T> void showListInNiceJson(List<T> items, BufferedReader br) {
        if (items == null || items.isEmpty()) {
            System.out.println("No items to display.");
            return;
        }

        // Create pretty-printing Gson instance once
        Gson prettyGson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

        boolean oneByOne = br != null && items.size() > 1 && Inputer.askIfYes(br, "Show them one by one with enter?");

        for (int i = 0; i < items.size(); i++) {
            T item = items.get(i);
            System.out.println("\n=== Item " + (i + 1) + " of " + items.size() + " ===");
            try {
                String jsonOutput = prettyGson.toJson(item);
                System.out.println(jsonOutput);
                if(oneByOne) Menu.anyKeyToContinue(br);
            } catch (Exception e) {
                System.out.println("Error converting item to JSON: " + e.getMessage());
                System.out.println("Raw toString(): " + item.toString());
            }
        }
    }

    public static <T> T fromJson(String string, Class<T> class1) {
        return new Gson().fromJson(string, class1);
    }
//
//    public static <T> T fromJsonExactly(String string, Class<T> class1) {
//        if (string == null || string.isEmpty()) {
//            return null;
//        }
//        try {
//            // First validate if it's a valid JSON
//            JsonElement jsonElement = JsonParser.parseString(string);
//            if (!jsonElement.isJsonObject()) {
//                return null;
//            }
//
//            Gson gson = new GsonBuilder()
//                .registerTypeAdapterFactory(new StrictTypeAdapterFactory())
//                .create();
//
//            // Use JsonReader to handle the parsing more safely
//            try (JsonReader reader = new JsonReader(new StringReader(string))) {
//                reader.setLenient(false);
//                return gson.fromJson(reader, class1);
//            }catch (Exception e){
//                e.printStackTrace();
//                return null;
//            }
//        } catch (Exception e) {
//            return null;
//        }
//    }
//
//    private static class StrictTypeAdapterFactory implements TypeAdapterFactory {
//        @Override
//        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
//            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
//            return new TypeAdapter<T>() {
//                @Override
//                public void write(JsonWriter out, T value) throws IOException {
//                    delegate.write(out, value);
//                }
//
//                @Override
//                public T read(JsonReader in) throws IOException {
//                    try {
//                        // Create a JsonObject to store all properties
//                        JsonObject jsonObject = new JsonObject();
//                        in.beginObject();
//                        while (in.hasNext()) {
//                            String name = in.nextName();
//                            JsonElement value = JsonParser.parseReader(in);
//                            jsonObject.add(name, value);
//                        }
//                        in.endObject();
//
//                        // Get all fields from the target class
//                        Set<String> classFields = new HashSet<>();
//                        for (java.lang.reflect.Field field : type.getRawType().getDeclaredFields()) {
//                            classFields.add(field.getName());
//                        }
//
//                        // Check if all JSON properties exist in the class
//                        for (String jsonField : jsonObject.keySet()) {
//                            if (!classFields.contains(jsonField)) {
//                                return null;
//                            }
//                        }
//
//                        // Parse the JSON object to the target type
//                        return gson.fromJson(jsonObject, type);
//                    } catch (Exception e) {
//                        return null;
//                    }
//                }
//            };
//        }
//    }

    public static String toNiceText(String json, int startBraceCount) {
        if (json == null || json.isEmpty()) return "";
        
        // Parse JSON to get pretty-printed version first
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        JsonElement je = JsonParser.parseString(json);
        String prettyJson = gson.toJson(je);

        // Process the JSON structure while maintaining indentation
        StringBuilder result = new StringBuilder();
        String[] lines = prettyJson.split("\n");
        int braceCount = startBraceCount;  // Track nested level using braces

        for (String line : lines) {
            line = line.trim();
            // Skip empty lines
            if (line.isEmpty()) continue;

            // Track array status
            if (line.endsWith("[")) {
                line = line.substring(0,line.length()-1).replaceAll("\"","");
                result.append(line).append("\n");
                braceCount++;
                continue;
            }else if ("]".equals(line.trim()) || "],".equals(line.trim())) {
                braceCount--;
                continue;
            }else if ("}".equals(line.trim()) || "},".equals(line.trim())) {
                braceCount--;
                continue;
            }else if(line.endsWith("{")){
                line = line.substring(0,line.length()-1).replaceAll("\"","");
                result.append(line).append("\n");
                braceCount++;
                continue;
            }
            result.append("  ".repeat(braceCount-1));
            line = line.replaceAll("\"","");
            if(line.endsWith(","))line = line.substring(0,line.length()-1);
            result.append(line).append("\n");
        }
        return result.toString().trim();
    }

    public static <T> List<T> listFromJson(String json, Class<T> clazz) {
        Gson gson = new Gson();
        Type type = TypeToken.getParameterized(ArrayList.class, clazz).getType();
        if(json.startsWith("\""))json = json.substring(1);
        if(json.endsWith("\""))json = json.substring(0,json.length()-1);
        List<T> tempList = gson.fromJson(json, type);
        return new ArrayList<>(tempList);
    }
    
    /**
     * Checks if a string is valid JSON.
     * 
     * @param json The string to check
     * @return true if the string is valid JSON, false otherwise
     */
    public static boolean isJson(String json) {
        if (json == null || json.isEmpty()) {
            return false;
        }
        
        // Check if the string contains JSON object markers
        if (!json.contains("{") || !json.contains("}")) {
            return false;
        }
        
        try {
            JsonElement element = JsonParser.parseString(json);
            // Only consider JSON objects as valid JSON
            return element.isJsonObject();
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    public static String makeJsonListString(List<String> jsonList){
        if(jsonList==null || jsonList.isEmpty())return null;
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < jsonList.size(); i++){
            sb.append(jsonList.get(i));
            if (i < jsonList.size() - 1) { // Add newline only if it's not the last item
                sb.append("\n\n"); // Use escaped newline for string literal, or System.lineSeparator()
            }
        }
        return sb.toString();
    }
}

