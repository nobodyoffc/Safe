package com.fc.fc_ajdk.app;

import static com.fc.fc_ajdk.core.crypto.KeyTools.prikeyToFid;
import static com.fc.fc_ajdk.core.crypto.KeyTools.prikeyToPubkey;

import com.fc.fc_ajdk.core.crypto.Base58;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.ui.Inputer;
import com.fc.fc_ajdk.ui.Menu;
import com.fc.fc_ajdk.ui.Shower;
import com.fc.fc_ajdk.utils.Hex;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.MainNetParams;

import java.io.BufferedReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class CryptoSignMethods {

    public static void showPrikeyInfo(BufferedReader br, byte[] prikey) {
        if (prikey == null) return;
        String fid = prikeyToFid(prikey);

        Shower.printUnderline(10);
        System.out.println("* FID:" + fid);
        System.out.println("* Pubkey:" + Hex.toHex(prikeyToPubkey(prikey)));

        String prikey32 = Hex.toHex(prikey);
        System.out.println("* Prikey in hex:" + prikey32);
        System.out.println("* Prikey WIF compressed:" + KeyTools.prikey32To38WifCompressed(prikey32));
        System.out.println("* Prikey WIF:" + KeyTools.prikey32To37(prikey32));
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
    }

    public static Map<String, String> convert(String fidOrPubkey) {
        Map<String, String> addrMap = null;
        if (fidOrPubkey.length() < 66) {
            addrMap = convertAddresses(fidOrPubkey);

        } else if (KeyTools.isValidPubkey(fidOrPubkey)) {
            String pubkey = KeyTools.getPubkey33(fidOrPubkey);
            addrMap = KeyTools.pubkeyToAddresses(pubkey);
        }
        return addrMap;
    }

    public static Map<String,String> convertAddresses(String id){
        Map<String,String>addrMap;
        if(id.startsWith("F")
                || id.startsWith("1")
                || id.startsWith("D")
                || id.startsWith("L")) {
            byte[] hash160 = KeyTools.addrToHash160(id);
            addrMap = KeyTools.hash160ToAddresses(hash160);
        } else if(id.startsWith("bc")) {
            byte[] hash160 = KeyTools.bech32BtcToHash160(id);
            addrMap = KeyTools.hash160ToAddresses(hash160);
        } else if(id.startsWith("bitcoincash") || id.startsWith("bch")||id.startsWith("q")||id.startsWith("p")) {
            byte[] hash160 = KeyTools.bech32BchToHash160(id);
            addrMap = KeyTools.hash160ToAddresses(hash160);
        } else{
            System.out.println("Wrong Address. Try again.");
            return null;
        }
        return addrMap;
    }

    public static void findNiceFid(BufferedReader br) {
        String input;
        SimpleDateFormat sdf = new SimpleDateFormat();
        Date begin = new Date();
        System.out.println(sdf.format(begin));
        while (true) {
            input = Inputer.inputString(br, "Input 4 characters you want them to be in the end of your FID, enter to exit:");
            if ("".equals(input)) {
                return;
            }
            if (input.length() != 4) {
                System.out.println("Input 4 characters you want them be in the end of your fid:");
                continue;
            }
            if (!Base58.isBase58Encoded(input)) {
                System.out.println("It's not a Base58 encoded. The string can't contain '0', 'O', 'l', 'L', '+', '/'.");
                continue;
            }
            break;
        }
        long i = 0;
        long j = 0;
        System.out.println("Finding...");
        while (true) {
            ECKey ecKey = new ECKey();

            String fid = KeyTools.pubkeyToFchAddr(ecKey.getPubKey());
            if (fid.substring(30).equals(input)) {
                System.out.println("----");
                System.out.println("FID:" + fid);
                System.out.println("Pubkey: " + ecKey.getPublicKeyAsHex());
                System.out.println("PrikeyHex: " + ecKey.getPrivateKeyAsHex());
                System.out.println("PrikeyBase58: " + ecKey.getPrivateKeyEncoded(MainNetParams.get()));
                System.out.println("----");
                System.out.println("Begin at: " + sdf.format(begin));
                Date end = new Date();
                System.out.println("End at: " + sdf.format(end));
                System.out.println("----");
                break;
            }
            i++;
            if (i % 1000000 == 0) {
                j++;
                System.out.println(sdf.format(new Date()) + ": " + j + " million tryings.");
            }
        }
    }
}
