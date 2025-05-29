package com.fc.fc_ajdk.core.fch;

import com.fc.fc_ajdk.clients.ApipClient;
import com.fc.fc_ajdk.config.ApiAccount;
import com.fc.fc_ajdk.data.fcData.AlgorithmId;
import com.fc.fc_ajdk.data.fchData.SendTo;
import org.bitcoinj.fch.FchMainNetwork;
import com.fc.fc_ajdk.utils.http.AuthType;
import com.fc.fc_ajdk.utils.http.RequestMethod;
import com.fc.fc_ajdk.data.nasa.TxInput;
import com.fc.fc_ajdk.data.nasa.TxOutput;
import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.P2SH;
import com.fc.fc_ajdk.data.fcData.Signature;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.JsonUtils;
import com.fc.fc_ajdk.ui.Inputer;
import com.fc.fc_ajdk.ui.Shower;
import org.bitcoinj.core.*;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;


import static com.fc.fc_ajdk.constants.Constants.COIN_TO_SATOSHI;
import static com.fc.fc_ajdk.core.crypto.KeyTools.getPrikey32;
import static com.fc.fc_ajdk.core.fch.TxCreator.*;

@SuppressWarnings("unused")
public class TxTest {

    public static void main(String[] args) throws IOException {

        String prikeyA = "L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8";
        String prikeyB = "L5DDxf3PkFwi1jArqYokpTsntthLvhDYg44FXyTSgdTx3XEFR1iB";
        String prikeyC = "Kybd6FqL2xBEknFV2rcxvYsTZwqAbk99FyN3EBnWdi2M5UxiJL8A";

        String prikey32A = Hex.toHex(getPrikey32(prikeyA));
        String prikey32B = Hex.toHex(getPrikey32(prikeyB));
        String prikey32C = Hex.toHex(getPrikey32(prikeyC));

        byte[] prikeyBytesA = Hex.fromHex(prikey32A);
        byte[] prikeyBytesB = Hex.fromHex(prikey32B);
        byte[] prikeyBytesC = Hex.fromHex(prikey32C);

        ECKey ecKeyA = ECKey.fromPrivate(prikeyBytesA);
        ECKey ecKeyB = ECKey.fromPrivate(prikeyBytesB);
        ECKey ecKeyC = ECKey.fromPrivate(prikeyBytesC);

        String pubkeyA = ecKeyA.getPublicKeyAsHex();
        String pubkeyB = ecKeyB.getPublicKeyAsHex();
        String pubkeyC = ecKeyC.getPublicKeyAsHex();

        String fidA = KeyTools.pubkeyToFchAddr(pubkeyA);
        String fidB = KeyTools.pubkeyToFchAddr(pubkeyB);
        String fidC = KeyTools.pubkeyToFchAddr(pubkeyC);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

        byte[] sessionKey = getSessionKey(br);
        if (sessionKey == null) return;

        //Make multi-sign address
        List<byte[]> pubkeyList = new ArrayList<>();

        pubkeyList.add(Hex.fromHex(pubkeyA));
        pubkeyList.add(Hex.fromHex(pubkeyB));
        pubkeyList.add(Hex.fromHex(pubkeyC));

        P2SH p2SH = createP2sh(pubkeyList, 2);
        if(p2SH==null)return;
        String mFid = p2SH.getId();

        System.out.println("Multisig address:" + mFid);
        //Get multisig address information
        String urlHead = Constants.UrlHead_CID_CASH;

        ApipClient apipClient = new ApipClient();
        apipClient.setUrlHead(urlHead);
        apipClient.setSessionKey(sessionKey);
        String id = mFid;
        Map<String, P2SH> p2SHMap = apipClient.p2shByIds(RequestMethod.POST,AuthType.FC_SIGN_BODY,mFid);
        if(p2SHMap==null)return;
        P2SH p2sh = p2SHMap.get(mFid);
        JsonUtils.printJson(p2sh);

        //Get cashes of the multisig address

        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();
        sendTo.setFid(mFid);
        sendTo.setAmount(0.1);
        sendToList.add(sendTo);
        SendTo sendTo1 = new SendTo();
        sendTo1.setFid(fidA);
        sendTo1.setAmount(0.2);
        sendToList.add(sendTo1);

        String msg = "hi";

        List<Cash> cashList = apipClient.cashValid(mFid, 0.1,null,sendToList.size(),msg.getBytes().length, RequestMethod.POST, AuthType.FC_SIGN_BODY);

        if(cashList==null)return;

        JsonUtils.printJson(cashList);

        //Make raw tx
        Transaction transaction = createUnsignedTx(cashList, sendToList, msg, p2sh, DEFAULT_FEE_RATE, null, FchMainNetwork.MAINNETWORK);
        byte[] txBytes = transaction.bitcoinSerialize();
        System.out.println(Hex.toHex(txBytes));
        Shower.printUnderline(10);
        //Sign raw tx
        byte[] redeemScript = Hex.fromHex(p2sh.getRedeemScript());
        RawTxInfo multiSignData = new RawTxInfo(txBytes, p2sh, cashList);

        RawTxInfo multiSignDataA = signSchnorrMultiSignTx(multiSignData, prikeyBytesA, FchMainNetwork.MAINNETWORK);
        RawTxInfo multiSignDataB = signSchnorrMultiSignTx(multiSignData, prikeyBytesB, FchMainNetwork.MAINNETWORK);
        RawTxInfo multiSignDataC = signSchnorrMultiSignTx(multiSignData, prikeyBytesC, FchMainNetwork.MAINNETWORK);

        Map<String, List<String>> sig1 = multiSignDataA.getFidSigMap();
        Map<String, List<String>> sig2 = multiSignDataB.getFidSigMap();
        Map<String, List<String>> sig3 = multiSignDataC.getFidSigMap();

        System.out.println("Verify sig3:" + rawTxSigVerify(txBytes, ecKeyC.getPubKey(), Hex.fromHex(sig3.get(fidC).get(0)), 0, cashList.get(0).getValue(), redeemScript, FchMainNetwork.MAINNETWORK));

        Map<String, List<String>> sigAll = new HashMap<>();
        sigAll.putAll(sig1);
        sigAll.putAll(sig2);

        for (String fid : sigAll.keySet()) {
            System.out.println(fid + ":");
            List<String> sigList = sigAll.get(fid);
            for (String sig : sigList) {
                System.out.println("    " + sig);
            }
        }
        Shower.printUnderline(10);
        //build signed tx
        String signedTx = buildSchnorrMultiSignTx(txBytes, sigAll, p2sh, FchMainNetwork.MAINNETWORK);

        System.out.println(signedTx);

        String msC = multiSignDataC.toNiceJson();
        System.out.println(msC);
        RawTxInfo multiSignDataD = RawTxInfo.fromJson(msC, RawTxInfo.class);
        System.out.println("New:" + multiSignDataD.toNiceJson());
    }

    @Nullable
    private static byte[] getSessionKey(BufferedReader br) {
        System.out.println("Confirm or set your password...");
        byte[] passwordBytes = Inputer.getPasswordBytes(br);
        byte[] symKey = Hash.sha256x2(passwordBytes);
        byte[] sessionKey = new byte[0];
        try {
            ApiAccount apiAccount = ApiAccount.checkApipAccount(br, passwordBytes.clone());
            if (apiAccount == null) return null;
            sessionKey = ApiAccount.decryptSessionKey(apiAccount.getSession().getKeyCipher(), Hash.sha256x2(passwordBytes));
            if (sessionKey == null) return null;
            BytesUtils.clearByteArray(passwordBytes);
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Wrong password, try again.");
        }
        return sessionKey;
    }

    private static void lockTimeTxTest(ApipClient apipClient) {
        String prikey = "L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8";
        String prikey32 = Hex.toHex(getPrikey32(prikey));
        if (prikey32 == null) return;
        byte[] prikeyBytes = Hex.fromHex(prikey32);
        ECKey ecKey = ECKey.fromPrivate(prikeyBytes);
        String pubkey = ecKey.getPublicKeyAsHex();
        String fid = KeyTools.pubkeyToFchAddr(pubkey);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        byte[] sessionKey = getSessionKey(br);
        if (sessionKey == null) return;

        String urlHead = Constants.UrlHead_CID_CASH;

        List<SendTo> sendToList = new ArrayList<>();
        SendTo sendTo = new SendTo();
        sendTo.setFid(fid);
        sendTo.setAmount(0.1);
        sendToList.add(sendTo);

        String msg = "hi";

        List<Cash> cashList  = apipClient.cashValid(fid,0.1,null,sendToList.size(),msg.getBytes().length, RequestMethod.POST, AuthType.FC_SIGN_BODY);

        String txSigned = createTimeLockedTransaction(cashList, prikeyBytes, sendToList, 1999900, msg, FchMainNetwork.MAINNETWORK);
        System.out.println(txSigned);
    }


    private static void schnorrMsgTest() throws IOException {
        String prikey = "L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8";
        String prikey32 = Hex.toHex(getPrikey32(prikey));
        if (prikey32 == null) return;
        byte[] prikeyBytes = Hex.fromHex(prikey32);
        ECKey ecKey = ECKey.fromPrivate(prikeyBytes);
        String pubkey = ecKey.getPublicKeyAsHex();
        String fid = KeyTools.pubkeyToFchAddr(pubkey);
        String msg = "hello";
        System.out.println(msg);
        String sign = Signature.schnorrMsgSign(msg, prikeyBytes);
        System.out.println("sign:" + sign);

        boolean verify = Signature.schnorrMsgVerify(msg, sign, fid);
        System.out.println("verify '" + msg + "':" + verify);
        verify = Signature.schnorrMsgVerify(msg + " ", sign, fid);
        System.out.println("verify '" + msg + " " + "':" + verify);
        Signature signature = new Signature(fid, msg, sign, AlgorithmId.FC_SchnorrSignTx_No1_NrC7, null);
        System.out.println(JsonUtils.toNiceJson(signature));
    }

    public static void schnorrTxTest() {
        NetworkParameters params = org.bitcoinj.fch.FchMainNetwork.MAINNETWORK;
        Transaction transaction = new Transaction(params);
        int inputIndex = 0;
        String prikey = "L2bHRej6Fxxipvb4TiR5bu1rkT3tRp8yWEsUy4R1Zb8VMm2x7sd8";
        byte[] prikeyBytes = getPrikey32(prikey);
        String prikey32 = Hex.toHex(prikeyBytes);
        System.out.println("prikey32:" + prikey32);

        ECKey ecKey = ECKey.fromPrivate(prikeyBytes);
        System.out.println("Pubkey:" + ecKey.getPublicKeyAsHex());
        System.out.println("Address:" + ecKey.toAddress(params));


        Address address = Address.fromKey(params, ecKey);
        String addr = address.toBase58();
        System.out.println("addr to:" + addr);
        Script script = ScriptBuilder.createOutputScript(address);


        Transaction.SigHash sigHash = Transaction.SigHash.ALL;
        boolean anyoneCanPay = false;
        Coin value = Coin.valueOf(100000000);

        List<TxInput> inputs = new ArrayList<>();
        TxInput txInput = new TxInput();
        txInput.setAmount(2 * COIN_TO_SATOSHI);
        txInput.setIndex(0);
        txInput.setPrikey32(prikeyBytes);
        txInput.setTxId("6a8ee1015faedaf31d2742c204ad34120426e656dcffbcaca74b919ce81f8e44");
        inputs.add(txInput);

        List<TxOutput> outputs = new ArrayList<>();
        TxOutput txOutput = new TxOutput();
        txOutput.setAddress(addr);
        txOutput.setAmount((long) (0.9 * COIN_TO_SATOSHI));
        outputs.add(txOutput);


        String opreturn = "text";
        long fee = 1000;
        String signed = createTxFch(inputs, outputs, opreturn, addr);
        System.out.println(signed);
    }

    private static void testVarInt() {
        System.out.println("0:" + VarInt.sizeOf(0));
        System.out.println("1:" + VarInt.sizeOf(1));
        System.out.println("253:" + VarInt.sizeOf(253));
        System.out.println("65536:" + VarInt.sizeOf(65536));
        System.out.println("4294967296:" + VarInt.sizeOf(4294967296L));


        long inputNum = 1;
        long outputNum = 1;
        long opLen = 4;
        long length = 10 + (long) 141 * inputNum + (long) 34 * (outputNum + 1) + (opLen + VarInt.sizeOf(opLen) + 1 + VarInt.sizeOf(opLen + VarInt.sizeOf(opLen) + 1) + 8);
        System.out.println("4:" + length);
    }
}
