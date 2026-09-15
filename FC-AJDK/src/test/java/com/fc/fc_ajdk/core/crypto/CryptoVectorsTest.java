package com.fc.fc_ajdk.core.crypto;

import com.fc.fc_ajdk.core.crypto.Algorithm.Bitcore;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Checks this copy of the crypto package against the shared FTSP vectors, which FC-JDK generates
 * and every implementation must pass. Freeverse/tools/sync-ftsp-vectors.sh copies them into
 * src/test/resources/ftsp-vectors; rerun it whenever the vectors are regenerated.
 */
public class CryptoVectorsTest {

    @BeforeClass
    public static void addProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    public void kdf() throws Exception {
        for (JsonElement e : vectors("kdf.json")) {
            JsonObject v = e.getAsJsonObject();
            String id = str(v, "id");
            Kdf kdf = Kdf.fromDisplayName(str(v, "kdf"));
            assertEquals(id, kdf, Kdf.fromId(hex(v, "kdfId")[0]));
            assertEquals(id, str(v, "symkey"), toHex(kdf.deriveSymkey(str(v, "password").toCharArray(), hex(v, "salt"))));
        }
    }

    @Test
    public void phrase() throws Exception {
        for (JsonElement e : vectors("phrase.json")) {
            JsonObject v = e.getAsJsonObject();
            String id = str(v, "id");
            byte[] utf8 = hex(v, "phraseUtf8Hex");
            assertArrayEquals(id, str(v, "phrase").getBytes(StandardCharsets.UTF_8), utf8);
            if ("sha256".equals(str(v, "scheme"))) {
                assertEquals(id, str(v, "priKey32"), toHex(Hash.sha256(utf8)));
                continue;
            }
            byte[] key = Kdf.Argon2id_No1_NrC7.deriveSymkey(str(v, "phrase").toCharArray(), hex(v, "salt"));
            assertEquals(id, str(v, "priKey32"), toHex(key));
            // FTSP28: the char[] path must hash exactly like the UTF-8 bytes, non-ASCII included.
            assertArrayEquals(id, key, argon2idFromBytes(utf8, hex(v, "salt")));
        }
    }

    @Test
    public void cipherJson() throws Exception {
        Decryptor d = new Decryptor();
        for (JsonElement e : vectors("cipher-json.json")) {
            JsonObject v = e.getAsJsonObject();
            String json = str(v, "cipherJson");
            JsonObject secret = v.getAsJsonObject("secret");
            CryptoDataByte result = switch (EncryptType.valueOf(str(v, "type"))) {
                case Symkey -> d.decryptJsonBySymkey(json, hex(secret, "symkey"));
                case Password -> d.decryptJsonByPassword(json, str(secret, "password").toCharArray());
                case AsyOneWay -> d.decryptJsonByAsyOneWay(json, hex(secret, "prikey"));
                case AsyTwoWay -> d.decryptJsonByAsyTwoWay(json, hex(secret, "prikey"), hex(secret, "pubkey"));
            };
            assertDecrypted(v, result);
        }
    }

    @Test
    public void bundle() throws Exception {
        Decryptor d = new Decryptor();
        for (JsonElement e : vectors("bundle.json")) {
            JsonObject v = e.getAsJsonObject();
            String id = str(v, "id");
            byte[] bundle = hex(v, "bundleHex");
            assertArrayEquals(id, bundle, Base64.getDecoder().decode(str(v, "bundleBase64")));
            CryptoDataByte parsed = CryptoDataByte.fromBundle(bundle);
            if ("reject".equals(str(v, "expect"))) {
                assertNull(id + " must be rejected", parsed);
                continue;
            }
            assertNotNull(id, parsed);
            assertEquals(id, AlgorithmId.fromDisplayName(str(v, "alg")), parsed.getAlg());
            assertEquals(id, EncryptType.valueOf(str(v, "type")), parsed.getType());
            Kdf recorded = str(v, "kdfRecorded") == null ? null : Kdf.fromDisplayName(str(v, "kdfRecorded"));
            assertEquals(id, recorded, parsed.getKdf());
            if (v.get("canonical").getAsBoolean()) {
                assertArrayEquals(id + ": toBundle does not reproduce the bundle", bundle, parsed.toBundle());
            }
            JsonObject secret = v.getAsJsonObject("secret");
            CryptoDataByte result = switch (parsed.getType()) {
                case Symkey -> d.decryptBundleBySymkey(bundle, hex(secret, "symkey"));
                case Password -> d.decryptBundleByPassword(bundle, str(secret, "password").toCharArray());
                case AsyOneWay -> d.decryptBundleByAsyOneWay(bundle, hex(secret, "prikey"));
                case AsyTwoWay -> d.decryptBundleByAsyTwoWay(bundle, hex(secret, "prikey"), hex(secret, "pubkey"));
            };
            assertDecrypted(v, result);
        }
    }

    @Test
    public void wrongPasswordFails() throws Exception {
        for (JsonElement e : vectors("bundle.json")) {
            JsonObject v = e.getAsJsonObject();
            if (!"BUNDLE-T3-PASSWORD-GCM-ARGON2ID".equals(str(v, "id"))) continue;
            CryptoDataByte result = new Decryptor().decryptBundleByPassword(hex(v, "bundleHex"), "not the password".toCharArray());
            assertNotEquals(Integer.valueOf(0), result.getCode());
            assertNull(result.getData());
            assertNull("a failed trial must not report a KDF", result.getKdf());
            return;
        }
        fail("BUNDLE-T3-PASSWORD-GCM-ARGON2ID missing from bundle.json");
    }

    @Test
    public void algorithms() throws Exception {
        // Collect every failing vector, so one run shows the whole gap rather than the first entry.
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (JsonElement e : vectors("algorithms.json")) {
            JsonObject v = e.getAsJsonObject();
            String id = str(v, "id");
            boolean reject = "reject-decrypt".equals(str(v, "expect"));
            CryptoDataByte result;
            try {
                result = decryptAlgorithmVector(v);
            } catch (Throwable ex) {
                if (!reject) failures.add(id + " threw " + ex);
                continue;
            }
            boolean success = result != null && Integer.valueOf(0).equals(result.getCode());
            if (reject) {
                if (success) failures.add(id + ": tampered cipher reported success");
            } else if (!success || result.getData() == null || !str(v, "plaintextHex").equals(toHex(result.getData()))) {
                failures.add(id + ": code=" + (result == null ? null : result.getCode())
                        + " message=" + (result == null ? null : result.getMessage())
                        + " data=" + (result == null || result.getData() == null ? null : toHex(result.getData())));
            }
        }
        if (!failures.isEmpty()) fail(failures.size() + " algorithm vectors failed:\n" + String.join("\n", failures));
    }

    private static CryptoDataByte decryptAlgorithmVector(JsonObject v) throws Exception {
        String id = str(v, "id");
        JsonObject secret = v.getAsJsonObject("secret");
        byte[] prikey = secret.has("prikey") ? hex(secret, "prikey") : null;
        Decryptor d = new Decryptor();
        switch (str(v, "form")) {
            case "json": {
                String json = str(v, "cipherJson");
                return switch (EncryptType.valueOf(str(v, "type"))) {
                    case Symkey -> d.decryptJsonBySymkey(json, hex(secret, "symkey"));
                    case Password -> d.decryptJsonByPassword(json, str(secret, "password").toCharArray());
                    case AsyOneWay -> d.decryptJsonByAsyOneWay(json, prikey);
                    case AsyTwoWay -> d.decryptJsonByAsyTwoWay(json, prikey, hex(secret, "pubkey"));
                };
            }
            case "bundle": {
                byte[] bundle = hex(v, "bundleHex");
                CryptoDataByte parsed = CryptoDataByte.fromBundle(bundle);
                assertNotNull(id, parsed);
                assertEquals(id, AlgorithmId.fromDisplayName(str(v, "alg")), parsed.getAlg());
                return switch (parsed.getType()) {
                    case Symkey -> d.decryptBundleBySymkey(bundle, hex(secret, "symkey"));
                    case Password -> d.decryptBundleByPassword(bundle, str(secret, "password").toCharArray());
                    case AsyOneWay -> d.decryptBundleByAsyOneWay(bundle, prikey);
                    case AsyTwoWay -> d.decryptBundleByAsyTwoWay(bundle, prikey, hex(secret, "pubkey"));
                };
            }
            case "bitcoreEncbuf": {
                CryptoDataByte r = new CryptoDataByte();
                r.setData(Bitcore.decrypt(hex(v, "encbufHex"), prikey));
                r.set0CodeMessage();
                return r;
            }
            default:
                throw new AssertionError("unknown form in " + id);
        }
    }

    private static void assertDecrypted(JsonObject v, CryptoDataByte result) {
        String id = str(v, "id");
        assertNotNull(id, result);
        assertEquals(id + ": " + result.getMessage(), Integer.valueOf(0), result.getCode());
        assertEquals(id, str(v, "plaintextHex"), result.getData() == null ? null : toHex(result.getData()));
        if (str(v, "derivedWith") != null) {
            assertEquals(id + ": reported the wrong KDF", Kdf.fromDisplayName(str(v, "derivedWith")), result.getKdf());
        }
    }

    private static byte[] argon2idFromBytes(byte[] password, byte[] salt) {
        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(Kdf.ARGON2ID_ITERATIONS)
                .withMemoryAsKB(Kdf.ARGON2ID_MEMORY_KIB)
                .withParallelism(Kdf.ARGON2ID_PARALLELISM)
                .withSalt(salt)
                .build());
        byte[] out = new byte[Kdf.DERIVED_KEY_LEN];
        gen.generateBytes(password, out);
        return out;
    }

    private static JsonArray vectors(String file) throws Exception {
        String path = "ftsp-vectors/" + file;
        try (InputStream in = CryptoVectorsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(path + " is missing; run Freeverse/tools/sync-ftsp-vectors.sh", in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return JsonParser.parseString(out.toString("UTF-8")).getAsJsonObject().getAsJsonArray("vectors");
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static byte[] hex(JsonObject o, String key) {
        String s = str(o, key);
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
