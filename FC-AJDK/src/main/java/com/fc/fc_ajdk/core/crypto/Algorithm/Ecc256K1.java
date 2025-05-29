package com.fc.fc_ajdk.core.crypto.Algorithm;

import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class Ecc256K1 {
    public static byte[] getSharedSecret(byte[] prikeyBytes, byte[] pubkeyBytes) {
        ECPrivateKeyParameters prikey = KeyTools.prikeyFromBytes(prikeyBytes);
        ECPublicKeyParameters pubkey = KeyTools.pubkeyFromBytes(pubkeyBytes);
        ECDHBasicAgreement agreement = new ECDHBasicAgreement();
        agreement.init(prikey);
        return agreement.calculateAgreement(pubkey).toByteArray();
    }
    @NotNull
    public static byte[] sharedSecretToSymkey(byte[] sharedSecret, byte[] nonce) {
        byte[] symkey;
        byte[] secretHashWithNonce = new byte[sharedSecret.length+ nonce.length];
        System.arraycopy(nonce, 0, secretHashWithNonce, 0, nonce.length);
        System.arraycopy(sharedSecret, 0, secretHashWithNonce, nonce.length, sharedSecret.length);
        byte[] hash = Encryptor.sha512(secretHashWithNonce);

        symkey = new byte[32];
        System.arraycopy(hash,0,symkey,0,32);
        return symkey;
    }

    public static byte[] asyKeyToSymkey(byte[] prikey, byte[] pubkey,byte[]iv) {
        byte[] symkey;

        byte[] sharedSecret = getSharedSecret(prikey, pubkey);

        symkey = sharedSecretToSymkey(sharedSecret, iv);

        Arrays.fill(sharedSecret, (byte) 0);
        return symkey;
    }


}
