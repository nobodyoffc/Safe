package com.fc.fc_ajdk.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.fc.fc_ajdk.data.fcData.ReplyBody;
import com.fc.fc_ajdk.data.fchData.Cid;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;

public class ObjectUtils {

    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        ReplyBody replyBody = new ReplyBody();

        Cid cid = new Cid();
        cid.setCid("liu");
        cid.setHot(13424L);
        Map<String,Cid> map = new HashMap<>();
        map.put(cid.getCid(),cid);
        replyBody.setData(map);
        Gson gson = new Gson();
        String json = gson.toJson(replyBody);
        Object data = gson.fromJson(json, ReplyBody.class).getData();
        Map<String, Cid> newMap = objectToMap(data, String.class, Cid.class);
        JsonUtils.printJson(newMap);
    }

    /**
     * Helper method to convert an object to a specific type using Gson
     * @param obj The object to convert
     * @param type The target type
     * @param <R> The return type
     * @return Converted object or null if conversion fails
     */
    private static <R> R convertObject(Object obj, Type type) {
        if (obj == null) return null;
        try {
            String jsonString = (obj instanceof String) ? (String) obj : gson.toJson(obj);
            return gson.fromJson(jsonString, type);
        } catch (Exception ignore) {
            return null;
        }
    }

    public static <K, T> Map<K, T> objectToMap(Object obj, Class<K> kClass, Class<T> tClass) {
        if (obj == null) return null;
        try {
            Type type = TypeToken.getParameterized(Map.class, kClass, tClass).getType();
            Map<K, T> result = convertObject(obj, type);
            return result != null ? new HashMap<>(result) : new HashMap<>();
        } catch (Exception e) {
            System.out.println("Error converting object to map: " + e.getMessage());
            return new HashMap<>();
        }
    }

    public static <K, T> Map<K, T> objectToLinkedHashMap(Object obj, Class<K> kClass, Class<T> tClass) {
        Type type = TypeToken.getParameterized(Map.class, kClass, tClass).getType();
        Map<K, T> result = convertObject(obj, type);
        return result != null ? new LinkedHashMap<>(result) : null;
    }

    public static <T> List<T> objectToList(Object obj, Class<T> tClass) {
        Type type = TypeToken.getParameterized(ArrayList.class, tClass).getType();
        List<T> result = convertObject(obj, type);
        return result != null ? new ArrayList<>(result) : null;
    }

    public static <T, K> Map<K, T> listToMap(List<T> list, String keyFieldName) {
        Map<K, T> resultMap = new HashMap<>();
        try {
            if (list != null && !list.isEmpty()) {
                // Get the field from the class or its superclasses
                Class<?> currentClass = list.get(0).getClass();
                Field keyField = null;
                while (currentClass != null && keyField == null) {
                    try {
                        keyField = currentClass.getDeclaredField(keyFieldName);
                    } catch (NoSuchFieldException e) {
                        currentClass = currentClass.getSuperclass();
                    }
                }
                
                if (keyField == null) {
                    throw new NoSuchFieldException(keyFieldName);
                }
                
                keyField.setAccessible(true);

                for (T item : list) {
                    @SuppressWarnings("unchecked")
                    K key = (K) keyField.get(item);
                    resultMap.put(key, item);
                }
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return resultMap;
    }

    public static <T> T objectToClass(Object obj, Class<T> tClass) {
        return convertObject(obj, tClass);
    }

    public static Map<String, Long> convertToLongMap(Map<String, String> stringStringMap) {
        Map<String, Long> map = new HashMap<>();

        for (Map.Entry<String, String> entry : stringStringMap.entrySet()) {
            try {
                // Parse the String value to Long and put it in the new Map
                Long value = Long.parseLong(entry.getValue());
                map.put(entry.getKey(), value);
            } catch (NumberFormatException e) {
                // Handle the case where parsing fails
                System.out.println("Error parsing value for key " + entry.getKey() + ": " + entry.getValue());
            }
        }
        return map;
    }

    public static Map<String, String> convertToStringMap(Map<String, Long> longLongMap) {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, Long> entry : longLongMap.entrySet()) {
            map.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return map;
    }
    

    public static boolean isComplexType(Class<?> clazz) {
        return !clazz.isPrimitive() &&
               !clazz.isEnum() &&
               clazz != String.class &&
               !Number.class.isAssignableFrom(clazz) &&
               clazz != Boolean.class &&
               clazz != Character.class &&
               !clazz.isArray();  // Arrays are handled separately
    }

    public static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
               clazz == String.class ||
               clazz == Boolean.class ||
               clazz == Character.class ||
               clazz == Byte.class ||
               clazz == Short.class ||
               clazz == Integer.class ||
               clazz == Long.class ||
               clazz == Float.class ||
               clazz == Double.class;
    }

    @SuppressWarnings("unchecked")
    public static <T> T convertToType(String value, Class<T> tClass) {
        try {
            if (tClass == String.class) {
                return (T) value;
            } else if (tClass == Integer.class || tClass == int.class) {
                return (T) Integer.valueOf(value);
            } else if (tClass == Long.class || tClass == long.class) {
                return (T) Long.valueOf(value);
            } else if (tClass == Double.class || tClass == double.class) {
                return (T) Double.valueOf(value);
            } else if (tClass == Boolean.class || tClass == boolean.class) {
                return (T) Boolean.valueOf(value);
            } else if (tClass.isEnum()) {
                return (T) Enum.valueOf((Class<Enum>) tClass, value.toUpperCase());
            }
            System.out.println("Unsupported type: " + tClass.getSimpleName());
            return null;
        }catch (Exception e){
            return null;
        }
    }

    public static <T> T createSample(Class<T> tClass) throws ReflectiveOperationException {
        return createSample(tClass, 1); // Default mark value of 1
    }

    public static <T> T createSample(Class<T> tClass, Integer mark) throws ReflectiveOperationException {
        if (tClass.isArray()) {
            return (T) createSampleArray(tClass.getComponentType(), mark);
        }

        // Handle primitive and simple types
        if (!ObjectUtils.isComplexType(tClass)) {
            return createSampleValue(tClass, mark);
        }

        T instance = tClass.getDeclaredConstructor().newInstance();

        for (Field field : tClass.getDeclaredFields()) {
            // Skip static, transient, and final fields
            int modifiers = field.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(modifiers) ||
                    java.lang.reflect.Modifier.isTransient(modifiers) ||
                    java.lang.reflect.Modifier.isFinal(modifiers)) {
                continue;
            }

            field.setAccessible(true);
            Class<?> fieldType = field.getType();
            Object value;

            if (fieldType.isArray()) {
                value = createSampleArray(fieldType.getComponentType(), mark);
            } else if (ObjectUtils.isComplexType(fieldType)) {
                value = createSample(fieldType, mark);
            } else {
                value = createSampleValue(fieldType, mark);
            }

            field.set(instance, value);
        }

        return instance;
    }

    private static Object createSampleArray(Class<?> componentType, Integer mark) throws ReflectiveOperationException {
        // Create a sample array with 2 elements
        Object array = Array.newInstance(componentType, 2);

        for (int i = 0; i < 2; i++) {
            Object value;
            if (ObjectUtils.isComplexType(componentType)) {
                value = createSample(componentType, mark + i);
            } else {
                value = createSampleValue(componentType, mark + i);
            }
            Array.set(array, i, value);
        }

        return array;
    }

    @SuppressWarnings("unchecked")
    private static <T> T createSampleValue(Class<T> tClass, Integer mark) {
        if (tClass == String.class) {
            return (T) ("sample" + mark);
        } else if (tClass == Integer.class || tClass == int.class) {
            return (T) Integer.valueOf(mark * 42);
        } else if (tClass == Long.class || tClass == long.class) {
            return (T) Long.valueOf(mark * 42L);
        } else if (tClass == Double.class || tClass == double.class) {
            return (T) Double.valueOf(mark * 42.0);
        } else if (tClass == Float.class || tClass == float.class) {
            return (T) Float.valueOf(mark * 42.0f);
        } else if (tClass == Boolean.class || tClass == boolean.class) {
            return (T) Boolean.valueOf(mark % 2 == 0); // Alternates between true and false
        } else if (tClass == Byte.class || tClass == byte.class) {
            return (T) Byte.valueOf((byte) (mark * 42 % 128));
        } else if (tClass == Short.class || tClass == short.class) {
            return (T) Short.valueOf((short) (mark * 42));
        } else if (tClass == Character.class || tClass == char.class) {
            return (T) Character.valueOf((char) ('A' + (mark % 26))); // Cycles through alphabet
        } else if (tClass.isEnum()) {
            Object[] enumConstants = tClass.getEnumConstants();
            return (T) enumConstants[mark % enumConstants.length]; // Cycles through enum values
        }

        return null;
    }

    /**
     * Allows user to interactively select elements from a list, showing specified fields for each element
     * @param sourceList The source list to choose from
     * @param fieldNames Array of field names to display for each element
     * @param <T> The type of elements in the list
     * @return A new list containing only the selected elements
     */
    public static <T> List<T> chooseFromList(List<T> sourceList,BufferedReader reader, int width, String... fieldNames) throws IOException {
        List<T> selectedItems = new ArrayList<>();
        
        if (sourceList == null || sourceList.isEmpty()) {
            System.out.println("Source list is empty!");
            return selectedItems;
        }

        // Print header
        System.out.println("\nAvailable items:");
        StringBuilder header = new StringBuilder("No. ");
        for (String fieldName : fieldNames) {
            header.append("| ").append(fieldName).append(" ");
        }
        System.out.println(header);
        System.out.println("-".repeat(header.length()));

        // Print each item with specified fields
        for (int i = 0; i < sourceList.size(); i++) {
            T item = sourceList.get(i);
            StringBuilder line = new StringBuilder(String.format("%-3d ", i + 1));
            
            for (String fieldName : fieldNames) {
                try {
                    Field field = item.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object value = field.get(item);
                    line.append("| ").append(value == null ? "null" : StringUtils.omitMiddle(value.toString(), width)).append(" ");
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    line.append("| <error> ");
                }
            }
            System.out.println(line);
        }
        
        System.out.println("\nEnter item numbers to select (comma-separated), or 'all' for all items:");
        String input = reader.readLine().trim();
        
        if (input.equalsIgnoreCase("all")) {
            return new ArrayList<>(sourceList);
        }
        
        try {
            String[] selections = input.split(",");
            for (String selection : selections) {
                int index = Integer.parseInt(selection.trim()) - 1;
                if (index >= 0 && index < sourceList.size()) {
                    selectedItems.add(sourceList.get(index));
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input format. Please enter numbers separated by commas.");
        }
        
        return selectedItems;
    }

    public static <K, T> Map<K, List<T>> objectToMapWithListValues(Object obj, Class<K> kClass, Class<T> tClass) {
        Type listType = TypeToken.getParameterized(List.class, tClass).getType();
        Type mapType = TypeToken.getParameterized(Map.class, kClass, listType).getType();
        Map<K, List<T>> result = convertObject(obj, mapType);
        return result != null ? new HashMap<>(result) : null;
    }

    public static <T> String getValueByFieldName(T item, String fieldName) throws Exception {
        // Try direct method first
        try {
            return String.valueOf(item.getClass().getMethod(fieldName).invoke(item));
        } catch (NoSuchMethodException e) {
            // Try getter method
            String getterMethod = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            return String.valueOf(item.getClass().getMethod(getterMethod).invoke(item));
        }
    }

    public static String objectToString(Object value) {
        if(value==null)return null;
        try{
            return String.valueOf(value);
        }catch (Exception ignore){
            return JsonUtils.toJson(value);
        }
    }
}
