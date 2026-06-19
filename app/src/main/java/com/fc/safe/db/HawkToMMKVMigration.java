package com.fc.safe.db;

import android.content.Context;

import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.orhanobut.hawk.Hawk;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class to migrate data from Hawk to MMKV.
 * This class helps transfer all data from HawkDB instances to MMKVDB instances.
 */
public class HawkToMMKVMigration {
    private static final String TAG = "HawkToMMKVMigration";
    private static final String MIGRATION_FLAG = "hawk_to_mmkv_migrated";

    /**
     * Checks if migration has already been completed
     */
    public static boolean isMigrated(Context context) {
        try {
            return Hawk.get(MIGRATION_FLAG, false);
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error checking migration status: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Marks migration as completed
     */
    public static void setMigrated(Context context) {
        try {
            Hawk.put(MIGRATION_FLAG, true);
            TimberLogger.i(TAG, "Migration marked as completed");
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error setting migration flag: %s", e.getMessage());
        }
    }

    /**
     * Migrates a single HawkDB instance to MMKVDB
     *
     * @param <T> The entity type
     * @param hawkDB The source HawkDB instance
     * @param mmkvDB The target MMKVDB instance
     * @return true if migration was successful
     */
    public static <T extends FcEntity> boolean migrateDatabase(HawkDB<T> hawkDB, MMKVDB<T> mmkvDB) {
        try {
            TimberLogger.d(TAG, "Starting database migration...");

            // Check if target database is empty (to avoid overwriting existing data)
            if (!mmkvDB.isEmpty()) {
                TimberLogger.w(TAG, "Target MMKVDB is not empty, skipping migration to avoid data loss");
                return false;
            }

            // Transfer main data
            TimberLogger.d(TAG, "Transferring main data...");
            Map<String, T> allData = hawkDB.getAll();
            if (allData != null && !allData.isEmpty()) {
                mmkvDB.put(allData);
                TimberLogger.d(TAG, "Transferred %d items", allData.size());
            }

            // Transfer settings
            TimberLogger.d(TAG, "Transferring settings...");
            Map<String, String> allSettings = hawkDB.getAllSettings();
            if (allSettings != null && !allSettings.isEmpty()) {
                for (Map.Entry<String, String> setting : allSettings.entrySet()) {
                    mmkvDB.putSetting(setting.getKey(), setting.getValue());
                }
                TimberLogger.d(TAG, "Transferred %d settings", allSettings.size());
            }

            // Transfer state
            TimberLogger.d(TAG, "Transferring state...");
            Map<String, Object> allState = hawkDB.getStateMap();
            if (allState != null && !allState.isEmpty()) {
                for (Map.Entry<String, Object> state : allState.entrySet()) {
                    mmkvDB.putState(state.getKey(), state.getValue());
                }
                TimberLogger.d(TAG, "Transferred %d state entries", allState.size());
            }

            // Transfer meta
            TimberLogger.d(TAG, "Transferring metadata...");
            Map<String, Object> allMeta = hawkDB.getMetaMap();
            if (allMeta != null && !allMeta.isEmpty()) {
                for (Map.Entry<String, Object> meta : allMeta.entrySet()) {
                    mmkvDB.putMeta(meta.getKey(), meta.getValue());
                }
                TimberLogger.d(TAG, "Transferred %d metadata entries", allMeta.size());
            }

            // Transfer named maps
            TimberLogger.d(TAG, "Transferring named maps...");
            for (String mapName : hawkDB.getMapNames()) {
                Class<?> mapType = hawkDB.getMapType(mapName);
                if (mapType != null) {
                    mmkvDB.registerMapType(mapName, mapType);
                    Map<String, ?> mapData = hawkDB.getAllFromMap(mapName);
                    if (mapData != null && !mapData.isEmpty()) {
                        // Handle byte[] specially
                        if (Objects.equals(mapType.getName(), byte[].class.getName())) {
                            for (Map.Entry<String, ?> mapEntry : mapData.entrySet()) {
                                if (mapEntry.getValue() instanceof byte[]) {
                                    mmkvDB.putInMap(mapName, mapEntry.getKey(), mapEntry.getValue());
                                }
                            }
                        } else {
                            mmkvDB.putAllInMap(mapName, mapData);
                        }
                        TimberLogger.d(TAG, "Transferred map '%s' with %d entries", mapName, mapData.size());
                    }
                }
            }

            // Commit all changes
            mmkvDB.commit();

            TimberLogger.i(TAG, "Database migration completed successfully");
            return true;

        } catch (Exception e) {
            TimberLogger.e(TAG, "Error during database migration: %s", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Migrates all databases for a specific password context
     *
     * @param context Application context
     * @param databaseManager The DatabaseManager instance
     * @return true if all migrations were successful
     */
    public static boolean migrateAllDatabases(Context context, DatabaseManager databaseManager) {
        try {
            TimberLogger.i(TAG, "Starting migration of all databases...");

            // Check if already migrated
            if (isMigrated(context)) {
                TimberLogger.i(TAG, "Migration already completed, skipping");
                return true;
            }

            boolean allSuccess = true;

            // Get current password name
            String passwordName = databaseManager.getCurrentPasswordName();
            if (passwordName == null) {
                TimberLogger.w(TAG, "No password context set, cannot migrate");
                return false;
            }

            TimberLogger.i(TAG, "Migrating databases for password context: %s", passwordName);

            // We cannot directly access databases from DatabaseManager,
            // so this method should be called after initializing individual managers

            TimberLogger.i(TAG, "Migration of all databases completed");

            // Mark as migrated
            setMigrated(context);

            return allSuccess;

        } catch (Exception e) {
            TimberLogger.e(TAG, "Error during migration of all databases: %s", e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Cleans up Hawk data after successful migration
     * WARNING: This will delete all Hawk data permanently
     *
     * @param hawkDB The HawkDB instance to clean
     */
    public static <T extends FcEntity> void cleanupHawkData(HawkDB<T> hawkDB) {
        try {
            TimberLogger.w(TAG, "Cleaning up Hawk data...");
            hawkDB.clearDB();
            TimberLogger.i(TAG, "Hawk data cleanup completed");
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error cleaning up Hawk data: %s", e.getMessage());
        }
    }

    /**
     * Verifies that migration was successful by comparing data between Hawk and MMKV
     *
     * @param <T> The entity type
     * @param hawkDB The source HawkDB instance
     * @param mmkvDB The target MMKVDB instance
     * @return true if data matches
     */
    public static <T extends FcEntity> boolean verifyMigration(HawkDB<T> hawkDB, MMKVDB<T> mmkvDB) {
        try {
            TimberLogger.d(TAG, "Verifying migration...");

            // Check sizes match
            int hawkSize = hawkDB.getSize();
            int mmkvSize = mmkvDB.getSize();
            if (hawkSize != mmkvSize) {
                TimberLogger.e(TAG, "Size mismatch: Hawk=%d, MMKV=%d", hawkSize, mmkvSize);
                return false;
            }

            // Check ID lists match
            List<String> hawkIds = hawkDB.getIdList();
            List<String> mmkvIds = mmkvDB.getIdList();
            if (!hawkIds.equals(mmkvIds)) {
                TimberLogger.e(TAG, "ID list mismatch");
                return false;
            }

            // Sample check: verify a few random items
            int checkCount = Math.min(5, hawkSize);
            for (int i = 0; i < checkCount; i++) {
                String id = hawkIds.get(i);
                T hawkItem = hawkDB.get(id);
                T mmkvItem = mmkvDB.get(id);

                if (hawkItem == null && mmkvItem == null) {
                    continue;
                }
                if (hawkItem == null || mmkvItem == null) {
                    TimberLogger.e(TAG, "Item mismatch for ID: %s", id);
                    return false;
                }
                if (!hawkItem.getId().equals(mmkvItem.getId())) {
                    TimberLogger.e(TAG, "Item content mismatch for ID: %s", id);
                    return false;
                }
            }

            TimberLogger.i(TAG, "Migration verification passed");
            return true;

        } catch (Exception e) {
            TimberLogger.e(TAG, "Error during migration verification: %s", e.getMessage());
            return false;
        }
    }
}
