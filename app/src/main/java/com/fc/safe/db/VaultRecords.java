package com.fc.safe.db;

import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.core.crypto.VaultMigration;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the ciphers made with the vault key anywhere inside a stored JSON value: the fields
 * {@code prikeyCipher} (KeyInfo), {@code contentCipher} (Secret) and {@code keyCipher} (FcSession),
 * at any depth, when they hold a Symkey cipher. A cipher of another type, such as a secret encrypted
 * to the owner's public key, does not use the vault key and is left alone, as is every other field.
 */
public final class VaultRecords {
    static final List<String> CIPHER_FIELDS = Arrays.asList("prikeyCipher", "contentCipher", "keyCipher");

    /** {@link #opens} result for a value without vault-key ciphers. */
    public static final int NO_CIPHER = -1;
    /** {@link #opens} result for a value whose vault-key ciphers do not open with the key. */
    public static final int CLOSED = 0;
    /** {@link #opens} result for a value with a vault-key cipher that opens with the key. */
    public static final int OPENS = 1;

    private static final Gson GSON = new Gson();

    private interface Visitor {
        /** @return the replacement cipher, or null to keep it. */
        String visit(String recordId, String cipher) throws VaultMigration.UnreadableRecordException;
    }

    private VaultRecords() {
    }

    /** @return {@code json} with every vault-key cipher passed through {@code reencrypt}; the same string if it has none. */
    public static String reencrypt(String recordId, String json, VaultMigration.Reencrypt reencrypt)
            throws VaultMigration.UnreadableRecordException {
        JsonElement root = parse(json);
        if (root == null) return json;
        return walk(root, recordId, reencrypt::apply) ? GSON.toJson(root) : json;
    }

    /** Calls {@code check} with every vault-key cipher in {@code json}. */
    public static void check(String recordId, String json, VaultMigration.Check check)
            throws VaultMigration.UnreadableRecordException {
        JsonElement root = parse(json);
        if (root == null) return;
        walk(root, recordId, (id, cipher) -> {
            check.accept(id, cipher);
            return null;
        });
    }

    /** @return {@link #OPENS}, {@link #CLOSED} or {@link #NO_CIPHER} for the vault-key ciphers in {@code json} under {@code key}. */
    public static int opens(String json, byte[] key) {
        JsonElement root = parse(json);
        if (root == null) return NO_CIPHER;
        int[] result = {NO_CIPHER};
        try {
            walk(root, "", (id, cipher) -> {
                if (result[0] == OPENS) return null;
                byte[] plain = Decryptor.decryptPrikey(cipher, key);
                if (plain != null) {
                    Arrays.fill(plain, (byte) 0);
                    result[0] = OPENS;
                } else {
                    result[0] = CLOSED;
                }
                return null;
            });
        } catch (VaultMigration.UnreadableRecordException impossible) {
            // The visitor above never throws.
        }
        return result[0];
    }

    private static boolean walk(JsonElement element, String path, Visitor visitor)
            throws VaultMigration.UnreadableRecordException {
        boolean changed = false;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            Map<String, String> replacements = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String cipher = vaultCipher(entry.getKey(), entry.getValue());
                if (cipher != null) {
                    String replacement = visitor.visit(path, cipher);
                    if (replacement != null) replacements.put(entry.getKey(), replacement);
                } else if (walk(entry.getValue(), path + "/" + entry.getKey(), visitor)) {
                    changed = true;
                }
            }
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                object.add(replacement.getKey(), new JsonPrimitive(replacement.getValue()));
                changed = true;
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                if (walk(array.get(i), path + "/" + i, visitor)) changed = true;
            }
        }
        return changed;
    }

    /** @return the cipher if {@code value} of field {@code name} is a vault-key cipher, else null. */
    private static String vaultCipher(String name, JsonElement value) {
        if (!CIPHER_FIELDS.contains(name) || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
        String cipher = value.getAsString();
        if (cipher.isEmpty()) return null;
        JsonElement parsed = parse(cipher);
        if (parsed != null && parsed.isJsonObject()) {
            JsonElement type = parsed.getAsJsonObject().get("type");
            if (type != null && type.isJsonPrimitive() && !EncryptType.Symkey.name().equals(type.getAsString())) return null;
        }
        return cipher;
    }

    private static JsonElement parse(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            JsonElement element = JsonParser.parseString(json);
            return element.isJsonObject() || element.isJsonArray() ? element : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
