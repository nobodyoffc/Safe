package com.fc.safe.db;

import static com.fc.fc_ajdk.constants.FieldNames.SAVE_TIME;

import android.content.Context;
import android.widget.Toast;

import com.fc.fc_ajdk.data.fchData.P2SH;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.DateUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A singleton class to manage and share the P2SH database across activities.
 * This provides a centralized way to access the P2SH database from any activity.
 */
public class MultisignManager {
    private static final String TAG = "MultisignManager";

    private static MultisignManager instance;
    private LocalDB<P2SH> multisignDB;

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
                TimberLogger.e(TAG, "Error closing existing database: " + e.getMessage());
            }
        }
        multisignDB = dbManager.getEntityDatabase(P2SH.class, LocalDB.SortType.BIRTH_ORDER, SAVE_TIME);
    }

    /**
     * Gets the P2SH database.
     * 
     * @return The P2SH database
     */
    public LocalDB<P2SH> getMultisignDB() {
        return multisignDB;
    }

    /**
     * Gets all P2SH objects from the database.
     * 
     * @return A map of P2SH objects with their IDs as keys
     */
    public Map<String, P2SH> getAllMultisigns() {
        return multisignDB.getAll();
    }

    /**
     * Gets a list of all P2SH objects from the database.
     * 
     * @return A list of all P2SH objects
     */
    public List<P2SH> getAllMultisignList() {
        return new ArrayList<>(multisignDB.getAll().values());
    }

    /**
     * Gets a P2SH object by its ID.
     * 
     * @param id The ID of the P2SH object to get
     * @return The P2SH object, or null if not found
     */
    public P2SH getMultisignById(String id) {
        return multisignDB.get(id);
    }

    /**
     * Adds a P2SH object to the database.
     * 
     * @param script The redeem script to create and add P2SH
     */
    public void addMultisign(String script,Context context) {
        P2SH p2sh = P2SH.parseP2shRedeemScript(script);
        if(p2sh==null){
            Toast.makeText(context,"Failed to parse script to multisign.",Toast.LENGTH_LONG).show();
            return;
        }
        p2sh.setSaveTime(DateUtils.longToTime(System.currentTimeMillis(), DateUtils.TO_MINUTE));
        multisignDB.put(p2sh.getId(), p2sh);
        TimberLogger.i(TAG, "Added P2SH with ID: %s", p2sh.getId());
    }

    /**
     * Removes a P2SH object from the database.
     * 
     * @param p2sh The P2SH object to remove
     */
    public void removeMultisign(P2SH p2sh) {
        multisignDB.remove(p2sh.getId());
    }

    /**
     * Removes multiple P2SH objects from the database.
     * 
     * @param p2shs The list of P2SH objects to remove
     */
    public void removeMultisigns(List<P2SH> p2shs) {
        multisignDB.remove(p2shs);
    }

    /**
     * Commits changes to the database.
     */
    public void commit() {
        multisignDB.commit();
    }

    /**
     * Gets a paginated list of P2SH objects.
     * 
     * @param pageSize The number of items per page
     * @param lastIndex The index of the last item from the previous page, or null for the first page
     * @param descending Whether to sort in descending order
     * @return A list of P2SH objects for the requested page
     */
    public List<P2SH> getPaginatedMultisigns(int pageSize, Long lastIndex, boolean descending) {
        if(multisignDB.getSortType().equals(LocalDB.SortType.NO_SORT)){
            TimberLogger.e(TAG, "getPaginatedMultisigns: The DB should been sorted.", SafeApplication.TOAST_LASTING);
            return null;
        }

        TimberLogger.d(TAG, "getPaginatedMultisigns: pageSize=%d, lastIndex=%d, descending=%b",
            pageSize, lastIndex, descending);

        // If we have items but pagination returns none, try without sorting
        List<P2SH> result = multisignDB.getList(pageSize, null, lastIndex, true, null, null, false, descending);

        TimberLogger.d(TAG, "getPaginatedMultisigns: Retrieved %d items", result != null ? result.size() : 0);
        if (result != null && !result.isEmpty()) {
            for (P2SH p2sh : result) {
                TimberLogger.d(TAG, "getPaginatedMultisigns: Retrieved Item ID: %s, SaveTime: %s", 
                    p2sh.getId(), p2sh.getSaveTime());
            }
        } else {
            TimberLogger.d(TAG, "getPaginatedMultisigns: No items retrieved");
        }
        return result;
    }

    /**
     * Gets the index of a P2SH object by its ID.
     * 
     * @param id The ID of the P2SH object
     * @return The index of the P2SH object
     */
    public Long getIndexById(String id) {
        return multisignDB.getIndexById(id);
    }

    /**
     * Checks if a P2SH with the given ID already exists in the database.
     * 
     * @param id The ID to check
     * @return true if the key exists, false otherwise
     */
    public boolean checkIfExisted(String id) {
        return multisignDB.get(id) != null;
    }
} 