package com.fc.fc_ajdk.data.fchData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.TimberLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptOpCodes;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * P2SH (Pay-to-Script-Hash) transaction details
 * Supports CLTV (CheckLockTimeVerify), Multisig, and combined CLTV+Multisig
 * Used to mark time-locked UTXO information in opReturn
 *
 * Extends FcEntity to use hash160Hex as the id field for automatic serialization
 */
public class P2SH extends FcEntity {

    public static final String TAG = "P2SH";
    private P2shType type;
    private String redeemScript; // Redeem script in hex format (for signing)

    // Additional fields to store script parameters (for rebuilding)
    private Long lockTime;
    private String pubkeyHash;
    private List<String> pubkeys;
    private Integer m;
    private Integer n;
    private String fid;

    private Long birthHeight;
    private Long birthTime;
    private String birthTxId;

    public P2SH() {
    }

    /**
     * Create single-sig CLTV P2SH
     * @param fid Address that can spend after lockTime
     * @param lockTime Unix timestamp when funds become spendable
     */
    public P2SH(String fid, Long lockTime) {
        if (lockTime != null && fid != null){
            this.fid = fid;
            this.lockTime = lockTime;

            Script script = lockTimeRedeemScript(lockTime, KeyTools.addrToHash160(fid));
            this.redeemScript = Hex.toHex(script.getProgram()); // Hex format (for signing)

            // Set id as hash160Hex of the redeemScript
            byte[] hash160 = Hash.sha256hash160(script.getProgram());
            this.id = Hex.toHex(hash160);
            this.type = P2shType.CLTV;
        }
    }

    /**
     * Create multisig CLTV P2SH
     * @param pubkeys List of public keys in hex format
     * @param m Minimum number of signatures required
     * @param n Total number of public keys
     * @param lockTime Unix timestamp when funds become spendable (null for no time lock)
     */
    public P2SH(List<String> pubkeys, int m, int n, Long lockTime) {
        if (pubkeys == null || pubkeys.isEmpty()) {
            throw new IllegalArgumentException("Public keys list cannot be null or empty");
        }

        this.lockTime = lockTime;
        this.pubkeys = pubkeys;
        this.m = m;
        this.n = n;


        if (lockTime != null && lockTime > 0) {
            this.type = P2shType.MULTISIG_CLTV;
            Script script = makeMultisigLockTimeRedeemScript(lockTime, pubkeys, m, n);
            this.redeemScript = Hex.toHex(script.getProgram()); // Hex format (for signing)

            // Set id as hash160Hex of the full CLTV+multisig redeemScript
            byte[] hash160 = Hash.sha256hash160(script.getProgram());
            this.id = Hex.toHex(hash160);

            // Generate FID from the multisig portion (without CLTV) for multisig+CLTV
            Script multisigScript = makeMultisigRedeemScript(pubkeys, m, n);
            byte[] multisigHash160 = Hash.sha256hash160(multisigScript.getProgram());
            this.fid = KeyTools.hash160ToMultiAddr(multisigHash160);
        } else {
            this.type = P2shType.MULTISIG;
            Script script = makeMultisigRedeemScript(pubkeys, m, n);
            this.redeemScript = Hex.toHex(script.getProgram()); // Hex format (for signing)

            // Set id as hash160Hex of the multisig redeemScript
            byte[] hash160 = Hash.sha256hash160(script.getProgram());
            this.id = Hex.toHex(hash160);

            // Generate FID from the multisig redeem script hash
            byte[] multisigHash160 = Hash.sha256hash160(script.getProgram());
            this.fid = KeyTools.hash160ToMultiAddr(multisigHash160);
        }
    }

    /**
     * Create single-sig CLTV redeemScript
     * Format: <lockTime> OP_CLTV OP_DROP OP_DUP OP_HASH160 <pubkeyHash> OP_EQUALVERIFY OP_CHECKSIG
     */
    public static Script lockTimeRedeemScript(long lockUntil, byte[] pubkeyHash) {
        ScriptBuilder builder = new ScriptBuilder();

        builder.number(lockUntil)
                .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY)
                .op(ScriptOpCodes.OP_DROP);

        builder.op(ScriptOpCodes.OP_DUP)
                .op(ScriptOpCodes.OP_HASH160)
                .data(pubkeyHash)
                .op(ScriptOpCodes.OP_EQUALVERIFY)
                .op(ScriptOpCodes.OP_CHECKSIG);

        return builder.build();
    }

    /**
     * Create multisig redeemScript (no time lock)
     * Format: <m> <pubkey1> <pubkey2> ... <pubkeyN> <n> OP_CHECKMULTISIG
     */
    public static Script makeMultisigRedeemScript(List<String> pubkeyHexList, int m, int n) {
        ScriptBuilder builder = new ScriptBuilder();

        builder.smallNum(m);
        for (String pubkeyHex : pubkeyHexList) {
            builder.data(Hex.fromHex(pubkeyHex));
        }
        builder.smallNum(n);
        builder.op(ScriptOpCodes.OP_CHECKMULTISIG);

        return builder.build();
    }

    /**
     * Create multisig + CLTV redeemScript
     * Format: <lockTime> OP_CLTV OP_DROP <m> <pubkey1> <pubkey2> ... <pubkeyN> <n> OP_CHECKMULTISIG
     * This means: First the time lock must be satisfied, then m-of-n signatures are required
     */
    public static Script makeMultisigLockTimeRedeemScript(long lockUntil, List<String> pubkeyHexList, int m, int n) {
        ScriptBuilder builder = new ScriptBuilder();

        // CLTV part: time lock validation
        builder.number(lockUntil)
               .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY)
               .op(ScriptOpCodes.OP_DROP);

        // Multisig part: m-of-n signature requirement
        builder.smallNum(m);
        for (String pubkeyHex : pubkeyHexList) {
            builder.data(Hex.fromHex(pubkeyHex));
        }
        builder.smallNum(n);
        builder.op(ScriptOpCodes.OP_CHECKMULTISIG);

        return builder.build();
    }


    /**
     * Create P2SH from existing redeemScript hex
     * Automatically detects the type (MULTISIG, CLTV, or MULTISIG_CLTV) and extracts parameters
     *
     * @param redeemScript The redeemScript in hex format
     * @throws IllegalArgumentException if redeemScript is invalid or hash160 doesn't match
     */
    public P2SH(String redeemScript) {
        if (redeemScript == null || redeemScript.isEmpty()) {
            throw new IllegalArgumentException("RedeemScript cannot be null or empty");
        }

        if (!Hex.isHexString(redeemScript)) {
            throw new IllegalArgumentException("RedeemScript is not valid hex");
        }

        this.redeemScript = redeemScript;

        // Calculate and validate hash160
        byte[] redeemScriptBytes = Hex.fromHex(redeemScript);
        byte[] calculatedHash160 = Hash.sha256hash160(redeemScriptBytes);


        this.id = Hex.toHex(calculatedHash160);

        try {
            // Parse the redeemScript to extract type and parameters
            Script script = new Script(redeemScriptBytes);
            List<ScriptChunk> chunks = script.getChunks();

            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("RedeemScript has no chunks");
            }

            // Check for CLTV prefix
            int startIdx = 0;
            boolean hasCLTV = false;

            if (isCltvPrefix(chunks)) {
                hasCLTV = true;
                this.lockTime = lockTimeFromChunk(chunks.get(0));
                startIdx = 3; // Skip lockTime, CLTV, DROP
            }

            // Parse the remaining script (either P2PKH or multisig)
            if (startIdx >= chunks.size()) {
                throw new IllegalArgumentException("No script body after CLTV prefix");
            }

            ScriptChunk nextChunk = chunks.get(startIdx);

            // Check if it's single-sig P2PKH pattern (OP_DUP)
            if (nextChunk.opcode == ScriptOpCodes.OP_DUP) {
                // Single-sig CLTV: DUP HASH160 <pubkeyHash> EQUALVERIFY CHECKSIG
                this.type = P2shType.CLTV;

                if (chunks.size() < startIdx + 5) {
                    throw new IllegalArgumentException("Incomplete single-sig CLTV script");
                }

                ScriptChunk pubkeyHashChunk = chunks.get(startIdx + 2);
                if (pubkeyHashChunk.data != null && pubkeyHashChunk.data.length == 20) {
                    this.pubkeyHash = Hex.toHex(pubkeyHashChunk.data);
                    this.fid = KeyTools.hash160ToFchAddr(pubkeyHashChunk.data);
                }
            } else {
                // Multisig pattern: <m> <pubkey1> ... <pubkeyN> <n> OP_CHECKMULTISIG
                // Extract m value
                this.m = decodeSmallNum(nextChunk);

                // Extract n from second-to-last chunk
                ScriptChunk nChunk = chunks.get(chunks.size() - 2);
                this.n = decodeSmallNum(nChunk);

                // Extract pubkeys (between m and n)
                this.pubkeys = new ArrayList<>();
                for (int i = startIdx + 1; i < chunks.size() - 2; i++) {
                    ScriptChunk pubkeyChunk = chunks.get(i);
                    if (pubkeyChunk.data != null) {
                        this.pubkeys.add(Hex.toHex(pubkeyChunk.data));
                    }
                }

                // Set type based on whether CLTV is present
                if (hasCLTV) {
                    this.type = P2shType.MULTISIG_CLTV;
                    // Generate FID from the multisig portion (without CLTV)
                    Script multisigScript = makeMultisigRedeemScript(this.pubkeys, this.m, this.n);
                    byte[] multisigHash160 = Hash.sha256hash160(multisigScript.getProgram());
                    this.fid = KeyTools.hash160ToMultiAddr(multisigHash160);
                } else {
                    this.type = P2shType.MULTISIG;
                    // FID is from the multisig script itself
                    this.fid = KeyTools.hash160ToMultiAddr(calculatedHash160);
                }
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse redeemScript: " + e.getMessage(), e);
        }
    }

    /**
     * Create a map of hash160Hex to redeemScriptHex for opReturn
     * This is the NEW format that eliminates duplicates for outputs to the same P2SH address
     *
     * @param p2SHOutputs List of P2SH outputs to include in the map
     * @return JSON string representing the hash160->redeemScript map
     */
    @NonNull
    public static String makeRedeemScriptListJsonForOpReturn(List<P2SH> p2SHOutputs) {
        List<String> hash160ToRedeemScript = new ArrayList<>();

        for (P2SH p2sh : p2SHOutputs) {
            if (p2sh.getRedeemScript() == null || p2sh.getRedeemScript().isEmpty()) {
                TimberLogger.w(TAG, "Skipping P2SH with null/empty redeemScript");
                continue;
            }

            // Validate redeemScript syntax strictly
            if (!validateRedeemScriptSyntax(p2sh.getRedeemScript())) {
                throw new IllegalArgumentException("Invalid redeemScript syntax - script would be unspendable: "
                    + p2sh.getRedeemScript().substring(0, Math.min(40, p2sh.getRedeemScript().length())) + "...");
            }

            // Calculate hash160 of the redeemScript
            byte[] redeemScriptBytes = Hex.fromHex(p2sh.getRedeemScript());
            String scriptHex = Hex.toHex(redeemScriptBytes);

            // Only add if not already present (automatic de-duplication)
            if (!hash160ToRedeemScript.contains(scriptHex)) {
                hash160ToRedeemScript.add(p2sh.getRedeemScript());
                TimberLogger.d(TAG, "Added P2SH to map.");
            } else {
                TimberLogger.d(TAG, "Skipped duplicate P2SH");
            }
        }

        return new Gson().toJson(hash160ToRedeemScript);
    }

    /**
     * Parse P2SH map from OP_RETURN output (NEW format: hash160->redeemScript map)
     *
     * @param transaction The transaction to parse
     * @return Map of hash160Hex to redeemScriptHex if found, null otherwise
     */
    @Nullable
    public static Map<String, String> parseP2SHMapFromOpReturn(Transaction transaction) {
        try {
            List<TransactionOutput> outputs = transaction.getOutputs();
            for (TransactionOutput output : outputs) {
                Script script = output.getScriptPubKey();

                // Check if this is an OP_RETURN output
                if (script.isOpReturn()) {
                    // Get the data from OP_RETURN
                    byte[] opReturnData = script.getChunks().get(1).data;
                    if (opReturnData != null && opReturnData.length > 0) {
                        Map<String, String> p2shMap = parseP2SHMapFromOpReturn(opReturnData);
                        if (p2shMap != null) return p2shMap;
                    }
                }
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error parsing P2SH map from OP_RETURN: " + e.getMessage());
        }
        return null;
    }

    /**
     * Parse P2SH map from OP_RETURN data bytes (NEW format: hash160->redeemScript map)
     *
     * @param opReturnData The OP_RETURN data bytes
     * @return Map of hash160Hex to redeemScriptHex if valid, null otherwise
     */
    @Nullable
    public static Map<String, String> parseP2SHMapFromOpReturn(byte[] opReturnData) {
        try {
            // Try to parse as text
            String opReturnText = new String(opReturnData, StandardCharsets.UTF_8);

            // Check if it's a JSON object (map format)
            if (opReturnText.trim().startsWith("[")) {
                // Parse as scriptHexList
                Type type = new TypeToken<List<String>>(){}.getType();
                List<String> scriptHexList = new Gson().fromJson(opReturnText, type);

                if (scriptHexList == null || scriptHexList.isEmpty()) {
                    return null;
                }

                TimberLogger.d(TAG, "Successfully parsed P2SH scriptHexList with " + scriptHexList.size() + " entries");
                return scriptHexListToMap(scriptHexList);
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to parse P2SH map: " + e.getMessage());
        }
        return null;
    }

    @NonNull
    public static Map<String, String> scriptHexListToMap(List<String> scriptHexList) {
        Map<String, String> scriptHexMap = new HashMap<>();
        for (String scriptHex : scriptHexList){
            if (Hex.isHexString(scriptHex)) {
                byte[] scriptBytes = Hex.fromHex(scriptHex);
                byte[] calculatedHash = Hash.sha256hash160(scriptBytes);
                if(calculatedHash.length>0){
                    scriptHexMap.put(Hex.toHex(calculatedHash), scriptHex);
                }
            }
        }
        return scriptHexMap;
    }

    /**
     * Strictly validate redeemScript syntax for CLTV and multisig to ensure spendability
     * This prevents creating cash that cannot be spent due to malformed scripts
     *
     * @param redeemScriptHex The redeemScript in hex format
     * @return true if the script is valid and spendable, false otherwise
     */
    public static boolean validateRedeemScriptSyntax(String redeemScriptHex) {
        if (redeemScriptHex == null || redeemScriptHex.isEmpty()) {
            TimberLogger.e(TAG, "RedeemScript is null or empty");
            return false;
        }

        if (!Hex.isHexString(redeemScriptHex)) {
            TimberLogger.e(TAG, "RedeemScript is not valid hex");
            return false;
        }

        try {
            byte[] redeemScriptBytes = Hex.fromHex(redeemScriptHex);
            Script script = new Script(redeemScriptBytes);
            List<ScriptChunk> chunks = script.getChunks();

            if (chunks.isEmpty()) {
                TimberLogger.e(TAG, "RedeemScript has no chunks");
                return false;
            }

            // Determine script type and validate accordingly
            int startIdx = 0;
            boolean hasCLTV = false;

            // Check for CLTV prefix: <lockTime-push-or-OP_N> OP_CLTV OP_DROP
            if (isCltvPrefix(chunks)) {
                hasCLTV = true;
                startIdx = 3;
                Long lockTime = lockTimeFromChunk(chunks.get(0));
                if (lockTime == null || lockTime <= 0) {
                    TimberLogger.e(TAG, "Invalid lockTime value: " + lockTime);
                    return false;
                }
            }

            // Validate the remaining script (either single-sig P2PKH or multisig)
            if (startIdx >= chunks.size()) {
                TimberLogger.e(TAG, "No script body after CLTV prefix");
                return false;
            }

            ScriptChunk nextChunk = chunks.get(startIdx);

            // Check if it's single-sig P2PKH pattern
            if (nextChunk.opcode == ScriptOpCodes.OP_DUP) {
                // Validate single-sig CLTV: DUP HASH160 <pubkeyHash> EQUALVERIFY CHECKSIG
                if (chunks.size() < startIdx + 5) {
                    TimberLogger.e(TAG, "Incomplete single-sig CLTV script");
                    return false;
                }

                if (chunks.get(startIdx + 1).opcode != ScriptOpCodes.OP_HASH160) {
                    TimberLogger.e(TAG, "Missing OP_HASH160 in single-sig script");
                    return false;
                }

                ScriptChunk pubkeyHashChunk = chunks.get(startIdx + 2);
                if (pubkeyHashChunk.data == null || pubkeyHashChunk.data.length != 20) {
                    TimberLogger.e(TAG, "Invalid pubkeyHash length: " +
                        (pubkeyHashChunk.data != null ? pubkeyHashChunk.data.length : "null"));
                    return false;
                }

                if (chunks.get(startIdx + 3).opcode != ScriptOpCodes.OP_EQUALVERIFY) {
                    TimberLogger.e(TAG, "Missing OP_EQUALVERIFY in single-sig script");
                    return false;
                }

                if (chunks.get(startIdx + 4).opcode != ScriptOpCodes.OP_CHECKSIG) {
                    TimberLogger.e(TAG, "Missing OP_CHECKSIG in single-sig script");
                    return false;
                }

                TimberLogger.d(TAG, "Valid " + (hasCLTV ? "CLTV+" : "") + "single-sig redeemScript");
                return true;

            } else {
                // Validate multisig pattern: <m> <pubkey1> ... <pubkeyN> <n> OP_CHECKMULTISIG
                // Extract m value
                int m = decodeSmallNum(nextChunk);
                if (m <= 0 || m > 16) {
                    TimberLogger.e(TAG, "Invalid m value: " + m);
                    return false;
                }

                // Find n and OP_CHECKMULTISIG at the end
                if (chunks.size() < startIdx + 3) { // Minimum: m, 1 pubkey, n, CHECKMULTISIG
                    TimberLogger.e(TAG, "Incomplete multisig script");
                    return false;
                }

                ScriptChunk lastChunk = chunks.get(chunks.size() - 1);
                if (lastChunk.opcode != ScriptOpCodes.OP_CHECKMULTISIG) {
                    TimberLogger.e(TAG, "Missing OP_CHECKMULTISIG at end of multisig script");
                    return false;
                }

                ScriptChunk nChunk = chunks.get(chunks.size() - 2);
                int n = decodeSmallNum(nChunk);
                if (n <= 0 || n > 16 || m > n) {
                    TimberLogger.e(TAG, "Invalid n value: " + n + " (m=" + m + ")");
                    return false;
                }

                // Validate pubkeys count
                int expectedPubkeys = n;
                int actualPubkeys = chunks.size() - startIdx - 3; // Exclude m, n, CHECKMULTISIG

                if (actualPubkeys != expectedPubkeys) {
                    TimberLogger.e(TAG, "Pubkey count mismatch: expected " + expectedPubkeys +
                        ", got " + actualPubkeys);
                    return false;
                }

                // Validate each pubkey
                for (int i = startIdx + 1; i < chunks.size() - 2; i++) {
                    ScriptChunk pubkeyChunk = chunks.get(i);
                    if (pubkeyChunk.data == null || (pubkeyChunk.data.length != 33 && pubkeyChunk.data.length != 65)) {
                        TimberLogger.e(TAG, "Invalid pubkey length at index " + i + ": " +
                            (pubkeyChunk.data != null ? pubkeyChunk.data.length : "null"));
                        return false;
                    }
                }

                TimberLogger.d(TAG, "Valid " + (hasCLTV ? "CLTV+" : "") + "multisig redeemScript (m=" + m + ", n=" + n + ")");
                return true;
            }

        } catch (Exception e) {
            TimberLogger.e(TAG, "RedeemScript validation failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Decode small number from script chunk (OP_0 to OP_16)
     */
    private static int decodeSmallNum(ScriptChunk chunk) {
        // OP_1 = 81, OP_2 = 82, ..., OP_16 = 96
        if (chunk.opcode >= ScriptOpCodes.OP_1 && chunk.opcode <= ScriptOpCodes.OP_16) {
            return chunk.opcode - 80;
        }
        // OP_0 = 0
        if (chunk.opcode == ScriptOpCodes.OP_0) {
            return 0;
        }
        // If it's data, try to decode as number
        if (chunk.data != null && chunk.data.length == 1) {
            return chunk.data[0] & 0xFF;
        }
        return -1;
    }

    public enum P2shType {
        MULTISIG,
        CLTV,
        MULTISIG_CLTV  // Combined multisig + CLTV
    }

    public String getRedeemScript() {
        return redeemScript;
    }

    public void setRedeemScript(String redeemScript) {
        this.redeemScript = redeemScript;
    }

    /**
     * Get redeemScript as bytes (for actual transaction signing)
     */
    public byte[] getRedeemScriptBytes() {
        if (redeemScript != null) {
            return Hex.fromHex(redeemScript);
        }
        return null;
    }

    public P2shType getType() {
        return type;
    }

    public void setType(P2shType type) {
        this.type = type;
    }

    public Long getLockTime() {
        return lockTime;
    }

    public void setLockTime(Long lockTime) {
        this.lockTime = lockTime;
    }

    public List<String> getPubkeys() {
        return pubkeys;
    }

    public void setPubkeys(List<String> pubkeys) {
        this.pubkeys = pubkeys;
    }

    public Integer getM() {
        return m;
    }

    public void setM(Integer m) {
        this.m = m;
    }

    public Integer getN() {
        return n;
    }

    public void setN(Integer n) {
        this.n = n;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    /**
     * Convert to JSON string
     */
    public String toJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    /**
     * Convert to compact JSON string (no pretty printing, used for opReturn)
     */
    public String toCompactJson() {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(this);
    }

    /**
     * Calculate the size of P2SH opReturn for a list of CLTV outputs
     * This is used to predict the opReturn size before actually creating the outputs
     * @param existingP2SHCount Number of existing P2SH outputs already in the transaction
     * @param newP2SHCount Number of new P2SH outputs to be added
     * @param newP2SHs List of new P2SH objects to be added (for accurate size calculation)
     * @return The total opReturn size in bytes
     */
    public static int calculateP2SHOpReturnSize(int existingP2SHCount, int newP2SHCount, List<P2SH> newP2SHs) {
        if (existingP2SHCount == 0 && newP2SHCount == 0) {
            return 0;
        }

        int totalSize = 0;

        // Array brackets: [ and ]
        totalSize += 2;

        // Separators (commas) between existing and new outputs
        int totalP2SHCount = existingP2SHCount + newP2SHCount;
        if (totalP2SHCount > 1) {
            totalSize += (totalP2SHCount - 1); // commas
        }

        // Add size of each new P2SH JSON (compact format, no pretty printing)
        if (newP2SHs != null) {
            for (P2SH p2sh : newP2SHs) {
                String compactJson = p2sh.toCompactJson();
                totalSize += compactJson.length();
            }
        }

        return totalSize;
    }

    /**
     * Estimate the JSON size for a single P2SH entry (for quick calculation without creating object)
     * @param fid FID address (for single-sig CLTV)
     * @param lockTime Lock time value
     * @param isMultisig Whether this is multisig
     * @param pubkeyCount Number of pubkeys (for multisig)
     * @return Estimated JSON size in bytes
     */
    public static int estimateP2SHJsonSize(String fid, Long lockTime, boolean isMultisig, int pubkeyCount) {
        // Approximate JSON structure:
        // {"fid":"FPqZ...","lockTime":123456789}
        // or for multisig:
        // {"pubkeys":["03abcd...","03efgh..."],"m":2,"n":3,"lockTime":123456789}

        int size = 2; // {}

        if (isMultisig) {
            size += 12; // "pubkeys":[]
            size += pubkeyCount * 68; // Each pubkey: "03abcd...ef"(66) + ","(1) + quotes(2) ≈ 69, minus 1 for last
            size += 10; // ,"m":2,"n":3
            if (lockTime != null) {
                size += 20; // ,"lockTime":123456789
            }
        } else {
            size += 40; // "fid":"FPqZ...Abc"  (FID is ~34 chars)
            if (lockTime != null) {
                size += 25; // ,"lockTime":123456789
            }
        }

        return size;
    }

    /**
     * Create from JSON string
     */
    public static P2SH fromJson(String json) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(json, P2SH.class);
    }

    @Override
    public String toString() {
        return toJson();
    }

    public String getPubkeyHash() {
        return pubkeyHash;
    }

    public void setPubkeyHash(String pubkeyHash) {
        this.pubkeyHash = pubkeyHash;
    }

    /**
     * Convert script hex to ASM (human-readable) format
     * @param scriptHex Script in hexadecimal format
     * @return Script in ASM format (human-readable)
     */
    public static String scriptHexToAsm(String scriptHex) {
        if (scriptHex == null || scriptHex.isEmpty()) {
            return null;
        }
        try {
            byte[] scriptBytes = Hex.fromHex(scriptHex);
            Script script = new Script(scriptBytes);
            return script.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid script hex: " + e.getMessage(), e);
        }
    }

    /**
     * Convert script ASM to hex format
     * Note: This is a simplified conversion. For complex scripts, use ScriptBuilder directly.
     * @param scriptAsm Script in ASM format (human-readable)
     * @return Script in hexadecimal format
     */
    public static String scriptAsmToHex(String scriptAsm) {
        if (scriptAsm == null || scriptAsm.isEmpty()) {
            return null;
        }
        try {
            // Parse ASM and rebuild script
            Script script = parseAsmToScript(scriptAsm);
            return Hex.toHex(script.getProgram());
        } catch (Exception e) {
            TimberLogger.d("TxHandler",e.getMessage());
            throw new IllegalArgumentException("Invalid script ASM: " + e.getMessage(), e);
        }
    }

    /**
     * Parse ASM format string to Script object
     * This is a helper method for scriptAsmToHex
     */
    private static Script parseAsmToScript(String asm) {
        // Split by whitespace first to handle PUSHDATA format
        String[] parts = asm.trim().split("\\s+");

        ScriptBuilder builder = new ScriptBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            // Handle PUSHDATA(n)[hex] format from BitcoinJ's Script.toString()
            // Example: PUSHDATA(3)[6c7c2d] or PUSHDATA(20)[61c42abb...]
            // This is a single token containing both the size and the hex data
            if (part.startsWith("PUSHDATA(") && part.contains("[") && part.endsWith("]")) {
                // Extract hex data from PUSHDATA(n)[hex]
                int bracketStart = part.indexOf('[');
                int bracketEnd = part.indexOf(']');

                if (bracketStart != -1 && bracketEnd != -1 && bracketEnd > bracketStart) {
                    String hexData = part.substring(bracketStart + 1, bracketEnd);
                    try {
                        byte[] data = Hex.fromHex(hexData);
                        builder.data(data);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Invalid hex data in PUSHDATA: " + hexData);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid PUSHDATA format: " + part);
                }
            }
            // Check if it's an opcode (with or without OP_ prefix)
            else if (part.startsWith("OP_") || isOpcodeWithoutPrefix(part)) {
                // Try to get opcode, adding OP_ prefix if needed
                String opcodeName = part.startsWith("OP_") ? part : "OP_" + part;
                try {
                    int opcode = getOpcodeFromName(opcodeName);
                    builder.op(opcode);
                } catch (IllegalArgumentException e) {
                    // If it's not a recognized opcode, fall through to try as hex
                    try {
                        byte[] data = Hex.fromHex(part);
                        builder.data(data);
                    } catch (Exception hexError) {
                        throw new IllegalArgumentException("Unknown opcode or invalid hex: " + part);
                    }
                }
            }
            // Check if it's a number
            else if (part.matches("-?\\d+")) {
                long number = Long.parseLong(part);
                if (number >= 0 && number <= 16) {
                    builder.smallNum((int) number);
                } else {
                    builder.number(number);
                }
            }
            // Check if it's a standalone hex in brackets [hex] (shouldn't happen with PUSHDATA, but handle it)
            else if (part.startsWith("[") && part.endsWith("]")) {
                String hexData = part.substring(1, part.length() - 1);
                try {
                    byte[] data = Hex.fromHex(hexData);
                    builder.data(data);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid hex data in brackets: " + hexData);
                }
            }
            // Otherwise treat as raw hex data (for backward compatibility)
            else {
                try {
                    byte[] data = Hex.fromHex(part);
                    builder.data(data);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid hex data in ASM: " + part);
                }
            }
        }

        return builder.build();
    }

    /**
     * Check if a string is likely an opcode name without the OP_ prefix
     * BitcoinJ's Script.toString() sometimes omits the OP_ prefix
     */
    private static boolean isOpcodeWithoutPrefix(String part) {
        // Common opcodes that might appear without OP_ prefix
        switch (part) {
            case "DUP":
            case "HASH160":
            case "EQUALVERIFY":
            case "CHECKSIG":
            case "CHECKMULTISIG":
            case "CHECKLOCKTIMEVERIFY":
            case "DROP":
            case "EQUAL":
            case "VERIFY":
            case "RETURN":
            case "IF":
            case "ELSE":
            case "ENDIF":
            case "NOTIF":
            case "CHECKSIGVERIFY":
            case "CHECKMULTISIGVERIFY":
                return true;
            default:
                return false;
        }
    }

    /**
     * Check if the hash160 of the redeem script matches the provided hash160
     * This is used to verify P2SH addresses
     * @param redeemScript The redeem script as hex
     * @param lockScript The lock script
     * @return true if the hash160 of the redeem script matches the provided hash160
     */
    public static boolean checkRedeemScriptHash(String redeemScript, String lockScript) {
        if (!Hex.isHexString(redeemScript) || !Hex.isHexString(lockScript)) {
            return false;
        }
        byte[] redeemScriptBytes = Hex.fromHex(redeemScript);
        byte[] lockScriptBytes = Hex.fromHex(lockScript);

        return checkRedeemScriptHash(lockScriptBytes, redeemScriptBytes);
    }

    public static boolean checkRedeemScriptHash(byte[] lockScriptBytes, byte[] redeemScriptBytes) {
        byte[] hash160 = Arrays.copyOfRange(lockScriptBytes,2,22);

        // Calculate hash160 of the redeem script (SHA256 then RIPEMD160)
        byte[] calculatedHash = Hash.sha256hash160(redeemScriptBytes);

        // Compare the calculated hash with the provided hash
        if (calculatedHash.length != hash160.length) {
            return false;
        }

        for (int i = 0; i < calculatedHash.length; i++) {
            if (calculatedHash[i] != hash160[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extract the script hash (hash160) from a P2SH output script
     * P2SH script format: OP_HASH160 <20 bytes hash160> OP_EQUAL
     * @param scriptBytes The P2SH output script bytes
     * @return The 20-byte hash160, or null if not a valid P2SH script
     */
    public static byte[] extractP2SHScriptHash(byte[] scriptBytes) {
        // P2SH script format: OP_HASH160 (0xa9) <20 bytes> OP_EQUAL (0x87)
        // Total length should be 23 bytes: 1 + 1 + 20 + 1
        if (scriptBytes == null || scriptBytes.length != 23) {
            return null;
        }

        if (scriptBytes[0] != (byte) 0xa9 || // OP_HASH160
            scriptBytes[1] != 0x14 ||        // Push 20 bytes
            scriptBytes[22] != (byte) 0x87) { // OP_EQUAL
            return null;
        }

        // Extract the 20-byte hash160 (bytes 2-21)
        return Arrays.copyOfRange(scriptBytes, 2, 22);
    }

    /**
     * Extract lockTime value from a redeemScript hex string
     * @param redeemScriptHex The redeemScript in hex format
     * @return The lockTime value if present, null otherwise
     */
    public static Long extractLockTimeFromRedeemScript(String redeemScriptHex) {
        if (redeemScriptHex == null || redeemScriptHex.isEmpty()) {
            return null;
        }

        try {
            byte[] redeemScriptBytes = Hex.fromHex(redeemScriptHex);
            Script script = new Script(redeemScriptBytes);
            List<ScriptChunk> chunks = script.getChunks();

            if (chunks.isEmpty()) {
                return null;
            }

            if (isCltvPrefix(chunks)) {
                return lockTimeFromChunk(chunks.get(0));
            }

            return null;
        } catch (Exception e) {
            TimberLogger.e(TAG, "Error extracting lockTime from redeemScript: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get opcode value from opcode name
     */
    private static int getOpcodeFromName(String opcodeName) {
        switch (opcodeName) {
            case "OP_DUP": return ScriptOpCodes.OP_DUP;
            case "OP_HASH160": return ScriptOpCodes.OP_HASH160;
            case "OP_EQUALVERIFY": return ScriptOpCodes.OP_EQUALVERIFY;
            case "OP_CHECKSIG": return ScriptOpCodes.OP_CHECKSIG;
            case "OP_CHECKMULTISIG": return ScriptOpCodes.OP_CHECKMULTISIG;
            case "OP_CHECKLOCKTIMEVERIFY": return ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY;
            case "OP_DROP": return ScriptOpCodes.OP_DROP;
            case "OP_EQUAL": return ScriptOpCodes.OP_EQUAL;
            case "OP_VERIFY": return ScriptOpCodes.OP_VERIFY;
            case "OP_RETURN": return ScriptOpCodes.OP_RETURN;
            case "OP_IF": return ScriptOpCodes.OP_IF;
            case "OP_ELSE": return ScriptOpCodes.OP_ELSE;
            case "OP_ENDIF": return ScriptOpCodes.OP_ENDIF;
            case "OP_NOTIF": return ScriptOpCodes.OP_NOTIF;
            case "OP_CHECKSIGVERIFY": return ScriptOpCodes.OP_CHECKSIGVERIFY;
            case "OP_CHECKMULTISIGVERIFY": return ScriptOpCodes.OP_CHECKMULTISIGVERIFY;
            default:
                throw new IllegalArgumentException("Unknown opcode: " + opcodeName);
        }
    }

    /**
     * Parse P2SH list from OP_RETURN output
     * @param transaction The transaction to parse
     * @return List of P2SH objects if found, null otherwise
     */
    public static List<P2SH> parseP2SHListFromOpReturn(Transaction transaction) {
        try {
            List<TransactionOutput> outputs = transaction.getOutputs();
            for (TransactionOutput output : outputs) {
                Script script = output.getScriptPubKey();

                // Check if this is an OP_RETURN output
                if (script.isOpReturn()) {
                    // Get the data from OP_RETURN
                    byte[] opReturnData = script.getChunks().get(1).data;
                    if (opReturnData != null && opReturnData.length > 0) {
                        List<P2SH> p2SHList = parseP2SHListFromOpReturn(opReturnData);
                        if (p2SHList != null) return p2SHList;
                    }
                }
            }
        } catch (Exception e) {
            TimberLogger.e("TxSender", "Error parsing P2SH list from OP_RETURN: " + e.getMessage());
        }
        return null;
    }

    @Nullable
    public static List<P2SH> parseP2SHListFromOpReturn(byte[] opReturnData) {
        try {
            // Try to parse as text
            String opReturnText = new String(opReturnData, StandardCharsets.UTF_8);

            // Check if it's a JSON array
            if (!opReturnText.isEmpty() && opReturnText.trim().startsWith("[{")) {
                List<P2SH> p2SHList = JsonUtils.listFromJson(opReturnText, P2SH.class);
                if (!p2SHList.isEmpty()) {
                    TimberLogger.d("TxSender", "Found P2SH list with " + p2SHList.size() + " entries in OP_RETURN");
                    return p2SHList;
                }
            }
        } catch (Exception e) {
            // Not a valid P2SH JSON array, continue
            TimberLogger.d("TxSender", "OP_RETURN is not P2SH JSON array: " + e.getMessage());
        }
        return null;
    }


    /**
     * Validate redeemScript and add CLTV info to cash in one pass for better performance
     * This method combines validation, lockTime extraction, and cash property setting
     * to avoid parsing the same script multiple times.
     * @param cash The cash object to update
     * @param redeemScriptHex The redeem script in hex format
     * @return true if it's a valid CLTV script and cash was updated, false otherwise
     */
    public static boolean addCLTVInfoToCashWithValidation(Cash cash, String redeemScriptHex) {
        if (redeemScriptHex == null || redeemScriptHex.isEmpty()) {
            return false;
        }

        if (!Hex.isHexString(redeemScriptHex)) {
            return false;
        }

        try {
            // Parse script once
            byte[] redeemScriptBytes = Hex.fromHex(redeemScriptHex);
            Script script = new Script(redeemScriptBytes);
            List<ScriptChunk> chunks = script.getChunks();

            if (chunks.isEmpty()) {
                return false;
            }

            // Check for CLTV prefix: <lockTime-push-or-OP_N> OP_CLTV OP_DROP
            if (!isCltvPrefix(chunks)) {
                return false;
            }
            Long lockTimeBoxed = lockTimeFromChunk(chunks.get(0));
            if (lockTimeBoxed == null || lockTimeBoxed <= 0) {
                return false;
            }
            long lockTime = lockTimeBoxed;

            int startIdx = 3; // Skip lockTime, CLTV, DROP

            if (startIdx >= chunks.size()) {
                return false; // No script body after CLTV
            }

            ScriptChunk nextChunk = chunks.get(startIdx);

            // Validate and set cash properties based on script type
            if (nextChunk.opcode == 118) { // OP_DUP
                // Single-sig CLTV validation: DUP HASH160 <pubkeyHash> EQUALVERIFY CHECKSIG
                if (chunks.size() < startIdx + 5 ||
                        chunks.get(startIdx + 1).opcode != 169) { // OP_HASH160
                    return false;
                }

                ScriptChunk pubkeyHashChunk = chunks.get(startIdx + 2);
                if (pubkeyHashChunk.data == null || pubkeyHashChunk.data.length != 20) {
                    return false;
                }

                if (chunks.get(startIdx + 3).opcode != 136 || // OP_EQUALVERIFY
                        chunks.get(startIdx + 4).opcode != 172) { // OP_CHECKSIG
                    return false;
                }

                // Valid single-sig CLTV - set properties
                byte[] pubkeyHashBytes = pubkeyHashChunk.data;
                String fid = KeyTools.hash160ToFchAddr(pubkeyHashBytes);
                cash.setOwner(fid);
                cash.setLockTime(lockTime);
                cash.setType(Cash.CashType.P2SH_CLTV.getValue());
                return true;

            } else {
                // Multisig + CLTV validation: <m> <pubkey1> ... <pubkeyN> <n> CHECKMULTISIG
                int m = decodeSmallNum(nextChunk);
                if (m <= 0 || m > 16 || chunks.size() < startIdx + 3) {
                    return false;
                }

                ScriptChunk lastChunk = chunks.get(chunks.size() - 1);
                if (lastChunk.opcode != 174) { // OP_CHECKMULTISIG
                    return false;
                }

                ScriptChunk nChunk = chunks.get(chunks.size() - 2);
                int n = decodeSmallNum(nChunk);
                if (n <= 0 || n > 16 || m > n) {
                    return false;
                }

                // Validate pubkey count
                int actualPubkeys = chunks.size() - startIdx - 3; // Exclude m, n, CHECKMULTISIG
                if (actualPubkeys != n) {
                    return false;
                }

                // Validate each pubkey (33 bytes compressed or 65 bytes uncompressed)
                for (int i = startIdx + 1; i < chunks.size() - 2; i++) {
                    ScriptChunk pubkeyChunk = chunks.get(i);
                    if (pubkeyChunk.data == null ||
                            (pubkeyChunk.data.length != 33 && pubkeyChunk.data.length != 65)) {
                        return false;
                    }
                }

                // Valid multisig+CLTV - set properties
                byte[] multisigScript = extractMultisigScript(chunks, startIdx);
                if (multisigScript != null) {
                    byte[] multisigHash160 = Hash.sha256hash160(multisigScript);
                    String multisigFid = KeyTools.hash160ToMultiAddr(multisigHash160);
                    cash.setOwner(multisigFid);
                    cash.setLockTime(lockTime);
                    cash.setType(Cash.CashType.P2SH_MULTISIG_CLTV.getValue());
                    return true;
                }
                return false;
            }

        } catch (Exception e) {
            return false; // Silently fail on any parsing errors
        }
    }

//
//    public static void addCLTVInfoToCash(Cash cash, String redeemScriptHex) {
//        try {
//            // Parse P2SH script from hex format by converting to Script object
//            // Three possible formats:
//            // 1. Single-sig CLTV: <lockTime> CLTV DROP DUP HASH160 <pubkeyHash> EQUALVERIFY CHECKSIG
//            //    -> Set lockTime AND update owner to pubkeyHash
//            // 2. Multisig: <m> <pubkey1> ... <pubkeyN> <n> CHECKMULTISIG
//            //    -> Ignore (do nothing)
//            // 3. Multisig + CLTV: <lockTime> CLTV DROP <m> <pubkey1> ... <pubkeyN> <n> CHECKMULTISIG
//            //    -> Set lockTime but keep original owner
//
//            // Convert hex to Script object
//            byte[] redeemScriptBytes = Hex.fromHex(redeemScriptHex);
//            Script script = new Script(redeemScriptBytes);
//
//            // Get script chunks (parsed opcodes and data)
//            List<ScriptChunk> chunks = script.getChunks();
//
//            if(chunks.isEmpty()) return;
//
//            // Check if first chunk is a lockTime (data or number)
//            Long lockTime = null;
//            int startIdx = 0;
//
//            ScriptChunk firstChunk = chunks.get(0);
//            if(firstChunk.data != null && firstChunk.data.length > 0) {
//                // Check if followed by CLTV (0xb1 = 177)
//                if(chunks.size() > 2 && chunks.get(1).opcode == 177) { // OP_CHECKLOCKTIMEVERIFY
//                    lockTime = bytesToLong(firstChunk.data);
//                    cash.setLockTime(lockTime);
//                    cash.setType(Cash.CashType.P2SH_CLTV.getValue());
//                    startIdx = 3; // Skip lockTime, CLTV, DROP
//                }
//            }
//
//            // If no CLTV found, this is plain multisig - ignore it
//            if(lockTime == null) {
//                return;
//            }
//
//            // Now check if it's single-sig CLTV or multisig+CLTV
//            if(startIdx < chunks.size()) {
//                ScriptChunk nextChunk = chunks.get(startIdx);
//
//                // Check if it's single-sig CLTV (has DUP HASH160 pattern)
//                if(nextChunk.opcode == 118) { // OP_DUP
//                    // Single-sig CLTV: DUP HASH160 <pubkeyHash> EQUALVERIFY CHECKSIG
//                    // Extract pubkeyHash and update owner
//                    if(startIdx + 4 < chunks.size() && chunks.get(startIdx + 1).opcode == 169) { // OP_HASH160
//                        ScriptChunk pubkeyHashChunk = chunks.get(startIdx + 2);
//                        if(pubkeyHashChunk.data != null && pubkeyHashChunk.data.length == 20) {
//                            byte[] pubkeyHashBytes = pubkeyHashChunk.data;
//                            String fid = KeyTools.hash160ToFchAddr(pubkeyHashBytes);
//                            cash.setOwner(fid);
//                            cash.setType(Cash.CashType.P2SH_CLTV.getValue());
//                        }
//                    }
//                }else{  // This is multisig+CLTV - extract multisig script and compute owner FID
//                    cash.setType(Cash.CashType.P2SH_MULTISIG_CLTV.getValue());
//
//                    // Extract multisig portion (everything from startIdx onwards)
//                    // to create the standard multisig FID
//                    byte[] multisigScript = extractMultisigScript(chunks, startIdx);
//                    if(multisigScript != null) {
//                        byte[] multisigHash160 = Hash.sha256hash160(multisigScript);
//                        String multisigFid = KeyTools.hash160ToMultiAddr(multisigHash160);
//                        cash.setOwner(multisigFid);
//                    }
//                }
//            }
//        } catch (Exception ignore) {
//            // Silently continue if parsing fails
//        }
//    }

//    // Verify integrity: hash160(redeemScript) must match key
//    // AND validate syntax of each redeemScript
//                for (Map.Entry<String, String> entry : map.entrySet()) {
//        String hash160Hex = entry.getKey();
//        String redeemScriptHex = entry.getValue();
//
//        if (!Hex.isHexString(hash160Hex) || !Hex.isHexString(redeemScriptHex)) {
//            throw new IllegalArgumentException("Invalid hex format in P2SH map");
//        }
//
//        // Verify hash160 integrity
//        byte[] redeemScript = Hex.fromHex(redeemScriptHex);
//        byte[] calculatedHash = Hash.sha256hash160(redeemScript);
//        String calculatedHashHex = Hex.toHex(calculatedHash);
//
//        if (!calculatedHashHex.equals(hash160Hex)) {
//            TimberLogger.d(TAG,"Hash160 mismatch for entry: " + hash160Hex);
//        }
//
//        // Validate redeemScript syntax - CRITICAL for spendability
//        if (!validateRedeemScriptSyntax(redeemScriptHex)) {
//            TimberLogger.d(TAG,"Invalid redeemScript syntax in map: " + hash160Hex);
//        }
//    }

    /**
     * Extract multisig script portion from script chunks
     * Used to rebuild the standard multisig script without CLTV for proper FID generation
     * @param chunks The script chunks
     * @param startIdx Starting index (after CLTV DROP)
     * @return The multisig script bytes, or null if extraction fails
     */
    private static byte[] extractMultisigScript(List<ScriptChunk> chunks, int startIdx) {
        try {
            // Rebuild script from remaining chunks (multisig portion only)
            // Format: <m> <pubkey1> ... <pubkeyN> <n> CHECKMULTISIG
            ScriptBuilder builder = new ScriptBuilder();

            for(int i = startIdx; i < chunks.size(); i++) {
                ScriptChunk chunk = chunks.get(i);
                if(chunk.data != null) {
                    builder.data(chunk.data);
                } else {
                    builder.op(chunk.opcode);
                }
            }

            return builder.build().getProgram();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert byte array to long value (little-endian)
     * Used for parsing lockTime from Bitcoin script chunks
     * @param bytes The byte array
     * @return The long value
     */
    private static long bytesToLong(byte[] bytes) {
        long result = 0;
        for(int i = 0; i < bytes.length && i < 8; i++) {
            result |= ((long)(bytes[i] & 0xFF)) << (8 * i);
        }
        return result;
    }

    /**
     * Extract a lockTime value from a script chunk that may be either
     * a data push (arbitrary size) or a small-number opcode OP_0..OP_16.
     * Returns null if the chunk does not encode a valid lockTime value.
     */
    private static Long lockTimeFromChunk(ScriptChunk chunk) {
        if (chunk == null) return null;
        if (chunk.opcode == ScriptOpCodes.OP_0) return 0L;
        if (chunk.opcode >= ScriptOpCodes.OP_1 && chunk.opcode <= ScriptOpCodes.OP_16) {
            return (long) (chunk.opcode - ScriptOpCodes.OP_1 + 1);
        }
        if (chunk.data != null && chunk.data.length > 0) {
            return bytesToLong(chunk.data);
        }
        return null;
    }

    /**
     * Detect whether the chunk at the given index encodes a CLTV prefix
     * (lockTime, OP_CHECKLOCKTIMEVERIFY, OP_DROP).
     */
    private static boolean isCltvPrefix(List<ScriptChunk> chunks) {
        if (chunks.size() <= 2) return false;
        if (chunks.get(1).opcode != ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY) return false;
        if (chunks.get(2).opcode != ScriptOpCodes.OP_DROP) return false;
        return lockTimeFromChunk(chunks.get(0)) != null;
    }

    public Long getBirthHeight() {
        return birthHeight;
    }

    public void setBirthHeight(Long birthHeight) {
        this.birthHeight = birthHeight;
    }

    public Long getBirthTime() {
        return birthTime;
    }

    public void setBirthTime(Long birthTime) {
        this.birthTime = birthTime;
    }

    public String getBirthTxId() {
        return birthTxId;
    }

    public void setBirthTxId(String birthTxId) {
        this.birthTxId = birthTxId;
    }
}
