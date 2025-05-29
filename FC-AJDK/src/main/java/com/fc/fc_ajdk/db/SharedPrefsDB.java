package com.fc.fc_ajdk.db;

import android.content.Context;
import android.content.SharedPreferences;
import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.fc.fc_ajdk.utils.TimberLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Base64;

/**
 * A SharedPreferences implementation of the LocalDB interface.
 * This implementation uses Android's SharedPreferences for persistent storage and provides:
 * 
 * 1. A main map with String keys and FcEntity values 
 * 2. Three system maps (meta, settings, state) for storing configuration and internal state
 * 3. User-defined maps with customizable value types
 * 4. Various sort order implementations (key, access, update, and birth order)
 * 5. Thread-safe operations via read-write locks
 * 6. In-memory caching for improved performance
 *
 * @param <T> The type of objects to store, must extend FcEntity
 */
public class SharedPrefsDB<T extends FcEntity> implements LocalDB<T> {
    private static final String TAG = "SharedPrefsDB";
    private static final String ITEM_PREFIX = "item:";
    private static final String META_PREFIX = "meta:";
    private static final String SETTING_PREFIX = "setting:";
    private static final String STATE_PREFIX = "state:";
    private static final String MAP_PREFIX = "map:";
    private static final String LIST_COUNT_PREFIX = "list_count:";
    private static final String LIST_ITEM_PREFIX = "list_item:";

    private final Context context;
    private final SortType sortType;
    private final Class<T> entityClass;
    private final Gson gson;
    private volatile boolean isClosed = false;
    private String dbName;
    private SharedPreferences sharedPreferences;
    private final String sortField;

    // Add read-write lock
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    
    // Thread-local variables
    private final ThreadLocal<Long> tempIndex = new ThreadLocal<>();
    private final ThreadLocal<String> tempId = new ThreadLocal<>();
    
    // Index maps (kept in memory for performance)
    private final ConcurrentHashMap<Long, String> indexIdMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> idIndexMap = new ConcurrentHashMap<>();
    private final Map<String, Object> metaCache = new ConcurrentHashMap<>();
    private final Map<String, Object> settingsCache = new ConcurrentHashMap<>();
    private final Map<String, Object> stateCache = new ConcurrentHashMap<>();
    private final Set<String> mapNames = new HashSet<>();
    private Map<String, String> mapTypes;
    private final Set<String> listNames = new HashSet<>();

    /**
     * Creates a new SharedPrefsDB instance with the specified parameters.
     *
     * @param context The Android context
     * @param sortType The sort type to use for the main map
     * @param entityClass The class of entities being stored
     * @param sortField The sort field to use for sorting
     */
    public SharedPrefsDB(Context context, SortType sortType, Class<T> entityClass, String sortField) {
        this.context = context.getApplicationContext();
        this.sortType = sortType;
        this.entityClass = entityClass;
        this.sortField = sortField;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public void initialize(String fid, String sid, String dbPath, String dbName) {
        this.dbName = dbName;
        
        if (isClosed) {
            this.isClosed = false;
        }
        
        String prefsName = makePrefsName(fid, sid, dbName);
        sharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        
        TimberLogger.d(TAG,"SharedPrefsDB initialized: %s", prefsName);
        
        // Load metadata and indices
        loadMetaData();
        if (sortType != SortType.NO_SORT) {
            loadIndices();
        }
    }

    private String makePrefsName(String fid, String sid, String dbName) {
        return (fid != null ? fid + "_" : "") + 
               (sid != null ? sid + "_" : "") + 
               dbName.toLowerCase() + "_prefs";
    }

    @Override
    public void put(String key, T value) {
        if (key == null || value == null) {
            TimberLogger.w(TAG,"Attempted to put null key or value");
            return;
        }
        
        writeLock.lock();
        try {
            String jsonValue = value.toJson();
            sharedPreferences.edit()
                .putString(getItemKey(key), jsonValue)
                .apply();
            
            // Update indices if needed
            if (sortType != SortType.NO_SORT) {
                updateIndex(key);
            }
        } catch (Exception e) {
            TimberLogger.e(TAG,"Error putting item with key %s: %s", key, e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public T get(String key) {
        if (key == null) return null;
        
        T item = null;
        
        readLock.lock();
        try {
            String jsonValue = sharedPreferences.getString(getItemKey(key), null);
            if (jsonValue == null) {
                return null;
            }
            
            item = FcEntity.fromJson(jsonValue, entityClass);
        } finally {
            readLock.unlock();
        }
        
        // Update access order if needed
        if (sortType == SortType.ACCESS_ORDER && item != null) {
            updateAccessOrder(key);
        }
        
        return item;
    }

    @Override
    public void remove(String key) {
        if (key == null) return;
        
        writeLock.lock();
        try {
            sharedPreferences.edit()
                .remove(getItemKey(key))
                .apply();
            
            // Update indices if needed
            if (sortType != SortType.NO_SORT) {
                Long index = idIndexMap.get(key);
                if (index != null) {
                    indexIdMap.remove(index);
                    idIndexMap.remove(key);
                    shiftHigherIndicesDown1(index);
                }
            }
            
            // Mark as locally removed
            putInMap(LOCAL_REMOVED_MAP, key, System.currentTimeMillis());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void commit() {
        // SharedPreferences writes are automatically committed
    }

    @Override
    public void close() {
        if (!isClosed) {
            try {
                TimberLogger.d(TAG,"Closing SharedPrefsDB instance: %s", dbName);
                
                // Clean up thread-local variables
                cleanupThreadLocals();
                
                // Clear caches
                indexIdMap.clear();
                idIndexMap.clear();
                metaCache.clear();
                settingsCache.clear();
                stateCache.clear();
                mapNames.clear();
                if (mapTypes != null) {
                    mapTypes.clear();
                }
                
                TimberLogger.d(TAG,"SharedPrefsDB instance closed successfully: %s", dbName);
            } catch (Exception e) {
                TimberLogger.e(TAG,"Error during SharedPrefsDB cleanup: %s", e.getMessage());
                throw new RuntimeException("Failed to close SharedPrefsDB", e);
            } finally {
                isClosed = true;
            }
        }
    }

    private void cleanupThreadLocals() {
        tempIndex.remove();
        tempId.remove();
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public long getTempIndex() {
        Long value = tempIndex.get();
        return value != null ? value : 0L;
    }

    @Override
    public String getTempId() {
        return tempId.get();
    }

    @Override
    public Map<String, T> getItemMap() {
        Map<String, T> result = new HashMap<>();
        readLock.lock();
        try {
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(ITEM_PREFIX)) {
                    String itemKey = key.substring(ITEM_PREFIX.length());
                    String jsonValue = (String) entry.getValue();
                    T item = FcEntity.fromJson(jsonValue, entityClass);
                    if (item != null) {
                        result.put(itemKey, item);
                    }
                }
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    @Override
    public Map<String, Object> getMetaMap() {
        return new HashMap<>(metaCache);
    }

    @Override
    public Map<String, Object> getSettingsMap() {
        return new HashMap<>(settingsCache);
    }

    @Override
    public Map<String, Object> getStateMap() {
        return new HashMap<>(stateCache);
    }

    @Override
    public NavigableMap<Long, String> getIndexIdMap() {
        return new TreeMap<>(indexIdMap);
    }

    @Override
    public NavigableMap<String, Long> getIdIndexMap() {
        return new TreeMap<>(idIndexMap);
    }

    @Override
    public Long getIndexById(String id) {
        return idIndexMap.get(id);
    }

    @Override
    public String getIdByIndex(long index) {
        return indexIdMap.get(index);
    }

    @Override
    public T getByIndex(long index) {
        String id = getIndexIdMap().get(index);
        return id != null ? get(id) : null;
    }

    @Override
    public int getSize() {
        int count = 0;
        readLock.lock();
        try {
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            for (String key : allPrefs.keySet()) {
                if (key.startsWith(ITEM_PREFIX)) {
                    count++;
                }
            }
        } finally {
            readLock.unlock();
        }
        return count;
    }

    @Override
    public boolean isEmpty() {
        return getAll().isEmpty();
    }

    // Helper methods for key generation
    private String getItemKey(String key) {
        return ITEM_PREFIX + key;
    }

    private String getMetaKey(String key) {
        return META_PREFIX + key;
    }

    private String getSettingsKey(String key) {
        return SETTING_PREFIX + key;
    }

    private String getStateKey(String key) {
        return STATE_PREFIX + key;
    }

    private String getMapKey(String mapName, String key) {
        return getMapKeyPrefix(mapName) + key;
    }

    private String getMapKeyPrefix(String mapName) {
        return MAP_PREFIX + mapName + ":";
    }

    // Additional methods for handling indices and metadata
    private void loadMetaData() {
        readLock.lock();
        try {
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(META_PREFIX)) {
                    String metaKey = key.substring(META_PREFIX.length());
                    Object value = entry.getValue();
                    
                    // Handle special cases for metadata
                    if (metaKey.equals("map_names")) {
                        if (value instanceof String) {
                            try {
                                @SuppressWarnings("unchecked")
                                Set<String> mapNamesSet = gson.fromJson((String) value, HashSet.class);
                                mapNames.addAll(mapNamesSet);
                            } catch (Exception e) {
                                TimberLogger.e(TAG, "Error parsing map_names: %s", e.getMessage());
                            }
                        }
                    } else if (metaKey.equals("map_types")) {
                        if (value instanceof String) {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, String> typesMap = gson.fromJson((String) value, HashMap.class);
                                mapTypes = typesMap;
                            } catch (Exception e) {
                                TimberLogger.e(TAG, "Error parsing map_types: %s", e.getMessage());
                            }
                        }
                    } else {
                        metaCache.put(metaKey, value);
                    }
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    private void loadIndices() {
        if (sortType == SortType.NO_SORT) return;

        readLock.lock();
        try {
            // Clear existing indices
            indexIdMap.clear();
            idIndexMap.clear();

            // Get all items
            Map<String, T> allItems = getItemMap();
            
            // First try to load existing indices from SharedPreferences
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            boolean hasValidIndices = false;
            
            // Check if we have valid indices stored
            for (String key : allPrefs.keySet()) {
                if (key.startsWith("index:")) {
                    String id = (String) allPrefs.get(key);
                    if (id != null && allItems.containsKey(id)) {
                        hasValidIndices = true;
                        break;
                    }
                }
            }
            
            if (hasValidIndices) {
                // Load existing indices
                for (String key : allPrefs.keySet()) {
                    if (key.startsWith("index:")) {
                        try {
                            long index = Long.parseLong(key.substring("index:".length()));
                            String id = (String) allPrefs.get(key);
                            if (id != null && allItems.containsKey(id)) {
                                indexIdMap.put(index, id);
                                idIndexMap.put(id, index);
                            }
                        } catch (NumberFormatException e) {
                            TimberLogger.e(TAG, "Invalid index format: %s", key);
                        }
                    }
                }
                
                // Verify if all items have indices
                if (indexIdMap.size() != allItems.size()) {
                    // Some items are missing indices, rebuild them
                    reIndex(null, null);
                }
            } else {
                // No valid indices found, rebuild them
                reIndex(null, null);
            }
        } finally {
            readLock.unlock();
        }
    }

    private void updateIndex(String key) {
        if (sortType == SortType.NO_SORT) return;

        writeLock.lock();
        try {
            // Get current index for this key if it exists
            Long currentIndex = idIndexMap.get(key);
            
            // Get all items to determine proper ordering
            Map<String, T> allItems = getItemMap();
            
            // If this is a new item or we need to reorder
            if (currentIndex == null || sortType != SortType.BIRTH_ORDER) {
                // For simplicity and consistency, just rebuild all indices
                // This ensures correct ordering based on the sort type
                reIndex(null, null);
            } else {
                // For BIRTH_ORDER, if the item already has an index, keep it
                // Just ensure it's saved to SharedPreferences
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("index:" + currentIndex, key);
                editor.putString("id:" + key, String.valueOf(currentIndex));
                editor.apply();
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void updateAccessOrder(String key) {
        if (sortType != SortType.ACCESS_ORDER) {
            return;
        }
        
        writeLock.lock();
        try {
            // Get current index for this key if it exists
            Long currentIndex = idIndexMap.get(key);
            
            // Remove old index if exists
            if (currentIndex != null) {
                indexIdMap.remove(currentIndex);
                idIndexMap.remove(key);
            }
            
            // Add at the end with next available index
            long newIndex = indexIdMap.isEmpty() ? 1 : Collections.max(indexIdMap.keySet()) + 1;
            indexIdMap.put(newIndex, key);
            idIndexMap.put(key, newIndex);
            
            // Save to SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("index:" + newIndex, key);
            editor.putString("id:" + key, String.valueOf(newIndex));
            editor.apply();
        } finally {
            writeLock.unlock();
        }
    }

    private void shiftHigherIndicesDown1(Long index) {
        writeLock.lock();
        try {
            // Get all entries with indices higher than the removed index
            List<Map.Entry<Long, String>> entriesToShift = new ArrayList<>();
            for (Map.Entry<Long, String> entry : indexIdMap.entrySet()) {
                if (entry.getKey() > index) {
                    entriesToShift.add(entry);
                }
            }
            
            // Sort by index in descending order to avoid conflicts
            entriesToShift.sort((e1, e2) -> Long.compare(e2.getKey(), e1.getKey()));
            
            // Shift each entry down by 1
            for (Map.Entry<Long, String> entry : entriesToShift) {
                String id = entry.getValue();
                long oldIndex = entry.getKey();
                long newIndex = oldIndex - 1;
                
                // Remove old mappings
                indexIdMap.remove(oldIndex);
                idIndexMap.remove(id);
                
                // Add new mappings
                indexIdMap.put(newIndex, id);
                idIndexMap.put(id, newIndex);
                
                // Update in SharedPreferences
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.remove("index:" + oldIndex);
                editor.putString("index:" + newIndex, id);
                editor.putString("id:" + id, String.valueOf(newIndex));
                editor.apply();
            }
        } finally {
            writeLock.unlock();
        }
    }

    // Add helper methods for field value extraction and comparison
    private Long getFieldValue(T obj, String fieldName) {
        try {
            return (Long) obj.getClass().getMethod(fieldName).invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }
    
    private int compareValues(Long h1, Long h2) {
        if (h1 == null && h2 == null) return 0;
        if (h1 == null) return -1;
        if (h2 == null) return 1;
        return h1.compareTo(h2);
    }

    // Additional required interface methods
    @Override
    public void putMeta(String key, Object value) {
        writeLock.lock();
        try {
            metaCache.put(key, value);
            String jsonValue = gson.toJson(value);
            sharedPreferences.edit()
                .putString(getMetaKey(key), jsonValue)
                .apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void putSetting(String key, Object value) {
        writeLock.lock();
        try {
            settingsCache.put(key, value);
            String jsonValue = gson.toJson(value);
            sharedPreferences.edit()
                .putString(getSettingsKey(key), jsonValue)
                .apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void putState(String key, Object value) {
        if (value == null || key == null) return;
        writeLock.lock();
        try {
            stateCache.put(key, value);
            String jsonValue = gson.toJson(value);
            sharedPreferences.edit()
                .putString(getStateKey(key), jsonValue)
                .apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Object getMeta(String key) {
        readLock.lock();
        try {
            if (metaCache.containsKey(key)) {
                return metaCache.get(key);
            }
            
            String jsonValue = sharedPreferences.getString(getMetaKey(key), null);
            if (jsonValue == null) {
                return null;
            }
            
            Object value = gson.fromJson(jsonValue, Object.class);
            metaCache.put(key, value);
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Object getSetting(String key) {
        readLock.lock();
        try {
            if (settingsCache.containsKey(key)) {
                return settingsCache.get(key);
            }
            
            String jsonValue = sharedPreferences.getString(getSettingsKey(key), null);
            if (jsonValue == null) {
                return null;
            }
            
            Object value = gson.fromJson(jsonValue, Object.class);
            settingsCache.put(key, value);
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Object getState(String key) {
        readLock.lock();
        try {
            if (stateCache.containsKey(key)) {
                return stateCache.get(key);
            }
            
            String jsonValue = sharedPreferences.getString(getStateKey(key), null);
            if (jsonValue == null) {
                return null;
            }
            
            Object value = gson.fromJson(jsonValue, Object.class);
            stateCache.put(key, value);
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void removeMeta(String key) {
        writeLock.lock();
        try {
            metaCache.remove(key);
            sharedPreferences.edit()
                .remove(getMetaKey(key))
                .apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeSetting(String key) {
        writeLock.lock();
        try {
            settingsCache.remove(key);
            sharedPreferences.edit()
                .remove(getSettingsKey(key))
                .apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeState(String key) {
        writeLock.lock();
        try {
            stateCache.remove(key);
            sharedPreferences.edit()
                .remove(getStateKey(key))
                .apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            for (String key : allPrefs.keySet()) {
                if (key.startsWith(ITEM_PREFIX)) {
                    editor.remove(key);
                }
            }
            editor.apply();
            
            if (sortType != SortType.NO_SORT) {
                indexIdMap.clear();
                idIndexMap.clear();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clearDB() {
        writeLock.lock();
        try {
            sharedPreferences.edit().clear().apply();
            
            // Clear in-memory data
            indexIdMap.clear();
            idIndexMap.clear();
            metaCache.clear();
            settingsCache.clear();
            stateCache.clear();
            mapNames.clear();
            
            // Reinitialize standard maps
            createMap(LOCAL_REMOVED_MAP, Long.class);
            createMap(ON_CHAIN_DELETED_MAP, Long.class);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeList(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        
        writeLock.lock();
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            for (String id : ids) {
                editor.remove(getItemKey(id));
                
                if (sortType != SortType.NO_SORT) {
                    Long index = idIndexMap.get(id);
                    if (index != null) {
                        indexIdMap.remove(index);
                        idIndexMap.remove(id);
                    }
                }
                
                putInMap(LOCAL_REMOVED_MAP, id, System.currentTimeMillis());
            }
            editor.apply();
            
            if (sortType != SortType.NO_SORT) {
                reIndex(null, null);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Map<String, T> getAll() {
        readLock.lock();
        try {
            return getItemMap();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public List<T> searchString(String part) {
        if (part == null || part.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<T> matches = new ArrayList<>();
        readLock.lock();
        try {
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(ITEM_PREFIX)) {
                    String jsonValue = (String) entry.getValue();
                    if (jsonValue.contains(part)) {
                        T item = FcEntity.fromJson(jsonValue, entityClass);
                        if (item != null) {
                            matches.add(item);
                        }
                    }
                }
            }
        } finally {
            readLock.unlock();
        }
        
        return matches;
    }

    @Override
    public void removeAllSettings() {
        writeLock.lock();
        try {
            settingsCache.clear();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            for (String key : allPrefs.keySet()) {
                if (key.startsWith(SETTING_PREFIX)) {
                    editor.remove(key);
                }
            }
            editor.apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> void putInMap(String mapName, String key, V value) {
        writeLock.lock();
        try {
            String jsonValue;
            if (value instanceof byte[]) {
                // Special handling for byte arrays - use Base64 encoding
                jsonValue = Base64.getEncoder().encodeToString((byte[]) value);
                // Store a special marker to indicate this is a Base64-encoded byte array
                sharedPreferences.edit()
                    .putString(getMapKey(mapName, key), jsonValue)
                    .putBoolean(getMapKey(mapName, key) + "_isByteArray", true)
                    .apply();
            } else {
                // Normal handling for other types
                jsonValue = gson.toJson(value);
                sharedPreferences.edit()
                    .putString(getMapKey(mapName, key), jsonValue)
                    .apply();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> V getFromMap(String mapName, String key) {
        if (mapName == null || key == null) return null;
        
        readLock.lock();
        try {
            String jsonValue = sharedPreferences.getString(getMapKey(mapName, key), null);
            if (jsonValue == null) {
                return null;
            }
            
            // Check if this is a Base64-encoded byte array
            boolean isByteArray = sharedPreferences.getBoolean(getMapKey(mapName, key) + "_isByteArray", false);
            if (isByteArray) {
                // Decode from Base64
                @SuppressWarnings("unchecked")
                V value = (V) Base64.getDecoder().decode(jsonValue);
                return value;
            }
            
            // Get the type from mapTypes if available
            Class<?> valueType = Object.class;
            if (mapTypes != null && mapTypes.containsKey(mapName)) {
                try {
                    valueType = Class.forName(mapTypes.get(mapName));
                } catch (ClassNotFoundException e) {
                    TimberLogger.e(TAG,"Error getting type for map %s: %s", mapName, e.getMessage());
                }
            }
            
            @SuppressWarnings("unchecked")
            V value = (V) gson.fromJson(jsonValue, valueType);
            return value;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void removeFromMap(String mapName, String key) {
        writeLock.lock();
        try {
            sharedPreferences.edit()
                .remove(getMapKey(mapName, key))
                .apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void clearMap(String mapName) {
        writeLock.lock();
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            String mapPrefix = getMapKeyPrefix(mapName);
            for (String key : allPrefs.keySet()) {
                if (key.startsWith(mapPrefix)) {
                    editor.remove(key);
                }
            }
            editor.apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> void createMap(String mapName, Class<V> vClass) {
        writeLock.lock();
        try {
            if (mapName == null || mapName.isEmpty()) {
                TimberLogger.w(TAG, "Attempted to create map with null or empty name");
                return;
            }
            
            // Register the map type
            registerMapType(mapName, vClass);
            
            // Add to map names set
            mapNames.add(mapName);
            
            // Save map names to preferences
            String mapNamesJson = gson.toJson(mapNames);
            sharedPreferences.edit()
                .putString(META_PREFIX + "map_names", mapNamesJson)
                .apply();
            
            TimberLogger.d(TAG, "Created map: %s with type: %s", mapName, vClass.getName());
        } finally {
            writeLock.unlock();
        }
    }

    public void removeMap(String mapName) {
        writeLock.lock();
        try {
            clearMap(mapName);
            mapNames.remove(mapName);
            if (mapTypes != null) {
                mapTypes.remove(mapName);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Set<String> getMapNames() {
        return new HashSet<>(mapNames);
    }

    @Override
    public <V> long addToList(String listName, V value) {
        if (listName == null || value == null) {
            return -1;
        }
        
        writeLock.lock();
        try {
            long count = sharedPreferences.getLong(getListCountKey(listName), 0);
            String jsonValue = gson.toJson(value);
            sharedPreferences.edit()
                .putString(getListItemKey(listName, count), jsonValue)
                .putLong(getListCountKey(listName), count + 1)
                .apply();
            return count;
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
            long count = sharedPreferences.getLong(getListCountKey(listName), 0);
            long startIndex = count;
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            for (V value : values) {
                if (value != null) {
                    String jsonValue = gson.toJson(value);
                    editor.putString(getListItemKey(listName, count), jsonValue);
                    count++;
                }
            }
            
            editor.putLong(getListCountKey(listName), count);
            editor.apply();
            
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
            String jsonValue = sharedPreferences.getString(getListItemKey(listName, index), null);
            if (jsonValue == null) {
                return null;
            }
            return gson.fromJson(jsonValue, vClass);
        } finally {
            readLock.unlock();
        }
    }

    private String getListCountKey(String listName) {
        return LIST_COUNT_PREFIX + listName;
    }

    private String getListItemKey(String listName, long index) {
        return LIST_ITEM_PREFIX + listName + ":" + index;
    }

    public void reIndex(String updateOrderField, String birthOrderField) {
        if (sortType == SortType.NO_SORT) return;

        writeLock.lock();
        try {
            // Clear existing indices
            indexIdMap.clear();
            idIndexMap.clear();

            // Get all items
            Map<String, T> allItems = getItemMap();

            // Sort items based on sort type
            List<Map.Entry<String, T>> sortedEntries = new ArrayList<>(allItems.entrySet());

            switch (sortType) {
                case KEY_ORDER:
                    sortedEntries.sort(Map.Entry.comparingByKey());
                    break;
                case UPDATE_ORDER:
                case ACCESS_ORDER:
                    if (updateOrderField != null) {
                        sortedEntries.sort((e1, e2) -> {
                            Long h1 = getFieldValue(e1.getValue(), updateOrderField);
                            Long h2 = getFieldValue(e2.getValue(), updateOrderField);
                            return compareValues(h1, h2);
                        });
                    }
                    break;
                case BIRTH_ORDER:
                    if (birthOrderField != null) {
                        sortedEntries.sort((e1, e2) -> {
                            Long h1 = getFieldValue(e1.getValue(), birthOrderField);
                            Long h2 = getFieldValue(e2.getValue(), birthOrderField);
                            return compareValues(h1, h2);
                        });
                    }
                    break;
                default:
                    // For NO_SORT, we'll use the current order
                    break;
            }

            // Rebuild indices
            long index = 0;
            for (Map.Entry<String, T> entry : sortedEntries) {
                String id = entry.getKey();
                indexIdMap.put(index, id);
                idIndexMap.put(id, index);
                index++;
            }

            // Save indices to SharedPreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();

            // Save index to ID mapping
            for (Map.Entry<Long, String> entry : indexIdMap.entrySet()) {
                editor.putString("index:" + entry.getKey(), entry.getValue());
            }

            // Save ID to index mapping
            for (Map.Entry<String, Long> entry : idIndexMap.entrySet()) {
                editor.putString("id:" + entry.getKey(), String.valueOf(entry.getValue()));
            }

            editor.apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public SortType getSortType() {
        return sortType;
    }

    @Override
    public String getSortField() {
        return sortField;
    }

    @Override
    public List<T> get(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<T> result = new ArrayList<>();
        readLock.lock();
        try {
            for (String key : keys) {
                T item = get(key);
                if (item != null) {
                    result.add(item);
                }
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    @Override
    public void remove(List<T> list) {
        if (list == null || list.isEmpty()) return;
        
        writeLock.lock();
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            List<String> keysToRemove = new ArrayList<>();
            
            for (T item : list) {
                if (item != null) {
                    String key = item.getId();
                    if (key != null) {
                        editor.remove(getItemKey(key));
                        keysToRemove.add(key);
                    }
                }
            }
            
            editor.apply();
            
            // Update indices if needed
            if (sortType != SortType.NO_SORT) {
                for (String key : keysToRemove) {
                    Long index = idIndexMap.get(key);
                    if (index != null) {
                        indexIdMap.remove(index);
                        idIndexMap.remove(key);
                    }
                }
                
                // Reindex after removing items
                reIndex(null, null);
            }
            
            // Mark as locally removed
            for (String key : keysToRemove) {
                putInMap(LOCAL_REMOVED_MAP, key, System.currentTimeMillis());
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void saveIdIndexMap() {

    }

    @Override
    public void saveIndexIdMap() {

    }

    @Override
    public void saveStateMap() {

    }

    @Override
    public Map<String, String> getAllSettings() {
        Map<String, String> result = new HashMap<>();
        readLock.lock();
        try {
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(SETTING_PREFIX)) {
                    String settingKey = key.substring(SETTING_PREFIX.length());
                    String value = (String) entry.getValue();
                    result.put(settingKey, value);
                }
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    @Override
    public void clearAllState() {
        writeLock.lock();
        try {
            stateCache.clear();
            SharedPreferences.Editor editor = sharedPreferences.edit();
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            for (String key : allPrefs.keySet()) {
                if (key.startsWith(STATE_PREFIX)) {
                    editor.remove(key);
                }
            }
            editor.apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Map<String, String> getAllState() {
        Map<String, String> result = new HashMap<>();
        readLock.lock();
        try {
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(STATE_PREFIX)) {
                    String stateKey = key.substring(STATE_PREFIX.length());
                    String value = (String) entry.getValue();
                    result.put(stateKey, value);
                }
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    @Override
    public LinkedHashMap<String, T> getMap(Integer size, String fromId, Long fromIndex,
                                          boolean isFromInclude, String toId, Long toIndex, 
                                          boolean isToInclude, boolean isFromEnd) {
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        
        if (sortType == SortType.NO_SORT) {
            // For NO_SORT, just return all items
            Map<String, T> allItems = getItemMap();
            if (size != null && size < allItems.size()) {
                int count = 0;
                for (Map.Entry<String, T> entry : allItems.entrySet()) {
                    if (count >= size) break;
                    result.put(entry.getKey(), entry.getValue());
                    count++;
                }
            } else {
                result.putAll(allItems);
            }
            return result;
        }
        
        // For sorted access, use the index maps
        readLock.lock();
        try {
            NavigableMap<Long, String> indexMap = getIndexIdMap();
            
            // Check if the index map is empty
            if (indexMap.isEmpty()) {
                return result; // Return empty result if there are no items
            }
            
            // Determine the range of indices to include
            Long startIndex = fromIndex;
            Long endIndex = toIndex;
            
            if (fromId != null) {
                startIndex = idIndexMap.get(fromId);
            }
            
            if (toId != null) {
                endIndex = idIndexMap.get(toId);
            }
            
            // If we're paginating from the end, reverse the order
            if (isFromEnd) {
                NavigableMap<Long, String> reversedMap = indexMap.descendingMap();
                
                // Determine the range
                Long currentIndex = endIndex != null ? endIndex : reversedMap.lastKey();
                Long stopIndex = startIndex != null ? startIndex : reversedMap.firstKey();
                
                // Include or exclude the boundary indices
                if (!isToInclude && currentIndex.equals(endIndex)) {
                    currentIndex = reversedMap.lowerKey(currentIndex);
                }
                
                if (!isFromInclude && currentIndex.equals(stopIndex)) {
                    currentIndex = reversedMap.higherKey(currentIndex);
                }
                
                // Iterate through the range
                int count = 0;
                while (currentIndex != null && 
                       (size == null || count < size) && 
                       (isFromEnd ? currentIndex >= stopIndex : currentIndex <= stopIndex)) {
                    
                    String id = reversedMap.get(currentIndex);
                    if (id != null) {
                        T item = get(id);
                        if (item != null) {
                            result.put(id, item);
                            count++;
                        }
                    }
                    
                    currentIndex = reversedMap.lowerKey(currentIndex);
                }
            } else {
                // Forward pagination
                Long currentIndex = startIndex != null ? startIndex : indexMap.firstKey();
                Long stopIndex = endIndex != null ? endIndex : indexMap.lastKey();
                
                // Include or exclude the boundary indices
                if (!isFromInclude && currentIndex.equals(startIndex)) {
                    currentIndex = indexMap.higherKey(currentIndex);
                }
                
                if (!isToInclude && currentIndex.equals(stopIndex)) {
                    currentIndex = indexMap.lowerKey(currentIndex);
                }
                
                // Iterate through the range
                int count = 0;
                while (currentIndex != null && 
                       (size == null || count < size) && 
                       currentIndex <= stopIndex) {
                    
                    String id = indexMap.get(currentIndex);
                    if (id != null) {
                        T item = get(id);
                        if (item != null) {
                            result.put(id, item);
                            count++;
                        }
                    }
                    
                    currentIndex = indexMap.higherKey(currentIndex);
                }
            }
        } finally {
            readLock.unlock();
        }
        
        return result;
    }

    @Override
    public List<T> getList(Integer size, String fromId, Long fromIndex,
                          boolean isFromInclude, String toId, Long toIndex, 
                          boolean isToInclude, boolean isFromEnd) {
        return new ArrayList<>(getMap(size, fromId, fromIndex, isFromInclude, toId, toIndex, isToInclude, isFromEnd).values());
    }

    @Override
    public void putAll(Map<String, T> items) {
        if (items == null || items.isEmpty()) return;
        
        writeLock.lock();
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            for (Map.Entry<String, T> entry : items.entrySet()) {
                String key = entry.getKey();
                T value = entry.getValue();
                
                if (key != null && value != null) {
                    String jsonValue = value.toJson();
                    editor.putString(getItemKey(key), jsonValue);
                }
            }
            
            editor.apply();
            
            // Update indices if needed
            if (sortType != SortType.NO_SORT) {
                for (String key : items.keySet()) {
                    updateIndex(key);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void putAll(List<T> items, String idField) {
        if (items == null || items.isEmpty() || idField == null) return;
        
        writeLock.lock();
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            List<String> keys = new ArrayList<>();
            
            for (T item : items) {
                if (item != null) {
                    String key = item.getId();
                    if (key != null) {
                        String jsonValue = item.toJson();
                        editor.putString(getItemKey(key), jsonValue);
                        keys.add(key);
                    }
                }
            }
            
            editor.apply();
            
            // Update indices if needed
            if (sortType != SortType.NO_SORT) {
                for (String key : keys) {
                    updateIndex(key);
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> Map<String, V> getAllFromMap(String mapName) {
        Map<String, V> result = new HashMap<>();
        readLock.lock();
        try {
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            String mapPrefix = getMapKeyPrefix(mapName);
            
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(mapPrefix)) {
                    String mapKey = key.substring(mapPrefix.length());
                    String jsonValue = (String) entry.getValue();
                    
                    // Check if this is a Base64-encoded byte array
                    boolean isByteArray = sharedPreferences.getBoolean(key + "_isByteArray", false);
                    if (isByteArray) {
                        // Decode from Base64
                        @SuppressWarnings("unchecked")
                        V value = (V) Base64.getDecoder().decode(jsonValue);
                        result.put(mapKey, value);
                        continue;
                    }
                    
                    // Get the type from mapTypes if available
                    Class<?> valueType = Object.class;
                    if (mapTypes != null && mapTypes.containsKey(mapName)) {
                        try {
                            valueType = Class.forName(mapTypes.get(mapName));
                        } catch (ClassNotFoundException e) {
                            TimberLogger.e(TAG,"Error getting type for map %s: %s", mapName, e.getMessage());
                        }
                    }
                    
                    @SuppressWarnings("unchecked")
                    V value = (V) gson.fromJson(jsonValue, valueType);
                    result.put(mapKey, value);
                }
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    @Override
    public <V> List<V> getFromMap(String mapName, List<String> keyList) {
        if (mapName == null || keyList == null || keyList.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<V> result = new ArrayList<>();
        readLock.lock();
        try {
            // Get the type from mapTypes if available
            Class<?> valueType = Object.class;
            if (mapTypes != null && mapTypes.containsKey(mapName)) {
                try {
                    valueType = Class.forName(mapTypes.get(mapName));
                } catch (ClassNotFoundException e) {
                    TimberLogger.e(TAG,"Error getting type for map %s: %s", mapName, e.getMessage());
                }
            }
            
            for (String key : keyList) {
                String mapKey = getMapKey(mapName, key);
                String jsonValue = sharedPreferences.getString(mapKey, null);
                if (jsonValue != null) {
                    // Check if this is a Base64-encoded byte array
                    boolean isByteArray = sharedPreferences.getBoolean(mapKey + "_isByteArray", false);
                    if (isByteArray) {
                        // Decode from Base64
                        @SuppressWarnings("unchecked")
                        V value = (V) Base64.getDecoder().decode(jsonValue);
                        result.add(value);
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    V value = (V) gson.fromJson(jsonValue, valueType);
                    result.add(value);
                }
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    @Override
    public <V> void putAllInMap(String mapName, List<String> keyList, List<V> valueList) {
        if (mapName == null || keyList == null || valueList == null || 
            keyList.isEmpty() || valueList.isEmpty() || keyList.size() != valueList.size()) {
            return;
        }
        
        writeLock.lock();
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            for (int i = 0; i < keyList.size(); i++) {
                String key = keyList.get(i);
                V value = valueList.get(i);
                
                if (key != null && value != null) {
                    String mapKey = getMapKey(mapName, key);
                    if (value instanceof byte[]) {
                        // Special handling for byte arrays - use Base64 encoding
                        String jsonValue = Base64.getEncoder().encodeToString((byte[]) value);
                        editor.putString(mapKey, jsonValue);
                        editor.putBoolean(mapKey + "_isByteArray", true);
                    } else {
                        // Normal handling for other types
                        String jsonValue = gson.toJson(value);
                        editor.putString(mapKey, jsonValue);
                    }
                }
            }
            
            editor.apply();
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
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            for (Map.Entry<String, V> entry : map.entrySet()) {
                String key = entry.getKey();
                V value = entry.getValue();
                
                if (key != null && value != null) {
                    String mapKey = getMapKey(mapName, key);
                    if (value instanceof byte[]) {
                        // Special handling for byte arrays - use Base64 encoding
                        String jsonValue = Base64.getEncoder().encodeToString((byte[]) value);
                        editor.putString(mapKey, jsonValue);
                        editor.putBoolean(mapKey + "_isByteArray", true);
                    } else {
                        // Normal handling for other types
                        String jsonValue = gson.toJson(value);
                        editor.putString(mapKey, jsonValue);
                    }
                }
            }
            
            editor.apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public int getMapSize(String mapName) {
        if (mapName == null) return 0;
        
        int count = 0;
        readLock.lock();
        try {
            Map<String, ?> allPrefs = sharedPreferences.getAll();
            String mapPrefix = getMapKeyPrefix(mapName);
            
            for (String key : allPrefs.keySet()) {
                if (key.startsWith(mapPrefix)) {
                    count++;
                }
            }
        } finally {
            readLock.unlock();
        }
        return count;
    }

    @Override
    public void registerMapType(String mapName, Class<?> typeClass) {
        if (mapName == null || typeClass == null) return;
        
        writeLock.lock();
        try {
            if (mapTypes == null) {
                mapTypes = new HashMap<>();
            }
            mapTypes.put(mapName, typeClass.getName());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void registerListType(String listName, Class<?> typeClass) {

    }

    @Override
    public Class<?> getMapType(String mapName) {
        if (mapName == null || mapTypes == null) return null;
        
        readLock.lock();
        try {
            String typeName = mapTypes.get(mapName);
            if (typeName == null) return null;
            
            try {
                return Class.forName(typeName);
            } catch (ClassNotFoundException e) {
                TimberLogger.e(TAG,"Error getting type for map %s: %s", mapName, e.getMessage());
                return null;
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <V> void createOrderedList(String listName, Class<V> vClass) {
        if (listName == null || vClass == null) return;
        
        writeLock.lock();
        try {
            listNames.add(listName);
            
            // Initialize the count if it doesn't exist
            if (!sharedPreferences.contains(getListCountKey(listName))) {
                sharedPreferences.edit()
                    .putLong(getListCountKey(listName), 0)
                    .apply();
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public <V> List<V> getAllFromList(String listName) {
        if (listName == null) return new ArrayList<>();
        
        List<V> result = new ArrayList<>();
        readLock.lock();
        try {
            long count = sharedPreferences.getLong(getListCountKey(listName), 0);
            
            for (long i = 0; i < count; i++) {
                String jsonValue = sharedPreferences.getString(getListItemKey(listName, i), null);
                if (jsonValue != null) {
                    @SuppressWarnings("unchecked")
                    V value = (V) gson.fromJson(jsonValue, Object.class);
                    result.add(value);
                }
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    @Override
    public <V> List<V> getRangeFromList(String listName, long startIndex, long endIndex) {
        if (listName == null || startIndex < 0 || endIndex <= startIndex) {
            return new ArrayList<>();
        }
        
        List<V> result = new ArrayList<>();
        readLock.lock();
        try {
            long count = sharedPreferences.getLong(getListCountKey(listName), 0);
            endIndex = Math.min(endIndex, count);
            
            for (long i = startIndex; i < endIndex; i++) {
                String jsonValue = sharedPreferences.getString(getListItemKey(listName, i), null);
                if (jsonValue != null) {
                    @SuppressWarnings("unchecked")
                    V value = (V) gson.fromJson(jsonValue, Object.class);
                    result.add(value);
                }
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    @Override
    public <V> List<V> getRangeFromListReverse(String listName, long startIndex, long endIndex) {
        if (listName == null || startIndex < 0 || endIndex <= startIndex) {
            return new ArrayList<>();
        }
        
        List<V> result = new ArrayList<>();
        readLock.lock();
        try {
            long count = sharedPreferences.getLong(getListCountKey(listName), 0);
            startIndex = Math.min(startIndex, count - 1);
            endIndex = Math.min(endIndex, count);
            
            for (long i = startIndex; i >= endIndex; i--) {
                String jsonValue = sharedPreferences.getString(getListItemKey(listName, i), null);
                if (jsonValue != null) {
                    @SuppressWarnings("unchecked")
                    V value = (V) gson.fromJson(jsonValue, Object.class);
                    result.add(value);
                }
            }
        } finally {
            readLock.unlock();
        }
        return result;
    }

    @Override
    public boolean removeFromList(String listName, long index) {
        if (listName == null || index < 0) {
            return false;
        }
        
        writeLock.lock();
        try {
            long count = sharedPreferences.getLong(getListCountKey(listName), 0);
            if (index >= count) {
                return false;
            }
            
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            // Remove the item at the specified index
            editor.remove(getListItemKey(listName, index));
            
            // Shift all items after the removed index down by one
            for (long i = index + 1; i < count; i++) {
                String jsonValue = sharedPreferences.getString(getListItemKey(listName, i), null);
                if (jsonValue != null) {
                    editor.putString(getListItemKey(listName, i - 1), jsonValue);
                    editor.remove(getListItemKey(listName, i));
                }
            }
            
            // Update the count
            editor.putLong(getListCountKey(listName), count - 1);
            editor.apply();
            
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public int removeFromList(String listName, List<Long> indices) {
        if (listName == null || indices == null || indices.isEmpty()) {
            return 0;
        }
        
        writeLock.lock();
        try {
            long count = sharedPreferences.getLong(getListCountKey(listName), 0);
            
            // Sort indices in descending order to avoid shifting issues
            List<Long> sortedIndices = new ArrayList<>(indices);
            Collections.sort(sortedIndices, Collections.reverseOrder());
            
            int removedCount = 0;
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            for (Long index : sortedIndices) {
                if (index < 0 || index >= count) {
                    continue;
                }
                
                // Remove the item at the specified index
                editor.remove(getListItemKey(listName, index));
                
                // Shift all items after the removed index down by one
                for (long i = index + 1; i < count; i++) {
                    String jsonValue = sharedPreferences.getString(getListItemKey(listName, i), null);
                    if (jsonValue != null) {
                        editor.putString(getListItemKey(listName, i - 1), jsonValue);
                        editor.remove(getListItemKey(listName, i));
                    }
                }
                
                count--;
                removedCount++;
            }
            
            // Update the count
            editor.putLong(getListCountKey(listName), count);
            editor.apply();
            
            return removedCount;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public long getListSize(String listName) {
        if (listName == null) return 0;
        
        readLock.lock();
        try {
            return sharedPreferences.getLong(getListCountKey(listName), 0);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void clearList(String listName) {
        if (listName == null) return;
        
        writeLock.lock();
        try {
            long count = sharedPreferences.getLong(getListCountKey(listName), 0);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            
            // Remove all items
            for (long i = 0; i < count; i++) {
                editor.remove(getListItemKey(listName, i));
            }
            
            // Reset the count
            editor.putLong(getListCountKey(listName), 0);
            editor.apply();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void removeFromMap(String mapName, List<String> keys) {
        if (mapName == null || keys == null || keys.isEmpty()) return;
        
        writeLock.lock();
        try {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            for (String key : keys) {
                editor.remove(getMapKey(mapName, key));
            }
            editor.apply();
        } finally {
            writeLock.unlock();
        }
    }
} 