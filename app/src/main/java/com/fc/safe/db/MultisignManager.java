package com.fc.safe.db;

import static com.fc.fc_ajdk.constants.FieldNames.SAVE_TIME;

import android.content.Context;
import android.widget.Toast;

import com.fc.fc_ajdk.data.fchData.Multisign;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.DateUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.SafeApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A singleton class to manage and share the Multisign database across activities.
 * This provides a centralized way to access the Multisign database from any activity.
 */
public class MultisignManager {
    private static final String TAG = "MultisignManager";

    private static MultisignManager instance;
    private LocalDB<Multisign> multisignDB;

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
        multisignDB = dbManager.getEntityDatabase(Multisign.class, LocalDB.SortType.BIRTH_ORDER, SAVE_TIME);
    }

    /**
     * Gets the Multisign database.
     * 
     * @return The Multisign database
     */
    public LocalDB<Multisign> getMultisignDB() {
        return multisignDB;
    }

    /**
     * Gets all Multisign objects from the database.
     * 
     * @return A map of Multisign objects with their IDs as keys
     */
    public Map<String, Multisign> getAllMultisigns() {
        return multisignDB.getAll();
    }

    /**
     * Gets a list of all Multisign objects from the database.
     * 
     * @return A list of all Multisign objects
     */
    public List<Multisign> getAllMultisignList() {
        return new ArrayList<>(multisignDB.getAll().values());
    }

    /**
     * Gets a Multisign object by its ID.
     * 
     * @param id The ID of the Multisign object to get
     * @return The Multisign object, or null if not found
     */
    public Multisign getMultisignById(String id) {
        return multisignDB.get(id);
    }

    /**
     * Adds a Multisign object to the database.
     * 
     * @param script The redeem script to create and add Multisign
     */
    public void addMultisign(String script,Context context) {
        Multisign multisign = Multisign.parseMultisignRedeemScript(script);
        if(multisign ==null){
            Toast.makeText(context,"Failed to parse script to multisign.",Toast.LENGTH_LONG).show();
            return;
        }
        multisign.setSaveTime(DateUtils.longToTime(System.currentTimeMillis(), DateUtils.TO_MINUTE));
        multisignDB.put(multisign.getId(), multisign);
        TimberLogger.i(TAG, "Added Multisign with ID: %s", multisign.getId());
    }

    /**
     * Removes a Multisign object from the database.
     * 
     * @param multisign The Multisign object to remove
     */
    public void removeMultisign(Multisign multisign) {
        multisignDB.remove(multisign.getId());
    }

    /**
     * Removes multiple Multisign objects from the database.
     * 
     * @param multisigns The list of Multisign objects to remove
     */
    public void removeMultisigns(List<Multisign> multisigns) {
        multisignDB.remove(multisigns);
    }

    /**
     * Commits changes to the database.
     */
    public void commit() {
        multisignDB.commit();
    }

    /**
     * Gets a paginated list of Multisign objects.
     * 
     * @param pageSize The number of items per page
     * @param lastIndex The index of the last item from the previous page, or null for the first page
     * @param descending Whether to sort in descending order
     * @return A list of Multisign objects for the requested page
     */
    public List<Multisign> getPaginatedMultisigns(int pageSize, Long lastIndex, boolean descending) {
        if(multisignDB.getSortType().equals(LocalDB.SortType.NO_SORT)){
            TimberLogger.e(TAG, "getPaginatedMultisigns: The DB should been sorted.", SafeApplication.TOAST_LASTING);
            return null;
        }

        TimberLogger.d(TAG, "getPaginatedMultisigns: pageSize=%d, lastIndex=%d, descending=%b",
            pageSize, lastIndex, descending);

        // If we have items but pagination returns none, try without sorting
        List<Multisign> result = multisignDB.getList(pageSize, null, lastIndex, true, null, null, false, descending);

        TimberLogger.d(TAG, "getPaginatedMultisigns: Retrieved %d items", result != null ? result.size() : 0);
        if (result != null && !result.isEmpty()) {
            for (Multisign multisign : result) {
                TimberLogger.d(TAG, "getPaginatedMultisigns: Retrieved Item ID: %s, SaveTime: %s", 
                    multisign.getId(), multisign.getSaveTime());
            }
        } else {
            TimberLogger.d(TAG, "getPaginatedMultisigns: No items retrieved");
        }
        return result;
    }

    /**
     * Gets the index of a Multisign object by its ID.
     * 
     * @param id The ID of the Multisign object
     * @return The index of the Multisign object
     */
    public Long getIndexById(String id) {
        return multisignDB.getIndexById(id);
    }

    /**
     * Checks if a Multisign with the given ID already exists in the database.
     * 
     * @param id The ID to check
     * @return true if the key exists, false otherwise
     */
    public boolean checkIfExisted(String id) {
        return multisignDB.get(id) != null;
    }
} 