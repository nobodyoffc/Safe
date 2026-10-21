package com.fc.safe.myKeys;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.Kdf;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.safe.myKeys.KeyInputDetector.Kind;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * One import field takes every kind of key text, so these pin that each form is
 * told apart, that near-miss typos are reported rather than guessed, and that a
 * cipher in either form opens back to the same private key.
 */
public class KeyInputDetectorTest {

    private static final String PASSWORD = "correct horse";

    private static byte[] prikey() {
        byte[] prikey = new byte[32];
        for (int i = 0; i < prikey.length; i++) prikey[i] = (byte) (i + 1);
        return prikey;
    }

    private static String prikeyHex() {
        return Hex.toHex(prikey());
    }

    private static String wif() {
        return KeyTools.prikey32To38WifCompressed(prikeyHex());
    }

    /** Changes the last character to another Base58 character, so the checksum breaks. */
    private static String typo(String text) {
        char last = text.charAt(text.length() - 1);
        return text.substring(0, text.length() - 1) + (last == '2' ? '3' : '2');
    }

    private static CryptoDataByte encryptByPassword(byte[] plaintext) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        // A bundle carries no KDF and is always opened with SHA-256; it is also fast for tests.
        encryptor.setKdf(Kdf.Sha256Iv_No1_NrC7);
        CryptoDataByte cipher = encryptor.encryptByPassword(plaintext, PASSWORD.toCharArray());
        assertEquals(Integer.valueOf(0), cipher.getCode());
        return cipher;
    }

    private static byte[] decrypt(String input) {
        CryptoDataByte cipher = KeyInputDetector.parseCipher(input);
        assertNotNull(cipher);
        Decryptor.decryptByPassword(cipher, PASSWORD.toCharArray());
        assertEquals(Integer.valueOf(0), cipher.getCode());
        return cipher.getData();
    }

    @Test
    public void emptyInput() {
        assertEquals(Kind.EMPTY, KeyInputDetector.detect(null));
        assertEquals(Kind.EMPTY, KeyInputDetector.detect("  \n "));
    }

    @Test
    public void privateKeyForms() {
        assertEquals(Kind.PRIKEY, KeyInputDetector.detect(prikeyHex()));
        assertEquals(Kind.PRIKEY, KeyInputDetector.detect("0x" + prikeyHex()));
        assertEquals(Kind.PRIKEY, KeyInputDetector.detect(wif()));
        assertEquals(Kind.PRIKEY, KeyInputDetector.detect("  " + wif() + "\n"));
        assertArrayEquals(prikey(), KeyInputDetector.toPrikey32(wif()));
    }

    @Test
    public void watchOnlyForms() {
        String pubkey = Hex.toHex(KeyTools.prikeyToPubkey(prikey()));
        assertEquals(Kind.PUBKEY, KeyInputDetector.detect(pubkey));
        assertEquals(Kind.FID, KeyInputDetector.detect(KeyTools.prikeyToFid(prikey())));
    }

    @Test
    public void nearMissesAreReportedNotGuessed() {
        assertEquals(Kind.BAD_PRIKEY, KeyInputDetector.detect(typo(wif())));
        assertEquals(Kind.BAD_PRIKEY, KeyInputDetector.detect(prikeyHex().substring(0, 63) + "g"));
        assertEquals(Kind.BAD_FID, KeyInputDetector.detect(typo(KeyTools.prikeyToFid(prikey()))));

        String pubkey = Hex.toHex(KeyTools.prikeyToPubkey(prikey()));
        assertEquals(Kind.BAD_PUBKEY, KeyInputDetector.detect("05" + pubkey.substring(2)));
    }

    @Test
    public void somethingElseIsUnknownOrMultiple() {
        assertEquals(Kind.UNKNOWN, KeyInputDetector.detect("hello"));
        assertEquals(Kind.UNKNOWN, KeyInputDetector.detect("{\"unterminated\""));
        assertEquals(Kind.MULTIPLE, KeyInputDetector.detect(wif() + " " + wif()));
    }

    @Test
    public void jsonCipherOpensToThePrivateKey() {
        String json = encryptByPassword(prikey()).toJson();
        assertEquals(Kind.CIPHER, KeyInputDetector.detect(json));
        assertArrayEquals(prikey(), KeyInputDetector.prikeyFromPlaintext(decrypt(json)));
    }

    @Test
    public void base64CipherOpensToThePrivateKey() {
        CryptoDataByte cipher = encryptByPassword(prikey());
        cipher.makeKeyName(PASSWORD.getBytes(StandardCharsets.UTF_8));
        String base64 = Base64.getEncoder().encodeToString(cipher.toBundle());

        assertEquals(Kind.CIPHER, KeyInputDetector.detect(base64));
        assertArrayEquals(prikey(), KeyInputDetector.prikeyFromPlaintext(decrypt(base64)));
    }

    @Test
    public void encryptedWifTextOpensToThePrivateKey() {
        String json = encryptByPassword(wif().getBytes(StandardCharsets.UTF_8)).toJson();
        assertArrayEquals(prikey(), KeyInputDetector.prikeyFromPlaintext(decrypt(json)));
    }

    @Test
    public void encryptedKeyJsonIsNotMistakenForAKey() {
        byte[] plaintext = "{\"id\":\"F\"}".getBytes(StandardCharsets.UTF_8);
        String json = encryptByPassword(plaintext).toJson();
        assertEquals(Kind.CIPHER, KeyInputDetector.detect(json));

        byte[] opened = decrypt(json);
        assertNull(KeyInputDetector.prikeyFromPlaintext(opened));
        assertTrue(KeyInputDetector.isJson(opened));
        assertFalse(KeyInputDetector.isJson(prikey()));
    }

    @Test
    public void keyJsonIsABackup() {
        String keyInfo = "{\"id\":\"" + KeyTools.prikeyToFid(prikey()) + "\"}";
        assertEquals(Kind.BACKUP, KeyInputDetector.detect(keyInfo));

        String twoCiphers = encryptByPassword(prikey()).toJson() + "\n" + encryptByPassword(prikey()).toJson();
        assertEquals(Kind.BACKUP, KeyInputDetector.detect(twoCiphers));
    }

    @Test
    public void symkeyCipherIsNotOpenedWithAPassword() {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesGcm256_No1_NrC7);
        CryptoDataByte cipher = encryptor.encryptBySymkey(prikey(), new byte[32]);
        assertEquals(Kind.NON_PASSWORD_CIPHER, KeyInputDetector.detect(cipher.toJson()));
    }
}
