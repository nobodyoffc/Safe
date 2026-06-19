package com.fc.safe.db;

import static com.fc.fc_ajdk.data.feipData.Secret.Type.TOTP;

import android.content.Context;
import android.app.Activity;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.KeyInfo;
import com.fc.fc_ajdk.data.feipData.Secret;
import com.fc.fc_ajdk.utils.Base32;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.DateUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.fc.safe.R;
import com.fc.safe.initiate.ConfigureManager;
import com.fc.safe.ui.UserConfirmDialog;
import com.fc.safe.utils.IdUtils; // Added for avatar generation
import com.fc.safe.utils.ToastUtils;

import java.io.IOException; // Added for avatar generation method
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A singleton class to manage and share the Secret database across activities.
 * This provides a centralized way to access the Secret database from any activity.
 */
public class SecretManager {
    private static final String TAG = "SecretManager"; // Updated TAG

    private static SecretManager instance;
    private LocalDB<Secret> secretDetailDB; // Renamed and type updated

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

    public static synchronized void reset() {
        if (instance != null) {
            if (instance.secretDetailDB != null) {
                try {
                    instance.secretDetailDB.close();
                } catch (Exception e) {
                    TimberLogger.e(TAG, "Error closing database on reset: " + e.getMessage());
                }
                instance.secretDetailDB = null;
            }
            instance = null;
            TimberLogger.d(TAG, "SecretManager instance reset");
        }
    }

    /**
     * Initializes the SecretManager with the given context.
     * 
     * @param context The context to use for getting the DatabaseManager
     */
    public void initialize(Context context) {
        DatabaseManager dbManager = DatabaseManager.getInstance(context);
        if (secretDetailDB != null) {
            secretDetailDB.close();
        }
        secretDetailDB = dbManager.getEntityDatabase(Secret.class);
        preprocessDB();
    }

    protected void preprocessDB() {
        String defaultSecretId = "defaultSecret";
        if(checkIfExisted(defaultSecretId))return;

        Secret initialSecret = new Secret();
        initialSecret.setOnChain(false);
        byte[] randomBytes = BytesUtils.getRandomBytes(16);
        String base32 = Base32.toBase32(randomBytes);
        initialSecret.setContent(base32);
        initialSecret.setTitle("Sample: My TOTP");
        initialSecret.setType(TOTP.displayName);
        initialSecret.setLastHeight(999999999L);
        initialSecret.setSaveTime(DateUtils.longToTime(System.currentTimeMillis(),DateUtils.TO_MINUTE));
        byte[] symkey = ConfigureManager.getInstance().getSymkey();
            if(symkey!=null){
                CryptoDataByte result = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptBySymkey(initialSecret.getContent().getBytes(),symkey);
                if(result!=null && result.getCode()==0 && result.getCipher()!=null){
                    initialSecret.setContentCipher(result.toJson());
                    initialSecret.setContent(null);
                }
            }
        initialSecret.setId(defaultSecretId);
        secretDetailDB.put(initialSecret.getId(),initialSecret);
    }

    /**
     * Gets the Secret database.
     * 
     * @return The Secret database
     */
    public LocalDB<Secret> getSecretDetailDB() { // Renamed method
        return secretDetailDB;
    }

    /**
     * Gets all Secret objects from the database.
     * 
     * @return A map of Secret objects with their IDs as keys
     */
    public Map<String, Secret> getAllSecretDetails() { // Renamed method and updated type
        if(secretDetailDB==null)return new HashMap<>();
        return secretDetailDB.getAll();
    }

    /**
     * Gets a list of all Secret objects from the database.
     * 
     * @return A list of all Secret objects
     */
    public List<Secret> getAllSecretDetailList() { // Renamed method and updated type
        if(secretDetailDB==null)return new ArrayList<>();
        Map<String, Secret> all = secretDetailDB.getAll();
        if(all==null || all.isEmpty())return new ArrayList<>();
        return new ArrayList<>(all.values());
    }

    /**
     * Gets a Secret object by its ID.
     * 
     * @param id The ID of the Secret object to get
     * @return The Secret object, or null if not found
     */
    public Secret getSecretDetailById(String id) { // Renamed method and updated type
        return secretDetailDB.get(id);
    }

    /**
     * Adds a Secret object to the database.
     * 
     * @param secret The Secret object to add
     */
    public void addSecret(Secret secret) {
        secret.setSaveTime(System.currentTimeMillis());
        secret.checkIdWithCreate();
        secretDetailDB.put(secret.getId(), secret);
    }

    public void addAllSecretDetail(List<Secret> secretList) {
        Map<String, Secret> map = new HashMap<>();
        for(Secret secret : secretList) {
            if(secret.getSaveTime()==null)
                secret.setSaveTime(System.currentTimeMillis());
            secret.checkIdWithCreate();
            map.put(secret.getId(), secret);
        }
        secretDetailDB.put(map);
    }

    /**
     * Removes a Secret object from the database.
     * 
     * @param secret The Secret object to remove
     */
    public void removeSecretDetail(Secret secret) { // Renamed method and updated type
        secretDetailDB.remove(secret.getId());
    }

    /**
     * Removes multiple Secret objects from the database.
     * 
     * @param secrets The list of Secret objects to remove
     */
    public void removeSecretDetails(List<Secret> secrets) { // Renamed method and updated type
        secretDetailDB.remove(secrets);
    }

    /**
     * Commits changes to the database.
     */
    public void commit() {
        secretDetailDB.commit();
    }

    /**
     * Gets a paginated list of Secret objects.
     * 
     * @param pageSize The number of items per page
     * @param lastIndex The index of the last item from the previous page, or null for the first page
     * @param descending Whether to sort in descending order
     * @return A list of Secret objects for the requested page
     */
    public List<Secret> getPaginatedSecretDetails(long pageSize, Long lastIndex, boolean descending) { // Renamed method and updated type
        return secretDetailDB.getList(pageSize, null, lastIndex, true, null, null, false, descending);
    }

    /**
     * Gets the index (0-based position) of a Secret object by its ID.
     *
     * @param id The ID of the Secret object
     * @return The index of the Secret object, or -1 if not found
     */
    public Long getIndexById(String id) {
        return secretDetailDB.getIndexById(id);
    }

    /**
     * Generates an avatar for the given entity ID.
     * It attempts to use the ID from the corresponding Secret object,
     * calling checkIdWithCreate if the Secret's ID is initially null or empty.
     * If the Secret object is not found, it uses the provided entityId directly.
     * Avatars are generated on the fly and not stored in the database.
     *
     * @param entityId The ID of the entity for which to generate an avatar.
     * @param context  The context.
     * @return Byte array of the generated avatar image, or null if generation fails.
     * @throws IOException If an I/O error occurs during avatar generation.
     */
    public byte[] generateAvatarById(String entityId, Context context) throws IOException {
        if (entityId == null || entityId.isEmpty()) {
            return null;
        }
        
        Secret secret = getSecretDetailById(entityId);
        String idForAvatar = secret != null ? secret.getId() : entityId;

        if (idForAvatar == null || idForAvatar.isEmpty()) {
            return null;
        }
        
        return IdUtils.makeCircleImageFromId(idForAvatar);
    }
    
    /**
     * Generates an avatar for a given Secret object.
     * This method ensures the Secret's ID is set (using checkIdWithCreate if necessary)
     * before generating the avatar. Avatars are not stored in the database.
     *
     * @param secret The Secret object.
     * @param context The context.
     * @return Byte array of the generated avatar image, or null if generation fails or input is null.
     * @throws IOException If an I/O error occurs.
     */
    public byte[] generateAvatarForSecret(Secret secret, Context context) throws IOException {
        if (secret == null) {
            return null;
        }
        String id = secret.getId();
        if (id == null || id.isEmpty()) {
            id = secret.checkIdWithCreate();
        }

        if (id == null || id.isEmpty()) {
            return null; 
        }
        return IdUtils.makeCircleImageFromId(id);
    }


    /**
     * Checks if a Secret with the given ID already exists in the database.
     * 
     * @param id The ID to check
     * @return true if the key exists, false otherwise
     */
    public boolean checkIfExisted(String id) {
        return secretDetailDB.get(id) != null;
    }

    /**
     * Utility method to save a Secret, commit, show a toast, set result, and finish the activity.
     */
    public static void saveAndFinish(Activity activity, Secret secret) {
        SecretManager secretManager = SecretManager.getInstance(activity);
        secretManager.addSecret(secret);
        secretManager.commit();
        ToastUtils.showInfo(activity, activity.getString(com.fc.safe.R.string.secret_saved_successfully));
        activity.setResult(Activity.RESULT_OK);
        activity.finish();
    }

    public static void saveAndFinish(Activity activity, List<Secret> secrets) {
        SecretManager secretManager = SecretManager.getInstance(activity);
        byte[] symkey = ConfigureManager.getInstance().getSymkey();

        processSecretSequentially(activity, secretManager, secrets, symkey, 0,0);
    }


    private static void processSecretSequentially(Activity activity, SecretManager secretManager, List<Secret> secretList, byte[] symkey, int index, int savedCount) {
        if (index >= secretList.size()) {
            secretManager.commit();
            ToastUtils.showInfo(activity, activity.getString(R.string.secrets_saved_successfully, savedCount));
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
            return;
        }
        Secret secret = secretList.get(index);

        // Decrypt detailCipher if content is null but detailCipher and owner exist
        if (secret.getContent() == null && secret.getDetailCipher() != null && secret.getOwner() != null) {
            try {
                KeyInfoManager keyInfoManager = KeyInfoManager.getInstance(activity);
                KeyInfo keyInfo = keyInfoManager.getKeyInfoById(secret.getOwner());

                if(keyInfo==null){
                    ToastUtils.showError(activity, activity.getString(R.string.key_not_found));
                    return;
                }

                byte[] prikey = null;

                // Get private key from KeyInfo
                if (keyInfo.getPrikeyBytes() != null) {
                    prikey = keyInfo.getPrikeyBytes();
                } else if (keyInfo.getPrikeyCipher() != null) {
                    // Decrypt private key using symkey
                    prikey = Decryptor.decryptPrikey(keyInfo.getPrikeyCipher(),symkey);
                } else if (keyInfo.getPrikey() != null) {
                    prikey = KeyTools.getPrikey32(keyInfo.getPrikey());
                }else {
                    ToastUtils.showError(activity, activity.getString(R.string.key_not_found));
                    return;
                }

                if (prikey != null) {
                    // Decrypt the detailCipher using the private key
                    CryptoDataByte cryptoDataByte = Decryptor.decryptTry(secret.getDetailCipher(), prikey);
                    if(cryptoDataByte!=null && cryptoDataByte.getCode()==0) {
                        String json = new String(cryptoDataByte.getData());
                        // Update secret with decrypted content
                        Secret decryptedSecret = Secret.fromJson(json, secret.getClass());
                        if(decryptedSecret !=null) {
                            secret.setContent(decryptedSecret.getContent());
                            secret.setTitle(decryptedSecret.getTitle());
                            secret.setType(decryptedSecret.getType());
                            secret.setMemo(decryptedSecret.getMemo());
                            secret.setDetailCipher(null); // Clear cipher after successful decryption
                        }
                    }
                }

            } catch (Exception e) {
                TimberLogger.e(TAG, "Failed to decrypt detailCipher: " + e.getMessage());
            }
        }

        if(secret.getContent()!=null){
            String cipher = Encryptor.encryptBySymkeyToJson(secret.getContent().getBytes(), symkey);
            secret.setContentCipher(cipher);
            secret.setContent(null);
        }
        if(secret.getId()==null) secret.makeId();
        if (secretManager.checkIfExisted(secret.getId())) {
            String prompt = secret.getTitle() + " existed. Replace it?";
            UserConfirmDialog dialog = new UserConfirmDialog(activity, prompt, choice -> {
                if (choice == UserConfirmDialog.Choice.YES) {
                    secretManager.addSecret(secret);
                    processSecretSequentially(activity, secretManager, secretList, symkey, index + 1,savedCount+1);
                } else if (choice == UserConfirmDialog.Choice.NO) {
                    processSecretSequentially(activity, secretManager, secretList, symkey, index +1,savedCount);
                } else if (choice == UserConfirmDialog.Choice.STOP) {
                    secretManager.commit();
                    ToastUtils.showInfo(activity, activity.getString(R.string.secrets_saved_successfully, savedCount));
                    activity.setResult(Activity.RESULT_OK);
                    activity.finish();
                }
            });
            dialog.show();
        } else {
            secretManager.addSecret(secret);
            processSecretSequentially(activity, secretManager, secretList, symkey, index + 1,savedCount+1);
        }
    }
} 