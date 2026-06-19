package com.fc.fc_ajdk.core.crypto.Algorithm;

import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;

import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.*;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.EllipticCurve;
import java.security.spec.ECFieldFp;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;

public class Bitcore {
    private static final boolean SHORT_TAG = false;
    private static final boolean NO_KEY = false;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public static byte[] encrypt(byte[] message, PublicKey recipientPublicKey) throws Exception {
        // Generate ephemeral key pair
        ECNamedCurveParameterSpec params = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECDomainParameters domainParams = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
        ECKeyPairGenerator keyPairGenerator = new ECKeyPairGenerator();
        keyPairGenerator.init(new ECKeyGenerationParameters(domainParams, new SecureRandom()));
        AsymmetricCipherKeyPair ephemeralKeyPair = keyPairGenerator.generateKeyPair();

        ECPrivateKeyParameters ephemeralPrivateKey = (ECPrivateKeyParameters) ephemeralKeyPair.getPrivate();
        ECPublicKeyParameters ephemeralPublicKey = (ECPublicKeyParameters) ephemeralKeyPair.getPublic();

        // Get recipient's public key
        ECPublicKeyParameters recipientPublicKeyParams = (ECPublicKeyParameters) ECUtil.generatePublicKeyParameter(recipientPublicKey);

        // Generate shared secret
        byte[] sharedSecret = generateSharedSecret(ephemeralPrivateKey, recipientPublicKeyParams);
        byte[] kEkM = sha512(sharedSecret);
        byte[] kE = Arrays.copyOfRange(kEkM, 0, 32);
        byte[] kM = Arrays.copyOfRange(kEkM, 32, 64);

        // Generate IV
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);

        // Encrypt message
        byte[] c = encryptAESCBC(message, kE, iv);

        // Calculate HMAC
        byte[] ciphertext = new byte[iv.length + c.length];
        System.arraycopy(iv, 0, ciphertext, 0, iv.length);
        System.arraycopy(c, 0, ciphertext, iv.length, c.length);
        byte[] d = hmacSha256(ciphertext, kM);
        if (SHORT_TAG) {
            d = Arrays.copyOfRange(d, 0, 4);
        }

        // Combine all parts
        byte[] encbuf;
        if (NO_KEY) {
            encbuf = new byte[ciphertext.length + d.length];
            System.arraycopy(ciphertext, 0, encbuf, 0, ciphertext.length);
            System.arraycopy(d, 0, encbuf, ciphertext.length, d.length);
        } else {
            byte[] ephemeralPublicKeyBytes = ephemeralPublicKey.getQ().getEncoded(true);
            encbuf = new byte[ephemeralPublicKeyBytes.length + ciphertext.length + d.length];
            System.arraycopy(ephemeralPublicKeyBytes, 0, encbuf, 0, ephemeralPublicKeyBytes.length);
            System.arraycopy(ciphertext, 0, encbuf, ephemeralPublicKeyBytes.length, ciphertext.length);
            System.arraycopy(d, 0, encbuf, ephemeralPublicKeyBytes.length + ciphertext.length, d.length);
        }

        return encbuf;
    }


    private static byte[] generateSharedSecret(ECPrivateKeyParameters privateKey, ECPublicKeyParameters publicKey) {
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(privateKey);
        BigInteger sharedSecret = agreement.calculateAgreement(publicKey);
        return bigIntegerToBytes(sharedSecret, 32);
    }

    private static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        byte[] bytes = new byte[numBytes];
        byte[] biBytes = b.toByteArray();
        int start = (biBytes.length == numBytes + 1) ? 1 : 0;
        int length = Math.min(biBytes.length, numBytes);
        System.arraycopy(biBytes, start, bytes, numBytes - length, length);
        return bytes;
    }

    private static byte[] sha512(byte[] input) {
        SHA512Digest digest = new SHA512Digest();
        byte[] output = new byte[digest.getDigestSize()];
        digest.update(input, 0, input.length);
        digest.doFinal(output, 0);
        return output;
    }

    private static byte[] hmacSha256(byte[] data, byte[] key) {
        HMac hmac = new HMac(new SHA256Digest());
        hmac.init(new KeyParameter(key));
        byte[] output = new byte[hmac.getMacSize()];
        hmac.update(data, 0, data.length);
        hmac.doFinal(output, 0);
        return output;
    }

    public static byte[] decrypt(byte[] encbuf, byte[] prikeyBytes) throws Exception {
        PrivateKey privateKey = createPrivateKey(prikeyBytes);
        ECPrivateKeyParameters privKeyParams = (ECPrivateKeyParameters) ECUtil.generatePrivateKeyParameter(privateKey);

        int offset = 0;
        int tagLength = SHORT_TAG ? 4 : 32;

        ECPublicKeyParameters publicKey;
        if (!NO_KEY) {
            byte[] pubkeyBytes;
            switch (encbuf[0]) {
                case 4 -> {
                    pubkeyBytes = Arrays.copyOfRange(encbuf, 0, 65);
                    offset = 65;
                }
                case 3, 2 -> {
                    pubkeyBytes = Arrays.copyOfRange(encbuf, 0, 33);
                    offset = 33;
                }
                default -> throw new Error("Invalid public key type: " + encbuf[0]);
            }

            ECNamedCurveParameterSpec params = ECNamedCurveTable.getParameterSpec("secp256k1");
            ECDomainParameters domainParams = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
            org.bouncycastle.math.ec.ECPoint point = params.getCurve().decodePoint(pubkeyBytes);
            publicKey = new ECPublicKeyParameters(point, domainParams);
        } else {
            throw new Error("NO_KEY option is not supported in this implementation");
        }

        byte[] ciphertext = Arrays.copyOfRange(encbuf, offset, encbuf.length - tagLength);
        byte[] d = Arrays.copyOfRange(encbuf, encbuf.length - tagLength, encbuf.length);

        byte[] sharedSecret = generateSharedSecret(privKeyParams, publicKey);
        byte[] kEkM = sha512(sharedSecret);
        byte[] kE = Arrays.copyOfRange(kEkM, 0, 32);
        byte[] kM = Arrays.copyOfRange(kEkM, 32, 64);

        byte[] d2 = hmacSha256(ciphertext, kM);
        if (SHORT_TAG) {
            d2 = Arrays.copyOfRange(d2, 0, 4);
        }

        if (!constantTimeEquals(d, d2)) {
            return null;
        }

        byte[] iv = Arrays.copyOfRange(ciphertext, 0, 16);
        byte[] c = Arrays.copyOfRange(ciphertext, 16, ciphertext.length);

        return decryptAESCBC(c, kE, iv);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return java.security.MessageDigest.isEqual(a, b);
    }
    // ... (keep other methods like generateSharedSecret, sha512, hmacSha256, etc.)

    private static byte[] encryptAESCBC(byte[] plaintext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
        return cipher.doFinal(plaintext);
    }

    private static byte[] decryptAESCBC(byte[] ciphertext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec);
        return cipher.doFinal(ciphertext);
    }

    public static byte[] passwordToKey(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(
                password.getBytes(StandardCharsets.UTF_8));
    }

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen;
        try {
            keyGen = KeyPairGenerator.getInstance("EC", "BC");
        } catch (NoSuchProviderException | NoSuchAlgorithmException e) {
            // Fallback to default provider if BC is not available
            keyGen = KeyPairGenerator.getInstance("EC");
        }
        ECNamedCurveGenParameterSpec ecSpec = new ECNamedCurveGenParameterSpec("secp256k1");
        keyGen.initialize(ecSpec, new SecureRandom());
        return keyGen.generateKeyPair();
    }


    public static PrivateKey createPrivateKey(byte[] privateKeyBytes) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException  {
        // Ensure the private key is 32 bytes
        if (privateKeyBytes.length != 32) {
            throw new IllegalArgumentException("Private key must be 32 bytes");
        }

        // Convert byte array to BigInteger
        BigInteger privateKeyBigInteger = new BigInteger(1, privateKeyBytes);

        // Try BC provider first
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");

            // Get curve parameters for BC
            X9ECParameters params = SECNamedCurves.getByName("secp256k1");
            ECCurve curve = params.getCurve();

            // Create BC EC Parameter Spec
            org.bouncycastle.jce.spec.ECParameterSpec bcEcSpec =
                new org.bouncycastle.jce.spec.ECParameterSpec(curve, params.getG(), params.getN(), params.getH());

            // Create BC EC Private Key Spec
            org.bouncycastle.jce.spec.ECPrivateKeySpec bcPrivateKeySpec =
                new org.bouncycastle.jce.spec.ECPrivateKeySpec(privateKeyBigInteger, bcEcSpec);

            return keyFactory.generatePrivate(bcPrivateKeySpec);
        } catch (NoSuchProviderException | NoSuchAlgorithmException e) {
            // Fallback to default provider with standard Java specs
            KeyFactory keyFactory = KeyFactory.getInstance("EC");

            // Create standard Java EC parameter spec for secp256k1
            BigInteger p = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
            BigInteger a = BigInteger.ZERO;
            BigInteger b = BigInteger.valueOf(7);
            ECFieldFp field = new ECFieldFp(p);
            EllipticCurve curve = new EllipticCurve(field, a, b);

            BigInteger gx = new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16);
            BigInteger gy = new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16);
            java.security.spec.ECPoint g = new java.security.spec.ECPoint(gx, gy);

            BigInteger n = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
            int h = 1;

            java.security.spec.ECParameterSpec javaEcSpec =
                new java.security.spec.ECParameterSpec(curve, g, n, h);

            // Create standard Java EC Private Key Spec
            java.security.spec.ECPrivateKeySpec javaPrivateKeySpec =
                new java.security.spec.ECPrivateKeySpec(privateKeyBigInteger, javaEcSpec);

            return keyFactory.generatePrivate(javaPrivateKeySpec);
        }
    }

    public static KeyPair createKeyPair(byte[] privateKeyBytes) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException, InvalidKeySpecException {
        if (privateKeyBytes.length != 32) {
            throw new IllegalArgumentException("Private key must be 32 bytes");
        }

        // Convert byte array to BigInteger
        BigInteger privateKeyBigInteger = new BigInteger(1, privateKeyBytes);

        // Try BC provider first
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");

            // Get curve parameters for BC
            X9ECParameters params = SECNamedCurves.getByName("secp256k1");
            ECDomainParameters domainParams = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(), params.getH());
            org.bouncycastle.jce.spec.ECParameterSpec bcEcSpec =
                new org.bouncycastle.jce.spec.ECParameterSpec(params.getCurve(), params.getG(), params.getN(), params.getH());

            // Create BC private key spec
            org.bouncycastle.jce.spec.ECPrivateKeySpec bcPrivateKeySpec =
                new org.bouncycastle.jce.spec.ECPrivateKeySpec(privateKeyBigInteger, bcEcSpec);

            // Derive public key using BC
            org.bouncycastle.math.ec.ECPoint q = domainParams.getG().multiply(privateKeyBigInteger);
            org.bouncycastle.jce.spec.ECPublicKeySpec bcPublicKeySpec =
                new org.bouncycastle.jce.spec.ECPublicKeySpec(q, bcEcSpec);

            PrivateKey privateKey = keyFactory.generatePrivate(bcPrivateKeySpec);
            PublicKey publicKey = keyFactory.generatePublic(bcPublicKeySpec);

            return new KeyPair(publicKey, privateKey);
        } catch (NoSuchProviderException | NoSuchAlgorithmException e) {
            // Fallback to default provider with standard Java specs
            KeyFactory keyFactory = KeyFactory.getInstance("EC");

            // Create standard Java EC parameter spec for secp256k1
            BigInteger p = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
            BigInteger a = BigInteger.ZERO;
            BigInteger b = BigInteger.valueOf(7);
            ECFieldFp field = new ECFieldFp(p);
            EllipticCurve curve = new EllipticCurve(field, a, b);

            BigInteger gx = new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16);
            BigInteger gy = new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16);
            java.security.spec.ECPoint g = new java.security.spec.ECPoint(gx, gy);

            BigInteger n = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
            int h = 1;

            java.security.spec.ECParameterSpec javaEcSpec =
                new java.security.spec.ECParameterSpec(curve, g, n, h);

            // Create standard Java private key spec
            java.security.spec.ECPrivateKeySpec javaPrivateKeySpec =
                new java.security.spec.ECPrivateKeySpec(privateKeyBigInteger, javaEcSpec);

            // Derive public key - use manual calculation since we're in fallback mode
            // This is a simplified version that doesn't handle all edge cases
            PrivateKey privateKey = keyFactory.generatePrivate(javaPrivateKeySpec);

            // For public key, calculate the point using BC math but create Java spec
            X9ECParameters params = SECNamedCurves.getByName("secp256k1");
            org.bouncycastle.math.ec.ECPoint q = params.getG().multiply(privateKeyBigInteger);
            byte[] publicKeyBytes = q.getEncoded(false);

            // Parse the uncompressed public key (skip 0x04 prefix)
            byte[] xBytes = new byte[32];
            byte[] yBytes = new byte[32];
            System.arraycopy(publicKeyBytes, 1, xBytes, 0, 32);
            System.arraycopy(publicKeyBytes, 33, yBytes, 0, 32);

            BigInteger x = new BigInteger(1, xBytes);
            BigInteger y = new BigInteger(1, yBytes);
            java.security.spec.ECPoint javaPoint = new java.security.spec.ECPoint(x, y);

            java.security.spec.ECPublicKeySpec javaPublicKeySpec =
                new java.security.spec.ECPublicKeySpec(javaPoint, javaEcSpec);

            PublicKey publicKey = keyFactory.generatePublic(javaPublicKeySpec);

            return new KeyPair(publicKey, privateKey);
        }
    }

    public static void main(String[] args) throws Exception {
        testEncryptAndDecrypt();

        testDecryhptCipherFromFreeSignV1();


    }

    public static byte[] encrypt(byte[] msg,byte[] pubkey){
        PublicKey publicKey;
        try {
            publicKey = createPublicKey(pubkey);
            return Bitcore.encrypt(msg, publicKey );
        } catch (Exception e) {
            return null;
        }
    }

    

    private static void testEncryptAndDecrypt() throws Exception {
        byte[] privateKeyBytes = KeyTools.getPrikey32("L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8");
        KeyPair keyPair = createKeyPair(privateKeyBytes);

        String message = "Hello, ECIES!";
        byte[] encrypted = encrypt(message.getBytes(), keyPair.getPublic());
        // byte[] encrypted = encrypt(message.getBytes(), keyPair.getPublic());

        System.out.println("Encrypted: " + Hex.toHexString(encrypted));
        System.out.println("Cipher: "+Base64.getEncoder().encodeToString(encrypted));

        byte[] decrypted = decrypt(encrypted, privateKeyBytes);//keyPair.getPrivate());
        System.out.println("Decrypted: " + new String(decrypted));
    }

    private static void testDecryhptCipherFromFreeSignV1() throws Exception {
        String cipher = "AjTU0rGQvDxhCs3F5x4Pcz3Bsiiry2LryPcKcPIZ2iDsD68U5he9FkM6AVUzEHTjmfBLkhfFu7rv4fveoyMi5YH+wQoiWDxgs/MYjGZBL/Fuq6XZ6IOCXfWyfwphE4uxhEg5TD9ZBRsrJbNxwbdfee5ev5Gvc8kwYROycs0sAG3rNdoJbEZZ7bs2DqvHbAWdG7w4gYLhP9o+C/xVTZHz7Ks9VHb6i04/1at40etlWXxPWSvkdDWxTtyWSSsY2jrbYjfe+ytXQRTRY4gYQdwg+9s=";
                //"A1f7bKbSMYNVvfgTGY8yf+bD4RQEzouTnkJB7bDNRc1zZCYWTy+duQECOa+CMhkB7PVua6YAFm1UQdTsHRIML/ehzdic3tn+Vm11IMsuE0j6dgoiZMcja0fcRJifSieKqA==";

        byte[] cipherBytes = Base64.getDecoder().decode(cipher);

        byte[] privateKeyBytes = KeyTools.getPrikey32("L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8");
        System.out.println("Pubkey B:"+Hex.toHexString(KeyTools.prikeyToPubkey(privateKeyBytes)));

        assert privateKeyBytes != null;

        // You need to provide the correct private key here
//        PrivateKey privateKey = createPrivateKey(privateKeyBytes);// ... load your private key here

        byte[] decrypted = decrypt(cipherBytes, privateKeyBytes);
        System.out.println("Decrypted: " + new String(decrypted));

        CryptoDataByte bitcoreCipher = parseBitcoreCipher(cipher);
        System.out.println(bitcoreCipher.toNiceJson());

        byte[] cipher2 = fromCryptoDataByte(bitcoreCipher);
        System.out.println("Cipher2:"+Base64.getEncoder().encodeToString(cipher2));
    }

    public static CryptoDataByte parseBitcoreCipher(String base64Cipher) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(AlgorithmId.BitCore_EccAes256);
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        // Decode the Base64 string
        byte[] decodedCipher = Base64.getDecoder().decode(base64Cipher);

        // Extract the public key (first 33 bytes)
        byte[] pubkeyBytes = Arrays.copyOfRange(decodedCipher, 0, 33);
        cryptoDataByte.setPubkeyA(pubkeyBytes);



        // Extract the IV (next 16 bytes)
        byte[] ivBytes = Arrays.copyOfRange(decodedCipher, 33, 49);
        cryptoDataByte.setIv(ivBytes);

        // The rest is the ciphertext
        byte[] ciphertextBytes = Arrays.copyOfRange(decodedCipher, 49, decodedCipher.length);
        cryptoDataByte.setCipher(ciphertextBytes);

        return cryptoDataByte;
    }

    public static byte[] fromCryptoDataByte(CryptoDataByte cryptoDataByte) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write(cryptoDataByte.getPubkeyA());
            byteArrayOutputStream.write(cryptoDataByte.getIv());
            byteArrayOutputStream.write(cryptoDataByte.getCipher());
        } catch (IOException e) {
            // Should not happen with ByteArrayOutputStream
        }
        return byteArrayOutputStream.toByteArray();
    }

    public static PublicKey createPublicKey(byte[] publicKeyBytes) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
        // Try BC provider first
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("EC", "BC");

            // Get curve parameters for BC
            X9ECParameters params = SECNamedCurves.getByName("secp256k1");
            org.bouncycastle.jce.spec.ECParameterSpec bcEcSpec =
                new org.bouncycastle.jce.spec.ECParameterSpec(params.getCurve(), params.getG(), params.getN(), params.getH());

            // Create point from public key bytes
            org.bouncycastle.math.ec.ECPoint point = params.getCurve().decodePoint(publicKeyBytes);

            // Create BC public key spec
            org.bouncycastle.jce.spec.ECPublicKeySpec bcPublicKeySpec =
                new org.bouncycastle.jce.spec.ECPublicKeySpec(point, bcEcSpec);

            return keyFactory.generatePublic(bcPublicKeySpec);
        } catch (NoSuchProviderException | NoSuchAlgorithmException e) {
            // Fallback to default provider with standard Java specs
            KeyFactory keyFactory = KeyFactory.getInstance("EC");

            // Create standard Java EC parameter spec for secp256k1
            BigInteger p = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16);
            BigInteger a = BigInteger.ZERO;
            BigInteger b = BigInteger.valueOf(7);
            ECFieldFp field = new ECFieldFp(p);
            EllipticCurve curve = new EllipticCurve(field, a, b);

            BigInteger gx = new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16);
            BigInteger gy = new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16);
            java.security.spec.ECPoint g = new java.security.spec.ECPoint(gx, gy);

            BigInteger n = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
            int h = 1;

            java.security.spec.ECParameterSpec javaEcSpec =
                new java.security.spec.ECParameterSpec(curve, g, n, h);

            // Parse public key bytes and create standard Java EC point
            if (publicKeyBytes.length == 33 && (publicKeyBytes[0] == 0x02 || publicKeyBytes[0] == 0x03)) {
                // Compressed format - need to decompress
                X9ECParameters params = SECNamedCurves.getByName("secp256k1");
                org.bouncycastle.math.ec.ECPoint bcPoint = params.getCurve().decodePoint(publicKeyBytes);
                byte[] uncompressed = bcPoint.getEncoded(false);

                // Skip the 0x04 prefix and extract x, y coordinates
                byte[] xBytes = new byte[32];
                byte[] yBytes = new byte[32];
                System.arraycopy(uncompressed, 1, xBytes, 0, 32);
                System.arraycopy(uncompressed, 33, yBytes, 0, 32);

                BigInteger x = new BigInteger(1, xBytes);
                BigInteger y = new BigInteger(1, yBytes);
                java.security.spec.ECPoint javaPoint = new java.security.spec.ECPoint(x, y);

                java.security.spec.ECPublicKeySpec javaPublicKeySpec =
                    new java.security.spec.ECPublicKeySpec(javaPoint, javaEcSpec);

                return keyFactory.generatePublic(javaPublicKeySpec);
            } else if (publicKeyBytes.length == 65 && publicKeyBytes[0] == 0x04) {
                // Uncompressed format
                byte[] xBytes = new byte[32];
                byte[] yBytes = new byte[32];
                System.arraycopy(publicKeyBytes, 1, xBytes, 0, 32);
                System.arraycopy(publicKeyBytes, 33, yBytes, 0, 32);

                BigInteger x = new BigInteger(1, xBytes);
                BigInteger y = new BigInteger(1, yBytes);
                java.security.spec.ECPoint javaPoint = new java.security.spec.ECPoint(x, y);

                java.security.spec.ECPublicKeySpec javaPublicKeySpec =
                    new java.security.spec.ECPublicKeySpec(javaPoint, javaEcSpec);

                return keyFactory.generatePublic(javaPublicKeySpec);
            } else {
                throw new InvalidKeySpecException("Invalid public key format");
            }
        }
    }
}