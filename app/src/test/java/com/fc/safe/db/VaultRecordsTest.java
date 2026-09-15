package com.fc.safe.db;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.VaultMigration;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VaultRecordsTest {
    private static final byte[] LEGACY_KEY = filled(1);
    private static final byte[] DEK = filled(2);
    private static final byte[] PRIKEY = filled(3);

    @Test
    public void reencryptsAKeyInfoCipherAndKeepsEveryOtherField() throws Exception {
        String json = "{\"id\":\"FEk41Kqjar45fLDriztUDTUkdki7mmcjWK\",\"label\":\"main\",\"height\":123456789012,"
                + "\"prikeyCipher\":" + quote(Encryptor.encryptBySymkeyToJson(PRIKEY, LEGACY_KEY)) + "}";

        String out = VaultRecords.reencrypt("keyinfo/FEk41", json, VaultRecordsTest::legacyToDek);

        JsonObject item = JsonParser.parseString(out).getAsJsonObject();
        assertEquals("main", item.get("label").getAsString());
        assertEquals("123456789012", item.get("height").getAsString());
        assertArrayEquals(PRIKEY, Decryptor.decryptPrikey(item.get("prikeyCipher").getAsString(), DEK));
        assertNull(Decryptor.decryptPrikey(item.get("prikeyCipher").getAsString(), LEGACY_KEY));
    }

    @Test
    public void leavesValuesWithoutVaultCiphersUntouched() throws Exception {
        String json = "{\"id\":\"multisig1\",\"detailCipher\":\"not a vault cipher\"}";
        assertSame(json, VaultRecords.reencrypt("multisig/1", json, VaultRecordsTest::mustNotBeCalled));
        assertEquals(VaultRecords.NO_CIPHER, VaultRecords.opens(json, LEGACY_KEY));
        assertSame("not json", VaultRecords.reencrypt("x/1", "not json", VaultRecordsTest::mustNotBeCalled));
        assertSame("[1,2,3]", VaultRecords.reencrypt("avatar/1", "[1,2,3]", VaultRecordsTest::mustNotBeCalled));
    }

    @Test
    public void leavesCiphersOfOtherTypesAlone() throws Exception {
        String asymmetric = "{\"type\":\"AsyOneWay\",\"alg\":\"EccK1AesGcm256@No1_NrC7\",\"cipher\":\"AAAA\"}";
        String json = "{\"contentCipher\":" + quote(asymmetric) + "}";
        assertSame(json, VaultRecords.reencrypt("secret/1", json, VaultRecordsTest::mustNotBeCalled));
        assertEquals(VaultRecords.NO_CIPHER, VaultRecords.opens(json, LEGACY_KEY));
    }

    @Test
    public void findsCiphersNestedInMapsAndLists() throws Exception {
        String json = "{\"passwordName\":\"abc123\","
                + "\"mainCidInfoMap\":{\"FEk41\":{\"prikeyCipher\":" + quote(Encryptor.encryptBySymkeyToJson(PRIKEY, LEGACY_KEY)) + "}},"
                + "\"apiAccountMap\":{\"acc\":{\"session\":{\"keyCipher\":" + quote(Encryptor.encryptBySymkeyToJson(DEK, LEGACY_KEY)) + "}}},"
                + "\"deleted\":[{\"contentCipher\":" + quote(Encryptor.encryptBySymkeyToJson(PRIKEY, LEGACY_KEY)) + "}]}";
        List<String> seen = new ArrayList<>();

        String out = VaultRecords.reencrypt("config", json, (id, cipher) -> {
            seen.add(id);
            return legacyToDek(id, cipher);
        });

        assertEquals(Arrays.asList("config/mainCidInfoMap/FEk41", "config/apiAccountMap/acc/session", "config/deleted/0"), seen);
        JsonObject root = JsonParser.parseString(out).getAsJsonObject();
        assertEquals("abc123", root.get("passwordName").getAsString());
        String prikeyCipher = root.getAsJsonObject("mainCidInfoMap").getAsJsonObject("FEk41").get("prikeyCipher").getAsString();
        assertArrayEquals(PRIKEY, Decryptor.decryptPrikey(prikeyCipher, DEK));
        List<String> checked = new ArrayList<>();
        VaultRecords.check("config", out, (id, cipher) -> {
            if (Decryptor.decryptPrikey(cipher, DEK) == null) throw new VaultMigration.UnreadableRecordException(id);
            checked.add(id);
        });
        assertEquals(seen, checked);
    }

    @Test
    public void namesTheRecordThatCannotBeDecrypted() {
        String json = "{\"contentCipher\":" + quote(Encryptor.encryptBySymkeyToJson("totp".getBytes(StandardCharsets.UTF_8), filled(9))) + "}";
        try {
            VaultRecords.reencrypt("secret/defaultSecret", json, VaultRecordsTest::legacyToDek);
            fail("an unreadable record must stop the copy");
        } catch (VaultMigration.UnreadableRecordException e) {
            assertEquals("secret/defaultSecret", e.getRecordId());
        }
    }

    @Test
    public void tellsWhetherAKeyOpensAValue() {
        String json = "{\"contentCipher\":" + quote(Encryptor.encryptBySymkeyToJson("totp".getBytes(StandardCharsets.UTF_8), LEGACY_KEY)) + "}";
        assertEquals(VaultRecords.OPENS, VaultRecords.opens(json, LEGACY_KEY));
        assertEquals(VaultRecords.CLOSED, VaultRecords.opens(json, DEK));
    }

    private static String legacyToDek(String recordId, String cipher) throws VaultMigration.UnreadableRecordException {
        byte[] plain = Decryptor.decryptPrikey(cipher, LEGACY_KEY);
        if (plain == null) throw new VaultMigration.UnreadableRecordException(recordId);
        return Encryptor.encryptBySymkeyToJson(plain, DEK);
    }

    private static String mustNotBeCalled(String recordId, String cipher) {
        throw new AssertionError("no vault-key cipher in " + recordId);
    }

    private static String quote(String s) {
        return new Gson().toJson(s);
    }

    private static byte[] filled(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
