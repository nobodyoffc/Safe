package com.fc.fc_ajdk.core.crypto.Algorithm;


import com.fc.fc_ajdk.core.crypto.CryptoDataByte;

/**
 * Interface for asymmetric cipher algorithms that combine:
 * - Key exchange (ECDH with secp256k1 or X25519)
 * - Key derivation (SHA512 or HKDF)
 * - Symmetric encryption (AES-CBC, AES-GCM, or ChaCha20)
 *
 * Implementations should support:
 * - AsyOneWay: Sender generates ephemeral key pair, encrypts with recipient's public key
 * - AsyTwoWay: Both parties use their respective key pairs for bidirectional encryption
 */
public interface AsymmetricCipher {

    byte[] getSharedSecret(byte[] priKeyBytes, byte[] pubKeyBytes);

    byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) throws Exception;

    byte[] asyKeyToSymkey(byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception;

    CryptoDataByte encrypt(byte[] plaintext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception;

    CryptoDataByte decrypt(byte[] ciphertext, byte[] priKey, byte[] pubKey, byte[] nonce) throws Exception;
}
