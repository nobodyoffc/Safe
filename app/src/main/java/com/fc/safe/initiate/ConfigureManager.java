package com.fc.safe.initiate;

import android.content.Context;
import android.content.SharedPreferences;

import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;

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
     * Creates a new Configure object and stores it in SharedPreferences.
     * @param passwordBytes The password bytes
     * @return The newly created Configure object
     */
    public static Configure createConfigure(byte[] passwordBytes) {
        // Create a new Configure object
        Configure configure = new Configure();

        // Generate nonce and symmetric key
        byte[] symkey = Configure.getSymkeyFromPassword(passwordBytes);

        // Set configure properties
        configure.setSymkey(symkey);

        // Generate password name and set it
        String passwordName = IdNameUtils.makePasswordHashName(passwordBytes);
        configure.setPasswordName(passwordName);

        TimberLogger.d("ConfigMethods", "Created new configure for password: " + passwordName);

        return configure;
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

        context = context.getApplicationContext();
        
        SharedPreferences prefs = context.getSharedPreferences(CONFIG_PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        // Get existing config map or create new one
        String configJson = prefs.getString(CONFIG_KEY, "{}");
        Map<String, Configure> configMap = JsonUtils.jsonToMap(configJson, String.class, Configure.class);
        if (configMap == null) {
            configMap = new HashMap<>();
        }
        
        // Add or update the configure object
        configMap.put(configure.getPasswordName(), configure);
        
        // Save back to SharedPreferences
        editor.putString(CONFIG_KEY, JsonUtils.toJson(configMap));
        editor.apply();
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
     * Verifies if the input password matches the current password name.
     * @param passwordBytes The password bytes to verify
     * @return true if the password name matches, false otherwise
     */
    public boolean verifyPasswordName(byte[] passwordBytes) {
        if (passwordBytes == null || configure == null) {
            return false;
        }
        String inputPasswordName = IdNameUtils.makePasswordHashName(passwordBytes);
        return inputPasswordName.equals(configure.getPasswordName());
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
            editor.apply();
        }
    }
} 