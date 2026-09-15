package com.fc.fc_ajdk.core.crypto;

import com.fc.fc_ajdk.data.fcData.AlgorithmId;

import org.junit.Test;

import java.security.SecureRandom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Port of FC-JDK's CryptoErrorPropagationTest: encryptions report failures instead of
 * success, and the legacy EccAes256K1P7 and asymmetric ChaCha20-Poly1305 profiles
 * round-trip, with tampering rejected.
 */
public class CryptoErrorPropagationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static byte[] prikey() {
        byte[] pri = new byte[32];
        RANDOM.nextBytes(pri);
        return pri;
    }

    private static CryptoDataByte received(CryptoDataByte sent, AlgorithmId alg,
                                           byte[] prikeyB, byte[] pubkeyA, byte[] cipher) {
        CryptoDataByte in = new CryptoDataByte();
        in.setAlg(alg);
        in.setType(EncryptType.AsyTwoWay);
        in.setCipher(cipher);
        in.setIv(sent.getIv());
        in.setSum(sent.getSum());
        in.setPrikeyB(prikeyB);
        in.setPubkeyA(pubkeyA);
        return in;
    }

    @Test
    public void encryptPreservesFailureCode() {
        // A 7-byte key cannot encrypt; the result must not claim success.
        CryptoDataByte result = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptBySymkey("payload".getBytes(), new byte[7]);
        assertNotNull("a failed encryption must carry a code", result.getCode());
        assertNotEquals("wrong key length must not be reported as success", Integer.valueOf(0), result.getCode());
    }

    @Test
    public void encryptStillReportsSuccess() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        CryptoDataByte result = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7).encryptBySymkey("payload".getBytes(), key);
        assertEquals(Integer.valueOf(0), result.getCode());
        assertNotNull(result.getCipher());
        assertTrue(result.getCipher().length > 0);
    }

    @Test
    public void legacyEccAesAsyTwoWayRoundTrip() {
        byte[] priA = prikey(), pubA = KeyTools.prikeyToPubkey(priA);
        byte[] priB = prikey();
        byte[] pubB = KeyTools.prikeyToPubkey(priB);
        byte[] data = "legacy algorithm payload".getBytes();
        byte[] priACopy = priA.clone();

        CryptoDataByte sent = new Encryptor(AlgorithmId.EccAes256K1P7_No1_NrC7).encryptByAsyTwoWay(data, priA, pubB);
        assertEquals(sent.getMessage(), Integer.valueOf(0), sent.getCode());
        assertEquals(EncryptType.AsyTwoWay, sent.getType());
        assertNotNull(sent.getCipher());
        assertTrue("cipher must not be empty", sent.getCipher().length > 0);
        assertNotNull("EccAes256K1P7 carries its own cipher-derived sum", sent.getSum());
        assertArrayEquals("caller's private key must survive encryption", priACopy, priA);

        CryptoDataByte back = received(sent, AlgorithmId.EccAes256K1P7_No1_NrC7, priB, pubA, sent.getCipher().clone());
        new Decryptor().decrypt(back);
        assertEquals(back.getMessage(), Integer.valueOf(0), back.getCode());
        assertArrayEquals(data, back.getData());
    }

    @Test
    public void eccChaCha20Poly1305RoundTripAndTamper() {
        byte[] priA = prikey(), pubA = KeyTools.prikeyToPubkey(priA);
        byte[] priB = prikey();
        byte[] pubB = KeyTools.prikeyToPubkey(priB);
        byte[] data = "ChaCha20-Poly1305 asymmetric payload".getBytes();
        AlgorithmId alg = AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7;

        CryptoDataByte sent = new Encryptor(alg).encryptByAsyTwoWay(data, priA, pubB);
        assertEquals(sent.getMessage(), Integer.valueOf(0), sent.getCode());
        assertNotNull(sent.getCipher());

        CryptoDataByte ok = received(sent, alg, priB, pubA, sent.getCipher().clone());
        new Decryptor().decrypt(ok);
        assertEquals(ok.getMessage(), Integer.valueOf(0), ok.getCode());
        assertArrayEquals(data, ok.getData());

        byte[] tampered = sent.getCipher().clone();
        tampered[tampered.length / 2] ^= 0x01;
        CryptoDataByte bad = received(sent, alg, priB, pubA, tampered);
        new Decryptor().decrypt(bad);
        assertNotEquals("tampered AEAD ciphertext must not decrypt as success", Integer.valueOf(0), bad.getCode());
        assertNull(bad.getData());

        CryptoDataByte wrongKey = received(sent, alg, prikey(), pubA, sent.getCipher().clone());
        new Decryptor().decrypt(wrongKey);
        assertNotEquals("wrong private key must not decrypt as success", Integer.valueOf(0), wrongKey.getCode());
    }

    @Test
    public void aeadClassification() {
        assertTrue(AlgorithmId.FC_AesGcm256_No1_NrC7.isAead());
        assertTrue(AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7.isAead());
        assertFalse(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7.isAead());
        assertFalse(AlgorithmId.EccAes256K1P7_No1_NrC7.isAead());

        byte[] priA = prikey(), priB = prikey();
        byte[] pubB = KeyTools.prikeyToPubkey(priB);
        byte[] data = "sum presence".getBytes();
        assertNull("AEAD output must not carry a redundant sum",
                new Encryptor(AlgorithmId.FC_EccK1ChaCha20Poly1305_No1_NrC7).encryptByAsyTwoWay(data, priA, pubB).getSum());
        assertNotNull("non-AEAD output must carry a sum",
                new Encryptor(AlgorithmId.FC_EccK1ChaCha20_No1_NrC7).encryptByAsyTwoWay(data, priA, pubB).getSum());
    }
}
