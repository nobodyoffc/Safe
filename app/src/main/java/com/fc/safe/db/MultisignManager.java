package com.fc.safe.db;

import android.content.Context;

import com.fc.fc_ajdk.data.fchData.Multisig;
import com.fc.fc_ajdk.utils.DateUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fc.safe.utils.ToastUtils;

/**
 * A singleton class to manage and share the Multisig database across activities.
 * This provides a centralized way to access the Multisig database from any activity.
 */
public class MultisignManager {
    private static final String TAG = "MultisignManager";

    private static MultisignManager instance;
    private LocalDB<Multisig> multisignDB;

    private MultisignManager() {
        // Private constructor to prevent direct instantiation
    }

    /**
     * Gets the singleton instance of MultisignManager.
     * 
     * @param context The context to use for getting the DatabaseManager
     * @return The MultisignManager instance
     */
    public static synchronized MultisignManager getInstance(Context context) {
        if (instance == null) {
            instance = new MultisignManager();
            instance.initialize(context.getApplicationContext());
        }
        return instance;
    }

    public static synchronized void reset() {
        if (instance != null) {
            if (instance.multisignDB != null) {
                try {
                    instance.multisignDB.close();
                } catch (Exception e) {
                    TimberLogger.e(TAG, "Error closing database on reset: " + e.getMessage());
                }
                instance.multisignDB = null;
            }
            instance = null;
            TimberLogger.d(TAG, "MultisignManager instance reset");
        }
    }

    /**
     * Initializes the MultisignManager with the given context.
     * 
     * @param context The context to use for getting the DatabaseManager
     */
    public void initialize(Context context) {
        DatabaseManager dbManager = DatabaseManager.getInstance(context);
        // Close existing database if it exists
        if (multisignDB != null) {
            try {
                multisignDB.close();
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error closing database: " + e.getMessage());
            }
        }
        multisignDB = dbManager.getEntityDatabase(Multisig.class);
    }

    /**
     * Gets the Multisig database.
     * 
     * @return The Multisig database
     */
    public LocalDB<Multisig> getMultisignDB() {
        return multisignDB;
    }

    /**
     * Gets all Multisig objects from the database.
     * 
     * @return A map of Multisig objects with their IDs as keys
     */
    public Map<String, Multisig> getAllMultisigns() {
        return multisignDB.getAll();
    }

    /**
     * Gets a list of all Multisig objects from the database.
     * 
     * @return A list of all Multisig objects
     */
    public List<Multisig> getAllMultisignList() {
        return new ArrayList<>(multisignDB.getAll().values());
    }

    /**
     * Gets a Multisig object by its ID.
     * 
     * @param id The ID of the Multisig object to get
     * @return The Multisig object, or null if not found
     */
    public Multisig getMultisignById(String id) {
        return multisignDB.get(id);
    }

    /**
     * Adds a Multisig object to the database.
     * 
     * @param script The redeem script to create and add Multisig
     */
    public void addMultisign(String script,Context context) {
        Multisig multisig = Multisig.parseMultisignRedeemScript(script);
        if(multisig ==null){
            ToastUtils.showError(context, context.getString(R.string.failed_to_parse_script_to_multisign));
            return;
        }
        multisig.setSaveTime(DateUtils.longToTime(System.currentTimeMillis(), DateUtils.TO_MINUTE));
        multisignDB.put(multisig.getId(), multisig);
        TimberLogger.i(TAG, "Added Multisig with ID: %s", multisig.getId());
    }

    /**
     * Removes a Multisig object from the database.
     * 
     * @param multisig The Multisig object to remove
     */
    public void removeMultisign(Multisig multisig) {
        multisignDB.remove(multisig.getId());
    }

    /**
     * Removes multiple Multisig objects from the database.
     * 
     * @param multisigs The list of Multisig objects to remove
     */
    public void removeMultisigns(List<Multisig> multisigs) {
        multisignDB.remove(multisigs);
    }

    /**
     * Commits changes to the database.
     */
    public void commit() {
        multisignDB.commit();
    }

    /**
     * Gets a paginated list of Multisig objects.
     * 
     * @param pageSize The number of items per page
     * @param lastIndex The index of the last item from the previous page, or null for the first page
     * @param descending Whether to sort in descending order
     * @return A list of Multisig objects for the requested page
     */
    public List<Multisig> getPaginatedMultisigns(long pageSize, Long lastIndex, boolean descending) {
        return multisignDB.getList(pageSize, null, lastIndex, true, null, null, false, descending);
    }

    /**
     * Gets the index (0-based position) of a Multisig object by its ID.
     *
     * @param id The ID of the Multisig object
     * @return The index of the Multisig object, or -1 if not found
     */
    public long getIndexById(String id) {
        return multisignDB.getIndexById(id);
    }

    /**
     * Checks if a Multisig with the given ID already exists in the database.
     * 
     * @param id The ID to check
     * @return true if the key exists, false otherwise
     */
    public boolean checkIfExisted(String id) {
        return multisignDB.get(id) != null;
    }
} 