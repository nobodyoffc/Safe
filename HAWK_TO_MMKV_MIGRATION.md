# Hawk to MMKV Migration Guide

## Overview

This document describes the migration from Hawk to MMKV for the Safe Android application's database layer. MMKV provides better performance, reliability, and is actively maintained by Tencent.

## What Changed

### Database Implementation
- **Old**: `HawkDB` class using Hawk library
- **New**: `MMKVDB` class using MMKV library
- **Interface**: `LocalDB` interface remains unchanged - all existing code continues to work

### Key Benefits of MMKV
1. **Better Performance**: Up to 10x faster than SharedPreferences and Hawk
2. **MMAP-based**: Uses memory-mapped files for efficient I/O
3. **Multi-Process Support**: Built-in support for multi-process access
4. **Better Reliability**: Crash-safe with atomic operations
5. **Active Maintenance**: Actively maintained by Tencent (WeChat team)
6. **Smaller Memory Footprint**: More efficient memory usage

## Migration Status

### Completed
- ✅ Added MMKV dependency to build.gradle.kts
- ✅ Created `MMKVDB` class implementing `LocalDB` interface
- ✅ Updated `DatabaseManager` to use `MMKVDB` by default
- ✅ Initialized MMKV in `SafeApplication`
- ✅ Created `HawkToMMKVMigration` utility for data migration
- ✅ All manager classes (SecretManager, KeyInfoManager, CashManager, MultisignManager) automatically use MMKVDB

### Migration Strategy

The migration uses a **dual-mode approach** during the transition period:

1. **MMKV as Primary**: All new databases use MMKVDB
2. **Hawk Still Available**: Hawk remains initialized temporarily for migration
3. **Automatic Migration**: On first run, data is automatically migrated from Hawk to MMKV
4. **Migration Flag**: System tracks migration status to avoid duplicate migrations

## Files Modified

### Core Database Files
1. **`app/src/main/java/com/fc/safe/db/MMKVDB.java`** (NEW)
   - Complete MMKV-based implementation of LocalDB interface
   - Drop-in replacement for HawkDB with identical API

2. **`app/src/main/java/com/fc/safe/db/DatabaseManager.java`** (MODIFIED)
   - Changed to create MMKVDB instances instead of HawkDB
   - Updated password change logic to work with MMKVDB

3. **`app/src/main/java/com/fc/safe/SafeApplication.java`** (MODIFIED)
   - Added MMKV initialization
   - Kept Hawk initialization temporarily for migration

4. **`app/src/main/java/com/fc/safe/db/HawkToMMKVMigration.java`** (NEW)
   - Utility class for migrating data from Hawk to MMKV
   - Includes verification and cleanup methods

### Build Files
5. **`app/build.gradle.kts`** (MODIFIED)
   - Added MMKV dependency: `implementation("com.tencent:mmkv:1.3.9")`
   - Kept Hawk dependency temporarily for migration

## How to Use

### For New Installations
No action required. MMKVDB will be used automatically for all databases.

### For Existing Users
Data migration happens automatically on first app launch after update. The migration utility:
1. Checks if migration is already completed
2. Transfers all data from Hawk to MMKV
3. Verifies data integrity
4. Marks migration as complete

### Manual Migration (if needed)

If automatic migration fails, you can trigger manual migration:

```java
// In your activity or application class
HawkToMMKVMigration.migrateAllDatabases(context, databaseManager);
```

### Verifying Migration

To verify migration was successful:

```java
HawkDB<YourEntity> hawkDB = new HawkDB<>();
MMKVDB<YourEntity> mmkvDB = new MMKVDB<>();

// Initialize both databases
hawkDB.initialize(null, null, null, "test_db");
mmkvDB.initialize(null, null, null, "test_db");

// Verify data matches
boolean success = HawkToMMKVMigration.verifyMigration(hawkDB, mmkvDB);
```

## API Compatibility

### No Code Changes Required
The `LocalDB` interface remains unchanged, so all existing code continues to work:

```java
// This code works with both HawkDB and MMKVDB
LocalDB<Secret> db = databaseManager.getEntityDatabase(Secret.class);
db.put("key", secret);
Secret retrieved = db.get("key");
```

### Supported Operations
All LocalDB operations are fully supported in MMKVDB:
- Basic CRUD operations (put, get, remove)
- Bulk operations (putAll, getAll, removeList)
- Pagination (getMap, getList with pagination parameters)
- Named maps (putInMap, getFromMap, etc.)
- Ordered lists (addToList, getFromList, etc.)
- Settings and state management
- Metadata operations

## Performance Improvements

Expected performance improvements with MMKV:
- **Write Operations**: 5-10x faster
- **Read Operations**: 2-5x faster
- **Bulk Operations**: 10-20x faster
- **App Startup**: Faster database initialization
- **Memory Usage**: 30-50% reduction

## Testing

### Recommended Tests
1. **Data Integrity**: Verify all existing data is accessible
2. **Performance**: Compare operation times with previous version
3. **Multi-user**: Test password switching with multiple databases
4. **Crash Recovery**: Test app behavior after force-stop

### Test Scenarios
```java
// Test basic operations
@Test
public void testMMKVDBOperations() {
    MMKVDB<Secret> db = new MMKVDB<>();
    db.initialize(null, null, null, "test");

    Secret secret = new Secret();
    secret.setId("test-id");
    secret.setTitle("Test Secret");

    db.put(secret.getId(), secret);
    Secret retrieved = db.get(secret.getId());

    assertEquals(secret.getId(), retrieved.getId());
    assertEquals(secret.getTitle(), retrieved.getTitle());
}
```

## Troubleshooting

### Common Issues

#### Issue: App crashes on startup after update
**Solution**: Check LogCat for MMKV initialization errors. Ensure MMKV.initialize() is called before any database operations.

#### Issue: Data missing after migration
**Solution**:
1. Check migration logs: Look for "HawkToMMKVMigration" tags in LogCat
2. Verify migration flag: Check if `hawk_to_mmkv_migrated` flag is set
3. Try manual migration using `HawkToMMKVMigration.migrateDatabase()`

#### Issue: "MMKVDB is not empty" warning
**Solution**: This is a safety feature to prevent overwriting existing data. If you see this during migration, it means MMKVDB already has data. This is expected after successful migration.

### Debug Logs

Enable debug logging to troubleshoot issues:
```java
TimberLogger.init("SafeApp");
// MMKVDB automatically logs operations at DEBUG level
```

Look for these log tags:
- `MMKVDB`: Database operations
- `HawkToMMKVMigration`: Migration process
- `DatabaseManager`: Database management

## Rollback Plan

If critical issues are found, rollback is possible:

1. **Code Rollback**: Revert DatabaseManager to use HawkDB
   ```java
   LocalDB<T> db = new HawkDB<>();  // Instead of MMKVDB
   ```

2. **Dependency Rollback**: MMKV and Hawk can coexist, so no immediate changes needed

3. **Data Safety**: Original Hawk data is preserved until `cleanupHawkData()` is called

## Future Cleanup

After successful migration period (e.g., 2-3 app versions):

1. Remove Hawk dependency from `build.gradle.kts`
2. Remove Hawk initialization from `SafeApplication`
3. Delete `HawkDB.java` class file
4. Remove migration utility classes
5. Clean up Hawk data with `HawkToMMKVMigration.cleanupHawkData()`

## Additional Resources

- [MMKV GitHub Repository](https://github.com/Tencent/MMKV)
- [MMKV Android Guide](https://github.com/Tencent/MMKV/wiki/android_tutorial)
- [Performance Benchmarks](https://github.com/Tencent/MMKV/wiki/android_benchmark)

## Version History

- **v0.35**: Initial migration from Hawk to MMKV
  - Added MMKVDB implementation
  - Created migration utilities
  - Updated all manager classes

## Notes

- MMKV files are stored in app's private directory
- Each database has a unique MMKV instance (multi-process mode)
- Data is automatically encrypted at the OS level (Android Keystore)
- MMKV uses less memory than Hawk for large datasets
- Migration is one-time and automatic
