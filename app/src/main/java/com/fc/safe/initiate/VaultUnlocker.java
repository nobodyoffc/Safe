package com.fc.safe.initiate;

import android.app.Activity;
import android.content.Context;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.core.crypto.VaultKey;
import com.fc.fc_ajdk.core.crypto.VaultMigration;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.db.CashManager;
import com.fc.safe.db.DatabaseManager;
import com.fc.safe.db.KeyInfoManager;
import com.fc.safe.db.MultisignManager;
import com.fc.safe.db.PendingTxManager;
import com.fc.safe.db.SafeVaultStore;
import com.fc.safe.db.SecretManager;
import com.fc.safe.db.ToastManager;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Opens a vault from its password. A vault with a data key is found by unwrapping that key. A legacy
 * vault is found by its password-derived name, confirmed with SHA256(password), and moved to a data key
 * and a random vault id on the way in. Every path runs Argon2id at least once, so call it off the UI thread.
 */
public final class VaultUnlocker {
    private static final String TAG = "VaultUnlocker";

    public static final class Result {
        /** The opened vault with its key set; null if no vault opens with the password. */
        public final Configure configure;
        /** A record that kept a legacy vault from moving to a data key; null otherwise. */
        public final String unreadableRecordId;

        private Result(Configure configure, String unreadableRecordId) {
            this.configure = configure;
            this.unreadableRecordId = unreadableRecordId;
        }
    }

    private VaultUnlocker() {
    }

    /**
     * @param migrate false to open a legacy vault as it is, as the password-change check does
     */
    public static Result unlock(Context context, byte[] passwordBytes, boolean migrate) {
        Map<String, Configure> configMap = ConfigureManager.loadConfigMap(context);
        char[] password = ConfigureManager.toChars(passwordBytes);

        Configure legacy = configMap.get(IdNameUtils.makePasswordHashName(passwordBytes));
        if (legacy != null) {
            Result result = openLegacy(context, legacy, passwordBytes, password, migrate);
            if (result != null) return result;
        }

        for (Map.Entry<String, Configure> entry : configMap.entrySet()) {
            Configure candidate = entry.getValue();
            if (candidate == null || VaultKey.isLegacyName(entry.getKey()) || candidate.getDekCipher() == null) continue;
            byte[] dek = VaultKey.unwrap(candidate.getDekCipher(), password);
            if (dek == null) continue;
            candidate.setSymkey(dek);
            if (migrate && candidate.getLegacyName() != null && isClosed(context, candidate.getLegacyName())) {
                // A crash after the flip left the legacy storage behind.
                try {
                    VaultMigration.run(new SafeVaultStore(context, candidate), password, null);
                } catch (Exception e) {
                    TimberLogger.e(TAG, "Failed to delete the legacy vault storage: " + e.getMessage(), e);
                }
            }
            return new Result(candidate, null);
        }
        return new Result(null, null);
    }

    /** Verifies the password of the open vault off the UI thread and reports back on it. */
    public static void verifyPasswordAsync(Activity activity, byte[] passwordBytes, Consumer<Boolean> onResult) {
        new Thread(() -> {
            boolean verified = ConfigureManager.getInstance().verifyPassword(passwordBytes);
            activity.runOnUiThread(() -> onResult.accept(verified));
        }).start();
    }

    /** @return the result for a confirmed legacy vault, or null if the password only shares its name. */
    private static Result openLegacy(Context context, Configure legacy, byte[] passwordBytes, char[] password, boolean migrate) {
        byte[] legacySymkey = Configure.getSymkeyFromPassword(passwordBytes);
        SafeVaultStore store = new SafeVaultStore(context, legacy);
        boolean confirmed = store.state() == VaultMigration.State.LEGACY
                ? store.legacyKeyOpensVault(legacySymkey)
                : VaultKey.unwrap(legacy.getDekCipher(), password) != null;
        if (!confirmed) return null;

        String legacyName = legacy.getPasswordName();
        if (!migrate || !isClosed(context, legacyName)) {
            // Its databases are open in this process, so it moves at the next start.
            store.reopenedAsLegacy();
            legacy.setSymkey(legacySymkey);
            return new Result(legacy, null);
        }

        resetManagers();
        try {
            VaultMigration.Result moved = VaultMigration.run(store, password, legacySymkey);
            if (moved.isMigrated()) {
                legacy.setSymkey(moved.dek);
                return new Result(legacy, null);
            }
            TimberLogger.w(TAG, "The vault keeps its legacy key: %s cannot be decrypted", moved.unreadableRecordId);
            legacy.setSymkey(legacySymkey);
            return new Result(legacy, moved.unreadableRecordId);
        } catch (Exception e) {
            TimberLogger.e(TAG, "Vault migration stopped: " + e.getMessage(), e);
            return reopenAfterFailure(context, legacyName, legacySymkey, password);
        }
    }

    /** Opens whichever side of the flip the saved configuration is on. */
    private static Result reopenAfterFailure(Context context, String legacyName, byte[] legacySymkey, char[] password) {
        Map<String, Configure> configMap = ConfigureManager.loadConfigMap(context);
        Configure stillLegacy = configMap.get(legacyName);
        if (stillLegacy != null) {
            stillLegacy.setSymkey(legacySymkey);
            return new Result(stillLegacy, null);
        }
        for (Configure flipped : configMap.values()) {
            if (flipped == null || !legacyName.equals(flipped.getLegacyName())) continue;
            byte[] dek = VaultKey.unwrap(flipped.getDekCipher(), password);
            if (dek != null) {
                flipped.setSymkey(dek);
                return new Result(flipped, null);
            }
        }
        return new Result(null, null);
    }

    /** @return true if no database of {@code vaultName} is open in this process. */
    private static boolean isClosed(Context context, String vaultName) {
        return !vaultName.equals(DatabaseManager.getInstance(context).getCurrentPasswordName());
    }

    /** Drops every manager, so nothing holds a storage the migration deletes. */
    private static void resetManagers() {
        KeyInfoManager.reset();
        SecretManager.reset();
        MultisignManager.reset();
        CashManager.reset();
        PendingTxManager.reset();
        ToastManager.reset();
    }
}
