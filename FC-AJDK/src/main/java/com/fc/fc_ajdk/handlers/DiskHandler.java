package com.fc.fc_ajdk.handlers;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.fc.fc_ajdk.constants.FieldNames;
import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.data.fcData.FcObject;
import com.fc.fc_ajdk.data.fcData.Hat;

import org.jetbrains.annotations.NotNull;

import com.fc.fc_ajdk.utils.IdNameUtils;
import com.fc.fc_ajdk.utils.FileUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.TimberLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

import com.fc.fc_ajdk.config.Settings;

public class DiskHandler extends Handler<FcObject> {
    private final String storageDir;

    public DiskHandler(String fid,String oid) {
        this.storageDir = IdNameUtils.makeKeyName(fid, oid, "DISK", true);
    }
    public DiskHandler(Settings settings){
        super(settings,HandlerType.DISK);
        this.storageDir = IdNameUtils.makeKeyName(settings.getMainFid(), settings.getSid(), "DISK", true);
    }

    public static String getDiskDataDir(Settings settings) {
        return IdNameUtils.makeKeyName(settings.getMainFid(), settings.getSid(), "DISK", true);
    }

    @NotNull
    public static String makeDataPath(String did, Settings settings) {
        String subDir = getSubPathForDisk(did);
        return settings.getSettingMap().get(FieldNames.DISK_DIR) + subDir;
    }

    public static String getSubPathForDisk(String did) {
        return "/"+did.substring(0,2)+"/"+did.substring(2,4)+"/"+did.substring(4,6)+"/"+did.substring(6,8);
    }

    public static Boolean checkFileOfDisk(String path, String did) {
        File file = new File(path,did);
        if(!file.exists())return false;

        String existDid;
        try {
            existDid = Hash.sha256x2(file);
        } catch (IOException e) {
            System.out.println("Failed to make sha256 of file "+did);
            return null;
        }

        return did.equals(existDid);
    }

    /**
     * Save byte array as a file using SHA256x2 hash as filename
     * @param bytes data to save
     * @return DID (SHA256x2 hash) of the saved file, or null if operation fails
     */
    public String put(byte[] bytes) {
        if (bytes == null) return null;
        String fullPath =null;
        String did = Hex.toHex(Hash.sha256x2(bytes));
        String subDir = getSubPathForDisk(did);
        fullPath = storageDir + subDir;
        
        File file = new File(fullPath, did);
        if (file.exists()) {
            if (Boolean.TRUE.equals(checkFileOfDisk(fullPath, did))) {
                return fullPath;
            }
        }

        if (FileUtils.createFileDirectories(fullPath)) {
            try {
                Files.write(file.toPath(), bytes);
                return fullPath;
            } catch (IOException e) {
                TimberLogger.e("Failed to write file: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    @NotNull
    public Hat put(InputStream inputStream){
        String diskDataDir = DiskHandler.getDiskDataDir(settings);
        Hat hat = new Hat();

        String tempFileName = FileUtils.getTempFileName();
        try (OutputStream outputStream = new FileOutputStream(tempFileName)) {

            HashFunction hashFunction = Hashing.sha256();
            Hasher hasher = hashFunction.newHasher();
            // Adjust buffer size as per your requirement
            byte[] buffer = new byte[8192];
            int bytesRead;
            long bytesLength = 0;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                // Write the bytes read from the request input stream to the output stream
                outputStream.write(buffer, 0, bytesRead);
                hasher.putBytes(buffer, 0, bytesRead);
                bytesLength +=bytesRead;
            }

            String did = Hex.toHex(Hash.sha256(hasher.hash().asBytes()));
            String subDir = getSubPathForDisk(did);
            String path = diskDataDir +subDir;

            hat.setId(did);
            hat.setLocas(new ArrayList<>());
            hat.getLocas().add(path);

            File file = new File(path,did);
            if(!file.exists() || Boolean.FALSE.equals(checkFileOfDisk(path, did))) {
                try {
                    Path source = Paths.get(tempFileName);
                    Path target = Paths.get(path, did);
                    Files.createDirectories(target.getParent());
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.err.println("Error moving file: " + e.getMessage());
                }
            }
            hat.setSize(bytesLength);
        } catch (IOException e) {
            TimberLogger.e("Failed to save file with a inputStream.");
        }
        return hat;
    }

    /**
     * Read file content as byte array
     * @param did SHA256x2 hash of the file to read
     * @return byte array of file content, or null if file doesn't exist or operation fails
     */
    public byte[] getBytes(String did) {
        if (did == null) return null;

        String subDir = getSubPathForDisk(did);
        Path filePath = Paths.get(storageDir + subDir, did);
        
        if (!Files.exists(filePath)) {
            return null;
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String checkDid = Hex.toHex(Hash.sha256x2(bytes));
            if (did.equals(checkDid)) {
                return bytes;
            } else {
                TimberLogger.e("File content hash mismatch");
                return null;
            }
        } catch (IOException e) {
            TimberLogger.e("Failed to read file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Delete file by its DID
     * @param did SHA256x2 hash of the file to delete
     * @return true if deletion successful, false otherwise
     */
    public boolean delete(String did) {
        if (did == null) return false;

        String subDir = getSubPathForDisk(did);
        Path filePath = Paths.get(storageDir + subDir, did);
        
        try {
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            TimberLogger.e("Failed to delete file: " + e.getMessage());
            return false;
        }
    }

    public String getDataPath(String did,Settings settings){
        String subDir = getSubPathForDisk(did);
        return settings.getSettingMap().get(FieldNames.DISK_DIR) + subDir;
    }

    public String getDataDir() {
        return this.storageDir;
    }
}