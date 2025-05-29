package com.fc.fc_ajdk.data.apipData;


import com.fc.fc_ajdk.core.fch.Inputer;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.core.crypto.old.EccAes256K1P7;
import com.fc.fc_ajdk.utils.Hex;

import java.io.BufferedReader;
import java.util.Map;

import static com.fc.fc_ajdk.core.crypto.KeyTools.prikeyToFid;
import static com.fc.fc_ajdk.core.crypto.KeyTools.prikeyToPubkey;

public class FidInfo {
    private Map<String, String> addresses;
    private byte[] prikey;
    private String fid;
    private String prikeyCipher;
    private String pubkey;
    private String[] cids;

    public FidInfo() {
    }

    public FidInfo(byte[] prikey) {
        this.prikey = prikey;
        this.fid = prikeyToFid(prikey);
        this.pubkey = Hex.toHex(prikeyToPubkey(prikey));
        this.addresses = KeyTools.pubkeyToAddresses(pubkey);
    }

    public FidInfo(byte[] prikey, byte[] symkey) {
        this.prikey = prikey;
        this.fid = prikeyToFid(prikey);
        this.pubkey = Hex.toHex(prikeyToPubkey(prikey));
        this.addresses = KeyTools.pubkeyToAddresses(pubkey);
        this.prikeyCipher = EccAes256K1P7.encryptWithSymkey(prikey, symkey);
    }

    public static FidInfo inputPrikey(BufferedReader br, byte[] initSymkey) {

        byte[] prikey32;

        while (true) {
            prikey32 = Inputer.importOrCreatePrikey(br);
            if (prikey32 == null) return null;

            FidInfo fidInfo = new FidInfo(prikey32, initSymkey);
            if (fidInfo.getFid() == null) {
                System.out.println("Wrong input. Try again.");
                continue;
            }
            return fidInfo;
        }
    }

    public Map<String, String> getAddresses() {
        return addresses;
    }

    public void setAddresses(Map<String, String> addresses) {
        this.addresses = addresses;
    }

    public byte[] getPrikey() {
        return prikey;
    }

    public void setPrikey(byte[] prikey) {
        this.prikey = prikey;
    }

    public String getFid() {
        return fid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getPrikeyCipher() {
        return prikeyCipher;
    }

    public void setPrikeyCipher(String prikeyCipher) {
        this.prikeyCipher = prikeyCipher;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public String[] getCids() {
        return cids;
    }

    public void setCids(String[] cids) {
        this.cids = cids;
    }
}
