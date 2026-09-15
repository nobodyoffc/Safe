package com.fc.safe.initiate;

import android.content.Context;
import android.content.SharedPreferences;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.core.crypto.VaultKey;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * A singleton class to manage and share the Configure object across activities.
 * This provides a safe way to access the Configure object and its symkey from any activity.
 */
public class ConfigureManager {
    private static ConfigureManager instance;
    private Configure configure;
    private static final String CONFIG_PREFS_NAME = "fc_config_prefs";
    private static final String CONFIG_KEY = "config_map";

    private ConfigureManager() {
        // Private constructor to prevent direct instantiation
    }

    public static synchronized ConfigureManager getInstance() {
        if (instance == null) {
            instance = new ConfigureManager();
        }
        return instance;
    }

    /**
     * Creates a new vault: a random data key wrapped under the password, stored under a random vault id.
     * Runs Argon2id, so call it off the UI thread. The caller stores the result.
     * @param passwordBytes The password bytes
     * @return The newly created Configure object, with its data key as the symkey
     */
    public static Configure createConfigure(byte[] passwordBytes) {
        Configure configure = new Configure();
        byte[] dek = VaultKey.newDek();
        configure.setDekCipher(VaultKey.wrap(dek, toChars(passwordBytes)));
        configure.setSymkey(dek);
        configure.setPasswordName(VaultKey.newVaultId());

        TimberLogger.d("ConfigMethods", "Created new vault: " + configure.getPasswordName());

        return configure;
    }

    /** The password as the characters VaultKey takes; the bytes come from {@code String.getBytes()}, which is UTF-8 on Android. */
    public static char[] toChars(byte[] passwordBytes) {
        return new String(passwordBytes, StandardCharsets.UTF_8).toCharArray();
    }

    /** @return every stored Configure by its vault name; never null. */
    public static Map<String, Configure> loadConfigMap(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(CONFIG_PREFS_NAME, Context.MODE_PRIVATE);
        Map<String, Configure> configMap = JsonUtils.jsonToMap(prefs.getString(CONFIG_KEY, "{}"), String.class, Configure.class);
        return configMap != null ? configMap : new HashMap<>();
    }

    /** Writes the whole Configure map durably. @return false if the write failed. */
    public static boolean commitConfigMap(Context context, Map<String, Configure> configMap) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(CONFIG_PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.edit().putString(CONFIG_KEY, JsonUtils.toJson(configMap)).commit();
    }

    /**
     * Sets the Configure object in memory for sharing across activities.
     * @param configure The Configure object to store
     */
    public void setConfigure(Configure configure) {
        this.configure = configure;
    }

    /**
     * Gets the Configure object from memory.
     * @return The Configure object
     */
    public Configure getConfigure() {
        return configure;
    }

    /**
     * Gets the symmetric key from the Configure object.
     * @return The symmetric key
     */
    public byte[] getSymkey() {
        return configure != null ? configure.getSymkey() : null;
    }

    /**
     * Stores a Configure object in SharedPreferences using its password name as the key.
     * @param context The application context
     * @param configure The Configure object to store
     */
    public void storeConfigure(Context context, Configure configure) {
        if (configure == null || configure.getPasswordName() == null) {
            throw new IllegalArgumentException("Configure object or password name is null");
        }
        
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }

        Map<String, Configure> configMap = loadConfigMap(context);
        configMap.put(configure.getPasswordName(), configure);

        // The wrapped data key lives only here, so the write must be durable before anyone relies on it.
        if (!commitConfigMap(context, configMap)) {
            throw new IllegalStateException("Failed to save the configuration");
        }
    }

    /**
     * Retrieves a Configure object from SharedPreferences using its password name.
     * @param context The application context
     * @param passwordName The password name to look up
     * @return The Configure object if found, null otherwise
     */
    public Configure getConfigure(Context context, String passwordName) {
        if (passwordName == null) {
            throw new IllegalArgumentException("Password name is null");
        }
        
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        
        SharedPreferences prefs = context.getSharedPreferences(CONFIG_PREFS_NAME, Context.MODE_PRIVATE);
        String configJson = prefs.getString(CONFIG_KEY, "{}");
        Map<String, Configure> configMap = JsonUtils.jsonToMap(configJson, String.class, Configure.class);
        
        return configMap != null ? configMap.get(passwordName) : null;
    }

    /**
     * Checks if the configuration is empty.
     * @param context The application context
     * @return true if the config is empty, false otherwise
     */
    public boolean isConfigEmpty(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        
        SharedPreferences prefs = context.getSharedPreferences(CONFIG_PREFS_NAME, Context.MODE_PRIVATE);
        String configJson = prefs.getString(CONFIG_KEY, "{}");
        Map<String, Configure> configMap = JsonUtils.jsonToMap(configJson, String.class, Configure.class);
        return configMap == null || configMap.isEmpty();
    }

    /**
     * Clears all configuration data from SharedPreferences.
     * @param context The application context
     */
    public void clearConfig(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        
        SharedPreferences prefs = context.getSharedPreferences(CONFIG_PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    /**
     * Verifies that the password opens the vault that is open now. For a vault with a data key this
     * runs Argon2id, so call it off the UI thread.
     * @param passwordBytes The password bytes to verify
     * @return true if the password opens the open vault
     */
    public boolean verifyPassword(byte[] passwordBytes) {
        if (passwordBytes == null || configure == null || configure.getSymkey() == null) {
            return false;
        }
        byte[] key;
        if (configure.getDekCipher() != null && !VaultKey.isLegacyName(configure.getPasswordName())) {
            key = VaultKey.unwrap(configure.getDekCipher(), toChars(passwordBytes));
        } else {
            key = Configure.getSymkeyFromPassword(passwordBytes);
        }
        return key != null && MessageDigest.isEqual(key, configure.getSymkey());
    }

    /**
     * Checks if a password already opens a stored vault. Runs Argon2id once per vault with a data key,
     * so call it off the UI thread.
     * @param context The application context
     * @param passwordBytes The password bytes to check
     * @return true if the password already exists, false otherwise
     */
    public boolean passwordExists(Context context, byte[] passwordBytes) {
        if (passwordBytes == null || context == null) {
            return false;
        }

        Map<String, Configure> configMap = loadConfigMap(context);
        if (configMap.containsKey(IdNameUtils.makePasswordHashName(passwordBytes))) {
            return true;
        }
        char[] password = toChars(passwordBytes);
        for (Map.Entry<String, Configure> entry : configMap.entrySet()) {
            Configure stored = entry.getValue();
            if (stored == null || VaultKey.isLegacyName(entry.getKey()) || stored.getDekCipher() == null) continue;
            if (VaultKey.unwrap(stored.getDekCipher(), password) != null) return true;
        }
        return false;
    }

    /**
     * Removes a Configure object from SharedPreferences using its password name as the key.
     * @param context The application context
     * @param passwordName The password name to remove
     */
    public void removeConfigure(Context context, String passwordName) {
        if (passwordName == null) {
            throw new IllegalArgumentException("Password name is null");
        }
        if (context == null) {
            throw new IllegalArgumentException("Context is null");
        }
        context = context.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(CONFIG_PREFS_NAME, Context.MODE_PRIVATE);
        String configJson = prefs.getString(CONFIG_KEY, "{}");
        Map<String, Configure> configMap = JsonUtils.jsonToMap(configJson, String.class, Configure.class);
        if (configMap != null && configMap.containsKey(passwordName)) {
            configMap.remove(passwordName);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(CONFIG_KEY, JsonUtils.toJson(configMap));
            editor.commit();
        }
    }
} 