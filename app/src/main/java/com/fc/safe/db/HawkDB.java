package com.fc.safe.db;

import androidx.annotation.NonNull;

import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.MapQueue;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.orhanobut.hawk.Hawk;
import com.fc.fc_ajdk.utils.TimberLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A simplified Hawk-based implementation of LocalDB interface.
 * Uses a simple ordered ID list to maintain insertion order, replacing the complex SortType system.
 *
 * @param <T> The type of entity to store, must extend FcEntity
 */
public class HawkDB<T extends FcEntity> implements LocalDB<T> {
    private static final String TAG = "HawkDB";
    private static final String SETTINGS_MAP = "settings";
    public static final String ITEMS = "items";
    public static final String ID_LIST = "id_list";
    public static final String META_MAP = "meta";
    public static final String STATE_MAP = "state";
    public static final String MAP_TYPES = "map_types";
    public static final int QUEUE_SIZE = 200;
    private volatile boolean isClosed = false;

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

    public HawkDB() {
        // Simplified constructor - no sort type needed
    }

    @Override
    public void initialize(String fid, String sid, String dbPath, String dbName) {
        if (isClosed) {
            this.isClosed = false;
        }

        // Create a unique namespace prefix for this user and app combination
        this.namespacePrefix = createNamespacePrefix(fid, sid, dbName);

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
        Hawk.put(metaMapKey, metaMap);
    }

    private void loadMetaMap() {
        String namespacedKey = getNamespacedKey(META_MAP);
        metaMap = Hawk.get(namespacedKey);
        if (metaMap == null) {
            metaMap = new HashMap<>();
        }
    }

    private void loadStateMap() {
        Map<String, Object> stateMapInHawk = Hawk.get(getNamespacedKey(STATE_MAP));
        stateMap.putAll(stateMapInHawk != null ? stateMapInHawk: new HashMap<>());
    }

    private void loadIdList() {
        List<String> savedIdList = Hawk.get(getNamespacedKey(ID_LIST), new ArrayList<>());
        idList.clear();
        idList.addAll(savedIdList);
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
     * Gets a namespaced key for Hawk storage
     */
    private String getNamespacedKey(String key) {
        return namespacePrefix + key;
    }

    @Override
    public void put(String key, T value) {
        writeLock.lock();
        try {
            // Add to cache
            itemMap.put(key, value);

            // Store individual item
            Hawk.put(getItemKey(key), value);

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
        return getNamespacedKey(HawkDB.ITEMS + "_" + key);
    }


    @Override
    public T get(String key) {
        readLock.lock();
        try {
            // Try to get from cache first
            T value = itemMap.get(key);
            if (value == null) {
                // If not in cache, load from Hawk
                value = Hawk.get(getItemKey(key));
                if (value != null) {
                    itemMap.put(key, value);
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
                T value = itemMap.get(key);
                if (value == null) {
                    value = Hawk.get(getItemKey(key));
                    if (value != null) {
                        itemMap.put(key, value);
                    }
                }
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

            // Remove from Hawk
            Hawk.delete(getItemKey(key));

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

                // Remove from Hawk
                Hawk.delete(getItemKey(key));

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

    private void saveToHawk() {
        saveIdList();
        saveStateMap();
    }

    @Override
    public void saveIdList(){
        Hawk.put(getNamespacedKey(ID_LIST), idList);
    }

    @Override
    public void saveStateMap(){
        Hawk.put(getNamespacedKey(STATE_MAP), stateMap);
    }

    @Override
    public void commit() {
        // No-op for HawkDB as changes are saved immediately
    }

    @Override
    public void close() {
        if (!isClosed) {
            saveToHawk();
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
        return Hawk.get(getNamespacedKey(META_MAP));
    }

    @Override
    public Map<String, Object> getSettingsMap() {
        return Hawk.get(getNamespacedKey(SETTINGS_MAP));
    }

    @Override
    public Map<String, Object> getStateMap() {
        return Hawk.get(getNamespacedKey(STATE_MAP));
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
                stringValue = new GsonBuilder().setPrettyPrinting().create().toJson(value);
            }

            Map<String, Object> settingsMap = Hawk.get(getNamespacedKey(SETTINGS_MAP));
            if (settingsMap == null) {
                settingsMap = new HashMap<>();
            }
            settingsMap.put(key, stringValue);
            Hawk.put(getNamespacedKey(SETTINGS_MAP), settingsMap);
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
            Map<String, Object> settingsMap = Hawk.get(getNamespacedKey(SETTINGS_MAP));
            if (settingsMap != null) {
                settingsMap.remove(key);
                Hawk.put(getNamespacedKey(SETTINGS_MAP), settingsMap);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeAllSettings() {
        writeLock.lock();
        try {
            Hawk.put(getNamespacedKey(SETTINGS_MAP), new HashMap<>());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Map<String, String> getAllSettings() {
        readLock.lock();
        try {
            Map<String, Object> settingsMap = Hawk.get(getNamespacedKey(SETTINGS_MAP));
            if (settingsMap == null) {
                return new HashMap<>();
            }
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
            Map<String, Object> settingsMap = Hawk.get(getNamespacedKey(SETTINGS_MAP));
            return settingsMap != null ? settingsMap.get(key) : null;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void putState(String key, Object value) {
        writeLock.lock();
        try {
            stateMap.put(key, value);
            Hawk.put(getNamespacedKey(STATE_MAP),stateMap);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeState(String key) {
        writeLock.lock();
        try {
            stateMap.remove(key);
            Hawk.put(getNamespacedKey(STATE_MAP),stateMap);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clearAllState() {
        writeLock.lock();
        try {
            stateMap.clear();
            Hawk.put(getNamespacedKey(STATE_MAP),stateMap);
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
            Map<String, Object> metaMap = Hawk.get(metaMapKey);
            Object value = metaMap != null ? metaMap.get(key) : null;
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
            Map<String, Object> metaMap = Hawk.get(metaMapKey);
            if (metaMap == null) {
                metaMap = new HashMap<>();
            }
            metaMap.put(key, value);
            Hawk.put(metaMapKey, metaMap);
            TimberLogger.d(TAG, "Updated metaMap: %s", metaMap);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeMeta(String key) {
        writeLock.lock();
        try {
            Map<String, Object> metaMap = Hawk.get(getNamespacedKey(META_MAP));
            if (metaMap != null) {
                metaMap.remove(key);
                Hawk.put(getNamespacedKey(META_MAP), metaMap);
            }
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

            // Clear all items from Hawk
            for (String key : idList) {
                Hawk.delete(getItemKey(key));
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

            // Clear all items from Hawk
            for (String key : idList) {
                Hawk.delete(getItemKey(key));
            }

            // Clear ID list
            idList.clear();
            saveIdList();

            // Clear meta, settings and state maps
            Hawk.put(getNamespacedKey(META_MAP), new HashMap<>());
            Hawk.put(getNamespacedKey(SETTINGS_MAP), new HashMap<>());
            Hawk.put(getNamespacedKey(STATE_MAP), new HashMap<>());
            stateMap.clear();

            // Clear all named maps
            for (String mapName : mapTypes.keySet()) {
                // Clear individual entries
                Set<String> keys = Hawk.get(getMapKeysKey(mapName), new HashSet<>());
                if (keys != null) {
                    for (String key : keys) {
                        Hawk.delete(getMapKey(mapName, key));
                    }
                }
                // Clear keys set
                Hawk.delete(getMapKeysKey(mapName));
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
                Hawk.put(getNamespacedKey(mapName),new HashMap<>());
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
            Hawk.put(getMapKey(mapName, key), str);
        }else{
            Hawk.put(getMapKey(mapName, key), value);
        }
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
                // If not in cache, load from Hawk
                if(Objects.equals(mapTypes.get(mapName), byte[].class.getName())){
                    String vStr = Hawk.get(getMapKey(mapName, key));
                    if(vStr!=null)
                        value = (V) Base64.getDecoder().decode(vStr);
                }else{
                    value = Hawk.get(getMapKey(mapName, key));
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
            Set<String> keys = Hawk.get(getMapKeysKey(mapName), new HashSet<>());
            Map<String, V> result = new HashMap<>();

            // Load each entry individually
            for (String key : keys) {
                V value = Hawk.get(getMapKey(mapName, key));
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
            Set<String> keys = Hawk.get(getMapKeysKey(mapName), new HashSet<>());

            // Remove each entry individually
            for (String key : keys) {
                Hawk.delete(getMapKey(mapName, key));
            }

            // Clear the keys set
            Hawk.delete(getMapKeysKey(mapName));

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
                Hawk.put(getMapKey(mapName, entry.getKey()), entry.getValue());
            }

            // Update the keys set
            Set<String> keys = Hawk.get(getMapKeysKey(mapName), new HashSet<>());
            keys.addAll(map.keySet());
            Hawk.put(getMapKeysKey(mapName), keys);

            // Update in-memory cache
            MapQueue<Object, Object> targetMap =getNamedMap(mapName);
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
            Hawk.delete(getMapKey(mapName, key));

            // Update keys set
            Set<String> keys = Hawk.get(getMapKeysKey(mapName), new HashSet<>());
            keys.remove(key);
            Hawk.put(getMapKeysKey(mapName), keys);

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
                Hawk.delete(getMapKey(mapName, key));
            }

            // Update keys set
            Set<String> existingKeys = Hawk.get(getMapKeysKey(mapName), new HashSet<>());
            keys.forEach(existingKeys::remove);
            Hawk.put(getMapKeysKey(mapName), existingKeys);

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
            // Get size from keys set instead of loading entire map
            Set<String> keys = Hawk.get(getMapKeysKey(mapName), new HashSet<>());
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

    /**
     * Internal method to register map type without acquiring lock.
     * Caller must hold writeLock.
     */
    private void registerMapTypeInternal(String mapName, Class<?> typeClass) {
        mapTypes.put(mapName, typeClass.getName());

        if (metaMap == null) {
            metaMap = new HashMap<>();
        }
        metaMap.put(MAP_TYPES_META_KEY, new Gson().toJson(mapTypes));
        Hawk.put(getNamespacedKey(META_MAP), metaMap);
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
            // Store the map types in meta
            Map<String, String> typeNames = new HashMap<>();
            Map<String, Object> metaMap = Hawk.get(getNamespacedKey(META_MAP));
            if (metaMap == null) {
                metaMap = new HashMap<>();
            }
            metaMap.put(LIST_TYPES_META_KEY, listTypes);
            Hawk.put(getNamespacedKey(META_MAP), metaMap);
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
                Map<String, Object> metaMap = Hawk.get(getNamespacedKey(META_MAP));
                if (metaMap == null) {
                    metaMap = new HashMap<>();
                }
                metaMap.put(MAP_NAMES_META_KEY, listNames);
                Hawk.put(getNamespacedKey(META_MAP), metaMap);
                registerMapType(listName, vClass);
                Hawk.put(getNamespacedKey(listName), new ArrayList<V>());
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

            // Increment the count
            saveListToHawk(listName,list);

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

            long startIndex =list.size();

                    // Add all elements
            list.addAll(values);

            // Update the count
            saveListToHawk(listName,list);

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

            // Update the count
            saveListToHawk(listName,list);

            return true;
        } finally {
            writeLock.unlock();
        }
    }

    private List<Object> getList(String listName) {
        return Hawk.get(getNamespacedKey(listName));
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

            // Update the count
            saveListToHawk(listName,list);

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
                saveListToHawk(listName,list);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public LinkedHashMap<String, T> getMap(Long size, String fromId, Long fromIndex,
                                           boolean isFromInclude, String toId, Long toIndex,
                                           boolean isToInclude, boolean isFromEnd) {
        if (idList.isEmpty()) {
            return new LinkedHashMap<>();
        }

        try {
            long startIdx = 0;
            long endIdx = idList.size();

            // Determine range based on parameters
            if (isFromEnd) {
                // Reverse iteration
                if (fromIndex != null) {
                    if (fromIndex == 0) return new LinkedHashMap<>();
                    endIdx = isFromInclude ? fromIndex + 1 : fromIndex;
                } else if (fromId != null) {
                    int idx = idList.indexOf(fromId);
                    if (idx >= 0) {
                        endIdx = isFromInclude ? idx + 1 : idx;
                    }
                }

                if (toIndex != null) {
                    startIdx = isToInclude ? toIndex : toIndex + 1;
                } else if (toId != null) {
                    int idx = idList.indexOf(toId);
                    if (idx >= 0) {
                        startIdx = isToInclude ? idx : idx + 1;
                    }
                }
            } else {
                // Forward iteration
                if (fromIndex != null) {
                    startIdx = isFromInclude ? fromIndex : fromIndex + 1;
                } else if (fromId != null) {
                    int idx = idList.indexOf(fromId);
                    if (idx >= 0) {
                        startIdx = isFromInclude ? idx : idx + 1;
                    }
                }

                if (toIndex != null) {
                    endIdx = isToInclude ? toIndex + 1 : toIndex;
                } else if (toId != null) {
                    int idx = idList.indexOf(toId);
                    if (idx >= 0) {
                        endIdx = isToInclude ? idx + 1 : idx;
                    }
                }
            }

            // Validate range
            startIdx = Math.max(0, startIdx);
            endIdx = Math.min(idList.size(), endIdx);
            if (startIdx >= endIdx) {
                return new LinkedHashMap<>();
            }

            // Build result map
            LinkedHashMap<String, T> result = new LinkedHashMap<>();
            String lastId = null;
            Long lastIndex = null;

            if (isFromEnd) {
                // Reverse iteration
                for (long i = endIdx - 1; i >= startIdx; i--) {
                    if (size != null && result.size() >= size) {
                        break;
                    }

                    lastIndex = i;
                    lastId = idList.get((int) i);
                    T item = get(lastId);
                    if (item != null) {
                        result.put(lastId, item);
                    }
                }
            } else {
                // Forward iteration
                for (long i = startIdx; i < endIdx; i++) {
                    if (size != null && result.size() >= size) {
                        break;
                    }

                    lastIndex = i;
                    lastId = idList.get((int) i);
                    T item = get(lastId);
                    if (item != null) {
                        result.put(lastId, item);
                    }
                }
            }

            // Store pagination state
            if (lastIndex != null) {
                tempIndex.set(Math.toIntExact(lastIndex));
            }
            tempId.set(lastId);

            return result;

        } catch (Exception e) {
            TimberLogger.e(TAG, "Error in getMap: %s", e.getMessage());
            return new LinkedHashMap<>();
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

            // Batch write all items to Hawk
            for (Map.Entry<String, T> entry : items.entrySet()) {
                String key = entry.getKey();
                Hawk.put(getItemKey(key), entry.getValue());

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
            Gson gson = new GsonBuilder().setPrettyPrinting().create();

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

                // Remove from Hawk
                Hawk.delete(getItemKey(key));

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

    private <V> void saveListToHawk(String listName, List<V> list) {
        writeLock.lock();
        try {
            Hawk.put(getNamespacedKey(listName), new ArrayList<>(list));
        } finally {
            writeLock.unlock();
        }
    }
}
