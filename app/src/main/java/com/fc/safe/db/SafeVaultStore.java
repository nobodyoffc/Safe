package com.fc.safe.db;

import android.content.Context;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.core.crypto.VaultKey;
import com.fc.fc_ajdk.core.crypto.VaultMigration;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.initiate.ConfigureManager;
import com.tencent.mmkv.MMKV;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The storage a {@link VaultMigration} moves in Safe: the vault's Configure entry in SharedPreferences,
 * every MMKV storage named after the vault, and the toast history kept under the vault's name.
 *
 * MMKVDB names a storage {@code <vault name>_<db name>_} and prefixes each of its keys with that id, so
 * a copy renames the storage and every key. Every value is searched for vault-key ciphers. Storages are found by listing the MMKV root directory, so
 * any database named after the vault moves, not just the ones this class knows about.
 */
public class SafeVaultStore implements VaultMigration.Store {
    private static final String TAG = "SafeVaultStore";

    private final Context context;
    private final Configure configure;
    private final String legacyName;
    private String configKey;

    public SafeVaultStore(Context context, Configure configure) {
        this.context = context.getApplicationContext();
        this.configure = configure;
        this.configKey = configure.getPasswordName();
        this.legacyName = configure.getLegacyName() != null ? configure.getLegacyName() : configure.getPasswordName();
    }

    public Configure getConfigure() {
        return configure;
    }

    /**
     * @return true if {@code legacySymkey} opens at least one cipher in the legacy vault, or the vault has none.
     * The legacy name matches about one wrong password in 16 million, so it cannot confirm a password alone.
     */
    public boolean legacyKeyOpensVault(byte[] legacySymkey) {
        boolean sawCipher = false;
        for (String id : storageIds(legacyName)) {
            MMKV mmkv = open(id);
            String[] keys = mmkv.allKeys();
            if (keys == null) continue;
            for (String key : keys) {
                int opens = VaultRecords.opens(mmkv.decodeString(key), legacySymkey);
                if (opens == VaultRecords.OPENS) return true;
                if (opens == VaultRecords.CLOSED) sawCipher = true;
            }
        }
        return !sawCipher;
    }

    /**
     * Call when the legacy vault is opened without moving it. The app may then change its records,
     * so a copy made earlier is stale and has to be made again.
     */
    public void reopenedAsLegacy() {
        if (state() != VaultMigration.State.COPIED) return;
        configure.setVaultState(VaultMigration.State.PREPARED.name());
        try {
            save();
        } catch (IOException e) {
            TimberLogger.e(TAG, "Failed to mark the vault copy stale: " + e.getMessage());
        }
    }

    @Override
    public VaultMigration.State state() {
        if (configure.getVaultState() != null) return VaultMigration.State.valueOf(configure.getVaultState());
        return VaultKey.isLegacyName(configure.getPasswordName()) ? VaultMigration.State.LEGACY : VaultMigration.State.DONE;
    }

    @Override
    public String pendingVaultId() {
        return configure.getPendingVaultId();
    }

    @Override
    public String pendingDekCipher() {
        return configure.getDekCipher();
    }

    @Override
    public void savePrepared(String vaultId, String dekCipher) throws IOException {
        configure.setPendingVaultId(vaultId);
        configure.setDekCipher(dekCipher);
        configure.setVaultState(VaultMigration.State.PREPARED.name());
        save();
    }

    @Override
    public void copyInto(String vaultId, VaultMigration.Reencrypt reencrypt) throws Exception {
        for (String id : storageIds(legacyName)) {
            String newId = rename(id, vaultId);
            MMKV from = open(id);
            MMKV to = open(newId);
            to.clearAll();
            String[] keys = from.allKeys();
            if (keys == null) continue;
            String dbName = dbName(id, legacyName);
            long copied = 0;
            for (String key : keys) {
                String value = from.decodeString(key);
                if (value == null) {
                    TimberLogger.w(TAG, "Skipped a non-string entry in %s", dbName);
                    continue;
                }
                value = VaultRecords.reencrypt(dbName + "/" + key.substring(id.length()), value, reencrypt);
                if (!to.encode(rename(key, vaultId), value)) throw new IOException("Failed to write " + dbName);
                copied++;
            }
            to.sync();
            if (to.count() != copied) throw new IOException("Copied " + to.count() + " of " + copied + " entries of " + dbName);
        }
        ToastManager.copyMessages(legacyName, vaultId);
    }

    @Override
    public void forEachCipherIn(String vaultId, VaultMigration.Check check) throws Exception {
        for (String id : storageIds(vaultId)) {
            MMKV mmkv = open(id);
            String[] keys = mmkv.allKeys();
            if (keys == null) continue;
            String dbName = dbName(id, vaultId);
            for (String key : keys) {
                String name = key.startsWith(id) ? key.substring(id.length()) : key;
                VaultRecords.check(dbName + "/" + name, mmkv.decodeString(key), check);
            }
        }
    }

    @Override
    public void discard(String vaultId) {
        removeStorages(vaultId);
        ToastManager.deleteMessages(vaultId);
    }

    @Override
    public void saveCopied() throws IOException {
        configure.setVaultState(VaultMigration.State.COPIED.name());
        save();
    }

    @Override
    public void flip(String vaultId) throws IOException {
        Map<String, Configure> configMap = ConfigureManager.loadConfigMap(context);
        configure.setLegacyName(legacyName);
        configure.setPasswordName(vaultId);
        configure.setVaultState(VaultMigration.State.FLIPPED.name());
        configMap.remove(legacyName);
        configMap.put(vaultId, configure);
        if (!ConfigureManager.commitConfigMap(context, configMap)) {
            configure.setPasswordName(legacyName);
            configure.setLegacyName(null);
            configure.setVaultState(VaultMigration.State.COPIED.name());
            throw new IOException("Failed to save the configuration");
        }
        configKey = vaultId;
    }

    @Override
    public void deleteLegacy() throws IOException {
        removeStorages(legacyName);
        ToastManager.deleteMessages(legacyName);
        Map<String, Configure> configMap = ConfigureManager.loadConfigMap(context);
        if (configMap.remove(legacyName) != null && !ConfigureManager.commitConfigMap(context, configMap)) {
            throw new IOException("Failed to save the configuration");
        }
    }

    @Override
    public void saveDone() throws IOException {
        configure.setVaultState(null);
        configure.setLegacyName(null);
        configure.setPendingVaultId(null);
        save();
    }

    private void save() throws IOException {
        Map<String, Configure> configMap = ConfigureManager.loadConfigMap(context);
        configMap.put(configKey, configure);
        if (!ConfigureManager.commitConfigMap(context, configMap)) throw new IOException("Failed to save the configuration");
    }

    private void removeStorages(String vaultName) {
        for (String id : storageIds(vaultName)) {
            if (!MMKV.removeStorage(id)) TimberLogger.w(TAG, "Failed to remove a storage of %s", dbName(id, vaultName));
        }
    }

    /** @return the ids of the MMKV storages named after {@code vaultName}. */
    private static List<String> storageIds(String vaultName) {
        List<String> ids = new ArrayList<>();
        String rootDir = MMKV.getRootDir();
        if (rootDir == null) return ids;
        File root = new File(rootDir);
        String[] names = root.list();
        if (names == null) return ids;
        String prefix = vaultName + "_";
        for (String name : names) {
            // MMKV keeps each storage in a file named by its id, beside a .crc meta file.
            if (name.startsWith(prefix) && !name.endsWith(".crc") && new File(root, name).isFile()) ids.add(name);
        }
        return ids;
    }

    private static MMKV open(String id) {
        MMKV mmkv = MMKV.mmkvWithID(id, MMKV.MULTI_PROCESS_MODE);
        if (mmkv == null) throw new IllegalStateException("Failed to open MMKV storage");
        return mmkv;
    }

    /** Replaces the vault name at the start of an MMKV id or key. */
    private String rename(String idOrKey, String vaultId) {
        return idOrKey.startsWith(legacyName) ? vaultId + idOrKey.substring(legacyName.length()) : idOrKey;
    }

    private static String dbName(String id, String vaultName) {
        String name = id.substring(vaultName.length() + 1);
        return name.endsWith("_") ? name.substring(0, name.length() - 1) : name;
    }
}
