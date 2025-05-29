package com.fc.fc_ajdk.db;

import java.util.*;

/**
 * Interface for local database operations that provides storage and retrieval of entities.
 * This interface defines methods for:
 * 
 * 1. Main entity map operations (CRUD for String key to FcEntity value mappings)
 * 2. System maps for metadata, settings, and state
 * 3. User-defined maps with customizable value types
 * 4. Various sort order and pagination capabilities
 * 
 * @param <T> The type of entity to store, must extend FcEntity
 */
public interface LocalDB<T> {
    String DOT_DB = ".db";
    String MAP_NAMES_META_KEY = "map_names";
    String LOCAL_REMOVED_MAP = "local_removed";
    String ON_CHAIN_DELETED_MAP = "on_chain_deleted";
    String MAP_TYPES_META_KEY = "map_types";
    String LIST_TYPES_META_KEY = "list_types";
    String SORT_TYPE_META_KEY = "sort_type";
    String LIST_COUNT_PREFIX = "count:";
    String LIST_ITEM_PREFIX = "item:";

    /**
     * Defines the sort order used for entities in the database.
     */
    enum SortType {
        /**
         * No sorting, entities will be stored and retrieved in implementation-defined order.
         */
        NO_SORT,
        
        /**
         * Entities are sorted by key in natural order.
         */
        KEY_ORDER,
        
        /**
         * Most recently accessed entities appear at the end.
         */
        ACCESS_ORDER,
        
        /**
         * Most recently updated entities appear at the end.
         */
        UPDATE_ORDER,
        
        /**
         * Entities are ordered by creation time.
         */
        BIRTH_ORDER
    }

    /**
     * Initializes the database with the specified parameters.
     *
     * @param fid Optional first identifier for the database
     * @param sid Optional second identifier for the database
     * @param dbPath Path where the database should be created
     * @param dbName Name of the database
     */
    void initialize(String fid, String sid, String dbPath, String dbName);

    /**
     * Gets the sort type used by this database instance.
     *
     * @return The sort type
     */
    SortType getSortType();

    /**
     * Gets the field name used for sorting.
     *
     * @return The field name used for sorting, or null if not applicable
     */
    String getSortField();

    /**
     * Stores an entity in the database with the specified key.
     *
     * @param key The key to store the entity under
     * @param value The entity to store
     */
    void put(String key, T value);

    /**
     * Retrieves an entity from the database by key.
     *
     * @param key The key of the entity to retrieve
     * @return The entity, or null if not found
     */
    T get(String key);

    /**
     * Retrieves multiple entities from the database by their keys.
     *
     * @param keys List of keys to retrieve
     * @return List of entities that were found (may be smaller than the input list)
     */
    List<T> get(List<String> keys);

    /**
     * Removes an entity from the database by key.
     *
     * @param key The key of the entity to remove
     */
    void remove(String key);

    /**
     * Removes multiple entities from the database in a batch operation.
     * This method is more efficient than calling remove() multiple times.
     *
     * @param list List of entities to remove
     */
    void remove(List<T> list);

    void saveIdIndexMap();

    void saveIndexIdMap();

    void saveStateMap();

    /**
     * Commits any pending changes to the database.
     * Implementation may be a no-op if changes are committed automatically.
     */
    void commit();

    /**
     * Closes the database and releases any resources.
     */
    void close();

    /**
     * Checks if the database is closed.
     *
     * @return true if the database is closed, false otherwise
     */
    boolean isClosed();

    /**
     * Gets the temporary index value set during the last pagination operation.
     *
     * @return The temporary index value
     */
    long getTempIndex();

    /**
     * Gets the temporary ID value set during the last pagination operation.
     *
     * @return The temporary ID value
     */
    String getTempId();

    /**
     * Gets a map of all entities in the database.
     *
     * @return Map of key to entity
     */
    Map<String, T> getItemMap();

    /**
     * Gets the index to ID mapping used for sorted access.
     *
     * @return Navigable map of index to ID
     */
    NavigableMap<Long, String> getIndexIdMap();

    /**
     * Gets the ID to index mapping used for sorted access.
     *
     * @return Navigable map of ID to index
     */
    NavigableMap<String, Long> getIdIndexMap();

    /**
     * Gets a map of all metadata in the database.
     *
     * @return Map of metadata key to value
     */
    Map<String, Object> getMetaMap();
    
    /**
     * Gets a map of all settings in the database.
     *
     * @return Map of settings key to value
     */
    Map<String, Object> getSettingsMap();
    
    /**
     * Gets a map of all state in the database.
     *
     * @return Map of state key to value
     */
    Map<String, Object> getStateMap();

    /**
     * Stores a settings value in the database.
     *
     * @param key The settings key
     * @param value The value to store
     */
    void putSetting(String key, Object value);
    
    /**
     * Removes a settings value from the database.
     *
     * @param key The settings key to remove
     */
    void removeSetting(String key);
    
    /**
     * Removes all settings values from the database.
     */
    void removeAllSettings();
    
    /**
     * Gets all settings as a map of string key to string value.
     *
     * @return Map of all settings
     */
    Map<String, String> getAllSettings();
    
    /**
     * Gets a settings value from the database.
     *
     * @param key The settings key
     * @return The settings value, or null if not found
     */
    Object getSetting(String key);
    
    /**
     * Stores a state value in the database.
     *
     * @param key The state key
     * @param value The value to store
     */
    void putState(String key, Object value);
    
    /**
     * Removes a state value from the database.
     *
     * @param key The state key to remove
     */
    void removeState(String key);
    
    /**
     * Removes all state values from the database.
     */
    void clearAllState();
    
    /**
     * Gets all state values as a map of string key to string value.
     *
     * @return Map of all state values
     */
    Map<String, String> getAllState();
    
    /**
     * Gets a state value from the database.
     *
     * @param key The state key
     * @return The state value, or null if not found
     */
    Object getState(String key);

    /**
     * Gets the index for an entity ID.
     *
     * @param id The entity ID
     * @return The index, or null if not found
     */
    Long getIndexById(String id);

    /**
     * Gets the entity ID for an index.
     *
     * @param index The index
     * @return The entity ID, or null if not found
     */
    String getIdByIndex(long index);
    T getByIndex(long index);

    /**
     * Gets the number of entities in the database.
     *
     * @return The number of entities
     */
    int getSize();

    /**
     * Checks if the database is empty.
     *
     * @return true if the database is empty, false otherwise
     */
    boolean isEmpty();

    /**
     * Gets a metadata value from the database.
     *
     * @param key The metadata key
     * @return The metadata value, or null if not found
     */
    Object getMeta(String key);
    
    /**
     * Gets a map of entities with pagination and sorting support.
     *
     * @param size Maximum number of entities to return, or null for all
     * @param fromId Starting entity ID for pagination, or null
     * @param fromIndex Starting index for pagination, or null
     * @param isFromInclude Whether to include the starting entity/index
     * @param toId Ending entity ID for pagination, or null
     * @param toIndex Ending index for pagination, or null
     * @param isToInclude Whether to include the ending entity/index
     * @param isFromEnd Whether to paginate from the end (reverse order)
     * @return LinkedHashMap of entities in the requested order
     */
    LinkedHashMap<String, T> getMap(Integer size, String fromId, Long fromIndex,
                                    boolean isFromInclude, String toId, Long toIndex, 
                                    boolean isToInclude, boolean isFromEnd);

    /**
     * Gets a list of entities with pagination and sorting support.
     *
     * @param size Maximum number of entities to return, or null for all
     * @param fromId Starting entity ID for pagination, or null
     * @param fromIndex Starting index for pagination, or null
     * @param isFromInclude Whether to include the starting entity/index
     * @param toId Ending entity ID for pagination, or null
     * @param toIndex Ending index for pagination, or null
     * @param isToInclude Whether to include the ending entity/index
     * @param isFromEnd Whether to paginate from the end (reverse order)
     * @return List of entities in the requested order
     */
    List<T> getList(Integer size, String fromId, Long fromIndex,
                    boolean isFromInclude, String toId, Long toIndex, 
                    boolean isToInclude, boolean isFromEnd);

    /**
     * Stores multiple entities in the database.
     *
     * @param items Map of key to entity
     */
    void putAll(Map<String, T> items);

    /**
     * Gets all entities in the database.
     *
     * @return Map of key to entity
     */
    Map<String, T> getAll();

    /**
     * Searches for entities containing the specified string.
     *
     * @param part The string to search for
     * @return List of matching entities
     */
    List<T> searchString(String part);

    /**
     * Stores a metadata value in the database.
     *
     * @param key The metadata key
     * @param value The value to store
     */
    void putMeta(String key, Object value);

    /**
     * Removes a metadata value from the database.
     *
     * @param key The metadata key to remove
     */
    void removeMeta(String key);
    
    /**
     * Removes all entities from the database but keeps metadata.
     */
    void clear();

    /**
     * Removes multiple entities from the database by their IDs.
     *
     * @param ids List of entity IDs to remove
     */
    void removeList(List<String> ids);

    /**
     * Removes a value from a named map.
     *
     * @param mapName The name of the map
     * @param key The key to remove
     */
    void removeFromMap(String mapName, String key);
    
    /**
     * Removes multiple values from a named map.
     *
     * @param mapName The name of the map
     * @param keys The keys to remove
     */
    void removeFromMap(String mapName, List<String> keys);

    /**
     * Deletes all database files and reinitializes the database.
     */
    void clearDB();

    /**
     * Stores multiple entities in the database using a specified ID field.
     *
     * @param items List of entities to store
     * @param idField Name of the field to use as the key
     */
    void putAll(List<T> items, String idField);

    /**
     * Gets the names of all user-defined maps.
     *
     * @return Set of map names
     */
    Set<String> getMapNames();
    
    /**
     * Puts a value into a named map with the specified serializer.
     *
     * @param <V>     The type of the value
     * @param mapName The name of the map to store the value in
     * @param key     The key to store the value under
     * @param value   The value to store
     */
    <V> void putInMap(String mapName, String key, V value);

    /**
     * Gets a value from a named map using the specified serializer.
     *
     * @param <V>     The type of the value
     * @param mapName The name of the map to retrieve from
     * @param key     The key to retrieve
     * @return The value associated with the key, or null if not found
     */
    <V> V getFromMap(String mapName, String key);

    /**
     * Gets all values from a named map using the specified serializer.
     *
     * @param <V>     The type of the values
     * @param mapName The name of the map to retrieve from
     * @return A Map containing all key-value pairs in the named map
     */
    <V> Map<String, V> getAllFromMap(String mapName);

    /**
     * Removes all values from a named map.
     *
     * @param mapName The name of the map to clear
     */
    void clearMap(String mapName);
    
    /**
     * Gets multiple values from a named map.
     *
     * @param <V>     The type of the values
     * @param mapName The name of the map
     * @param keyList List of keys to retrieve
     * @return List of values that were found
     */
    <V> List<V> getFromMap(String mapName, List<String> keyList);
    
    /**
     * Stores multiple values in a named map.
     *
     * @param <V>       The type of the values
     * @param mapName   The name of the map
     * @param keyList   List of keys
     * @param valueList List of values (must be same size as keyList)
     */
    <V> void putAllInMap(String mapName, List<String> keyList, List<V> valueList);

    /**
     * Stores multiple values in a named map.
     *
     * @param <V>     The type of the values
     * @param mapName The name of the map
     * @param map     Map containing the key-value pairs to store
     */
    <V> void putAllInMap(String mapName, Map<String, V> map);

    /**
     * Gets the number of entries in a named map.
     *
     * @param mapName The name of the map
     * @return The number of entries in the map, or 0 if the map doesn't exist
     */
    int getMapSize(String mapName);

    /**
     * Enum defining the type of database implementation.
     */
    enum DbType {
        /**
         * LevelDB implementation.
         */
        LEVEL_DB,
        
        /**
         * EasyDB implementation.
         */
        EASY_DB,

        /**
         * DataStore implementation.
         */
        SHARED_PREFS_DB
        // Add other DB types as needed
    }

    /**
     * Registers the type of a named map.
     *
     * @param mapName The name of the map
     * @param typeClass The class representing the type of values in the map
     */
    void registerMapType(String mapName, Class<?> typeClass);

    void registerListType(String listName, Class<?> typeClass);
    
    /**
     * Gets the registered type of a named map.
     *
     * @param mapName The name of the map
     * @return The class representing the type of values in the map, or null if not registered
     */
    Class<?> getMapType(String mapName);

    /**
     * Creates a named map with the specified value type.
     *
     * @param mapName The name of the map to create
     * @param vClass The class of values to be stored in the map
     * @param <V> The type of values in the map
     */
    <V> void createMap(String mapName, Class<V> vClass);

    /**
     * Creates an ordered list with the specified name and value type.
     * The list maintains insertion order and handles element removal properly.
     *
     * @param <V> The type of values in the list
     * @param listName The name of the list
     * @param vClass The class of values to be stored in the list
     */
    <V> void createOrderedList(String listName, Class<V> vClass);

    /**
     * Adds an element to the end of an ordered list.
     *
     * @param <V> The type of the value
     * @param listName The name of the list
     * @param value The value to add
     * @return The index of the added element
     */
    <V> long addToList(String listName, V value);

    /**
     * Adds multiple elements to the end of an ordered list.
     *
     * @param <V> The type of the values
     * @param listName The name of the list
     * @param values The values to add
     * @return The starting index of the added elements
     */
    <V> long addAllToList(String listName, List<V> values);

    /**
     * Gets an element from an ordered list by its index.
     *
     * @param <V>      The type of the value
     * @param listName The name of the list
     * @param index    The index of the element
     * @param vClass
     * @return The element, or null if not found
     */
    <V> V getFromList(String listName, long index, Class<V> vClass);

    /**
     * Gets all elements from an ordered list.
     *
     * @param <V> The type of the values
     * @param listName The name of the list
     * @return A list of all elements in the list
     */
    <V> List<V> getAllFromList(String listName);

    /**
     * Gets a range of elements from an ordered list.
     *
     * @param <V> The type of the values
     * @param listName The name of the list
     * @param startIndex The starting index (inclusive)
     * @param endIndex The ending index (exclusive)
     * @return A list of elements in the specified range
     */
    <V> List<V> getRangeFromList(String listName, long startIndex, long endIndex);

    /**
     * Gets a range of elements from an ordered list in reverse order (from end to beginning).
     *
     * @param <V> The type of the values
     * @param listName The name of the list
     * @param startIndex The starting index (inclusive) from the end of the list
     * @param endIndex The ending index (exclusive) from the end of the list
     * @return A list of elements in the specified range in reverse order
     */
    <V> List<V> getRangeFromListReverse(String listName, long startIndex, long endIndex);

    /**
     * Removes an element from an ordered list by its index.
     * The indices of subsequent elements are adjusted to maintain continuity.
     *
     * @param listName The name of the list
     * @param index The index of the element to remove
     * @return true if the element was removed, false otherwise
     */
    boolean removeFromList(String listName, long index);

    /**
     * Removes multiple elements from an ordered list by their indices.
     * The indices of subsequent elements are adjusted to maintain continuity.
     *
     * @param listName The name of the list
     * @param indices The indices of the elements to remove
     * @return The number of elements removed
     */
    int removeFromList(String listName, List<Long> indices);

    /**
     * Gets the size of an ordered list.
     *
     * @param listName The name of the list
     * @return The number of elements in the list
     */
    long getListSize(String listName);

    /**
     * Clears all elements from an ordered list.
     *
     * @param listName The name of the list
     */
    void clearList(String listName);

}
