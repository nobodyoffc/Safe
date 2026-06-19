package com.fc.safe.db;

import androidx.annotation.NonNull;

import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.MapQueue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.tencent.mmkv.MMKV;
import com.fc.fc_ajdk.utils.TimberLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * MMKV-based implementation of LocalDB interface.
 * Uses MMKV for persistent storage with improved performance over Hawk.
 * Maintains a simple ordered ID list to maintain insertion order.
 *
 * @param <T> The type of entity to store, must extend FcEntity
 */
public class MMKVDB<T extends FcEntity> implements LocalDB<T> {
    private static final String TAG = "MMKVDB";
    private static final String SETTINGS_MAP = "settings";
    public static final String ITEMS = "items";
    public static final String ID_LIST = "id_list";
    public static final String META_MAP = "meta";
    public static final String STATE_MAP = "state";
    public static final String MAP_TYPES = "map_types";
    public static final int QUEUE_SIZE = 200;
    private volatile boolean isClosed = false;

    // MMKV instance for this database
    private MMKV mmkv;

    // Add namespace prefix for unique storage spaces
    private String namespacePrefix;

    // Add read-write lock for thread safety
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    // Thread-local temporary variables for pagination
    private final ThreadLocal<Integer> tempIndex = new ThreadLocal<>();
    private final ThreadLocal<String> tempId = new ThreadLocal<>();

    // Main storage - simple ordered ID list for maintaining insertion order
    private final MapQueue<String, T> itemMap = new MapQueue<>(QUEUE_SIZE);
    private final List<String> idList = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Object> stateMap = new ConcurrentHashMap<>();

    // Named maps for additional storage
    private final Map<String, MapQueue<Object, Object>> namedMapsMap = new ConcurrentHashMap<>();

    private final Map<String, String> mapTypes = new ConcurrentHashMap<>();
    private final Map<String, String> listTypes = new ConcurrentHashMap<>();

    private final Set<String> listNames = new HashSet<>();
    private Map<String, String> metaMap;

    // Store the entity class for proper deserialization
    private Class<T> entityClass;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public MMKVDB() {
        // Simplified constructor
    }

    /**
     * Sets the entity class for this database.
     * This is required for proper deserialization of entities.
     */
    public void setEntityClass(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    @Override
    public void initialize(String fid, String sid, String dbPath, String dbName) {
        if (isClosed) {
            this.isClosed = false;
        }

        // Create a unique namespace prefix for this user and app combination
        this.namespacePrefix = createNamespacePrefix(fid, sid, dbName);

        // Initialize MMKV instance with unique ID
        this.mmkv = MMKV.mmkvWithID(this.namespacePrefix, MMKV.MULTI_PROCESS_MODE);

        if (this.mmkv == null) {
            TimberLogger.e(TAG, "Failed to initialize MMKV with ID: %s", this.namespacePrefix);
            throw new RuntimeException("Failed to initialize MMKV");
        }

        // Load ID list
        loadIdList();

        // Load metadata and settings
        loadMetaMap();

        // Initialize mapTypes from metaMap
        String storedMapTypes = metaMap.get(MAP_TYPES);
        if(storedMapTypes!=null) {
            Map<String, String> map = JsonUtils.jsonToMap(storedMapTypes, String.class, String.class);
            if (map != null) mapTypes.putAll(map);
        }

        loadStateMap();

        // Initialize standard maps
        createMap(LOCAL_REMOVED_MAP,String.class);
        createMap(ON_CHAIN_DELETED_MAP,String.class);
        createMap(SETTINGS_MAP,Object.class);

        String metaMapKey = getNamespacedKey(META_MAP);
        mmkv.putString(metaMapKey, gson.toJson(metaMap));
    }

    private void loadMetaMap() {
        String namespacedKey = getNamespacedKey(META_MAP);
        String metaMapJson = mmkv.decodeString(namespacedKey);
        if (metaMapJson == null || metaMapJson.isEmpty()) {
            metaMap = new HashMap<>();
        } else {
            Map<String, String> loadedMap = JsonUtils.jsonToMap(metaMapJson, String.class, String.class);
            metaMap = loadedMap != null ? loadedMap : new HashMap<>();
        }
    }

    private void loadStateMap() {
        String stateMapJson = mmkv.decodeString(getNamespacedKey(STATE_MAP));
        if (stateMapJson != null && !stateMapJson.isEmpty()) {
            Map<String, Object> loadedStateMap = JsonUtils.jsonToMap(stateMapJson, String.class, Object.class);
            if (loadedStateMap != null) {
                stateMap.putAll(loadedStateMap);
            }
        }
    }

    private void loadIdList() {
        String idListJson = mmkv.decodeString(getNamespacedKey(ID_LIST));
        idList.clear();
        if (idListJson != null && !idListJson.isEmpty()) {
            List<String> loadedList = JsonUtils.listFromJson(idListJson, String.class);
            if (loadedList != null) {
                boolean hadNulls = loadedList.removeIf(id -> id == null);
                idList.addAll(loadedList);
                if (hadNulls) {
                    saveIdList();
                }
            }
        }
    }

    /**
     * Creates a unique namespace prefix based on fid, sid, and dbName
     */
    private String createNamespacePrefix(String fid, String sid, String dbName) {
        StringBuilder prefix = new StringBuilder();
        if (fid != null && !fid.isEmpty()) {
            prefix.append(fid).append("_");
        }
        if (sid != null && !sid.isEmpty()) {
            prefix.append(sid).append("_");
        }
        if (dbName != null && !dbName.isEmpty()) {
            prefix.append(dbName.toLowerCase()).append("_");
        }
        String result = prefix.toString();
        TimberLogger.d(TAG, "Created namespace prefix: %s with fid=%s, sid=%s, dbName=%s", result, fid, sid, dbName);
        return result;
    }

    /**
     * Gets a namespaced key for MMKV storage
     */
    private String getNamespacedKey(String key) {
        return namespacePrefix + key;
    }

    @Override
    public void put(String key, T value) {
        if (key == null) return;
        writeLock.lock();
        try {
            // Add to cache
            itemMap.put(key, value);

            // Store individual item
            String itemKey = getItemKey(key);
            mmkv.putString(itemKey, gson.toJson(value));

            // Update ID list if new item
            if (!idList.contains(key)) {
                idList.add(key);
                saveIdList();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @NonNull
    private String getItemKey(String key) {
        return getNamespacedKey(MMKVDB.ITEMS + "_" + key);
    }


    @Override
    public T get(String key) {
        readLock.lock();
        try {
            // Try to get from cache first
            T value = itemMap.get(key);
            if (value == null) {
                // If not in cache, load from MMKV
                String json = mmkv.decodeString(getItemKey(key));
                if (json != null && !json.isEmpty()) {
                    // Use the actual entity class for deserialization
                    try {
                        if (entityClass != null) {
                            @SuppressWarnings("unchecked")
                            T item = (T) gson.fromJson(json, entityClass);
                            if (item != null) {
                                itemMap.put(key, item);
                                value = item;
                            }
                        } else {
                            TimberLogger.e(TAG, "Entity class not set for MMKVDB. Cannot deserialize item.");
                        }
                    } catch (Exception e) {
                        TimberLogger.e(TAG, "Error deserializing item: %s", e.getMessage());
                    }
                }
            }
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<T> get(List<String> keys) {
        readLock.lock();
        try {
            List<T> values = new ArrayList<>();

            for (String key : keys) {
                T value = get(key);
                if (value != null) {
                    values.add(value);
                }
            }

            return values;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void remove(String key) {
        writeLock.lock();
        try {
            // Remove from cache
            itemMap.remove(key);

            // Remove from MMKV
            mmkv.removeValueForKey(getItemKey(key));

            // Remove from ID list
            idList.remove(key);
            saveIdList();

            // Mark as locally removed
            putInMapInternal(LOCAL_REMOVED_MAP, key, System.currentTimeMillis());

        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void remove(List<T> list) {
        if (list == null || list.isEmpty()) return;

        writeLock.lock();
        try {
            for (T item : list) {
                if (item == null) continue;

                String key = item.getId();
                if (key == null) continue;

                // Remove from cache
                itemMap.remove(key);

                // Remove from MMKV
                mmkv.removeValueForKey(getItemKey(key));

                // Remove from ID list
                idList.remove(key);

                // Mark as locally removed
                putInMapInternal(LOCAL_REMOVED_MAP, key, System.currentTimeMillis());
            }

            saveIdList();
        } finally {
            writeLock.unlock();
        }
    }

    private void saveToMMKV() {
        saveIdList();
        saveStateMap();
    }

    @Override
    public void saveIdList(){
        mmkv.putString(getNamespacedKey(ID_LIST), gson.toJson(idList));
    }

    @Override
    public void saveStateMap(){
        mmkv.putString(getNamespacedKey(STATE_MAP), gson.toJson(stateMap));
    }

    @Override
    public void commit() {
        // MMKV commits automatically, but we can force a sync
        mmkv.sync();
    }

    @Override
    public void close() {
        if (!isClosed) {
            saveToMMKV();
            mmkv.sync();
            isClosed = true;
        }
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public long getTempIndex() {
        Integer value = tempIndex.get();
        return value != null ? value : 0L;
    }

    @Override
    public String getTempId() {
        return tempId.get();
    }

    @Override
    public Map<String, T> getItemMap() {
        return getAll();
    }

    @Override
    public List<String> getIdList() {
        return new ArrayList<>(idList);
    }

    @Override
    public Map<String, Object> getMetaMap() {
        String metaMapJson = mmkv.decodeString(getNamespacedKey(META_MAP));
        if (metaMapJson == null || metaMapJson.isEmpty()) {
            return new HashMap<>();
        }
        return JsonUtils.jsonToMap(metaMapJson, String.class, Object.class);
    }

    @Override
    public Map<String, Object> getSettingsMap() {
        String settingsJson = mmkv.decodeString(getNamespacedKey(SETTINGS_MAP));
        if (settingsJson == null || settingsJson.isEmpty()) {
            return new HashMap<>();
        }
        return JsonUtils.jsonToMap(settingsJson, String.class, Object.class);
    }

    @Override
    public Map<String, Object> getStateMap() {
        String stateJson = mmkv.decodeString(getNamespacedKey(STATE_MAP));
        if (stateJson == null || stateJson.isEmpty()) {
            return new HashMap<>();
        }
        return JsonUtils.jsonToMap(stateJson, String.class, Object.class);
    }

    @Override
    public void putSetting(String key, Object value) {
        if (key == null) {
            return;
        }

        writeLock.lock();
        try {
            String stringValue;

            if (value == null) {
                stringValue = null;
            } else if (value instanceof String) {
                stringValue = (String) value;
            } else if (value instanceof Number || value instanceof Boolean) {
                stringValue = value.toString();
            } else {
                stringValue = gson.toJson(value);
            }

            Map<String, Object> settingsMap = getSettingsMap();
            settingsMap.put(key, stringValue);
            mmkv.putString(getNamespacedKey(SETTINGS_MAP), gson.toJson(settingsMap));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeSetting(String key) {
        if (key == null) {
            return;
        }

        writeLock.lock();
        try {
            Map<String, Object> settingsMap = getSettingsMap();
            settingsMap.remove(key);
            mmkv.putString(getNamespacedKey(SETTINGS_MAP), gson.toJson(settingsMap));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeAllSettings() {
        writeLock.lock();
        try {
            mmkv.putString(getNamespacedKey(SETTINGS_MAP), gson.toJson(new HashMap<>()));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Map<String, String> getAllSettings() {
        readLock.lock();
        try {
            Map<String, Object> settingsMap = getSettingsMap();
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : settingsMap.entrySet()) {
                result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Object getSetting(String key) {
        if (key == null) {
            return null;
        }
        readLock.lock();
        try {
            Map<String, Object> settingsMap = getSettingsMap();
            return settingsMap.get(key);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void putState(String key, Object value) {
        writeLock.lock();
        try {
            stateMap.put(key, value);
            mmkv.putString(getNamespacedKey(STATE_MAP), gson.toJson(stateMap));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeState(String key) {
        writeLock.lock();
        try {
            stateMap.remove(key);
            mmkv.putString(getNamespacedKey(STATE_MAP), gson.toJson(stateMap));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clearAllState() {
        writeLock.lock();
        try {
            stateMap.clear();
            mmkv.putString(getNamespacedKey(STATE_MAP), gson.toJson(stateMap));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Map<String, String> getAllState() {
        readLock.lock();
        try {
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, Object> entry : stateMap.entrySet()) {
                result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Object getState(String key) {
        readLock.lock();
        try {
            return stateMap.get(key);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Long getIndexById(String id) {
        return (long) idList.indexOf(id);
    }

    @Override
    public String getIdByIndex(int index) {
        if (index < 0 || index >= idList.size()) {
            return null;
        }
        return idList.get(index);
    }

    @Override
    public T getByIndex(int index) {
        String id = getIdByIndex(index);
        return id != null ? get(id) : null;
    }

    @Override
    public int getSize() {
        return idList.size();
    }

    @Override
    public boolean isEmpty() {
        return idList.isEmpty();
    }

    @Override
    public Object getMeta(String key) {
        readLock.lock();
        try {
            String metaMapKey = getNamespacedKey(META_MAP);
            TimberLogger.d(TAG, "Getting meta with key: %s", key);
            Map<String, Object> metaMap = getMetaMap();
            Object value = metaMap.get(key);
            TimberLogger.d(TAG, "Retrieved meta value: %s", value);
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void putMeta(String key, Object value) {
        writeLock.lock();
        try {
            String metaMapKey = getNamespacedKey(META_MAP);
            TimberLogger.d(TAG, "Putting meta with key: %s, value: %s", key, value);
            Map<String, Object> metaMap = getMetaMap();
            metaMap.put(key, value);
            mmkv.putString(metaMapKey, gson.toJson(metaMap));
            TimberLogger.d(TAG, "Updated metaMap: %s", metaMap);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeMeta(String key) {
        writeLock.lock();
        try {
            Map<String, Object> metaMap = getMetaMap();
            metaMap.remove(key);
            mmkv.putString(getNamespacedKey(META_MAP), gson.toJson(metaMap));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            // Clear in-memory cache
            itemMap.clear();

            // Clear all items from MMKV
            for (String key : idList) {
                mmkv.removeValueForKey(getItemKey(key));
            }

            // Clear ID list
            idList.clear();
            saveIdList();

        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clearDB() {
        writeLock.lock();
        try {
            // Clear in-memory cache
            itemMap.clear();

            // Clear all items from MMKV
            for (String key : idList) {
                mmkv.removeValueForKey(getItemKey(key));
            }

            // Clear ID list
            idList.clear();
            saveIdList();

            // Clear meta, settings and state maps
            mmkv.putString(getNamespacedKey(META_MAP), gson.toJson(new HashMap<>()));
            mmkv.putString(getNamespacedKey(SETTINGS_MAP), gson.toJson(new HashMap<>()));
            mmkv.putString(getNamespacedKey(STATE_MAP), gson.toJson(new HashMap<>()));
            stateMap.clear();

            // Clear all named maps
            for (String mapName : mapTypes.keySet()) {
                // Clear individual entries
                Set<String> keys = getMapKeys(mapName);
                if (keys != null) {
                    for (String key : keys) {
                        mmkv.removeValueForKey(getMapKey(mapName, key));
                    }
                }
                // Clear keys set
                mmkv.removeValueForKey(getMapKeysKey(mapName));
            }

            // Clear map types
            mapTypes.clear();

            // Recreate default maps
            createMap(LOCAL_REMOVED_MAP, String.class);
            createMap(ON_CHAIN_DELETED_MAP, String.class);

        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> void createMap(String mapName, Class<V> vClass) {
        if (mapName == null || vClass == null) {
            return;
        }

        writeLock.lock();
        try {
            if (!mapTypes.containsKey(mapName)) {
                registerMapType(mapName,vClass);
                mmkv.putString(getNamespacedKey(mapName), gson.toJson(new HashMap<>()));
            }
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public Set<String> getMapNames() {
        return mapTypes.keySet();
    }

    /**
     * Internal method to put value in map without acquiring lock.
     * Caller must hold writeLock.
     */
    private <V> void putInMapInternal(String mapName, String key, V value) {
        if(!mapTypes.containsKey(mapName))
            registerMapTypeInternal(mapName,value.getClass());

        if(Objects.equals(mapTypes.get(mapName), byte[].class.getName())){
            String str = Base64.getEncoder().encodeToString((byte[]) value);
            mmkv.putString(getMapKey(mapName, key), str);
        }else{
            mmkv.putString(getMapKey(mapName, key), gson.toJson(value));
        }

        // Update keys set
        Set<String> keys = getMapKeys(mapName);
        keys.add(key);
        saveMapKeys(mapName, keys);

        // Update in-memory cache
        MapQueue<Object, Object> map = getNamedMap(mapName);
        map.put(key, value);
    }

    @Override
    public <V> void putInMap(String mapName, String key, V value) {
        writeLock.lock();
        try {
            putInMapInternal(mapName, key, value);
        } finally {
            writeLock.unlock();
        }
    }

    @NonNull
    private MapQueue<Object, Object> getNamedMap(String mapName) {
        return namedMapsMap.computeIfAbsent(mapName, k -> new MapQueue<>(QUEUE_SIZE));
    }

    @Override
    public <V> V getFromMap(String mapName, String key) {
        readLock.lock();
        if(mapTypes.get(mapName)==null)return null;
        try {
            // Try to get from cache first
            V value = null;
            MapQueue<Object, Object> map = getNamedMap(mapName);
            Object obj  = map.get(key);
            if(obj!=null)value = (V) obj;
            if (value == null) {
                // If not in cache, load from MMKV
                if(Objects.equals(mapTypes.get(mapName), byte[].class.getName())){
                    String vStr = mmkv.decodeString(getMapKey(mapName, key));
                    if(vStr!=null)
                        value = (V) Base64.getDecoder().decode(vStr);
                }else{
                    String json = mmkv.decodeString(getMapKey(mapName, key));
                    if (json != null && !json.isEmpty()) {
                        try {
                            @SuppressWarnings("unchecked")
                            V item = (V) gson.fromJson(json, Object.class);
                            value = item;
                        } catch (Exception e) {
                            TimberLogger.e(TAG, "Error deserializing map value: %s", e.getMessage());
                        }
                    }
                }
                if (value != null) {
                    map.put(key, value);
                }
            }
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> Map<String, V> getAllFromMap(String mapName) {
        readLock.lock();
        try {
            // Get all keys for this map
            Set<String> keys = getMapKeys(mapName);
            Map<String, V> result = new HashMap<>();

            // Load each entry individually
            for (String key : keys) {
                V value = getFromMap(mapName, key);
                if (value != null) {
                    result.put(key, value);
                }
            }

            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void clearMap(String mapName) {
        writeLock.lock();
        try {
            // Get all keys for this map
            Set<String> keys = getMapKeys(mapName);

            // Remove each entry individually
            for (String key : keys) {
                mmkv.removeValueForKey(getMapKey(mapName, key));
            }

            // Clear the keys set
            mmkv.removeValueForKey(getMapKeysKey(mapName));

            // Clear in-memory cache
            MapQueue<Object, Object> map = getNamedMap(mapName);
            map.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> void putAllInMap(String mapName, Map<String, V> map) {
        if (mapName == null || map == null || map.isEmpty()) {
            return;
        }

        writeLock.lock();
        if(!mapTypes.containsKey(mapName))registerMapType(mapName,map.values().iterator().next().getClass());

        try {
            // Store each entry individually
            for (Map.Entry<String, V> entry : map.entrySet()) {
                mmkv.putString(getMapKey(mapName, entry.getKey()), gson.toJson(entry.getValue()));
            }

            // Update the keys set
            Set<String> keys = getMapKeys(mapName);
            keys.addAll(map.keySet());
            saveMapKeys(mapName, keys);

            // Update in-memory cache
            MapQueue<Object, Object> targetMap = getNamedMap(mapName);
            for (Map.Entry<String, V> entry : map.entrySet()) {
                targetMap.put(entry.getKey(), entry.getValue());
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeFromMap(String mapName, String key) {
        writeLock.lock();
        try {
            // Remove individual entry
            mmkv.removeValueForKey(getMapKey(mapName, key));

            // Update keys set
            Set<String> keys = getMapKeys(mapName);
            keys.remove(key);
            saveMapKeys(mapName, keys);

            // Update in-memory cache
            MapQueue<Object, Object> map = getNamedMap(mapName);
            map.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeFromMap(String mapName, List<String> keys) {
        writeLock.lock();
        try {
            // Remove each entry individually
            for (String key : keys) {
                mmkv.removeValueForKey(getMapKey(mapName, key));
            }

            // Update keys set
            Set<String> existingKeys = getMapKeys(mapName);
            keys.forEach(existingKeys::remove);
            saveMapKeys(mapName, existingKeys);

            // Update in-memory cache
            MapQueue<Object, Object> map = getNamedMap(mapName);
            for (String key : keys) {
                map.remove(key);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public int getMapSize(String mapName) {
        readLock.lock();
        try {
            // Get size from keys set
            Set<String> keys = getMapKeys(mapName);
            return keys.size();
        } finally {
            readLock.unlock();
        }
    }

    private String getMapKey(String mapName, String key) {
        return getNamespacedKey(mapName + "_entry_" + key);
    }

    private String getMapKeysKey(String mapName) {
        return getNamespacedKey(mapName + "_keys");
    }

    private Set<String> getMapKeys(String mapName) {
        String keysJson = mmkv.decodeString(getMapKeysKey(mapName));
        if (keysJson == null || keysJson.isEmpty()) {
            return new HashSet<>();
        }
        List<String> keysList = JsonUtils.listFromJson(keysJson, String.class);
        return keysList != null ? new HashSet<>(keysList) : new HashSet<>();
    }

    private void saveMapKeys(String mapName, Set<String> keys) {
        mmkv.putString(getMapKeysKey(mapName), gson.toJson(new ArrayList<>(keys)));
    }

    /**
     * Internal method to register map type without acquiring lock.
     * Caller must hold writeLock.
     */
    private void registerMapTypeInternal(String mapName, Class<?> typeClass) {
        mapTypes.put(mapName, typeClass.getName());

        if (metaMap == null) {
            metaMap = new HashMap<>();
        }
        metaMap.put(MAP_TYPES_META_KEY, gson.toJson(mapTypes));
        mmkv.putString(getNamespacedKey(META_MAP), gson.toJson(metaMap));
    }

    @Override
    public void registerMapType(String mapName, Class<?> typeClass) {
        writeLock.lock();
        try {
            registerMapTypeInternal(mapName, typeClass);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void registerListType(String listName, Class<?> typeClass) {
        writeLock.lock();
        try {
            listTypes.put(listName, typeClass.getName());
            Map<String, Object> metaMap = getMetaMap();
            metaMap.put(LIST_TYPES_META_KEY, listTypes);
            mmkv.putString(getNamespacedKey(META_MAP), gson.toJson(metaMap));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Class<?> getMapType(String mapName) {
        readLock.lock();
        try {
            String className = mapTypes.get(mapName);
            if (className != null) {
                try {
                    return Class.forName(className);
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }
            return null;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> void createOrderedList(String listName, Class<V> vClass) {
        if (listName == null || vClass == null) {
            return;
        }

        writeLock.lock();
        try {
            if (!listNames.contains(listName)) {
                listNames.add(listName);
                Map<String, Object> metaMap = getMetaMap();
                metaMap.put(MAP_NAMES_META_KEY, listNames);
                mmkv.putString(getNamespacedKey(META_MAP), gson.toJson(metaMap));
                registerMapType(listName, vClass);
                mmkv.putString(getNamespacedKey(listName), gson.toJson(new ArrayList<V>()));
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> long addToList(String listName, V value) {
        if (listName == null || value == null) {
            return -1;
        }

        writeLock.lock();
        try {
            List<V> list = (List<V>) getList(listName);

            // Add the new element
            long index = list.size();
            list.add(value);

            // Save list
            saveListToMMKV(listName, list);

            return index;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> long addAllToList(String listName, List<V> values) {
        if (listName == null || values == null || values.isEmpty()) {
            return -1;
        }

        writeLock.lock();
        try {
            List<Object> list = getList(listName);

            long startIndex = list.size();

            // Add all elements
            list.addAll(values);

            // Save list
            saveListToMMKV(listName, list);

            return startIndex;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> V getFromList(String listName, long index, Class<V> vClass) {
        if (listName == null || index < 0) {
            return null;
        }

        readLock.lock();
        try {
            List<V> list = (List<V>) getList(listName);

            if (list == null || index >= list.size()) {
                return null;
            }

            return list.get((int) index);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> List<V> getAllFromList(String listName) {
        if (listName == null) {
            return new ArrayList<>();
        }

        readLock.lock();
        try {
            List<V> list = (List<V>) getList(listName);

            if (list == null) {
                return new ArrayList<>();
            }

            return new ArrayList<>(list);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> List<V> getRangeFromList(String listName, long startIndex, long endIndex) {
        if (listName == null || startIndex < 0 || endIndex <= startIndex) {
            return new ArrayList<>();
        }

        readLock.lock();
        try {
            List<V> list = (List<V>) getList(listName);

            if (list == null || startIndex >= list.size()) {
                return new ArrayList<>();
            }

            // Adjust endIndex if it's beyond the list size
            endIndex = Math.min(endIndex, list.size());

            // Create a new list with the elements in the range
            List<V> result = new ArrayList<>();
            for (long i = startIndex; i < endIndex; i++) {
                V value = list.get((int) i);
                result.add(value);
            }

            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> List<V> getRangeFromListReverse(String listName, long startIndex, long endIndex) {
        if (listName == null || startIndex < 0 || endIndex <= startIndex) {
            return new ArrayList<>();
        }

        readLock.lock();
        try {
            List<V> list = (List<V>) getList(listName);

            if (list == null || list.isEmpty()) {
                return new ArrayList<>();
            }

            int count = list.size();

            // Convert from end-based indices to start-based indices
            int startFromBeginning = Math.max(0, count - (int)endIndex);
            int endFromBeginning = Math.min(count, count - (int)startIndex);

            // Adjust if the range is beyond the list size
            if (startFromBeginning >= count || endFromBeginning <= 0) {
                return new ArrayList<>();
            }

            List<V> result = new ArrayList<>();
            // Iterate in reverse order
            for (int i = endFromBeginning - 1; i >= startFromBeginning; i--) {
                V value = list.get(i);
                result.add(value);
            }

            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean removeFromList(String listName, long index) {
        if (listName == null || index < 0) {
            return false;
        }

        writeLock.lock();
        try {
            List<Object> list = getList(listName);

            if (list == null || index >= list.size()) {
                return false;
            }

            // Remove the element
            list.remove((int) index);

            // Save list
            saveListToMMKV(listName, list);

            return true;
        } finally {
            writeLock.unlock();
        }
    }

    private List<Object> getList(String listName) {
        String listJson = mmkv.decodeString(getNamespacedKey(listName));
        if (listJson == null || listJson.isEmpty()) {
            return new ArrayList<>();
        }
        List<Object> list = JsonUtils.listFromJson(listJson, Object.class);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public int removeFromList(String listName, List<Long> indices) {
        if (listName == null || indices == null || indices.isEmpty()) {
            return 0;
        }

        writeLock.lock();
        try {
            List<Object> list = getList(listName);
            if (list == null) {
                return 0;
            }

            // Sort indices in descending order to avoid shifting issues
            List<Long> sortedIndices = new ArrayList<>(indices);
            sortedIndices.sort(Collections.reverseOrder());

            int removedCount = 0;
            for (Long index : sortedIndices) {
                if (index != null && index >= 0 && index < list.size()) {
                    list.remove(index.intValue());
                    removedCount++;
                }
            }

            // Save list
            saveListToMMKV(listName, list);

            return removedCount;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long getListSize(String listName) {
        if (listName == null) {
            return 0;
        }

        readLock.lock();
        try {
            List<Object> list = getList(listName);
            return list != null ? list.size() : 0;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void clearList(String listName) {
        if (listName == null) {
            return;
        }

        writeLock.lock();
        try {
            List<Object> list = getList(listName);
            if (list != null) {
                list.clear();
                saveListToMMKV(listName, list);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public LinkedHashMap<String, T> getMap(Long size, String fromId, Long fromIndex,
                                           boolean isFromInclude, String toId, Long toIndex,
                                           boolean isToInclude, boolean isFromEnd) {
//        if (idList.isEmpty()) {
//            return new LinkedHashMap<>();
//        }
//
//        try {
//            long startIdx = 0;
//            long endIdx = idList.size();
//
//            // Determine range based on parameters
//            if (isFromEnd) {
//                // Reverse iteration
//                if (fromIndex != null) {
//                    if (fromIndex == 0) return new LinkedHashMap<>();
//                    endIdx = isFromInclude ? fromIndex + 1 : fromIndex;
//                } else if (fromId != null) {
//                    int idx = idList.indexOf(fromId);
//                    if (idx >= 0) {
//                        endIdx = isFromInclude ? idx + 1 : idx;
//                    }
//                }
//
//                if (toIndex != null) {
//                    startIdx = isToInclude ? toIndex : toIndex + 1;
//                } else if (toId != null) {
//                    int idx = idList.indexOf(toId);
//                    if (idx >= 0) {
//                        startIdx = isToInclude ? idx : idx + 1;
//                    }
//                }
//            } else {
//                // Forward iteration
//                if (fromIndex != null) {
//                    startIdx = isFromInclude ? fromIndex : fromIndex + 1;
//                } else if (fromId != null) {
//                    int idx = idList.indexOf(fromId);
//                    if (idx >= 0) {
//                        startIdx = isFromInclude ? idx : idx + 1;
//                    }
//                }
//
//                if (toIndex != null) {
//                    endIdx = isToInclude ? toIndex + 1 : toIndex;
//                } else if (toId != null) {
//                    int idx = idList.indexOf(toId);
//                    if (idx >= 0) {
//                        endIdx = isToInclude ? idx + 1 : idx;
//                    }
//                }
//            }
//
//            // Validate range
//            startIdx = Math.max(0, startIdx);
//            endIdx = Math.min(idList.size(), endIdx);
//            if (startIdx >= endIdx) {
//                return new LinkedHashMap<>();
//            }
//
//            // Build result map
//            LinkedHashMap<String, T> result = new LinkedHashMap<>();
//            String lastId = null;
//            Long lastIndex = null;
//
//            if (isFromEnd) {
//                // Reverse iteration
//                for (long i = endIdx - 1; i >= startIdx; i--) {
//                    if (size != null && result.size() >= size) {
//                        break;
//                    }
//
//                    lastIndex = i;
//                    lastId = idList.get((int) i);
//                    T item = get(lastId);
//                    if (item != null) {
//                        result.put(lastId, item);
//                    }
//                }
//            } else {
//                // Forward iteration
//                for (long i = startIdx; i < endIdx; i++) {
//                    if (size != null && result.size() >= size) {
//                        break;
//                    }
//
//                    lastIndex = i;
//                    lastId = idList.get((int) i);
//                    T item = get(lastId);
//                    if (item != null) {
//                        result.put(lastId, item);
//                    }
//                }
//            }
//
//            // Store pagination state
//            if (lastIndex != null) {
//                tempIndex.set(Math.toIntExact(lastIndex));
//            }
//            tempId.set(lastId);
//
//            return result;
//
//        } catch (Exception e) {
//            TimberLogger.e(TAG, "Error in getMap: %s", e.getMessage());
//            return new LinkedHashMap<>();
//        }

        readLock.lock();
        try {
            if (idList.isEmpty()) {
                return new LinkedHashMap<>();
            }

            // Determine start and end indices
            int startIdx = 0;
            int endIdx = idList.size();

            if (isFromEnd) {
                // When paginating from end (reverse order), fromIndex/fromId specifies where to stop (exclusive end)
                // and toIndex/toId specifies where to start (inclusive start from beginning)
                if (fromIndex != null) {
                    // fromIndex specifies the boundary - exclude items at or after this index
                    endIdx = (int) (isFromInclude ? fromIndex + 1 : fromIndex);
                } else if (fromId != null) {
                    int idx = idList.indexOf(fromId);
                    if (idx >= 0) {
                        // fromId specifies the boundary - exclude items at or after this ID
                        endIdx = isFromInclude ? idx + 1 : idx;
                    }
                }

                if (toIndex != null) {
                    startIdx = (int) (isToInclude ? toIndex : toIndex + 1);
                } else if (toId != null) {
                    int idx = idList.indexOf(toId);
                    if (idx >= 0) {
                        startIdx = isToInclude ? idx : idx + 1;
                    }
                }
            } else {
                // Normal forward pagination
                if (fromIndex != null) {
                    startIdx = (int) (isFromInclude ? fromIndex : fromIndex + 1);
                } else if (fromId != null) {
                    int idx = idList.indexOf(fromId);
                    if (idx >= 0) {
                        startIdx = isFromInclude ? idx : idx + 1;
                    }
                }

                if (toIndex != null) {
                    endIdx = (int) (isToInclude ? toIndex + 1 : toIndex);
                } else if (toId != null) {
                    int idx = idList.indexOf(toId);
                    if (idx >= 0) {
                        endIdx = isToInclude ? idx + 1 : idx;
                    }
                }
            }

            // Ensure valid range
            startIdx = Math.max(0, Math.min(startIdx, idList.size()));
            endIdx = Math.max(0, Math.min(endIdx, idList.size()));

            if (startIdx >= endIdx) {
                return new LinkedHashMap<>();
            }

            // Get the sublist
            List<String> subList = idList.subList(startIdx, endIdx);

            // Reverse if needed
            if (isFromEnd) {
                List<String> reversed = new ArrayList<>(subList);
                Collections.reverse(reversed);
                subList = reversed;
            }

            // Apply size limit
            if (size != null && size < subList.size()) {
                subList = subList.subList(0, Math.toIntExact(size));
            }

            // Build result map
            LinkedHashMap<String, T> result = new LinkedHashMap<>();
            String lastId = null;
            int lastIndex = 0;

            for (String id : subList) {
                if (id == null) continue;
                T item = itemMap.get(id);
                if (item == null) {
                    String json = mmkv.decodeString(getItemKey(id));
                    if (json != null) {
                        item = gson.fromJson(json, entityClass);
                        if (item != null) {
                            itemMap.put(id, item);
                        }
                    }
                }
                if (item != null) {
                    result.put(id, item);
                    lastId = id;
                    lastIndex = idList.indexOf(id);
                }
            }

            // Store temp values for pagination
            if (lastId != null) {
                tempIndex.set( lastIndex);
                tempId.set(lastId);
            }

            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<T> getList(Long size, String fromId, Long fromIndex,
                           boolean isFromInclude, String toId, Long toIndex,
                           boolean isToInclude, boolean isFromEnd) {
        return new ArrayList<>(getMap(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd).values());
    }

    @Override
    public void put(Map<String, T> items) {
        if (items == null || items.isEmpty()) return;

        writeLock.lock();
        try {
            // Update in-memory cache
            itemMap.putAll(items);

            // Batch write all items to MMKV
            for (Map.Entry<String, T> entry : items.entrySet()) {
                String key = entry.getKey();
                if (key == null) continue;
                mmkv.putString(getItemKey(key), gson.toJson(entry.getValue()));

                // Add to ID list if new
                if (!idList.contains(key)) {
                    idList.add(key);
                }
            }

            // Single save of ID list at the end
            saveIdList();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void put(List<T> items, String idField) {
        if (items == null || items.isEmpty()) return;

        writeLock.lock();
        try {
            // Create a map of id -> item
            Map<String, T> itemMap = new HashMap<>();
            for (T item : items) {
                try {
                    String id = item.getId();
                    itemMap.put(id, item);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to get ID from field: " + idField, e);
                }
            }

            put(itemMap);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Map<String, T> getAll() {
        readLock.lock();
        try {
            Map<String,T> itemMap = new LinkedHashMap<>();

            for (String id : idList) {
                T item = get(id);
                if (item != null) {
                    itemMap.put(id, item);
                }
            }
            return itemMap;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<T> searchString(String part) {
        readLock.lock();
        try {
            List<T> matches = new ArrayList<>();

            for (String id : idList) {
                T item = get(id);
                if (item != null) {
                    String json = gson.toJson(item);
                    if (json.contains(part)) {
                        matches.add(item);
                    }
                }
            }

            return matches;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void removeList(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        writeLock.lock();
        try {
            for (String key : ids) {
                // Remove from cache
                itemMap.remove(key);

                // Remove from MMKV
                mmkv.removeValueForKey(getItemKey(key));

                // Remove from ID list
                idList.remove(key);

                // Mark as locally removed
                putInMapInternal(LOCAL_REMOVED_MAP, key, System.currentTimeMillis());
            }

            saveIdList();
        } finally {
            writeLock.unlock();
        }
    }


    @Override
    public <V> List<V> getFromMap(String mapName, List<String> keyList) {
        readLock.lock();
        try {
            List<V> result = new ArrayList<>();
            for (String key : keyList) {
                V value = getFromMap(mapName, key);
                if (value != null) {
                    result.add(value);
                }
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> void putAllInMap(String mapName, List<String> keyList, List<V> valueList) {
        if (keyList.size() != valueList.size()) {
            throw new IllegalArgumentException("Key list and value list must be the same size");
        }

        writeLock.lock();
        try {
            for (int i = 0; i < keyList.size(); i++) {
                putInMap(mapName, keyList.get(i), valueList.get(i));
            }
        } finally {
            writeLock.unlock();
        }
    }

    private <V> void saveListToMMKV(String listName, List<V> list) {
        writeLock.lock();
        try {
            mmkv.putString(getNamespacedKey(listName), gson.toJson(new ArrayList<>(list)));
        } finally {
            writeLock.unlock();
        }
    }
}
