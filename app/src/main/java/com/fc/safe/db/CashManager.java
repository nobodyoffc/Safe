package com.fc.safe.db;

import static com.fc.fc_ajdk.constants.FieldNames.BIRTH_TIME;

import android.content.Context;
import android.widget.Toast;

import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.TimberLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A singleton class to manage and share the Cash database across activities.
 * This provides a centralized way to access the Cash database from any activity.
 */
public class CashManager {
    private static final String TAG = "CashManager";

    private static CashManager instance;
    private LocalDB<Cash> cashDB;

    private CashManager() {
        // Private constructor to prevent direct instantiation
    }

    /**
     * Gets the singleton instance of CashManager.
     * 
     * @param context The context to use for getting the DatabaseManager
     * @return The CashManager instance
     */
    public static synchronized CashManager getInstance(Context context) {
        if (instance == null) {
            instance = new CashManager();
            instance.initialize(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Initializes the CashManager with the given context.
     * 
     * @param context The context to use for getting the DatabaseManager
     */
    public void initialize(Context context) {
        DatabaseManager dbManager = DatabaseManager.getInstance(context);
        // Close existing database if it exists
        if (cashDB != null) {
            try {
                cashDB.close();
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error closing database: " + e.getMessage());
            }
        }
        cashDB = dbManager.getEntityDatabase(Cash.class, LocalDB.SortType.BIRTH_ORDER, BIRTH_TIME);
    }

    /**
     * Gets the Cash database.
     * 
     * @return The Cash database
     */
    public LocalDB<Cash> getCashDB() {
        return cashDB;
    }

    /**
     * Gets all Cash objects from the database.
     * 
     * @return A map of Cash objects with their IDs as keys
     */
    public Map<String, Cash> getAllCashes() {
        if(cashDB==null)return new HashMap<>();
        return cashDB.getAll();
    }

    /**
     * Gets a list of all Cash objects from the database.
     * 
     * @return A list of all Cash objects
     */
    public List<Cash> getAllCashList() {
        if(cashDB==null)return new ArrayList<>();
        Map<String, Cash> all = cashDB.getAll();
        if(all == null || all.isEmpty())return new ArrayList<>();
        return new ArrayList<>(all.values());
    }

    /**
     * Gets a Cash object by its ID.
     * 
     * @param id The ID of the Cash object to get
     * @return The Cash object, or null if not found
     */
    public Cash getCashById(String id) {
        return cashDB.get(id);
    }

    /**
     * Adds a Cash object to the database.
     * 
     * @param cash The Cash object to add
     */
    public void addCash(Cash cash) {
        if (cash.getId() == null) {
            cash.makeId();
        }
        cashDB.put(cash.getId(), cash);
        TimberLogger.i(TAG, "Added Cash with ID: %s", cash.getId());
    }

    /**
     * Adds multiple Cash objects to the database.
     * 
     * @param cashList The list of Cash objects to add
     */
    public void addAllCash(List<Cash> cashList) {
        Map<String, Cash> cashMap = new HashMap<>();
        int count = 0;
        for(Cash cash: cashList) {
            if(cash.getId() == null) {
                cash.makeId();
            }
            cashMap.put(cash.getId(), cash);
            count++;
        }
        cashDB.putAll(cashMap);
        TimberLogger.i(TAG, "%s cashes added.", count);
    }

    /**
     * Removes a Cash object from the database.
     * 
     * @param cash The Cash object to remove
     */
    public void removeCash(Cash cash) {
        cashDB.remove(cash.getId());
    }

    public void removeCash(String cashId) {
        cashDB.remove(cashId);
    }

    /**
     * Removes multiple Cash objects from the database.
     * 
     * @param cashes The list of Cash objects to remove
     */
    public void removeCashes(List<Cash> cashes) {
        cashDB.remove(cashes);
    }

    /**
     * Commits changes to the database.
     */
    public void commit() {
        cashDB.commit();
    }

    /**
     * Gets a paginated list of Cash objects.
     * 
     * @param pageSize The number of items per page
     * @param lastIndex The index of the last item from the previous page, or null for the first page
     * @param descending Whether to sort in descending order
     * @return A list of Cash objects for the requested page
     */
    public List<Cash> getPaginatedCashes(int pageSize, Long lastIndex, boolean descending) {
        return cashDB.getList(pageSize, null, lastIndex, true, null, null, false, descending);
    }

    /**
     * Gets the index of a Cash object by its ID.
     * 
     * @param id The ID of the Cash object
     * @return The index of the Cash object
     */
    public Long getIndexById(String id) {
        return cashDB.getIndexById(id);
    }

    /**
     * Checks if a Cash with the given ID already exists in the database.
     * 
     * @param id The ID to check
     * @return true if the cash exists, false otherwise
     */
    public boolean checkIfExisted(String id) {
        return cashDB.get(id) != null;
    }

    /**
     * Gets all valid (unspent) Cash objects.
     * 
     * @return A list of valid Cash objects
     */
    public List<Cash> getValidCashes() {
        List<Cash> allCashes = getAllCashList();
        List<Cash> validCashes = new ArrayList<>();
        for (Cash cash : allCashes) {
            if (cash.isValid() != null && cash.isValid()) {
                validCashes.add(cash);
            }
        }
        return validCashes;
    }

    /**
     * Gets all Cash objects owned by a specific FID.
     * 
     * @param ownerFid The FID of the owner
     * @return A list of Cash objects owned by the specified FID
     */
    public List<Cash> getCashesByOwner(String ownerFid) {
        List<Cash> allCashes = getAllCashList();
        List<Cash> ownerCashes = new ArrayList<>();
        for (Cash cash : allCashes) {
            if (ownerFid.equals(cash.getOwner())) {
                ownerCashes.add(cash);
            }
        }
        return ownerCashes;
    }

    /**
     * Gets all valid Cash objects owned by a specific FID.
     * 
     * @param ownerFid The FID of the owner
     * @return A list of valid Cash objects owned by the specified FID
     */
    public List<Cash> getValidCashesByOwner(String ownerFid) {
        List<Cash> allCashes = getAllCashList();
        List<Cash> validOwnerCashes = new ArrayList<>();
        for (Cash cash : allCashes) {
            if (ownerFid.equals(cash.getOwner()) && cash.isValid() != null && cash.isValid()) {
                validOwnerCashes.add(cash);
            }
        }
        return validOwnerCashes;
    }

    /**
     * Utility method to save a Cash, commit, show a toast, set result, and finish the activity.
     */
    public static void saveAndFinish(android.app.Activity activity, Cash cash) {
        CashManager cashManager = CashManager.getInstance(activity);
        cashManager.addCash(cash);
        cashManager.commit();
        Toast.makeText(activity, com.fc.safe.R.string.cash_saved_successfully, Toast.LENGTH_SHORT).show();
        activity.setResult(android.app.Activity.RESULT_OK);
        activity.finish();
    }

    /**
     * Utility method to save multiple Cash objects, commit, show a toast, set result, and finish the activity.
     */
    public static void saveAndFinish(android.app.Activity activity, List<Cash> cashList) {
        CashManager cashManager = CashManager.getInstance(activity);
        cashManager.addAllCash(cashList);
        cashManager.commit();
        Toast.makeText(activity, activity.getString(com.fc.safe.R.string.cash_saved_successfully_count, cashList.size()), Toast.LENGTH_SHORT).show();
        activity.setResult(android.app.Activity.RESULT_OK);
        activity.finish();
    }
} 