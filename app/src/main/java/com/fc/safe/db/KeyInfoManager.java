package com.fc.safe.db;

import static com.fc.fc_ajdk.constants.FieldNames.SAVE_TIME;
import static com.fc.safe.utils.IdUtils.AVATAR_MAP;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.feature.avatar.AvatarMaker;
import com.fc.fc_ajdk.utils.DateUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.ui.UserConfirmDialog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A singleton class to manage and share the KeyInfo database across activities.
 * This provides a centralized way to access the KeyInfo database from any activity.
 */
public class KeyInfoManager {
    private static final String TAG = "KeyInfoManager";

    private static KeyInfoManager instance;
    private LocalDB<KeyInfo> keyInfoDB;

    private KeyInfoManager() {
        // Private constructor to prevent direct instantiation
    }

    /**
     * Gets the singleton instance of KeyInfoManager.
     * 
     * @param context The context to use for getting the DatabaseManager
     * @return The KeyInfoManager instance
     */
    public static synchronized KeyInfoManager getInstance(Context context) {
        if (instance == null) {
            instance = new KeyInfoManager();
            instance.initialize(context.getApplicationContext());
        }
        return instance;
    }

    public void saveAvatar( String fid,byte[] avatarBytes) {
        keyInfoDB.putInMap(AVATAR_MAP, fid,avatarBytes);
    }

    /**
     * Initializes the KeyInfoManager with the given context.
     * 
     * @param context The context to use for getting the DatabaseManager
     */
    public void initialize(Context context) {
        DatabaseManager dbManager = DatabaseManager.getInstance(context);
        // Close existing database if it exists
        if (keyInfoDB != null) {
            try {
                keyInfoDB.close();
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error closing existing database: " + e.getMessage());
            }
        }
        keyInfoDB = dbManager.getEntityDatabase(KeyInfo.class, LocalDB.SortType.BIRTH_ORDER, SAVE_TIME);
    }

    /**
     * Gets the KeyInfo database.
     * 
     * @return The KeyInfo database
     */
    public LocalDB<KeyInfo> getKeyInfoDB() {
        return keyInfoDB;
    }

    /**
     * Gets all KeyInfo objects from the database.
     * 
     * @return A map of KeyInfo objects with their IDs as keys
     */
    public Map<String, KeyInfo> getAllKeyInfos() {
        if(keyInfoDB==null)return new HashMap<>();
        return keyInfoDB.getAll();
    }

    /**
     * Gets a list of all KeyInfo objects from the database.
     * 
     * @return A list of all KeyInfo objects
     */
    public List<KeyInfo> getAllKeyInfoList() {
        if(keyInfoDB==null)return new ArrayList<>();
        Map<String, KeyInfo> all = keyInfoDB.getAll();
        if(all == null || all.isEmpty())return new ArrayList<>();
        return new ArrayList<>(all.values());
    }

    /**
     * Gets a KeyInfo object by its ID.
     * 
     * @param id The ID of the KeyInfo object to get
     * @return The KeyInfo object, or null if not found
     */
    public KeyInfo getKeyInfoById(String id) {
        return keyInfoDB.get(id);
    }

    /**
     * Adds a KeyInfo object to the database.
     * 
     * @param keyInfo The KeyInfo object to add
     */
    public void addKeyInfo(KeyInfo keyInfo) {
        keyInfo.setSaveTime(DateUtils.longToTime(System.currentTimeMillis(), DateUtils.TO_MINUTE));
        if(keyInfo.getPrikeyCipher()==null){
            if( keyInfo.getPrikey()!=null || keyInfo.getPrikeyBytes()!=null) {
                if (keyInfo.getPrikeyBytes() == null)
                    keyInfo.setPrikeyBytes(KeyTools.getPrikey32(keyInfo.getPrikey()));
                String prikeyCipher = Encryptor.encryptBySymkeyToJson(keyInfo.getPrikeyBytes(), ConfigureManager.getInstance().getSymkey());
                keyInfo.setPrikeyCipher(prikeyCipher);
            }
        }

        keyInfoDB.put(keyInfo.getId(), keyInfo);
        TimberLogger.i(TAG, "Added KeyInfo with ID: %s", keyInfo.getId());
    }

    public void addAllKeyInfo(List<KeyInfo> keyInfoList) {
        Map<String,KeyInfo> keyInfoMap = new HashMap();
        int count = 0;
        for(KeyInfo keyInfo: keyInfoList) {
            if(keyInfo.getSaveTime()==null)
                keyInfo.setSaveTime(DateUtils.longToTime(System.currentTimeMillis(), DateUtils.TO_MINUTE));
            if (keyInfo.getPrikeyCipher() == null) {
                if (keyInfo.getPrikey() != null || keyInfo.getPrikeyBytes() != null) {
                    if (keyInfo.getPrikeyBytes() == null)
                        keyInfo.setPrikeyBytes(KeyTools.getPrikey32(keyInfo.getPrikey()));
                    String prikeyCipher = Encryptor.encryptBySymkeyToJson(keyInfo.getPrikeyBytes(), ConfigureManager.getInstance().getSymkey());
                    keyInfo.setPrikeyCipher(prikeyCipher);
                }
            }
            if(keyInfo.getId()==null){
                try {
                    if (keyInfo.getPrikey() != null)
                        keyInfo.setId(KeyTools.prikeyToFid(KeyTools.getPrikey32(keyInfo.getPrikey())));
                    else if (keyInfo.getPubkey() != null)
                        keyInfo.setId(KeyTools.pubkeyToFchAddr(keyInfo.getPubkey()));
                    else continue;
                }catch (Exception e){
                    continue;
                }
            }
            keyInfoMap.put(keyInfo.getId(),keyInfo);
            count++;
        }
        keyInfoDB.putAll(keyInfoMap);
        TimberLogger.i(TAG, "%s keyInfos added.", count);
    }

    /**
     * Removes a KeyInfo object from the database.
     * 
     * @param keyInfo The KeyInfo object to remove
     */
    public void removeKeyInfo(KeyInfo keyInfo) {
        keyInfoDB.remove(keyInfo.getId());
    }

    /**
     * Removes multiple KeyInfo objects from the database.
     * 
     * @param keyInfos The list of KeyInfo objects to remove
     */
    public void removeKeyInfos(List<KeyInfo> keyInfos) {
        keyInfoDB.remove(keyInfos);
    }

    /**
     * Commits changes to the database.
     */
    public void commit() {
        keyInfoDB.commit();
    }

    /**
     * Gets a paginated list of KeyInfo objects.
     * 
     * @param pageSize The number of items per page
     * @param lastIndex The index of the last item from the previous page, or null for the first page
     * @param descending Whether to sort in descending order
     * @return A list of KeyInfo objects for the requested page
     */
    public List<KeyInfo> getPaginatedKeyInfos(int pageSize, Long lastIndex, boolean descending) {
        return keyInfoDB.getList(pageSize, null, lastIndex, true, null, null, false, descending);
    }

    /**
     * Gets the index of a KeyInfo object by its ID.
     * 
     * @param id The ID of the KeyInfo object
     * @return The index of the KeyInfo object
     */
    public Long getIndexById(String id) {
        return keyInfoDB.getIndexById(id);
    }

    public byte[] getAvatarById(String id) {
        return keyInfoDB.getFromMap(AVATAR_MAP, id);
    }

    public void makeAvatarByFid(String fid,Context context) throws IOException {
        byte[] avatarBytes = AvatarMaker.createAvatar(fid, context);

        // Save avatar to database
        if (avatarBytes != null) {
            keyInfoDB.putInMap(AVATAR_MAP, fid, avatarBytes);
            TimberLogger.i(TAG, "Created and saved avatar for %s", fid);
        }
    }

    /**
     * Checks if a KeyInfo with the given ID already exists in the database.
     * 
     * @param id The ID to check
     * @return true if the key exists, false otherwise
     */
    public boolean checkIfExisted(String id) {
        return keyInfoDB.get(id) != null;
    }

    public static void saveAndFinish(Activity activity, List<KeyInfo> keyInfoList) {
        KeyInfoManager keyInfoManager = KeyInfoManager.getInstance(activity);
        byte[] symkey = ConfigureManager.getInstance().getSymkey();

        processKeyInfoSequentially(activity, keyInfoManager, keyInfoList, symkey, 0,0);
    }

    private static void processKeyInfoSequentially(Activity activity, KeyInfoManager keyInfoManager, List<KeyInfo> keyInfoList, byte[] symkey, int index,int savedCount) {
        if (index >= keyInfoList.size()) {
            keyInfoManager.commit();
            Toast.makeText(activity, activity.getString(R.string.secrets_saved_successfully, savedCount), Toast.LENGTH_SHORT).show();
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
            return;
        }
        KeyInfo keyInfo = keyInfoList.get(index);
        if (keyInfo.getPrikey() != null) {
            if (keyInfo.getId() == null)
                keyInfo.setId(KeyTools.prikeyToFid(KeyTools.getPrikey32(keyInfo.getPrikey())));
            String cipher = Encryptor.encryptBySymkeyToJson(KeyTools.getPrikey32(keyInfo.getPrikey()), symkey);
            keyInfo.setPrikeyCipher(cipher);
            keyInfo.setPrikey(null);
        } else if (keyInfo.getPrikeyCipher() == null) {
            // skip this key if no cipher or prikey
            processKeyInfoSequentially(activity, keyInfoManager, keyInfoList, symkey, index + 1,savedCount);
            return;
        }
        if (keyInfoManager.checkIfExisted(keyInfo.getId())) {
            String prompt = keyInfo.getId() + " existed. Replace it?";
            UserConfirmDialog dialog = new UserConfirmDialog(activity, prompt, choice -> {
                if (choice == UserConfirmDialog.Choice.YES) {
                    keyInfoManager.addKeyInfo(keyInfo);
                    keyInfoManager.commit();
                    processKeyInfoSequentially(activity, keyInfoManager, keyInfoList, symkey, index + 1,savedCount+1);
                } else if (choice == UserConfirmDialog.Choice.NO) {
                    processKeyInfoSequentially(activity, keyInfoManager, keyInfoList, symkey, index +1,savedCount);
                } else if (choice == UserConfirmDialog.Choice.STOP) {
                    keyInfoManager.commit();
                    Toast.makeText(activity, activity.getString(R.string.secrets_saved_successfully, savedCount), Toast.LENGTH_SHORT).show();
                    activity.setResult(Activity.RESULT_OK);
                    activity.finish();
                }
            });
            dialog.show();
        } else {
            keyInfoManager.addKeyInfo(keyInfo);
            processKeyInfoSequentially(activity, keyInfoManager, keyInfoList, symkey, index + 1,savedCount+1);
        }
    }
} 