package com.fc.safe.db;

import android.content.Context;

import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.db.HawkDB;
import com.fc.fc_ajdk.utils.TimberLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class DatabaseManager {
    private static DatabaseManager instance;
    private final Context context;
    private String currentPasswordName;
    private final Map<String, LocalDB<? extends FcEntity>> databases;
    private final Map<String, Class<? extends FcEntity>> databaseClasses = new HashMap<>();

    private DatabaseManager(Context context) {
        this.context = context.getApplicationContext();
        this.databases = new HashMap<>();
    }

    public static synchronized DatabaseManager getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseManager(context.getApplicationContext());
        }
        return instance;
    }

    public void setCurrentPasswordName(String passwordName) {
        // Close current databases before switching
        if (currentPasswordName != null && !currentPasswordName.equals(passwordName)) {
            closeCurrentDatabases();
        }
        currentPasswordName = passwordName;
    }

    private void closeCurrentDatabases() {
        // Close and clear all current databases
        for (LocalDB<? extends FcEntity> db : databases.values()) {
            if (db != null) {
                try {
                    db.close();
                } catch (Exception e) {
                    TimberLogger.e("DatabaseManager", "Error closing database: " + e.getMessage());
                }
            }
        }
        databases.clear();
        databaseClasses.clear();
    }

    public void changePassword(String passwordName) {
        if (!passwordName.equals(this.currentPasswordName)) {
            try {
                // Rename database keys from old password to new password
                Map<String, LocalDB<? extends FcEntity>> newDatabases = new HashMap<>();
                Map<String, Class<? extends FcEntity>> newDatabaseClasses = new HashMap<>();

                for (Map.Entry<String, LocalDB<? extends FcEntity>> entry : databases.entrySet()) {
                    String oldDbKey = entry.getKey();
                    if (oldDbKey.startsWith(this.currentPasswordName)) {
                        // Create new key with new password name
                        String dbName = oldDbKey.substring(this.currentPasswordName.length() + 1); // +1 for the underscore
                        String newDbKey = makeDatabaseKey(dbName, passwordName);

                        // Get the database and save its data with new namespace
                        LocalDB<? extends FcEntity> oldDb = entry.getValue();
                        if (oldDb instanceof HawkDB) {
                            HawkDB<?> oldHawkDB = (HawkDB<?>) oldDb;
                            
                            // Create new database instance
                            HawkDB<?> newHawkDB = new HawkDB<>(oldHawkDB.getSortType(), oldHawkDB.getSortField());
                            newHawkDB.initialize(null, null, null, newDbKey);
                            
                            // Process main data first
                            Map<String, ?> allData = oldHawkDB.getAll();
                            if (allData != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, FcEntity> typedData = (Map<String, FcEntity>) allData;
                                ((HawkDB<FcEntity>) newHawkDB).putAll(typedData);
                                allData = null; // Release the data
                            }

                            // Process ID maps
                            Map<String, Long> idIndexMap = oldHawkDB.getIdIndexMap();
                            if(idIndexMap != null) {
                                newHawkDB.saveIdIndexMap(idIndexMap);
                                idIndexMap = null; // Release the map
                            }

                            Map<Long,String> indexIdMap = oldHawkDB.getIndexIdMap();
                            if(indexIdMap != null) {
                                newHawkDB.saveIndexIdMap(indexIdMap);
                                indexIdMap = null; // Release the map
                            }
                            
                            // Process settings
                            Map<String, String> allSettings = oldHawkDB.getAllSettings();
                            if(allSettings != null) {
                                for (Map.Entry<String, String> setting : allSettings.entrySet()) {
                                    newHawkDB.putSetting(setting.getKey(), setting.getValue());
                                }
                                allSettings = null; // Release the settings
                            }

                            // Process state
                            Map<String, Object> allState = oldHawkDB.getStateMap();
                            if(allState != null) {
                                for (Map.Entry<String, Object> state : allState.entrySet()) {
                                    newHawkDB.putState(state.getKey(), state.getValue());
                                }
                                allState = null; // Release the state
                            }

                            // Process meta
                            Map<String, Object> allMeta = oldHawkDB.getMetaMap();
                            if(allMeta != null) {
                                for (Map.Entry<String, Object> meta : allMeta.entrySet()) {
                                    newHawkDB.putMeta(meta.getKey(), meta.getValue());
                                }
                                allMeta = null; // Release the meta
                            }
                            
                            // Process maps one by one
                            for (String mapName : oldHawkDB.getMapNames()) {
                                Class<?> mapType = oldHawkDB.getMapType(mapName);
                                if (mapType != null) {
                                    newHawkDB.registerMapType(mapName, mapType);
                                    
                                    Map<String, ?> mapData = oldHawkDB.getAllFromMap(mapName);
                                    if (mapData != null && !mapData.isEmpty()) {
                                        String mapTypeName = mapType.getName();
                                        
                                        // Handle byte[] type specially
                                        if (Objects.equals(mapTypeName, byte[].class.getName())) {
                                            for (Map.Entry<String, ?> mapEntry : mapData.entrySet()) {
                                                if (mapEntry.getValue() instanceof byte[]) {
                                                    newHawkDB.putInMap(mapName, mapEntry.getKey(), mapEntry.getValue());
                                                }
                                            }
                                        } else {
                                            newHawkDB.putAllInMap(mapName, mapData);
                                        }
                                    }
                                    mapData = null; // Release the map data
                                }
                            }

                            // Only clear old database after successful data transfer
                            oldHawkDB.clearDB();
                            
                            // Use the new database instance
                            newDatabases.put(newDbKey, newHawkDB);
                            newDatabaseClasses.put(newDbKey, databaseClasses.get(oldDbKey));
                        }
                    }
                }

                // Update the maps with renamed databases
                databases.clear();
                databases.putAll(newDatabases);
                databaseClasses.clear();
                databaseClasses.putAll(newDatabaseClasses);

                // Set the new password name only after successful migration
                this.currentPasswordName = passwordName;
            } catch (Exception e) {
                // If any error occurs during password change, revert the changes
                TimberLogger.e("DatabaseManager", "Error changing password: " + e.getMessage());
                throw new RuntimeException("Failed to change password: " + e.getMessage());
            }
        }
    }

    public  <T extends FcEntity> LocalDB<T> createEntityDatabase(String dbName, String passwordName, Class<T> entityClass, LocalDB.SortType sortType, String sortField) {
        // No need to initialize Hawk here, it's already initialized at the application level
        LocalDB<T> db = new HawkDB<>(sortType, sortField);
        String dbKey = makeDatabaseKey(dbName, passwordName);
        db.initialize(null, null, null, dbKey);
        databases.put(dbKey, db);
        databaseClasses.put(dbKey, entityClass);
        return db;
    }

    /**
     * Gets or creates an encrypted database for the specified parameters.
     *
     * @param entityClass The entity class for the database
     * @param sortType
     * @param sortField
     * @return The encrypted database
     */
    public <T extends FcEntity> LocalDB<T> getEntityDatabase(Class<T> entityClass, LocalDB.SortType sortType, String sortField) {
        String dbKey = makeDatabaseKey(entityClass.getSimpleName(), currentPasswordName);
        
        // Check if database already exists for this password
        if (databases.containsKey(dbKey)) {
            return (LocalDB<T>) databases.get(dbKey);
        }
        
        // Create a new encrypted database for this password
        return createEntityDatabase(entityClass.getSimpleName(), currentPasswordName, entityClass, sortType, sortField);
    }

    public Class<? extends FcEntity> getDatabaseClass(String dbName) {
        return databaseClasses.get(makeDatabaseKey(dbName, currentPasswordName));
    }

    public String makeDatabaseKey(String dbName, String passwordName) {
        return passwordName + "_" + dbName;
    }

    public void clearDatabase(String dbName) {
        String dbKey = makeDatabaseKey(dbName, currentPasswordName);
        LocalDB<?> db = databases.get(dbKey);
        if (db != null) {
            db.clearDB();
            databases.remove(dbKey);
        }
    }

    public void clearAllDatabases() {
        // Clear all password-specific databases
        for (String dbKey : databases.keySet()) {
            if (dbKey.startsWith(currentPasswordName)) {
                LocalDB<?> db = databases.get(dbKey);
                if (db != null) {
                    db.clearDB();
                }
            }
        }
        databases.clear();
    }

    public String getCurrentPasswordName() {
        return currentPasswordName;
    }

    /**
     * Shuts down the DatabaseManager and properly closes all databases.
     * This method should be called when the application is terminating.
     * It does not delete any data, it just releases resources.
     */
    public static void shutdown() {
        if (instance != null) {
            // Close all databases without deleting data
            for (LocalDB<?> db : instance.databases.values()) {
                db.close();
            }
            // Reset the instance
            instance = null;
        }
    }
} 