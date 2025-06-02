package com.fc.fc_ajdk.app;

import android.content.Context;
import android.app.Activity;

import com.fc.fc_ajdk.config.Starter;
import com.fc.fc_ajdk.core.crypto.EncryptType;
import com.fc.fc_ajdk.data.fcData.KeyInfo;

import com.fc.fc_ajdk.data.fchData.Multisign;
import com.fc.fc_ajdk.ui.AndroidMenu;
import com.fc.fc_ajdk.config.Configure;
import com.fc.fc_ajdk.config.Settings;
import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.core.crypto.Algorithm.Bitcore;

import com.fc.fc_ajdk.core.crypto.Base58;
import com.fc.fc_ajdk.core.crypto.CryptoDataByte;
import com.fc.fc_ajdk.core.crypto.Decryptor;
import com.fc.fc_ajdk.core.crypto.Encryptor;
import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.core.fch.RawTxInfo;
import com.fc.fc_ajdk.core.fch.TxCreator;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fcData.FcEntity;
import com.fc.fc_ajdk.data.fcData.SecretDetail;
import com.fc.fc_ajdk.data.fcData.Signature;
import com.fc.fc_ajdk.data.fchData.RawTxForCsV1;
import com.fc.fc_ajdk.ui.Inputer;
import com.fc.fc_ajdk.ui.Menu;
import com.fc.fc_ajdk.ui.Shower;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.FileUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.utils.QRCodeScanner;
import com.fc.fc_ajdk.utils.QRCodeUtils;
import com.fc.fc_ajdk.utils.StringUtils;
import com.fc.fc_ajdk.ui.UIManager;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.fch.FchMainNetwork;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.fc.fc_ajdk.app.CryptoSignMethods.convert;
import static com.fc.fc_ajdk.app.CryptoSignMethods.findNiceFid;
import static com.fc.fc_ajdk.app.CryptoSignMethods.showPrikeyInfo;
import static com.fc.fc_ajdk.core.fch.Inputer.inputPrikeyHexOrBase58;
import static com.fc.fc_ajdk.handlers.CashHandler.showRawMultiSignTxInfo;
import static com.fc.fc_ajdk.ui.Inputer.askIfYes;
import static com.fc.fc_ajdk.ui.Inputer.inputStringMultiLine;
import static com.fc.fc_ajdk.ui.Shower.DEFAULT_PAGE_SIZE;
import static com.fc.fc_ajdk.constants.Strings.DOT_JSON;
import static com.fc.fc_ajdk.constants.Tickers.BCH;
import static com.fc.fc_ajdk.constants.Tickers.FCH;
import static com.fc.fc_ajdk.utils.FileUtils.getAvailableFile;

public class CryptoSign extends FcApp {

    private final Context context;
    private final Activity activity;
    private final UIManager uiManager;

    private final AndroidMenu menu;

    private static final String APP_NAME = "CryptoSign";
    private final Settings settings;
    private final byte[] symkey;
    private static BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    private QRCodeScanner scanner;

    public CryptoSign(Context context, Activity activity, Settings settings) {
        this.context = context;
        this.activity = activity;
        this.settings = settings;
        this.symkey = settings.getSymkey();
        this.uiManager = new UIManager(context, activity);
        this.menu = new AndroidMenu(context, activity);
    }


    public static void main(String[] args) throws Exception {
        Menu.welcome(APP_NAME);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Map<String,Object> settingMap = new HashMap<>();
        Settings settings = Starter.startTool(null,APP_NAME, settingMap, br, null, null);
        if (settings == null) return;

        CryptoSign cryptoSign = new CryptoSign(null,null,settings);
        cryptoSign.menu();

    }

    public void menu() throws Exception {
        Menu menu = new Menu("Home", this::close);

        menu.add("My Keys", () -> keyManagement(br));
        menu.add("Secret", () -> secret(br));
        menu.add("Key Tools",()-> keyTools(br));
        menu.add("Sign TX", () -> signTx( br));
        menu.add("Sign Multisig TX", () -> multiSign(br));
        menu.add("Sign Message", () -> signMsg(br));
        menu.add("Verify Signature", () -> verify(br));
        menu.add("Encrypt", () -> encrypt(br));
        menu.add("Decrypt", () -> decrypt(br));
        menu.add("Hash", () -> hash(br));
        menu.add("Decode", () -> decode(br));
        menu.add("QR code", () -> qrCode(br));
        menu.add("Settings", () -> setting(br));

        menu.showAndSelect(br);
    }

    public static void hash(BufferedReader br) {
        Menu menu = new Menu("Hash");
        menu.add("SHA256 String")
                .add("SHA256 File")
                .add("SHA256x2 String")
                .add("SHA256x2 File")
                .add("SHA512 String")
                .add("SHA512x2 String")
                .add("RIPEMD160 String")
                .add("SHA3 String");

        while (true) {
            System.out.println(" << Hash Functions >>");
            menu.show();
            int choice = menu.choose(br);
            switch (choice) {
                case 1 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null) break;
                    String hash = Hash.sha256(text);
                    System.out.println("raw string: " + text);
                    System.out.println("sha256:");
                    System.out.println("----");
                    System.out.println(hash);
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 2 -> {
                    while (true) {
                        File file = inputFilePath(br);
                        if (file == null) break;
                        byte[] hash = Hash.sha256Stream(file);
                        if (hash == null) {
                            System.out.println("Error calculating SHA-256 hash.");
                            break;
                        }
                        System.out.println("file:" + file.getAbsolutePath());
                        System.out.println("sha256:");
                        System.out.println("----");
                        System.out.println(Hex.toHex(hash));
                        System.out.println("----");
                        Menu.anyKeyToContinue(br);
                    }
                }
                case 3 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null) break;
                    String hash = Hash.sha256x2(text);
                    System.out.println("----");
                    System.out.println("raw string: " + text);
                    System.out.println("sha256x2:");
                    System.out.println("----");
                    System.out.println(hash);
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 4 -> {
                    while (true) {
                        File file = inputFilePath(br);
                        if (file == null) break;
                        try {
                            String hash = Hash.sha256x2(file);
                            if (hash == null) {
                                System.out.println("Error calculating SHA-256x2 hash.");
                                break;
                            }
                            System.out.println("file:" + file.getAbsolutePath());
                            System.out.println("sha256x2:");
                            System.out.println("----");
                            System.out.println(hash);
                            System.out.println("----");
                            Menu.anyKeyToContinue(br);
                        } catch (IOException e) {
                            System.out.println("Error reading file: " + e.getMessage());
                            break;
                        }
                    }
                }
                case 5 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null || text.isEmpty()) break;
                    byte[] hashBytes = Hash.sha512(text.getBytes());
                    System.out.println("----");
                    System.out.println("raw string: " + text);
                    System.out.println("sha512:");
                    System.out.println("----");
                    System.out.println(Hex.toHex(hashBytes));
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 6 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null || text.isEmpty()) break;
                    String hash = Hash.sha512x2(text);
                    System.out.println("----");
                    System.out.println("raw string: " + text);
                    System.out.println("sha512x2:" + hash);
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 7 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null) break;
                    byte[] hashBytes = Hash.Ripemd160(text.getBytes());
                    System.out.println("----");
                    System.out.println("raw string: " + text);
                    System.out.println("ripemd160:");
                    System.out.println("----");
                    System.out.println(Hex.toHex(hashBytes));
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 8 -> {
                    String text = Inputer.inputStringMultiLine(br);
                    if (text == null || text.isEmpty()) break;
                    String hash = Hash.sha3String(text);
                    System.out.println("raw string: " + text);
                    System.out.println("sha3:");
                    System.out.println("----");
                    System.out.println(hash);
                    System.out.println("----");
                    Menu.anyKeyToContinue(br);
                }
                case 0 -> {
                    return;
                }
            }
        }
    }

    private static File inputFilePath(BufferedReader br) {
        System.out.println("Input the full path of the file to be hashed, enter to exit:");
        String filePath = Inputer.inputString(br);
        if ("".equals(filePath)) return null;

        File file = new File(filePath);
        if (file.isDirectory()) {
            System.out.println("It's a directory.");
            return null;
        }
        if (!file.exists()) {
            System.out.println("File does not exist.");
            return null;
        }
        return file;
    }

    private void keyManagement(BufferedReader br) {
        Menu keyManagementMenu = new Menu("My keys");
        keyManagementMenu.add("List Keys", () -> listKeys(br));
        keyManagementMenu.add("New Key", () -> newKey(br));
        keyManagementMenu.add("Import Key", () -> importKey(br));
        keyManagementMenu.add("Edit key info", () -> editKeyInfo(br));
        keyManagementMenu.add("Remove key", () -> removeKey(br));
        keyManagementMenu.add("Add watch only key", () -> addWatchOnlyKey(br));
        keyManagementMenu.showAndSelect(br);
    }

    private void removeKey(BufferedReader br) {
        List<KeyInfo> keyInfoList = KeyInfo.listFromFile(KeyInfo.class);
        if(keyInfoList ==null || keyInfoList.isEmpty()) {
            System.out.println("No keys found.");
            return;
        }
        List<KeyInfo> chosen = KeyInfo.showList(keyInfoList, br);
        removeKey(br, keyInfoList,chosen);
    }

    private void editKeyInfo(BufferedReader br) {
        List<KeyInfo> keyInfoList = KeyInfo.listFromFile(KeyInfo.class);
        if(keyInfoList ==null || keyInfoList.isEmpty()) {
            System.out.println("No keys found.");
            return;
        }
        List<KeyInfo> chosen = KeyInfo.showList(keyInfoList, br);
        if(chosen==null || chosen.isEmpty())return;
        for(KeyInfo keyInfo : chosen){
            System.out.println(keyInfo.getId()+":");
            try{
                keyInfo.setLabel(Inputer.promptAndUpdate(br, "Label", keyInfo.getLabel()));
                keyInfo.setWatchOnly(Inputer.promptAndUpdate(br, "Watch only", keyInfo.getWatchOnly()));
                if(Boolean.TRUE.equals(keyInfo.getWatchOnly())){
                    keyInfo.setPrikeyCipher(null);
                    keyInfo.setPrikeyBytes(null);
                }
                keyInfo.setNobody(Inputer.promptAndUpdate(br, "Nobody", keyInfo.getNobody()));
            }catch(IOException e){
                System.out.println("Failed to update key info.");
            }
        }
        KeyInfo.listToFile(keyInfoList, KeyInfo.class);
        System.out.println("Key information updated.");
        Menu.anyKeyToContinue(br);
    }

    private void addWatchOnlyKey(BufferedReader br) {
        List<KeyInfo> keyInfoList = KeyInfo.listFromFile(KeyInfo.class);
        if(keyInfoList == null) keyInfoList = new ArrayList<>();
        String pubkey = Inputer.inputString(br, "Input the public key:");
        String label = Inputer.inputString(br, "Input a label for the key:");
        KeyInfo keyInfo = KeyInfo.newKeyInfo(label, pubkey);
        keyInfo.setWatchOnly(true);
        keyInfoList.add(keyInfo);
        KeyInfo.listToFile(keyInfoList, KeyInfo.class);
    }
    private void listKeys(BufferedReader br) {
        List<KeyInfo> keyInfoList = KeyInfo.listFromFile(KeyInfo.class);
        if(keyInfoList ==null || keyInfoList.isEmpty()) {
            System.out.println("No keys found.");
            return;
        }
        List<KeyInfo> chosen = KeyInfo.showList(keyInfoList, br);
        if(chosen==null || chosen.isEmpty())return;
        String chosenOp = Inputer.chooseOne(new String[]{"Show Key Info", "Delete","Show Prikey"}, null,"Select an operation", br);
        if(chosenOp==null || chosenOp.isEmpty())return;
        switch (chosenOp) {
            case "Show Key Info" -> showKeyInfoList(chosen,br);
            case "Show Prikey" -> {
                System.out.println("To show prikey, you have to ensure the security of your environment.");
                if(!askIfYes(br, "Show prikey?")) break;

                for (KeyInfo keyInfo : chosen) {
                    if(Boolean.TRUE.equals(keyInfo.getWatchOnly())){
                        System.out.println(keyInfo.getId() +" is a watching FID.");
                        break;
                    }
                    byte[] prikey = Decryptor.decryptPrikey(keyInfo.getPrikeyCipher(), settings.getSymkey());
                    if (prikey != null) {
                        String prikeyHex = Hex.toHex(prikey);

                        System.out.println("FID:"+ keyInfo.getId());

                        System.out.println("Prikey in hex:" + Hex.toHex(prikey));
                        QRCodeUtils.generateQRCode(prikeyHex);

                        String prikeyBase58 = Base58.encode(prikey);
                        System.out.println("prikey in Base58:" + prikeyBase58);
                        QRCodeUtils.generateQRCode(prikeyBase58);
                    }
                }
                System.out.println();
            }
            case "Delete" -> {
                removeKey(br, keyInfoList, chosen);
            }
        }
        Menu.anyKeyToContinue(br);
    }

    private static void removeKey(BufferedReader br, List<KeyInfo> keyInfoList, List<KeyInfo> chosen) {
        if (askIfYes(br, "Are you sure you want to delete these keys?")) {
            for (KeyInfo keyInfo : chosen) {
                keyInfoList.remove(keyInfo);
            }
            KeyInfo.listToFile(keyInfoList, KeyInfo.class);
        }
    }


    public static void verify(BufferedReader br) {
        System.out.println("Set signature.");
        String input = Inputer.inputStringMultiLine(br);
        if (input == null || "".equals(input)) return;

        Signature signature = null;

        try {
            signature = Signature.parseSignature(input);

            if(signature!=null && signature.getKey()==null && signature.getAlg().equals(AlgorithmId.FC_Sha256SymSignMsg_No1_NrC7)){
                byte[] key = Inputer.inputSymkey32(br,"Input the symkey to verify the signature:");
                signature.setKey(key);
            }
        }catch (Exception e){
            System.out.println("Failed to read signature:"+e.getMessage());
        }

        if (signature == null) {
            System.out.println("Parse signature wrong.");
            return;
        }

        System.out.println("Resulet:"+signature.verify());
        Menu.anyKeyToContinue(br);
    }


    private void showKeyInfoList(List<KeyInfo> keyInfoList, BufferedReader br){
        for(KeyInfo keyInfo : keyInfoList){
            KeyInfo keyInfoWithoutCipher = new KeyInfo();
            keyInfoWithoutCipher.setLabel(keyInfo.getLabel());
            keyInfoWithoutCipher.setWatchOnly(keyInfo.getWatchOnly());
            keyInfoWithoutCipher.setNobody(keyInfo.getNobody());
            if(keyInfo.getPubkey()!=null) keyInfoWithoutCipher.setPubkey(keyInfo.getPubkey());
            keyInfoWithoutCipher.setId(keyInfo.getId());
            if(keyInfo.getBtcAddr()!=null) keyInfoWithoutCipher.setBtcAddr(keyInfo.getBtcAddr());
            if(keyInfo.getEthAddr()!=null) keyInfoWithoutCipher.setEthAddr(keyInfo.getEthAddr());
            if(keyInfo.getBchAddr()!=null) keyInfoWithoutCipher.setBchAddr(keyInfo.getBchAddr());
            if(keyInfo.getLtcAddr()!=null) keyInfoWithoutCipher.setLtcAddr(keyInfo.getLtcAddr());
            if(keyInfo.getDogeAddr()!=null) keyInfoWithoutCipher.setDogeAddr(keyInfo.getDogeAddr());
            if(keyInfo.getTrxAddr()!=null) keyInfoWithoutCipher.setTrxAddr(keyInfo.getTrxAddr());

            System.out.println(keyInfoWithoutCipher.toNiceJson());
            if(!askIfYes(br, "Stop?"))continue;
            break;
        }
    }

    private void newKey(BufferedReader br) {
        List<KeyInfo> keyInfoList = KeyInfo.listFromFile(KeyInfo.class);
        if(keyInfoList ==null) keyInfoList = new ArrayList<>();
        String op = Inputer.chooseOne(new String[]{"Random New","Import Prikey","Secret Words"}, null, "Select an algorithm", br);
        String label;
        byte[] prikey = null;
        ECKey ecKey;
        switch (op) {
            case "Random New" -> {
                ecKey = KeyTools.genNewFid(br);
                prikey = ecKey.getPrivKeyBytes();
            }
            case "Secret Words" -> {
                String secretWords;
                while (true) {
                    secretWords = Inputer.inputString(br, "Input the secret words:");
                    if (secretWords == null) return;
                    if (checkSecretWords(secretWords, br)) break;
                    System.out.println("Invalid secret words.");
                }
                ecKey = KeyTools.secretWordsToPrikey(secretWords);
                prikey = ecKey.getPrivKeyBytes();
            }
            case "Import Prikey" -> {
                if(scanner!=null && askIfYes(br,"Scan prikey QR code?"))
                    prikey = scanPrikeyQR();
                else prikey = KeyTools.importOrCreatePrikey(br);
            }
        }
        if(prikey==null)return;

        label = Inputer.inputString(br, "Input a label for the key:");

        KeyInfo keyInfo = KeyInfo.newKeyInfo(label,prikey,settings.getSymkey());
        if(askIfYes(br,"Is it a nobody(prikey leaked)?")){
            keyInfo.setNobody(true);
        }
        keyInfoList.add(keyInfo);
        KeyInfo.listToFile(keyInfoList, KeyInfo.class);
        System.out.println("New key "+ keyInfo.getId()+" created successfully.");
    }

    @Nullable
    private byte[] scanPrikeyQR() {
        byte[] prikey;
        System.out.println("Point your QR code to the camera. Scanning for private key...");
        final byte[][] scannedPrikey = new byte[1][]; // Using array to hold result from lambda
        final boolean[] scanComplete = new boolean[1]; // Flag to track scan completion

        // Start scanning for QR codes
        scanner.startScanning(new QRCodeScanner.QRCodeCallback() {
            @Override
            public void onQRCodeDetected(String text) {
                if (scanComplete[0]) return; // Avoid processing multiple times

                try {
                    // Attempt to process the scanned text as a private key
                    if (text.length() == 64 && Hex.isHexString(text)) {
                        // It's likely a hex private key
                        scannedPrikey[0] = Hex.fromHex(text);
                        System.out.println("Private key in hex format detected.");
                    } else if (text.length() >= 50 && text.length() <= 52) {
                        // It's likely a WIF or base58 private key
                        try {
                            byte[] prikey32 = KeyTools.getPrikey32(text);
                            if (prikey32 != null) {
                                scannedPrikey[0] = prikey32;
                                System.out.println("Private key in Base58/WIF format detected.");
                            } else {
                                System.out.println("Invalid Base58/WIF private key format.");
                                return;
                            }
                        } catch (Exception e) {
                            System.out.println("Invalid Base58/WIF private key format: " + e.getMessage());
                            return;
                        }
                    } else {
                        // Try to decode as JSON or other formats if needed
                        System.out.println("Unsupported private key format. Please use hex or WIF format.");
                        return;
                    }

                    scanComplete[0] = true;
                    System.out.println("Private key successfully scanned!");
                } catch (Exception e) {
                    System.out.println("Error processing QR code: " + e.getMessage());
                }
            }

            @Override
            public void onError(Exception e) {
                System.out.println("QR code scanning error: " + e.getMessage());
            }
        }, 200); // Scan every 200 milliseconds

        // Wait for scan to complete or user to cancel
        try {
            System.out.println("Scanning... Press Enter to cancel.");
            while (!scanComplete[0]) {
                if (System.in.available() > 0) {
                    System.in.read();
                    break;
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            System.out.println("Scanning interrupted: " + e.getMessage());
        } finally {
            scanner.stopScanning();
        }

        if (scannedPrikey[0] == null) {
            System.out.println("No valid private key was scanned.");
            return null;
        }

        prikey = scannedPrikey[0];
        return prikey;
    }

    public static boolean checkSecretWords(String secretWords, BufferedReader br) {
        if (secretWords == null || secretWords.trim().isEmpty()) {
            return false;
        }

        // Check minimum length
        if(secretWords.length()<40){
            System.out.println("The number of characters is less than 40.");
            if(!askIfYes(br, "Ignore?"))return false;
        }

        // Check if it's just repeating characters
        if (secretWords.matches("(.)\\1+")) {
            System.out.println("The secret words are too easy to guess due to repeating characters.");
            if(!askIfYes(br, "Ignore?"))return false;
        }

        // Check if it's a simple sequence
        if (secretWords.matches("123456.*") ||
                secretWords.matches("abcdef.*")) {
            System.out.println("The secret words are too easy to guess due to being a simple sequence.");
            if(!askIfYes(br, "Ignore?"))return false;
        }

        // Check if all words are the same
        boolean allSame = true;
        String firstWord = secretWords.split(" ")[0];
        for (String word : secretWords.split(" ")) {
            if (!word.equals(firstWord)) {
                allSame = false;
                break;
            }
        }
        if (allSame) {
            System.out.println("The secret words are too easy to guess due to being all the same.");
            return askIfYes(br, "Ignore?");
        }
        return true;
    }

    private void importKey(BufferedReader br) {
        byte[] prikey = KeyTools.importOrCreatePrikey(br);
        if(prikey==null)return;

        List<KeyInfo> keyInfoList = KeyInfo.listFromFile(KeyInfo.class);
        if(keyInfoList ==null) keyInfoList = new ArrayList<>();
        String label = Inputer.inputString(br, "Input a label for the key:");
        KeyInfo keyInfo = KeyInfo.newKeyInfo(label,prikey,settings.getSymkey());
        if(askIfYes(br,"Is it nobody(prikey leaked)?")){
            keyInfo.setNobody(true);
        }
        int index = FcEntity.updateIntoListById(keyInfo, keyInfoList);
        KeyInfo.listToFile(keyInfoList, KeyInfo.class);
        if(index == keyInfoList.size() - 1)
            System.out.println("Key added successfully.");
        else if(index!= -1)
            System.out.println("Key updated successfully.");
        else System.out.println("Failed to add keyInfo.");
    }

    private void signTx(BufferedReader br) {
        MainNetParams mainnetwork;

        mainnetwork = chooseMainNetWork(br);

        String rawTx;
        KeyInfo keyInfo = chooseKeyInfo();
        if (keyInfo == null) return;
        byte[] prikey = keyInfo.decryptPrikey(settings.getSymkey());
        Transaction transaction;
        RawTxInfo rawTxInfo = null;
        System.out.println("Input the json of off line TX information. 'f' to load from file. Enter to ignore:");
        rawTx = Inputer.inputStringMultiLine(br);
        if(rawTx==null || "".equals(rawTx))return;
        if("f".equals(rawTx)) {
            rawTx = Inputer.inputString(br,"Input the file path:");
            if(rawTx==null || "".equals(rawTx))return;

            try(FileInputStream fis = new FileInputStream(rawTx);) {
                rawTx = new String(BytesUtils.readAllBytes(fis), StandardCharsets.UTF_8);
            }catch (Exception e){
                System.out.println("Failed to load file:"+e.getMessage());
                return;
            }
        }
        try {
            rawTx = rawTx.trim();
            rawTxInfo = JsonUtils.fromJson(rawTx, RawTxInfo.class);
        } catch (Exception e) {
            try{
                List<RawTxForCsV1> rawTxForCsV1List = JsonUtils.listFromJson(rawTx, RawTxForCsV1.class);
                rawTxInfo = RawTxInfo.fromRawTxForCs(rawTxForCsV1List);
            }catch(Exception e2){
                System.out.println("Invalid off line TX information.");
                return;
            }
        }

        if(rawTxInfo == null) {
            if(!askIfYes(br, "Input TX items one by one?"))return;
            rawTxInfo = RawTxInfo.fromUserInput(br, keyInfo.getId());
            System.out.println("Off line Tx data:\n"+ rawTxInfo.toNiceJson());
        }

        transaction = TxCreator.createUnsignedTx(rawTxInfo, mainnetwork);

        if(transaction==null)return;
        String signedTxHex = TxCreator.signTx(prikey,transaction);

        System.out.println("Signed by "+ keyInfo.getId()+".");
        String txBase64 = Base64.getEncoder().encodeToString(Hex.fromHex(signedTxHex));
        System.out.println("Base64:\n"+ txBase64);
        QRCodeUtils.generateQRCode(txBase64);
        System.out.println("Hex:\n"+ signedTxHex);
        QRCodeUtils.generateQRCode(signedTxHex);
        Menu.anyKeyToContinue(br);
    }

    private static MainNetParams chooseMainNetWork(BufferedReader br) {
        String ticker = Inputer.chooseOne(new String[]{FCH, BCH}, null, "Select the network:", br);
        MainNetParams mainnetwork;
        switch(ticker){
            case FCH -> mainnetwork = FchMainNetwork.MAINNETWORK;
            case BCH -> mainnetwork = MainNetParams.get();
            default -> {
                System.out.println("Invalid network.");
                return null;
            }
        }
        return mainnetwork;
    }

    @Nullable
    private KeyInfo chooseKeyInfo() {
        List<KeyInfo> keyInfoList = KeyInfo.listFromFile(KeyInfo.class);
        if(keyInfoList ==null || keyInfoList.isEmpty()){
            System.out.println("No keys found.");
            return null;
        }
        KeyInfo keyInfo = Inputer.chooseOneFromList(keyInfoList, "id", "Select a key to get prikey", br);
        if(keyInfo ==null)return null;
        String prikeyCipher = keyInfo.getPrikeyCipher();
        if(prikeyCipher!=null){
            byte[] prikey = Decryptor.decryptPrikey(prikeyCipher, symkey);
            if(prikey!=null) {
                keyInfo.setPrikeyBytes(prikey);
            }
        }
        return keyInfo;
    }

    private void multiSignTx(BufferedReader br) {
        System.out.println("Input the unsigned data json string ");
        String multiSignDataJson = Inputer.inputStringMultiLine(br);
        do {
            KeyInfo keyInfo = getKeyInfoWithPrikey();
            if (keyInfo == null || keyInfo.getPrikeyBytes()==null) {
                System.out.println("Failed to get prikey. Sign below message with the prikey:");
                if(keyInfo !=null && keyInfo.getWatchOnly())
                    System.out.println("It's a FID only for watched");
                System.out.println(multiSignDataJson);
                QRCodeUtils.generateQRCode(multiSignDataJson);
                return;
            }

            showRawMultiSignTxInfo(multiSignDataJson, br);

            Shower.printUnderline(60);
            String signedSchnorrMultiSignTx = TxCreator.signSchnorrMultiSignTx(multiSignDataJson, keyInfo.getPrikeyBytes(), FchMainNetwork.MAINNETWORK);
            System.out.println(signedSchnorrMultiSignTx);
            Shower.printUnderline(60);
            multiSignDataJson = signedSchnorrMultiSignTx;
            System.out.println("Multisig data signed by " + keyInfo.getId() + ":");
        } while (askIfYes(br, "Sign with another prikey?"));
        Menu.anyKeyToContinue(br);
    }

    @Nullable
    private KeyInfo getKeyInfoWithPrikey() {
        KeyInfo keyInfo = chooseKeyInfo();
        if (keyInfo == null) return null;

        byte[] prikey = keyInfo.getPrikeyBytes();
        if (prikey == null) {
            System.out.println("Get prikey wrong");
            return null;
        }
        return keyInfo;
    }

    private void signMsg(BufferedReader br) {
        System.out.println("Input the msg:");
        String msg = Inputer.inputStringMultiLine(br);
        if(msg==null)return;

        String choose = Inputer.chooseOne(new String[]{
                        AlgorithmId.BTC_EcdsaSignMsg_No1_NrC7.getDisplayName(),
                        AlgorithmId.FC_SchnorrSignMsg_No1_NrC7.getDisplayName(),
                        AlgorithmId.FC_Sha256SymSignMsg_No1_NrC7.getDisplayName()},
                null, "Select an algorithm", br);

        byte[] key;
        switch (choose) {
            case AlgorithmId.Constants.BTC_ECDSA_SIGNMSG_NO1_NRC7, AlgorithmId.Constants.FC_SCHNORR_SIGNMSG_NO1_NRC7 -> {
                KeyInfo keyInfo = chooseKeyInfo();
                if (keyInfo == null) return;
                key = Decryptor.decryptPrikey(keyInfo.getPrikeyCipher(), settings.getSymkey());
            }
            case AlgorithmId.Constants.FC_SHA256SYM_SIGNMSG_NO1_NRC7 ->
                    key = Inputer.inputSymkey32(br, "Input the 32 bytes symkey:");
            default -> {
                return;
            }
        }

        if(key==null){
            System.out.println("Get prikey wrong");
            return;
        }

        Signature signature = new Signature(msg,key,AlgorithmId.fromDisplayName(choose));
        String result = signature.sign().toNiceJson();

        System.out.println("Signed by "+ signature.getKeyName()+":");
        System.out.println("Signature json:\n"+result);
        if(askIfYes(br, "Show QR Code?")) {
            System.out.println("Generating QR Code...");
            QRCodeUtils.generateQRCode(result);
        }
    }

    public void encrypt(BufferedReader br) {
        Menu menu = new Menu("Encrypt");
        menu.add("To json by symkey",()->encryptWithSymkeyToJson(br));
        menu.add("To json by password",()->encryptWithPasswordToJson(br));
        menu.add("To json by pubkey",()->encryptAsyToJson(br));
        menu.add("Encrypt file with symkey",()->encryptFileWithSymkey(br));
        menu.add("Encrypt file with pubkey",()->encryptFileAsy(br));

        menu.add("To bundle by password",()->encryptWithPasswordBundle(br));
        menu.add("To bundle by symkey",()->encryptWithSymkeyToBundle(br));
        menu.add("To bundle by pubkey one way",()->encryptAsyOneWayBundle(br));
        menu.add("To bundle by pubkey two way",()->encryptAsyTwoWayBundle(br));
        menu.add("By Bitcore-ECEIS",()->encryptBitcoreToBundle(br));

        menu.showAndSelect(br);
    }


    public void decrypt(BufferedReader br) {
        Menu menu = new Menu("Decrypt");
        menu.add("Json by symkey",()->decryptWithSymkeyFromJson(br));
        menu.add("Json by password",()->decryptWithPasswordFromJson(br));
        menu.add("Json by prikey",()->decryptAsyFromJson(br));

        menu.add("Bundle by symkey",()->decryptWithSymkeyFromBundle(br));
        menu.add("Bundle by password",()->decryptWithPasswordFromBundle(br));
        menu.add("Bundle by prikey B",()->decryptAsyOneWayFromBundle(br));
        menu.add("Bundle by prikey B and pubkey A",()->decryptAsyTwoWayFromBundle(br));
        menu.add("File by symkey",()->decryptFileSymkey(br));
        menu.add("File by prikey",()->decryptFileAsy(br));
        menu.add("Bitcore-ECEIS bundle",()->decryptBitcoreFromBundle(br));

        menu.showAndSelect(br);
    }

    public static void decryptFileSymkey(BufferedReader br) {
        File encryptedFile = getAvailableFile(br);
        Decryptor decryptor = new Decryptor();
        if (encryptedFile == null || encryptedFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;
        String ask = "Input the symkey in hex:";
        char[] symkey = Inputer.input32BytesKey(br, ask);
        if (symkey == null) return;
        CryptoDataByte result = decryptor.decryptFileBySymkey(encryptedFile.getName(), "decrypted" + encryptedFile.getName(), BytesUtils.hexCharArrayToByteArray(symkey));
        System.out.println(new String(result.getData()));
        Menu.anyKeyToContinue(br);
    }
    public static void decryptWithPasswordFromBundle(BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        String bundle = Inputer.inputString(br, "Input the bundle in Base64:");
        if (bundle == null) {
            System.out.println("Bundle is null.");
            return;
        }
        String ask = "Input the password:";
        char[] password = Inputer.inputPassword(br, ask);
        if (password == null) return;
        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        CryptoDataByte cryptoDataByte = decryptor.decryptBundleByPassword(bundleBytes, password);
        System.out.println(new String(cryptoDataByte.getData()));
    }
    public static void encryptBitcoreToBundle(BufferedReader br2) {
        String msg = Inputer.inputString(br2, "Input the message to be encrypted:");
        String pubkey = Inputer.inputString(br2, "Input the public key in hex:");
        byte[] encrypted = Bitcore.encrypt(msg.getBytes(), Hex.fromHex(pubkey));
        if (encrypted == null) {
            System.out.println("Encrypt failed.");
            return;
        }
        System.out.println("Cipher: \n" + Base64.getEncoder().encodeToString(encrypted));

    }
    public static void encryptWithPasswordBundle(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        String msg = Inputer.inputMsg(br);
        String ask = "Input the password:";
        char[] password = Inputer.inputPassword(br, ask);
        if (password == null) return;
        assert msg != null;
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] bundle = encryptor.encryptToBundleByPassword(msgBytes, password);
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }
    public static void encryptAsyOneWayBundle(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = getEncryptedEccAesDataOneWay(br);
        if (cryptoDataByte == null) return;
        if (cryptoDataByte.getData() == null) {
            System.out.println("Error: no message.");
            return;
        }
        byte[] bundle = encryptor.encryptByAsyOneWayToBundle(cryptoDataByte.getData(), cryptoDataByte.getPubkeyB());
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }


    public static CryptoDataByte getEncryptedEccAesDataOneWay(BufferedReader br) {
        CryptoDataByte cryptoDataByte = new CryptoDataByte();
        cryptoDataByte.setAlg(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        cryptoDataByte.setType(EncryptType.AsyOneWay);
        String pubkeyB;
        String msg;
        try {
            pubkeyB = Inputer.inputString(br, "Input the recipient public key in hex:");
            if (pubkeyB == null || pubkeyB.length() != 66) {
                System.out.println("The public key should be 66 characters of hex.");
                return null;
            }
            cryptoDataByte.setPubkeyB(Hex.fromHex(pubkeyB));
            msg = Inputer.inputString(br, "Input the msg:");
            if ("".equals(msg)) return null;
            cryptoDataByte.setData(msg.getBytes());
        } catch (Exception e) {
            System.out.println("BufferedReader wrong.");
            return null;
        }
        return cryptoDataByte;
    }
    public static void decryptAsyTwoWayFromBundle(byte[] prikey, BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        String bundle = Inputer.inputString(br, "Input the bundle in Base64:");
        if (bundle == null) {
            System.out.println("Bundle is null.");
            return;
        }
        String pubkey = Inputer.inputString(br, "Input the pubkey in hex:");
        if (pubkey == null) {
            System.out.println("Pubkey is null.");
            return;
        }

        pubkey = Inputer.inputString(br, "Input the pubkey in hex:");

        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;
        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        System.out.println(decryptor.decryptBundleByAsyTwoWay(bundleBytes, prikey, Hex.fromHex(pubkey)));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptAsyOneWayFromBundle(byte[] prikey, BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        String bundle = Inputer.inputString(br, "Input the bundle in Base64:");
        if (bundle == null) {
            System.out.println("Bundle is null.");
            return;
        }
        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;

        System.out.println(decryptor.decryptBundleByAsyOneWay(Base64.getDecoder().decode(bundle), prikey));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptAsyTwoWayBundle(BufferedReader br) {
        Encryptor encryptor = new Encryptor();
        String pubkeyB;
        String msg;
        String prikeyA;
        byte[] prikeyABytes = new byte[0];
        while (true) {
            try {
                pubkeyB = Inputer.inputString(br, "Input the recipient public key in hex:");
                if (pubkeyB.length() != 66) {
                    System.out.println("The public key should be 66 characters of hex.");
                    continue;
                }
                prikeyA = Inputer.inputString(br, "Input the sender's private key:");
                if (prikeyA == null) {
                    System.out.println("A private key is required.");
                    continue;
                }

                if (Base58.isBase58Encoded(prikeyA)) prikeyABytes = Base58.decode(prikeyA);
                else if (Hex.isHexString(prikeyA)) prikeyABytes = Hex.fromHex(prikeyA);
                msg = Inputer.inputString(br, "Input the msg:");
                if ("".equals(msg)) return;
                break;
            } catch (Exception e) {
                System.out.println("BufferedReader wrong.");
                return;
            }
        }
        byte[] bundle = encryptor.encryptByAsyTwoWayToBundle(msg.getBytes(), prikeyABytes, Hex.fromHex(pubkeyB));//ecc.encryptAsyTwoWayBundle(eccAesData.getMsg(),eccAesData.getPubkeyB(),eccAesData.getPrikeyA());
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptFileWithSymkey(BufferedReader br) {
        File originalFile = getAvailableFile(br);
        if (originalFile == null || originalFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;

        String ask = "Input the symkey in hex:";
        char[] symkeyHex = Inputer.input32BytesKey(br, ask);
        if (symkeyHex == null) return;
        byte[] symkey = BytesUtils.hexCharArrayToByteArray(symkeyHex);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        String destFileName = Encryptor.makeEncryptedFileName(originalFile.getName());
        System.out.println(encryptor.encryptFileBySymkey(originalFile.getName(), destFileName, symkey));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptWithSymkeyFromBundle(BufferedReader br) {
        String bundle = Inputer.inputString(br, "Input the bundle in Base64:");
        if (bundle == null) {
            System.out.println("Bundle is null.");
            return;
        }
        byte[] bundleBytes;
        bundleBytes = Base64.getDecoder().decode(bundle);
        if (bundleBytes == null) return;

        Decryptor decryptor = new Decryptor();
        String ask = "Input the symkey in hex:";
        char[] symkeyHex = Inputer.input32BytesKey(br, ask);
        if (symkeyHex == null) return;
        byte[] symkey = BytesUtils.hexCharArrayToByteArray(symkeyHex);
        CryptoDataByte cryptoDataByte = decryptor.decryptBundleBySymkey(bundleBytes, symkey);

        System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptWithSymkeyToBundle(BufferedReader br) {
        String msg = Inputer.inputMsg(br);
        if (msg == null) return;
        String ask = "Input the symkey in hex:";
        char[] symkeyHex = Inputer.input32BytesKey(br, ask);
        if (symkeyHex == null) return;
        byte[] symkey = BytesUtils.hexCharArrayToByteArray(symkeyHex);
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        byte[] bundle = encryptor.encryptToBundleBySymkey(msg.getBytes(), symkey);
        System.out.println(Base64.getEncoder().encodeToString(bundle));
        Menu.anyKeyToContinue(br);
    }

    public static void decryptFileAsy(byte[] prikey, BufferedReader br) {
        File encryptedFile = getAvailableFile(br);
        if (encryptedFile == null || encryptedFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;

        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;
        System.out.println("Input the recipient private key in hex:");
        Decryptor decryptor = new Decryptor();
        String plainFileName = Decryptor.recoverEncryptedFileName(encryptedFile.getName());
        CryptoDataByte cryptoDataByte = decryptor.decryptFileByAsyOneWay(encryptedFile.getName(), plainFileName, prikey);
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    public static void encryptFileAsy(BufferedReader br) {

        File originalFile = getAvailableFile(br);
        if (originalFile == null || originalFile.length() > Constants.MAX_FILE_SIZE_M * Constants.M_BYTES) return;
        String pubkeyB;
        pubkeyB = KeyTools.inputPubkey(br);
        if (pubkeyB == null) return;
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        String dataFileName = originalFile.getName();
        CryptoDataByte cryptoDataByte = encryptor.encryptFileByAsyOneWay(dataFileName, Encryptor.makeEncryptedFileName(dataFileName), Hex.fromHex(pubkeyB));
        System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }
    @Nullable
    public static byte[] checkPrikey(byte[] prikey, BufferedReader br) {
        if (prikey == null || Inputer.askIfYes(br, "Input a new private key?")) {
            System.out.println("Input the recipient private key in hex:");
            prikey = inputPrikeyHexOrBase58(br);
        }
        return prikey;
    }

    public static void encryptAsyToJson(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = getEncryptedEccAesDataOneWay(br);
        assert cryptoDataByte != null;
        cryptoDataByte = encryptor.encryptByAsyOneWay(cryptoDataByte.getData(), cryptoDataByte.getPubkeyB());
        if (cryptoDataByte == null) return;
        if (cryptoDataByte.getCode() != 0) {
            System.out.println(cryptoDataByte.getMessage());
        } else System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }
    public static void encryptWithSymkeyToJson(BufferedReader br) {
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        String msg = Inputer.inputMsg(br);
        if (msg == null) return;
        String ask = "Input the symkey in hex:";
        char[] symkey = Inputer.input32BytesKey(br, ask);
        if (symkey == null) return;
        CryptoDataByte cryptoDataByte = encryptor.encryptBySymkey(msg.getBytes(), BytesUtils.hexCharArrayToByteArray(symkey));
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }

    public static void decryptWithSymkeyFromJson(BufferedReader br) {

        String cipherJson = Inputer.inputString(br, "Input the cipher json string:");
        Decryptor decryptor = new Decryptor();
        String ask = "Input the symkey in hex:";
        byte[] symkey = Inputer.inputSymkey32(br, ask);
        if (symkey == null) return;
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonBySymkey(cipherJson, symkey);
        System.out.println(new String(cryptoDataByte.getData()));
        Menu.anyKeyToContinue(br);
    }

    public static void encryptWithPasswordToJson(BufferedReader br) {

        String ask = "Input the password no longer than 64:";
        char[] password = Inputer.inputPassword(br, ask);
        if (password == null) return;
        System.out.println("Password:" + Arrays.toString(password));

        String msg = Inputer.inputString(br, "Input the plaintext:");
        Encryptor encryptor = new Encryptor(AlgorithmId.FC_AesCbc256_No1_NrC7);
        CryptoDataByte cryptoDataByte = encryptor.encryptByPassword(msg.getBytes(), password);
        System.out.println(cryptoDataByte.toNiceJson());

        Menu.anyKeyToContinue(br);
    }

    public static void decryptWithPasswordFromJson(BufferedReader br) {
        String eccAesDataJson = Inputer.inputString(br, "Input the cipher json:");
        CryptoDataByte cryptoDataByte;
        while (true) {
            String ask = "Input the password no longer than 64:";
            char[] password = Inputer.inputPassword(br, ask);
            if (password == null) return;
            Decryptor decryptor = new Decryptor();

            cryptoDataByte = decryptor.decryptJsonByPassword(eccAesDataJson, password);
            if (cryptoDataByte.getCode() == 0) break;
            System.out.println("Wrong password. Try again.");
        }
        System.out.println(new String(cryptoDataByte.getData()));

        Menu.anyKeyToContinue(br);
    }

    public static void decryptAsyFromJson(byte[] prikey, BufferedReader br) {
        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;
        String eccAesDataJson = Inputer.inputString(br, "Input the json string of EccAesData:");

        decryptAsyJson(prikey, br, eccAesDataJson);

        Menu.anyKeyToContinue(br);

    }

    public static void decryptAsyJson(byte[] prikey, BufferedReader br, String eccAesDataJson) {
        prikey = checkPrikey(prikey, br);
        if (prikey == null) return;
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonByAsyOneWay(eccAesDataJson, prikey);
        System.out.println(new String(cryptoDataByte.getData()));
    }
    private void decode(BufferedReader br) {
        String text = Inputer.inputString(br, "Input the text:");
        if(text==null)return;
        byte[] bytes;
        StringUtils.EncodeType type = Inputer.chooseOne(StringUtils.EncodeType.values(), null, "Select the type of the text. Enter to use UTF-8:", br);
        if(type==null)type = StringUtils.EncodeType.UTF8;
        switch (type) {
            case HEX -> bytes = Hex.fromHex(text);
            case BASE58 -> bytes = Base58.decode(text);
            case BASE64 -> bytes = Base64.getDecoder().decode(text);
            default -> bytes = text.getBytes();
        }

        System.out.println("Source text type:" + type.getDisplayName());
        System.out.println("Hex:" + Hex.toHex(bytes));
        System.out.println("Base58:" + Base58.encode(bytes));
        System.out.println("Base64:" + Base64.getEncoder().encodeToString(bytes));
        System.out.println("UTF-8:" + new String(bytes));
        Menu.anyKeyToContinue(br);
    }

    private void secret(BufferedReader br) {
        List<KeyInfo> keyInfoList = KeyInfo.listFromFile(KeyInfo.class);
        if(keyInfoList ==null || keyInfoList.isEmpty()){
            System.out.println("No key info found. Add a key info first.");
            Menu.anyKeyToContinue(br);
            return;
        }

        List<SecretDetail> secretDetailList = SecretDetail.listFromFile(SecretDetail.class);
        if(secretDetailList==null)secretDetailList = new ArrayList<>();
        List<SecretDetail> finalSecretDetailList = secretDetailList;

        Menu menu = new Menu("Secret");
        menu.add("Find secret details",()->findSecretDetail(br, keyInfoList, finalSecretDetailList));
        menu.add("List secret details",()->listSecretDetail(br, keyInfoList, finalSecretDetailList));
        menu.add("Add new secret details",()->addSecrets(br, finalSecretDetailList));
        menu.add("Import secret details",()->importSecretDetail(br, finalSecretDetailList));
        menu.add("Update Secret Details",()->updateSecretDetail(br, finalSecretDetailList));
        menu.showAndSelect(br);
    }

    private void updateSecretDetail(BufferedReader br, List<SecretDetail> finalSecretDetailList) {
        List<SecretDetail> chosenSecretDetailList =  Shower.showOrChooseListInPages("FID Info", finalSecretDetailList, DEFAULT_PAGE_SIZE, null, true, SecretDetail.class, br);
        if(chosenSecretDetailList==null || chosenSecretDetailList.isEmpty()){
            System.out.println("No secret details selected.");
            return;
        }

        updateSecretDetail(br, finalSecretDetailList, chosenSecretDetailList);
        Menu.anyKeyToContinue(br);
    }

    private void updateSecretDetail(BufferedReader br, List<SecretDetail> finalSecretDetailList, List<SecretDetail> chosenSecretDetailList) {
        try{
            for(SecretDetail secretDetail : chosenSecretDetailList){
                secretDetail.setTitle(Inputer.promptAndUpdate(br, "Title", secretDetail.getTitle()));
                decryptContent(secretDetail);
                String content = Inputer.promptAndUpdate(br, "Content", secretDetail.getContent());
                if(content!=null && !content.isEmpty()){
                    String cipher = Encryptor.encryptBySymkeyToJson(content.getBytes(), symkey);
                    secretDetail.setContentCipher(cipher);
                }
                secretDetail.setType(Inputer.promptAndUpdate(br, "Type", secretDetail.getType()));
                secretDetail.setMemo(Inputer.promptAndUpdate(br, "Memo", secretDetail.getMemo()));
            }
            SecretDetail.listToFile(finalSecretDetailList, SecretDetail.class);
            System.out.println("Secret details updated.");
        }catch(IOException e){
            System.out.println("Failed to update secret details.");
        }
    }

    //TODO untested
    private void importSecretDetail(BufferedReader br, List<SecretDetail> finalSecretDetailList) {
        String choice = Inputer.chooseOne(new String[]{"From plain JSON","From cipher JSON","From cipher bundle"}, null, "Select the source of the secret details:", br);
        if(choice==null|| choice.equals("0"))return;

        KeyInfo keyInfo = null;
        if(choice.equals("From cipher JSON")||choice.equals("From cipher bundle")){
            keyInfo = chooseKeyInfo();
            if(keyInfo ==null)return;
        }
        CryptoDataByte cryptoDataByte = null;
        do{
            SecretDetail secretDetail = new SecretDetail();
            String input;
            if(scanner!=null && askIfYes(br,"Scan secretDetail QR code?")){
                try {
                    input = scanner.scanQRCodeList(br);
                } catch (IOException | InterruptedException e) {
                    System.out.println("Error scanning QR code: " + e.getMessage());
                    return;
                }
            }else{
                input = Inputer.inputString(br, "Input:");
            }
            if(input==null|| input.isEmpty())return;
            switch(choice){
                case "From plain JSON":
                    secretDetail = JsonUtils.fromJson(input, SecretDetail.class);
                    break;
                case "From cipher bundle":
                    cryptoDataByte = CryptoDataByte.fromBundle(Base64.getDecoder().decode(input));
                case "From cipher JSON":
                    if(cryptoDataByte==null)cryptoDataByte = JsonUtils.fromJson(input, CryptoDataByte.class);
                    if(cryptoDataByte==null)break;
                    cryptoDataByte.setPrikeyB(keyInfo.getPrikeyBytes());
                    CryptoDataByte result = new Decryptor().decrypt(cryptoDataByte);
                    if(result==null)break;
                    if(result.getCode()!=0){
                        System.out.println("Decrypt failed");
                        break;
                    }
                    secretDetail = JsonUtils.fromJson(new String(result.getData()), SecretDetail.class);
                    break;
            }
            if(secretDetail!=null)
                finalSecretDetailList.add(secretDetail);
        }while(!Inputer.askIfYes(br, "Finished?"));
        SecretDetail.listToFile(finalSecretDetailList, SecretDetail.class);
    }

    private void findSecretDetail(BufferedReader br, List<KeyInfo> keyInfoList, List<SecretDetail> finalSecretDetailList) {
        String keyword = Inputer.inputString(br, "Input the keyword:");
        if(keyword==null)return;
        List<SecretDetail> chosenSecretDetailList = finalSecretDetailList.stream()
                .filter(secretDetail -> secretDetail.getTitle().contains(keyword) || secretDetail.getType().contains(keyword) || secretDetail.getMemo().contains(keyword))
                .collect(Collectors.toList());
        listSecretDetail(br, keyInfoList, chosenSecretDetailList);
    }

    private void listSecretDetail(BufferedReader br, List<KeyInfo> keyInfoList, List<SecretDetail> secretDetailList) {
        List<SecretDetail> chosenSecretDetailList = Shower.showOrChooseListInPages("FID Info", secretDetailList, DEFAULT_PAGE_SIZE, null, true, SecretDetail.class, br);
        if(chosenSecretDetailList==null || chosenSecretDetailList.isEmpty()){
            System.out.println("No secret details selected.");
            return;
        }

        while(true){
            String op = Inputer.chooseOne(new String[]{"Show","Delete","Encrypt","Update"}, null, "Select an operation:", br);
            if(op==null) return;

            switch (op) {
                case "Show" -> showSecretDetail(chosenSecretDetailList);
                case "Delete" -> deleteSecretDetail(br, secretDetailList, chosenSecretDetailList);
                case "Encrypt" -> encryptSecretDetail(br, keyInfoList, chosenSecretDetailList);
                case "Update" -> updateSecretDetail(br, secretDetailList,chosenSecretDetailList);
            }
            Menu.anyKeyToContinue(br);
        }
    }

    private void encryptSecretDetail(BufferedReader br, List<KeyInfo> keyInfoList, List<SecretDetail> chosenSecretDetailList) {
        KeyInfo keyInfo = Inputer.chooseOneFromList(keyInfoList, "id", "Select a key:", br);
        if(keyInfo ==null) return;
        for (SecretDetail secretDetail : chosenSecretDetailList) {
            decryptContent(secretDetail);
            SecretDetail secretDetail1 = new SecretDetail();
            secretDetail1.setTitle(secretDetail.getTitle());
            secretDetail1.setContent(secretDetail.getContent());
            secretDetail1.setType(secretDetail.getType());
            secretDetail1.setMemo(secretDetail.getMemo());
            CryptoDataByte cryptoDataByte = new Encryptor(AlgorithmId.FC_EccK1AesCbc256_No1_NrC7).encryptByAsyOneWay(secretDetail1.toBytes(), keyInfo.getPubkeyBytes());
            System.out.println("Encrypted by "+ keyInfo.getId());
            String cipher = Base64.getEncoder().encodeToString(cryptoDataByte.toBundle());

            System.out.println(secretDetail.getTitle() + ":\n" + cipher);
            QRCodeUtils.generateQRCode(cipher);
        }
        System.out.println("Encrypted " + chosenSecretDetailList.size() + " secret details.");
    }

    private static void deleteSecretDetail(BufferedReader br, List<SecretDetail> secretDetailList, List<SecretDetail> chosenSecretDetailList) {
        if (askIfYes(br, "Are you sure you want to delete these secret details?")) {
            int count = chosenSecretDetailList.size();
            secretDetailList.removeAll(chosenSecretDetailList);
            SecretDetail.listToFile(secretDetailList,SecretDetail.class);
            System.out.println("Deleted " + count + " secret details.");
        }
    }

    private void showSecretDetail(List<SecretDetail> chosenSecretDetailList) {
        for (SecretDetail secretDetail : chosenSecretDetailList) {
            decryptContent(secretDetail);
            SecretDetail secretDetail1 = new SecretDetail();
            secretDetail1.setTitle(secretDetail.getTitle());
            secretDetail1.setContent(secretDetail.getContent());
            secretDetail1.setType(secretDetail.getType());
            secretDetail1.setMemo(secretDetail.getMemo());
            System.out.println(secretDetail1.toNiceJson());
        }
    }
    private void decryptContent(SecretDetail secretDetail) {
        String contentCipher = secretDetail.getContentCipher();
        CryptoDataByte result = new Decryptor().decryptJsonBySymkey(contentCipher, settings.getSymkey());
        if(result==null||result.getData()==null)return;
        secretDetail.setContent(new String(result.getData()));
    }

    private void addSecrets(BufferedReader br, List<SecretDetail> secretDetailList) {
        do{
            SecretDetail secretDetail = SecretDetail.inputSecret(br, settings.getSymkey());

            secretDetailList.add(secretDetail);
            secretDetail.setId(Hex.toHex(Hash.sha256x2(secretDetail.toBytes())));
        }while(Inputer.askIfYes(br, "Add another secret details?"));
        SecretDetail.listToFile(secretDetailList,SecretDetail.class);
    }

    private void qrCode(BufferedReader br) {
        String text = Inputer.inputString(br, "Input the text:");
        if(text==null)return;
        System.out.println("Generating QR Code...");
        QRCodeUtils.generateQRCode(text);
        Menu.anyKeyToContinue(br);
    }

    private void setting(BufferedReader br) {
        Menu menu = new Menu("Settings");
        menu.add("Backup", CryptoSign::backUpData);
        menu.add("Reset password",()-> resetPassword(br));
        menu.add("Clear all data",()->clearAllData(br));
        menu.add("Remove Me",()->removeMe(br));
        menu.add("Show Introduction",()->showIntroduction(br));
        menu.showAndSelect(br);
        Menu.anyKeyToContinue(br);
    }

    private static void backUpData() {
        FileUtils.backup(SecretDetail.class.getSimpleName()+DOT_JSON,null,5);
    }

    private void showIntroduction(BufferedReader br) {
        try {
            // Read security guidelines from the markdown file
            InputStream inputStream = getClass().getResourceAsStream("/security_guidelines.md");
            if (inputStream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            } else {
                System.out.println("Security guidelines file not found. Please check your installation.");
            }
        } catch (IOException e) {
            System.out.println("Error reading security guidelines: " + e.getMessage());
        }

        Menu.anyKeyToContinue(br);
    }

    private void removeMe(BufferedReader br) {
        if(askIfYes(br,"Are you sure you want to remove me and clear all data?")){
            settings.removeMe(br);
            clearAllData(br);
            System.out.println("Removed successfully.");
        }
    }

    private void clearAllData(BufferedReader br) {
        if(askIfYes(br,"Are you sure you want to clear all data?")){
            String keyInfoFilePath = KeyInfo.KEY_INFO_FILE_PATH;
            String secretDetailFilePath = "secretDetail.json";
            File keyInfoFile = new File(keyInfoFilePath);
            File secretDetailFile = new File(secretDetailFilePath);
            if(keyInfoFile.exists()){
                boolean done = keyInfoFile.delete();
                if(!done) System.out.println("Failed to delete "+ keyInfoFilePath);
            }
            if(secretDetailFile.exists()){
                boolean done = secretDetailFile.delete();
                if(!done) System.out.println("Failed to delete "+ secretDetailFilePath);
            }
            if(askIfYes(br,"Would you like to remove all backups?")){
                FileUtils.removeAllBackUps(SecretDetail.class.getSimpleName()+DOT_JSON,null);
            }
            System.out.println("All data cleared successfully.");
        }
    }

    private void resetPassword(BufferedReader br) {
        try{
            byte[] oldSymkey = settings.getSymkey().clone();
            settings.resetPassword(null, br);

            if(Arrays.equals(oldSymkey, settings.getSymkey()))return;

            reEncryptAllPrikeys(oldSymkey,settings.getSymkey());
            reEncryptAllSecretDetails(oldSymkey,settings.getSymkey());

            String newPasswordHash = Hex.toHex(Hash.sha256(settings.getSymkey()));
            Configure config = settings.getConfig();
            if(config==null)return;
            config.setPasswordHash(newPasswordHash);
            Configure.saveConfig();
            settings.saveSimpleSettings(APP_NAME);
            System.out.println("Password reset successfully.");
        }catch(Exception e){
            System.out.println("Error resetting password: "+e.getMessage());
        }
    }

    private void reEncryptAllPrikeys(byte[] oldSymkey, byte[] newSymkey){
        List<KeyInfo> keyInfoList = KeyInfo.listFromFile(KeyInfo.class);
        for(KeyInfo keyInfo : keyInfoList){
            CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymkey(keyInfo.getPrikeyCipher(), oldSymkey);
            if(cryptoDataByte.getCode()!=0)continue;
            keyInfo.setPrikeyCipher(Encryptor.encryptBySymkeyToJson(cryptoDataByte.getData(), newSymkey));
        }
        KeyInfo.listToFile(keyInfoList, KeyInfo.class);
    }

    private void reEncryptAllSecretDetails(byte[] oldSymkey, byte[] newSymkey){
        List<SecretDetail> secretDetailList = SecretDetail.listFromFile(SecretDetail.class);
        for(SecretDetail secretDetail:secretDetailList){
            String contentCipher = secretDetail.getContentCipher();
            if(contentCipher==null)continue;
            CryptoDataByte cryptoDataByte = new Decryptor().decryptJsonBySymkey(contentCipher,oldSymkey);
            if(cryptoDataByte.getCode()!=0)continue;
            secretDetail.setContentCipher(Encryptor.encryptBySymkeyToJson(cryptoDataByte.getData(), newSymkey));
        }
        SecretDetail.listToFile(secretDetailList,SecretDetail.class);
    }


    public void close() {
        backUpData();
        settings.close();
        System.exit(0);
    }

    public void decryptAsyTwoWayFromBundle(BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        System.out.println("Input the bundle in Base64:");
        String bundle = Inputer.inputString(br,"Input the bundle in Base64:");
        if(bundle==null){
            System.out.println("Bundle is null.");
            return;
        }
        String pubkey = Inputer.inputString(br,"Input the pubkey in hex:");

        KeyInfo keyInfo = chooseKeyInfo();
        if (keyInfo == null) return;
        byte[] bundleBytes = Base64.getDecoder().decode(bundle);
        System.out.println(decryptor.decryptBundleByAsyTwoWay(bundleBytes, keyInfo.getPrikeyBytes(), Hex.fromHex(pubkey)));
        Menu.anyKeyToContinue(br);
    }

    public void decryptAsyOneWayFromBundle(BufferedReader br) {
        Decryptor decryptor = new Decryptor();
        try {
            System.out.println("Input the bundle in Base64:");
            String bundle = br.readLine();
            if(bundle==null){
                System.out.println("Bundle is null.");
                return;
            }
            KeyInfo keyInfo = chooseKeyInfo();
            if (keyInfo == null) return;

            System.out.println(decryptor.decryptBundleByAsyOneWay(Base64.getDecoder().decode(bundle), keyInfo.getPrikeyBytes()));

        } catch (IOException e) {
            System.out.println("Something wrong:"+e.getMessage());
        }

        Menu.anyKeyToContinue(br);
    }

    public void decryptFileAsy(BufferedReader br) {
        File encryptedFile = getAvailableFile(br);
        if(encryptedFile==null||encryptedFile.length()> Constants.MAX_FILE_SIZE_M * Constants.M_BYTES)return;

        KeyInfo keyInfo = chooseKeyInfo();
        if (keyInfo == null) return;
        System.out.println("Input the recipient private key in hex:");
        Decryptor decryptor = new Decryptor();
        String plainFileName = Decryptor.recoverEncryptedFileName(encryptedFile.getName());
        CryptoDataByte cryptoDataByte = decryptor.decryptFileByAsyOneWay(encryptedFile.getName(),plainFileName, keyInfo.getPrikeyBytes());
        System.out.println(cryptoDataByte.toNiceJson());
        Menu.anyKeyToContinue(br);
    }


    public void decryptAsyFromJson(BufferedReader br) {
        String eccAesDataJson = Inputer.inputString(br,"Input the json string:");
        decryptAsyJson(eccAesDataJson);
        Menu.anyKeyToContinue(br);
    }

    public void decryptAsyJson(String eccAesDataJson)  {
        KeyInfo keyInfo = chooseKeyInfo();
        if (keyInfo == null) return;
        Decryptor decryptor = new Decryptor();
        CryptoDataByte cryptoDataByte = decryptor.decryptJsonByAsyOneWay(eccAesDataJson, keyInfo.getPrikeyBytes());
        System.out.println("UTF-8:\n"+new String(cryptoDataByte.getData()));
        System.out.println("Hex:\n"+Hex.toHex(cryptoDataByte.getData()));
    }


    public static void getRandom(BufferedReader br){
        int len;
        while(true) {
            System.out.println("Input the bytes length of the random you want. Enter to exit:");
            String input = Inputer.inputString(br);
            if ("".equals(input)) {
                return;
            }

            try {
                len = Integer.parseInt(input);
                break;
            }catch(Exception ignored) {
            }
        }

        byte[] bytes = BytesUtils.getRandomBytes(len);
        printRandomInMultipleFormats(bytes);
    }


    public static void printRandomInMultipleFormats(byte[] bytes) {
        // Convert to BigInteger (works for any length)
        BigInteger bigInt = new BigInteger(1, bytes); // Use 1 for positive number

        System.out.println("Base58:\n" + Base58.encode(bytes) + "\n");

        System.out.println("Integer:\n" + String.format("%,d", bigInt) + "\n");

        System.out.println("Hex:\n" + Hex.toHex(bytes) + "\n");

        System.out.println("Base64:\n" + Base64.getEncoder().encodeToString(bytes) + "\n");

        // Convert to Chinese characters (0x4E00-0x9FFF)
        StringBuilder chineseStr = new StringBuilder();
        int cjkRange = 0x9FFF - 0x4E00 + 1;

        // Process 12 bits at a time (4096 possibilities)
        for (int i = 0; i < bytes.length; i += 3) {
            if (i + 2 < bytes.length) {
                // Take 3 bytes (24 bits) and convert to two Chinese characters (12 bits each)
                int value1 = ((bytes[i] & 0xFF) << 4) | ((bytes[i + 1] & 0xF0) >> 4);
                int value2 = ((bytes[i + 1] & 0x0F) << 8) | (bytes[i + 2] & 0xFF);

                chineseStr.append(Character.toString((char) (0x4E00 + (value1 % cjkRange))));
                chineseStr.append(Character.toString((char) (0x4E00 + (value2 % cjkRange))));
            } else if (i + 1 < bytes.length) {
                // Handle last 2 bytes
                int value = ((bytes[i] & 0xFF) << 4) | ((bytes[i + 1] & 0xF0) >> 4);
                chineseStr.append(Character.toString((char) (0x4E00 + (value % cjkRange))));
            } else {
                // Handle last byte
                int value = bytes[i] & 0xFF;
                chineseStr.append(Character.toString((char) (0x4E00 + value)));
            }
        }
        System.out.println("Chinese:\n" + chineseStr + "\n");
    }

    public void decryptBitcoreFromBundle(BufferedReader br) {
        String cipher = Inputer.inputString(br,"Input the cipher:");
        KeyInfo keyInfo = chooseKeyInfo();
        if (keyInfo == null) return;
        byte[] decrypted;
        try {
            decrypted = Bitcore.decrypt(Base64.getDecoder().decode(cipher), keyInfo.getPrikeyBytes());
            if(decrypted==null){
                System.out.println("Decrypt failed.");
                return;
            }
            System.out.println("Decrypted: \n"+new String(decrypted));
        } catch (Exception e) {
            System.out.println("Decrypt failed.");
            e.printStackTrace();
        }
    }

    public void multiSign(BufferedReader br) {
        Menu menu = new Menu("Multisig");
        menu.add("Create multisig FID", () -> createFid(br));
        menu.add("Parse multisig FID from redeem script", () -> showFid(br));
        menu.add("Create new Tx",() -> createTx(br));
        menu.add("Sign multisig raw TX", () -> multiSignTx(br));
        menu.add("Build multisig TX", () -> buildSignedTx(br));
        menu.showAndSelect(br);
    }

    public void createTx(BufferedReader br) {
        MainNetParams mainnetwork = chooseMainNetWork(br);
        System.out.println("Input TX information String. Enter to input the element one by one");
        RawTxInfo rawTxInfo;
        String offLineTx = inputStringMultiLine(br);
        if("".equals(offLineTx))
            rawTxInfo = RawTxInfo.fromUserInput(br,null);
        else rawTxInfo = RawTxInfo.fromString(offLineTx);
        Multisign multisign;

        while(true) {
            String redeemScriptStr = Inputer.inputString(br, "Input the redeem script. Enter to exit:");
            if("".equals(redeemScriptStr))return;
            try {
                multisign = Multisign.parseMultisignRedeemScript(redeemScriptStr);
                if(multisign ==null){
                    System.out.println("Failed to parse redeemScript. Try again.");
                    continue;
                }
            } catch (Exception e) {
                System.out.println("Invalid redeem script.");
                continue;
            }
            System.out.println("MultiSign Info:\n"+ JsonUtils.toNiceJson(multisign));
            break;
        }
        if(rawTxInfo ==null)return;
        rawTxInfo.setMultisign(multisign);

        Transaction transaction = RawTxInfo.createMultisignTx(rawTxInfo, multisign, mainnetwork);
        if(transaction==null){
            System.out.println("Create unsigned tx failed.");
            return;
        }
        byte[] rawTx = transaction.bitcoinSerialize();

//        RawTxInfo multiSignData = new RawTxInfo(rawTx, rawTxInfo);

        showMultiUnsignedResult(br, multisign, rawTxInfo);

        if(askIfYes(br,"Would you sign it?")){
            do {
                KeyInfo keyInfo = chooseKeyInfo();
                if(keyInfo ==null)break;
                TxCreator.signSchnorrMultiSignTx(rawTxInfo, keyInfo.getPrikeyBytes(), mainnetwork);
                System.out.println("Signed by "+ keyInfo.getId());
            }while (askIfYes(br,"Sign with more keys?"));

            showMultiSignedResult(rawTxInfo);
        }
    }

    public static void showMultiSignedResult(RawTxInfo multiSignData) {
        System.out.println("Multisign data signed:");
        Shower.printUnderline(10);
        System.out.println(multiSignData.toNiceJson());
        Shower.printUnderline(10);
    }

    public static void showMultiUnsignedResult(BufferedReader br, Multisign multisign, RawTxInfo multiSignData) {
        System.out.println("Multisign data unsigned:");
        Shower.printUnderline(10);
        String unsignedJson = multiSignData.toNiceJson();
        System.out.println(unsignedJson);
        if(Inputer.askIfYes(br,"Show the QR codes?"))
            QRCodeUtils.generateQRCode(unsignedJson);
        Shower.printUnderline(10);

        System.out.println("Next step: sign it separately with the prikeys of: ");
        Shower.printUnderline(10);
        for (String fid1 : multisign.getFids()) System.out.println(fid1);
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
    }

    public static void buildSignedTx(BufferedReader br) {
        String[] signedData = Inputer.inputMultiLineStringArray(br, "Input the signatures one by one.");

        String signedTx = TxCreator.buildSignedTx(signedData, FchMainNetwork.MAINNETWORK);
        if (signedTx == null) return;
        System.out.println("Signed TX:");
        System.out.println(signedTx);
        QRCodeUtils.generateQRCode(signedTx);
        Menu.anyKeyToContinue(br);
    }

    public void createFid(BufferedReader br) {

        List<String> pubkeyStringList = inputPubkeys(br);
        int m = pubkeyStringList.size();

        List<byte[]> pubkeyList = new ArrayList<>();
        for (String pubkeyString : pubkeyStringList) {
            pubkeyList.add(Hex.fromHex(pubkeyString));
        }

        Multisign multisign = TxCreator.createMultisign(pubkeyList, m);

        Shower.printUnderline(10);
        System.out.println("The multisig information is: \n" + JsonUtils.toNiceJson(multisign));
        System.out.println("It's generated from :");
        for (String pubkeyString : pubkeyStringList) {
            System.out.println(KeyTools.pubkeyToFchAddr(pubkeyString));
        }
        Shower.printUnderline(10);
        if(multisign ==null)return;
        String fid = multisign.getId();
        System.out.println("Your multisig FID: \n" + fid);
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
    }


    public List<String> inputPubkeys(BufferedReader br) {
        List<String> pubkeyStringList = new ArrayList<>();
        while (true) {
            if(askIfYes(br,"Choose a pubkey from the local list?")){
                KeyInfo keyInfo = chooseKeyInfo();
                if(keyInfo ==null)continue;
                pubkeyStringList.add(keyInfo.getPubkey());
            }else{
                String pubkeyString = Inputer.inputString(br, "Input the public key. Enter to end:");
                if ("".equals(pubkeyString)) {
                    break;
                }
                if(Hex.isHexString(pubkeyString) && pubkeyString.length()==66){
                    pubkeyStringList.add(pubkeyString);
                }else{
                    System.out.println("Wrong pubkey.");
                }
            }
        }
        return pubkeyStringList;
    }

    public static void showFid(BufferedReader br) {
        Multisign multisign = inputMultisign(br);
        Shower.printUnderline(10);
        System.out.println("Multisig:");
        System.out.println(JsonUtils.toNiceJson(multisign));
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
    }

    @Nullable
    private static Multisign inputMultisign(BufferedReader br) {
        String redeemScript = Inputer.inputString(br, "Input the redeem script of the multisig FID:");
        if(redeemScript==null) return null;
        Multisign multisign;
        multisign = Multisign.parseMultisignRedeemScript(redeemScript);
        return multisign;
    }

    public void pubkeyConvert(BufferedReader br) {
        KeyInfo keyInfo = chooseKeyInfo();
        if(keyInfo ==null)return;
        String pubkey = keyInfo.getPubkey();
        pubkeyInMultiFormats(br, pubkey);
    }

    public static void pubkeyInMultiFormats(BufferedReader br, @Nullable String pubkey) {
        String pubkey33 = null;
        if(pubkey==null)
            while (true) {
                String input = Inputer.inputString(br,"Input the public key. Enter to exit:");
                if ("".equals(input)) {
                    break;
                } else {
                    try{
                        pubkey33 = KeyTools.getPubkey33(input);
                    }catch(Exception e){
                        System.out.println("Wrong pubkey.");
                        if(!askIfYes(br,"Try again?")) return;
                    }
                }
                if(pubkey33==null){
                    System.out.println("Wrong pubkey.");
                    if(!askIfYes(br,"Try again?")) return;
                } else break;
            }
        else {
            if(pubkey.length()!=66)
                pubkey33 = KeyTools.getPubkey33(pubkey);
            else pubkey33 = pubkey;
        }
        if(pubkey33==null)return;
        Shower.printUnderline(10);
        System.out.println("FID: \n" + KeyTools.pubkeyToFchAddr(pubkey33));
        Shower.printUnderline(10);
        System.out.println("* Pubkey 33 bytes compressed hex:\n" + pubkey33);
        Shower.printUnderline(10);
        System.out.println("* Pubkey 65 bytes uncompressed hex:\n" + KeyTools.recoverPK33ToPK65(pubkey33));
        Shower.printUnderline(10);
        System.out.println("* Pubkey WIF uncompressed:\n" + KeyTools.getPubkeyWifUncompressed(pubkey33));
        Shower.printUnderline(10);
        System.out.println("* Pubkey WIF compressed with ver 0:\n" + KeyTools.getPubkeyWifCompressedWithVer0(pubkey33));
        Shower.printUnderline(10);
        System.out.println("* Pubkey WIF compressed without ver:\n" + KeyTools.getPubkeyWifCompressedWithoutVer(pubkey33));
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
    }

    public void addressConvert(BufferedReader br) {
        Map<String, String> addrMap;
        String id;
        String pubkey;

        while (true) {
            System.out.println("Enter to convert the local FID, or input the address or pubkey:");
            String input = Inputer.inputString(br);
            if ("".equals(input)) {
                KeyInfo keyInfo = chooseKeyInfo();
                if(keyInfo ==null)return;
                String prikeyCipher = keyInfo.getPrikeyCipher();
                byte[] prikey = Decryptor.decryptPrikey(prikeyCipher, settings.getSymkey());
                pubkey = Hex.toHex(KeyTools.prikeyToPubkey(prikey));
                addrMap = KeyTools.pubkeyToAddresses(pubkey);
                break;
            }

            addrMap = convert(input);
            if(addrMap!=null) break;
            System.out.println("Wrong FID or pubkey. Try again.");
        }
        if(addrMap==null) return;
        Shower.printUnderline(10);
        System.out.println(JsonUtils.toNiceJson(addrMap));
        Shower.printUnderline(10);
        Menu.anyKeyToContinue(br);
    }


    public void prikeyConvert(BufferedReader br) {

        KeyInfo keyInfo = chooseKeyInfo();
        if(keyInfo ==null)return;
        String prikeyCipher = keyInfo.getPrikeyCipher();
        if(prikeyCipher==null){
            System.out.println(keyInfo.getId() + " is a watching FID.");
            return;
        }
        byte[] prikey = Decryptor.decryptPrikey(prikeyCipher, settings.getSymkey());
        showPrikeyInfo(br, prikey);
    }

    public void keyTools(BufferedReader br) {
        Menu menu = new Menu("Key & Address");
        menu.add("Random", () -> getRandom(br));
        menu.add("Find Nice FID", () -> findNiceFid(br));
        menu.add("Prikey Convert", () -> prikeyConvert(br));
        menu.add("Pubkey Convert", () -> pubkeyConvert(br));
        menu.add("Address Convert", () -> addressConvert(br));

        menu.showAndSelect(br);
    }

}

