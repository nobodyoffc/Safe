package com.fc.safe.db;

import static com.fc.fc_ajdk.constants.FieldNames.SAVE_TIME;

import android.content.Context;
import android.app.Activity;
import android.widget.Toast;

import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.data.fcData.SecretDetail;
import com.fc.fc_ajdk.db.LocalDB;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.ui.UserConfirmDialog;
import com.fc.safe.utils.IdUtils; // Added for avatar generation

import java.io.IOException; // Added for avatar generation method
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A singleton class to manage and share the SecretDetail database across activities.
 * This provides a centralized way to access the SecretDetail database from any activity.
 */
public class SecretManager {
    private static final String TAG = "SecretManager"; // Updated TAG

    private static SecretManager instance;
    private LocalDB<SecretDetail> secretDetailDB; // Renamed and type updated

    private SecretManager() {
        // Private constructor to prevent direct instantiation
    }

    /**
     * Gets the singleton instance of SecretManager.
     * 
     * @param context The context to use for getting the DatabaseManager
     * @return The SecretManager instance
     */
    public static synchronized SecretManager getInstance(Context context) {
        if (instance == null) {
            instance = new SecretManager();
            instance.initialize(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Initializes the SecretManager with the given context.
     * 
     * @param context The context to use for getting the DatabaseManager
     */
    public void initialize(Context context) {
        DatabaseManager dbManager = DatabaseManager.getInstance(context);
        // Close existing database if it exists
        if (secretDetailDB != null) {
            try {
                secretDetailDB.close();
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error closing existing database: " + e.getMessage());
            }
        }
        secretDetailDB = dbManager.getEntityDatabase(SecretDetail.class, LocalDB.SortType.BIRTH_ORDER, SAVE_TIME);
    }

    /**
     * Gets the SecretDetail database.
     * 
     * @return The SecretDetail database
     */
    public LocalDB<SecretDetail> getSecretDetailDB() { // Renamed method
        return secretDetailDB;
    }

    /**
     * Gets all SecretDetail objects from the database.
     * 
     * @return A map of SecretDetail objects with their IDs as keys
     */
    public Map<String, SecretDetail> getAllSecretDetails() { // Renamed method and updated type
        if(secretDetailDB==null)return new HashMap<>();
        return secretDetailDB.getAll();
    }

    /**
     * Gets a list of all SecretDetail objects from the database.
     * 
     * @return A list of all SecretDetail objects
     */
    public List<SecretDetail> getAllSecretDetailList() { // Renamed method and updated type
        if(secretDetailDB==null)return new ArrayList<>();
        Map<String, SecretDetail> all = secretDetailDB.getAll();
        if(all==null || all.isEmpty())return new ArrayList<>();
        return new ArrayList<>(all.values());
    }

    /**
     * Gets a SecretDetail object by its ID.
     * 
     * @param id The ID of the SecretDetail object to get
     * @return The SecretDetail object, or null if not found
     */
    public SecretDetail getSecretDetailById(String id) { // Renamed method and updated type
        return secretDetailDB.get(id);
    }

    /**
     * Adds a SecretDetail object to the database.
     * 
     * @param secretDetail The SecretDetail object to add
     */
    public void addSecretDetail(SecretDetail secretDetail) { // Renamed method and updated type
        // SecretDetail's saveTime is Long
        secretDetail.setSaveTime(System.currentTimeMillis());
        secretDetail.checkIdWithCreate(); 

        secretDetailDB.put(secretDetail.getId(), secretDetail);
        TimberLogger.i(TAG, "Added SecretDetail with ID: %s", secretDetail.getId());
    }

    public void addAllSecretDetail(List<SecretDetail> secretDetailList) { // Renamed method and updated type
        // SecretDetail's saveTime is Long
        Map<String,SecretDetail> map = new HashMap<>();
        for(SecretDetail secretDetail: secretDetailList) {
            if(secretDetail.getSaveTime()==null)
                secretDetail.setSaveTime(System.currentTimeMillis());
            secretDetail.checkIdWithCreate();
            map.put(secretDetail.getId(),secretDetail);
        }
        secretDetailDB.putAll(map);

        TimberLogger.i(TAG, "%s secrets added", map.size());
    }

    /**
     * Removes a SecretDetail object from the database.
     * 
     * @param secretDetail The SecretDetail object to remove
     */
    public void removeSecretDetail(SecretDetail secretDetail) { // Renamed method and updated type
        secretDetailDB.remove(secretDetail.getId());
    }

    /**
     * Removes multiple SecretDetail objects from the database.
     * 
     * @param secretDetails The list of SecretDetail objects to remove
     */
    public void removeSecretDetails(List<SecretDetail> secretDetails) { // Renamed method and updated type
        secretDetailDB.remove(secretDetails);
    }

    /**
     * Commits changes to the database.
     */
    public void commit() {
        secretDetailDB.commit();
    }

    /**
     * Gets a paginated list of SecretDetail objects.
     * 
     * @param pageSize The number of items per page
     * @param lastIndex The index of the last item from the previous page, or null for the first page
     * @param descending Whether to sort in descending order
     * @return A list of SecretDetail objects for the requested page
     */
    public List<SecretDetail> getPaginatedSecretDetails(int pageSize, Long lastIndex, boolean descending) { // Renamed method and updated type
        return secretDetailDB.getList(pageSize, null, lastIndex, true, null, null, false, descending);
    }

    /**
     * Gets the index of a SecretDetail object by its ID.
     * 
     * @param id The ID of the SecretDetail object
     * @return The index of the SecretDetail object
     */
    public Long getIndexById(String id) {
        return secretDetailDB.getIndexById(id);
    }

    /**
     * Generates an avatar for the given entity ID.
     * It attempts to use the ID from the corresponding SecretDetail object,
     * calling checkIdWithCreate if the SecretDetail's ID is initially null or empty.
     * If the SecretDetail object is not found, it uses the provided entityId directly.
     * Avatars are generated on the fly and not stored in the database.
     *
     * @param entityId The ID of the entity for which to generate an avatar.
     * @param context  The context.
     * @return Byte array of the generated avatar image, or null if generation fails.
     * @throws IOException If an I/O error occurs during avatar generation.
     */
    public byte[] generateAvatarById(String entityId, Context context) throws IOException {
        if (entityId == null || entityId.isEmpty()) {
            TimberLogger.w(TAG, "Input entityId is null or empty, cannot generate avatar.");
            return null;
        }
        
        SecretDetail secretDetail = getSecretDetailById(entityId); 
        String idForAvatar;

        if (secretDetail != null) {
            // We have the SecretDetail object. Use its ID, potentially after checkIdWithCreate.
            idForAvatar = secretDetail.getId(); 
            if (idForAvatar == null || idForAvatar.isEmpty()) {
                 idForAvatar = secretDetail.checkIdWithCreate(); 
            }
        } else {
            // SecretDetail not found, fall back to using the provided entityId directly.
            TimberLogger.w(TAG, "No SecretDetail found for ID: %s. Attempting to generate avatar with the provided ID string.", entityId);
            idForAvatar = entityId;
        }

        if (idForAvatar == null || idForAvatar.isEmpty()) {
            TimberLogger.w(TAG, "Failed to determine a valid ID for avatar generation (original entityId: %s).", entityId);
            return null;
        }
        
        TimberLogger.i(TAG, "Generating avatar for ID: %s (derived from original entityId: %s)", idForAvatar, entityId);
        return IdUtils.makeCircleImageFromId(idForAvatar);
    }
    
    /**
     * Generates an avatar for a given SecretDetail object.
     * This method ensures the SecretDetail's ID is set (using checkIdWithCreate if necessary)
     * before generating the avatar. Avatars are not stored in the database.
     *
     * @param secretDetail The SecretDetail object.
     * @param context The context.
     * @return Byte array of the generated avatar image, or null if generation fails or input is null.
     * @throws IOException If an I/O error occurs.
     */
    public byte[] generateAvatarForSecret(SecretDetail secretDetail, Context context) throws IOException {
        if (secretDetail == null) {
            TimberLogger.w(TAG, "SecretDetail object is null, cannot generate avatar.");
            return null;
        }
        String id = secretDetail.getId();
        if (id == null || id.isEmpty()) {
            id = secretDetail.checkIdWithCreate(); 
        }

        if (id == null || id.isEmpty()) {
            TimberLogger.w(TAG, "Cannot generate avatar for SecretDetail, ID is null or empty after checkIdWithCreate.");
            return null; 
        }
        TimberLogger.i(TAG, "Generating avatar for SecretDetail with ID: %s", id);
        return IdUtils.makeCircleImageFromId(id);
    }


    /**
     * Checks if a SecretDetail with the given ID already exists in the database.
     * 
     * @param id The ID to check
     * @return true if the key exists, false otherwise
     */
    public boolean checkIfExisted(String id) {
        return secretDetailDB.get(id) != null;
    }

    /**
     * Utility method to save a SecretDetail, commit, show a toast, set result, and finish the activity.
     */
    public static void saveAndFinish(Activity activity, SecretDetail secretDetail) {
        SecretManager secretManager = SecretManager.getInstance(activity);
        secretManager.addSecretDetail(secretDetail);
        secretManager.commit();
        Toast.makeText(activity, com.fc.safe.R.string.secret_saved_successfully, Toast.LENGTH_SHORT).show();
        activity.setResult(Activity.RESULT_OK);
        activity.finish();
    }

    public static void saveAndFinish(Activity activity, List<SecretDetail> secretDetails) {
        SecretManager secretManager = SecretManager.getInstance(activity);
        byte[] symkey = ConfigureManager.getInstance().getSymkey();

        processSecretSequentially(activity, secretManager, secretDetails, symkey, 0,0);

//        for(SecretDetail secretDetail : secretDetails) {
//            if(secretDetail.getContent()!=null){
//                String cipher = Encryptor.encryptBySymkeyToJson(secretDetail.getContent().getBytes(), symkey);
//                secretDetail.setContentCipher(cipher);
//                secretDetail.setContent(null);
//            }
//            secretManager.addSecretDetail(secretDetail);
//            secretManager.commit();
//        }
//        Toast.makeText(activity, activity.getString(R.string.secrets_saved_successfully, secretDetails.size()), Toast.LENGTH_SHORT).show();
//        activity.setResult(Activity.RESULT_OK);
//        activity.finish();
    }


    private static void processSecretSequentially(Activity activity, SecretManager secretManager, List<SecretDetail> secretList, byte[] symkey, int index, int savedCount) {
        if (index >= secretList.size()) {
            secretManager.commit();
            Toast.makeText(activity, activity.getString(R.string.secrets_saved_successfully, savedCount), Toast.LENGTH_SHORT).show();
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
            return;
        }
        SecretDetail secretDetail = secretList.get(index);

        if(secretDetail.getContent()!=null){
            String cipher = Encryptor.encryptBySymkeyToJson(secretDetail.getContent().getBytes(), symkey);
            secretDetail.setContentCipher(cipher);
            secretDetail.setContent(null);
        }
        if(secretDetail.getId()==null)secretDetail.makeId();
        if (secretManager.checkIfExisted(secretDetail.getId())) {
            String prompt = secretDetail.getTitle() + " existed. Replace it?";
            UserConfirmDialog dialog = new UserConfirmDialog(activity, prompt, choice -> {
                if (choice == UserConfirmDialog.Choice.YES) {
                    secretManager.addSecretDetail(secretDetail);
                    processSecretSequentially(activity, secretManager, secretList, symkey, index + 1,savedCount+1);
                } else if (choice == UserConfirmDialog.Choice.NO) {
                    processSecretSequentially(activity, secretManager, secretList, symkey, index +1,savedCount);
                } else if (choice == UserConfirmDialog.Choice.STOP) {
                    secretManager.commit();
                    Toast.makeText(activity, activity.getString(R.string.secrets_saved_successfully, savedCount), Toast.LENGTH_SHORT).show();
                    activity.setResult(Activity.RESULT_OK);
                    activity.finish();
                }
            });
            dialog.show();
        } else {
            secretManager.addSecretDetail(secretDetail);
            processSecretSequentially(activity, secretManager, secretList, symkey, index + 1,savedCount+1);
        }
    }
} 