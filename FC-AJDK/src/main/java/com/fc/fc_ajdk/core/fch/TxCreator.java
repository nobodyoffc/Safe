package com.fc.fc_ajdk.core.fch;

import com.fc.fc_ajdk.data.fcData.ReplyBody;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.google.common.base.Preconditions;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.core.crypto.KeyTools;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.P2SH;
import com.fc.fc_ajdk.data.fchData.RawTxForCsV1;
import com.fc.fc_ajdk.data.fchData.SendTo;
import org.bitcoinj.core.Address;
import org.bitcoinj.params.MainNetParams;

import com.fc.fc_ajdk.data.nasa.TxInput;
import com.fc.fc_ajdk.data.nasa.TxOutput;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.SchnorrSignature;
import org.bitcoinj.fch.FchMainNetwork;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptOpCodes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import com.fc.fc_ajdk.utils.TimberLogger;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.*;

import static com.fc.fc_ajdk.constants.Constants.COIN_TO_SATOSHI;
import static com.fc.fc_ajdk.core.crypto.KeyTools.prikeyToFid;

import androidx.annotation.Nullable;

/**
 * 工具类
 */
public class TxCreator {
    public static final double DEFAULT_FEE_RATE = 0.00001;
    public static final byte OFF_LINE_TX_START_FLAG = (byte) 0xFF;

    static {
        fixKeyLength();
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void fixKeyLength() {
        String errorString = "Failed manually overriding key-length permissions.";
        int newMaxKeyLength;
        try {
            if ((newMaxKeyLength = Cipher.getMaxAllowedKeyLength("AES")) < 256) {
                Class<?> c = Class.forName("javax.crypto.CryptoAllPermissionCollection");
                Constructor<?> con = c.getDeclaredConstructor();
                con.setAccessible(true);
                Object allPermissionCollection = con.newInstance();
                Field f = c.getDeclaredField("all_allowed");
                f.setAccessible(true);
                f.setBoolean(allPermissionCollection, true);

                c = Class.forName("javax.crypto.CryptoPermissions");
                con = c.getDeclaredConstructor();
                con.setAccessible(true);
                Object allPermissions = con.newInstance();
                f = c.getDeclaredField("perms");
                f.setAccessible(true);
                ((Map) f.get(allPermissions)).put("*", allPermissionCollection);

                c = Class.forName("javax.crypto.JceSecurityManager");
                f = c.getDeclaredField("defaultPolicy");
                f.setAccessible(true);
                Field mf = Field.class.getDeclaredField("modifiers");
                mf.setAccessible(true);
                mf.setInt(f, f.getModifiers() & ~Modifier.FINAL);
                f.set(null, allPermissions);

                newMaxKeyLength = Cipher.getMaxAllowedKeyLength("AES");
            }
        } catch (Exception e) {
            throw new RuntimeException(errorString, e);
        }
        if (newMaxKeyLength < 256)
            throw new RuntimeException(errorString); // hack failed
    }

    public static Transaction parseTx(String rawTx){
        return new Transaction(com.fc.fc_ajdk.core.fch.FchMainNetwork.MAINNETWORK,Hex.fromHex(rawTx));
    }

    public static String createTxFch(List<Cash> inputs, byte[] prikey, List<SendTo> outputs, String opReturn, FchMainNetwork mainnetwork) {
        return createTxFch(inputs, prikey, outputs, opReturn, 0, mainnetwork);
    }

    public static String createTxFch(List<Cash> inputs, byte[] prikey, List<SendTo> outputs, String opReturn, double feeRateDouble, MainNetParams mainnetwork) {
        String changeToFid = inputs.get(0).getOwner();
        if(outputs==null)outputs = new ArrayList<>();
        long txSize = opReturn == null ? calcTxSize(inputs.size(), outputs.size(), 0) : calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);

        long fee =calcFee(txSize,feeRateDouble);

        Transaction transaction = new Transaction(mainnetwork);

        long totalMoney = 0;
        long totalOutput = 0;

        ECKey eckey = ECKey.fromPrivate(prikey);

        for (SendTo output : outputs) {
            long value = FchUtils.coinToSatoshi(output.getAmount());
            totalOutput += value;
            transaction.addOutput(Coin.valueOf(value), Address.fromBase58(mainnetwork, output.getFid()));
        }

        if (opReturn != null && !opReturn.isEmpty()) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        totalMoney = addInputToTx(inputs, mainnetwork, transaction);

        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }
        long change = totalMoney - totalOutput - fee;
        if (change > Constants.DustInSatoshi) {
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(mainnetwork, changeToFid));
        }

        for (int i = 0; i < inputs.size(); ++i) {
            Cash input = inputs.get(i);
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(input.getValue()), Transaction.SigHash.ALL, false);
            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }
    public static String signTx(RawTxInfo rawTxInfo, byte[] prikey){
        Transaction transaction = TxCreator.createUnsignedTx(rawTxInfo, com.fc.fc_ajdk.core.fch.FchMainNetwork.MAINNETWORK);
        if(transaction==null)return null;
        return TxCreator.signTx(prikey,transaction);
    }

//    public static String signTx(String rawTx,byte[] prikey){
//        if(rawTx==null|| prikey==null)return null;
//        Transaction transaction = new Transaction(com.fc.fc_ajdk.core.fch.FchMainNetwork.MAINNETWORK,Hex.fromHex(rawTx));
//        return TxCreator.signTx(prikey,transaction);
//    }
    public static String createUnsignedTx(RawTxInfo rawTxInfo) {
        Transaction tx = createUnsignedTx(rawTxInfo, com.fc.fc_ajdk.core.fch.FchMainNetwork.MAINNETWORK);
        if(tx==null)return null;
        byte[] txBytes = tx.bitcoinSerialize();
        return Hex.toHex(txBytes);
    }
    public static Transaction createUnsignedTx(RawTxInfo rawTxInfo, MainNetParams mainnetwork) {
        try {
            if (rawTxInfo.getInputs().get(0).getOwner() == null)
                rawTxInfo.getInputs().get(0).setOwner(rawTxInfo.getSender());
        }catch (Exception e){
            TimberLogger.e("The sender is absent.");
            return null;
        }
        return createUnsignedTx(rawTxInfo.getInputs(), rawTxInfo.getOutputs(), rawTxInfo.getOpReturn(), rawTxInfo.getP2sh(), rawTxInfo.getFeeRate(), null, mainnetwork);
    }

    public static String createUnsignedTx(List<Cash> inputs, List<SendTo> outputs, String opReturn, Double feeRate){
        Transaction tx = createUnsignedTx(inputs, outputs, opReturn, null, feeRate, null, com.fc.fc_ajdk.core.fch.FchMainNetwork.MAINNETWORK);
        if(tx==null)return null;
        byte[] txBytes = tx.bitcoinSerialize();
        return Hex.toHex(txBytes);
    }

    public static Transaction createUnsignedTx(List<Cash> inputs, List<SendTo> outputs, String opReturn, P2SH p2shForMultiSign, Double feeRate, String changeToFid, MainNetParams mainnetwork) {
        if (inputs == null || inputs.isEmpty()) {
            TimberLogger.e("The sender is absent.");
            return null;
        }
        byte[] opReturnBytes= null;
        if(opReturn!=null) opReturnBytes = opReturn.getBytes();
        if(changeToFid==null)
            changeToFid = inputs.get(0).getOwner();

        boolean isMultiSign = inputs.get(0).getOwner().startsWith("3");

        if(feeRate==null || feeRate==0)feeRate=DEFAULT_FEE_RATE;
        long fee;

        int inputSize = inputs.size();
        int outputSize = outputs ==null ? 0 : outputs.size();
        int opReturnBytesLen = opReturn ==null ? 0 : opReturnBytes.length;

        fee = calcFee(inputSize, outputSize, opReturnBytesLen, feeRate, isMultiSign, p2shForMultiSign);

        Transaction transaction = new Transaction(mainnetwork);

        long totalOutput = 0;

        long totalMoney = addInputToTx(inputs, mainnetwork, transaction);

        if(outputs !=null && !outputs.isEmpty()){
            for (SendTo output : outputs) {
                long value = FchUtils.coinToSatoshi(output.getAmount());
                totalOutput += value;
                transaction.addOutput(Coin.valueOf(value), Address.fromBase58(mainnetwork, output.getFid()));
            }
        }
        long changeOutputFee = 34L * FchUtils.coinToSatoshi(feeRate/ 1000);

        if(!(totalOutput + fee - changeOutputFee == totalMoney)){

            if ((totalOutput + fee ) > totalMoney) {
                TimberLogger.i("Input is not enough");
                return null;
            }

            long change = totalMoney - totalOutput - fee;
            if (change > Constants.DustInSatoshi) {
                transaction.addOutput(Coin.valueOf(change), Address.fromBase58(mainnetwork, changeToFid));
            }
        }

        if (opReturn != null && opReturnBytes.length>0) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturnBytes);
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                TimberLogger.e("Failed to create opreturn script: "+e.getMessage());
                return null;
            }
        }

        return transaction;
    }

    private static long addInputToTx(List<Cash> valueTxIdIndexCashList, MainNetParams mainnetwork, Transaction transaction) {
        long totalMoney=0;

        for (Cash input : valueTxIdIndexCashList) {
            totalMoney += input.getValue();
            TransactionOutPoint outPoint = new TransactionOutPoint(mainnetwork, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(mainnetwork, null, new byte[0], outPoint, Coin.valueOf(input.getValue()));
            transaction.addInput(unsignedInput);
        }

        return totalMoney;
    }

    public static String signOffLineTx(byte[] prikey, RawTxInfo rawTxInfo, com.fc.fc_ajdk.core.fch.FchMainNetwork mainnetwork) {
        Transaction transaction = parseOffLineTx(rawTxInfo,mainnetwork);
        return signTx(prikey,transaction);
    }
    
    public static String signTx(byte[] prikey, Transaction transaction) {
        if(prikey==null){
            return null;
        }
        ECKey eckey = ECKey.fromPrivate(prikey);

        List<TransactionInput> inputs = transaction.getInputs();
        for (int i = 0; i < inputs.size(); ++i) {
            TransactionInput input = inputs.get(i);
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            Coin value = input.getValue();
            if(value==null)continue;
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(value.getValue()), Transaction.SigHash.ALL, false);
            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    public static long calcFee(int inputSize, int outputSize, int opReturnBytesLen, double feeRate, boolean isMultiSign, P2SH p2shForMultiSign) {
        long fee;
        if(isMultiSign) {
            long feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);
            fee = feeRateLong * TxCreator.calcSizeMultiSign(inputSize, outputSize, opReturnBytesLen, p2shForMultiSign.getM(), p2shForMultiSign.getN());
        }else {
            long txSize = calcTxSize(inputSize, outputSize, opReturnBytesLen);
            fee =calcFee(txSize, feeRate);
        }
        return fee;
    }


    public static String signRawTx(String valuesAndRawTx, byte[] prikey, MainNetParams mainnetwork) {
        Transaction transaction = parseOldCsRawTxToTx(valuesAndRawTx, mainnetwork);
        if (transaction == null) return null;
        return signTx(prikey, transaction);
    }

    //Off line TX methods

    /**
     * Parse user off-line TX request json
     */
    public static RawTxInfo parseDataForOffLineTxFromOther(String json) {
        Gson gson = new Gson();
        return gson.fromJson(json, RawTxInfo.class);
    }

    /**
     * Convert off-line TX information to Transaction.
     */
    public static Transaction parseOffLineTx(RawTxInfo rawTxInfo, MainNetParams mainnetwork) {
        List<Cash> cashList = rawTxInfo.getInputs();
        List<SendTo> sendToList = rawTxInfo.getOutputs();
        String msg = rawTxInfo.getOpReturn();
        return createUnsignedTx(cashList, sendToList, msg, rawTxInfo.getP2sh(), rawTxInfo.getFeeRate(), null, mainnetwork);
    }
//
//    public static class RawTxInfo {
//        private String sender;
//        private List<SendTo> outputs;
//        private Long cd;
//        private String msg;
//        private String ver;
//
//        public String getVer() {
//            return ver;
//        }
//
//        public void setVer(String ver) {
//            this.ver = ver;
//        }
//
//        public String getSender() {
//            return sender;
//        }
//
//        public void setSender(String sender) {
//            this.sender = sender;
//        }
//
//        public List<SendTo> getOutputs() {
//            return outputs;
//        }
//
//        public void setOutputs(List<SendTo> outputs) {
//            this.outputs = outputs;
//        }
//
//        public Long getCd() {
//            return cd;
//        }
//
//        public void setCd(Long cd) {
//            this.cd = cd;
//        }
//
//        public String getMsg() {
//            return msg;
//        }
//
//        public void setMsg(String msg) {
//            this.msg = msg;
//        }
//    }


    //Old methods
    public static String createTxFch(List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr) {
        FchMainNetwork mainnetwork = FchMainNetwork.MAINNETWORK;
        return createTxClassic(mainnetwork, inputs, outputs, opReturn, returnAddr, 0);
    }


    public static String createTxClassic(NetworkParameters networkParameters, List<TxInput> inputs, List<TxOutput> outputs, String opReturn, String returnAddr, double feeRateDouble) {

        long txSize = opReturn == null ? calcTxSize(inputs.size(), outputs.size(), 0) : calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);

        long feeRateLong;
        if (feeRateDouble != 0) {
            feeRateLong = (long) (feeRateDouble / 1000 * COIN_TO_SATOSHI);
        } else feeRateLong = (long) (DEFAULT_FEE_RATE / 1000 * COIN_TO_SATOSHI);
        long fee = feeRateLong * txSize;
        Transaction transaction = new Transaction(networkParameters);

        long totalMoney = 0;
        long totalOutput = 0;

        List<ECKey> ecKeys = new ArrayList<>();
        for (TxOutput output : outputs) {
            totalOutput += output.getAmount();
            transaction.addOutput(Coin.valueOf(output.getAmount()), Address.fromBase58(networkParameters, output.getAddress()));
        }

        if (opReturn != null && !opReturn.isEmpty()) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (TxInput input : inputs) {
            totalMoney += input.getAmount();

            ECKey eckey = ECKey.fromPrivate(input.getPrikey32());

            ecKeys.add(eckey);
            UTXO utxo = new UTXO(Sha256Hash.wrap(input.getTxId()), input.getIndex(), Coin.valueOf(input.getAmount()), 0, false, ScriptBuilder.createP2PKHOutputScript(eckey));
            TransactionOutPoint outPoint = new TransactionOutPoint(networkParameters, utxo.getIndex(), utxo.getHash());
            TransactionInput unsignedInput = new TransactionInput(new FchMainNetwork(), transaction, new byte[0], outPoint);
            transaction.addInput(unsignedInput);
        }
        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }
        long change = totalMoney - totalOutput - fee;

        if (change > Constants.DustInSatoshi) {
            if (returnAddr == null)
                returnAddr = ECKey.fromPrivate(inputs.get(0).getPrikey32()).toAddress(networkParameters).toBase58();
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(networkParameters, returnAddr));
        }

        for (int i = 0; i < inputs.size(); ++i) {
            TxInput input = inputs.get(i);
            ECKey eckey = ecKeys.get(i);
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(input.getAmount()), Transaction.SigHash.ALL, false);
            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }


    public static List<TxInput> cashListToTxInputList(List<Cash> cashList, byte[] prikey32) {
        List<TxInput> txInputList = new ArrayList<>();
        for (Cash cash : cashList) {
            TxInput txInput = cashToTxInput(cash, prikey32);
            if (txInput != null) {
                txInputList.add(txInput);
            }
        }
        if (txInputList.isEmpty()) return null;
        return txInputList;
    }

    public static TxInput cashToTxInput(Cash cash, byte[] prikey32) {
        if (cash == null) {
            System.out.println("Cash is null.");
            return null;
        }
        if (!cash.isValid()) {
            System.out.println("Cash has been spent.");
            return null;
        }
        TxInput txInput = new TxInput();

        txInput.setPrikey32(prikey32);
        txInput.setAmount(cash.getValue());
        txInput.setTxId(cash.getBirthTxId());
        txInput.setIndex(cash.getBirthIndex());

        return txInput;
    }

    //For old CryptoSign off-line TX

    public static String makeOffLineTxRequiredJson(RawTxInfo sendRequestForCs, List<Cash> meetList) {
        if(sendRequestForCs.getVer().equals("1"))return makeCsTxRequiredJsonV1(sendRequestForCs,meetList);
        sendRequestForCs.setInputs(meetList);
        return sendRequestForCs.toJson();
    }

        public static String makeCsTxRequiredJsonV1(RawTxInfo sendRequestForCs, List<Cash> meetList) {
        Gson gson = new Gson();
        StringBuilder RawTx = new StringBuilder("[");
        int i = 0;
        for (Cash cash : meetList) {
            if (i > 0) RawTx.append(",");
            RawTxForCsV1 rawTxForCsV1 = new RawTxForCsV1();
            rawTxForCsV1.setAddress(cash.getOwner());
            rawTxForCsV1.setAmount((double) cash.getValue() / COIN_TO_SATOSHI);
            rawTxForCsV1.setTxid(cash.getBirthTxId());
            rawTxForCsV1.setIndex(cash.getBirthIndex());
            rawTxForCsV1.setSeq(i);
            rawTxForCsV1.setDealType(RawTxForCsV1.DealType.INPUT);
            RawTx.append(gson.toJson(rawTxForCsV1));
            i++;
        }
        int j = 0;
        if (sendRequestForCs.getOutputs() != null) {
            for (SendTo sendTo : sendRequestForCs.getOutputs()) {
                RawTxForCsV1 rawTxForCsV1 = new RawTxForCsV1();
                rawTxForCsV1.setAddress(sendTo.getFid());
                rawTxForCsV1.setAmount(sendTo.getAmount());
                rawTxForCsV1.setSeq(j);
                rawTxForCsV1.setDealType(RawTxForCsV1.DealType.OUTPUT);
                RawTx.append(",");
                RawTx.append(gson.toJson(rawTxForCsV1));
                j++;
            }
        }

        if (sendRequestForCs.getOpReturn() != null) {
            RawTxForCsV1 rawOpReturnForCs = new RawTxForCsV1();
            rawOpReturnForCs.setMsg(sendRequestForCs.getOpReturn());
            rawOpReturnForCs.setSeq(j);
            rawOpReturnForCs.setDealType(RawTxForCsV1.DealType.OP_RETURN);
            RawTx.append(",");
            RawTx.append(gson.toJson(rawOpReturnForCs));
        }
        RawTx.append("]");
        return RawTx.toString();
    }

    public static Transaction parseOldCsRawTxToTx(String oldCsUnsignedTx, MainNetParams mainnetwork) {
        List<Cash> cashList = new ArrayList<>();
        List<SendTo> sendToList = new ArrayList<>();
        String msg = null;

        // Parse the JSON array
        List<RawTxForCsV1> rawTxForCsV1List = parseRawTxForCsList(oldCsUnsignedTx);

        for (RawTxForCsV1 element : rawTxForCsV1List) {
            
            int dealType = element.getDealType();

            switch (dealType) {
                case 1: // Cash entries
                    Cash cash = new Cash();
                    cash.setOwner(element.getAddress());
                    cash.setValue((long)(element.getAmount() * COIN_TO_SATOSHI));
                    cash.setBirthTxId(element.getTxid());
                    cash.setBirthIndex(element.getIndex());
                    cashList.add(cash);
                    break;

                case 2: // SendTo entries
                    SendTo sendTo = new SendTo();
                    sendTo.setFid(element.getAddress());
                    sendTo.setAmount(element.getAmount());
                    sendToList.add(sendTo);
                    break;

                case 3: // Message
                    msg = element.getMsg();
                    break;
            }
        }
        return createUnsignedTx(cashList, sendToList, msg, null, DEFAULT_FEE_RATE, null, mainnetwork);
    }

    private static List<RawTxForCsV1> parseRawTxForCsList(String oldCsUnsignedTx) {
        Gson gson = new Gson();
        return gson.fromJson(oldCsUnsignedTx, new TypeToken<List<RawTxForCsV1>>() {}.getType());
    }

// Sign TX
    public static String signSchnorrMultiSignTx(String multiSignDataJson, byte[] prikey, MainNetParams mainNetParams) {
        RawTxInfo multiSignData = com.fc.fc_ajdk.core.fch.RawTxInfo.fromJson(multiSignDataJson, RawTxInfo.class);
        return signSchnorrMultiSignTx(multiSignData, prikey, mainNetParams).toNiceJson();
    }

    public static RawTxInfo signSchnorrMultiSignTx(RawTxInfo multiSignData, byte[] prikey) {
        return signSchnorrMultiSignTx(multiSignData, prikey, com.fc.fc_ajdk.core.fch.FchMainNetwork.MAINNETWORK);
    }

    public static RawTxInfo signSchnorrMultiSignTx(RawTxInfo multiSignData, byte[] prikey, MainNetParams mainnetwork) {

        byte[] rawTx = multiSignData.getRawTx();
        byte[] redeemScript = Hex.fromHex(multiSignData.getP2sh().getRedeemScript());
        List<Cash> cashList = multiSignData.getInputs();

        Transaction transaction = new Transaction(mainnetwork, rawTx);
        List<TransactionInput> inputs = transaction.getInputs();

        ECKey ecKey = ECKey.fromPrivate(prikey);
        BigInteger prikeyBigInteger = ecKey.getPrivKey();
        List<String> sigList = new ArrayList<>();
        for (int i = 0; i < inputs.size(); ++i) {
            Script script = new Script(redeemScript);
            Sha256Hash hash = transaction.hashForSignatureWitness(i, script, Coin.valueOf(cashList.get(i).getValue()), Transaction.SigHash.ALL, false);
            byte[] sig = SchnorrSignature.schnorr_sign(hash.getBytes(), prikeyBigInteger);
            sigList.add(Hex.toHex(sig));
        }

        String fid = prikeyToFid(prikey);
        if (multiSignData.getFidSigMap() == null) {
            Map<String, List<String>> fidSigListMap = new HashMap<>();
            multiSignData.setFidSigMap(fidSigListMap);
        }
        multiSignData.getFidSigMap().put(fid, sigList);
        return multiSignData;
    }

    public static boolean rawTxSigVerify(byte[] rawTx, byte[] pubkey, byte[] sig, int inputIndex, long inputValue, byte[] redeemScript, MainNetParams mainnetwork) {
        Transaction transaction = new Transaction(mainnetwork, rawTx);
        Script script = new Script(redeemScript);
        Sha256Hash hash = transaction.hashForSignatureWitness(inputIndex, script, Coin.valueOf(inputValue), Transaction.SigHash.ALL, false);
        return SchnorrSignature.schnorr_verify(hash.getBytes(), pubkey, sig);
    }


    public static String buildSchnorrMultiSignTx(RawTxInfo rawTxInfo, MainNetParams mainnetwork) {
            return buildSchnorrMultiSignTx(rawTxInfo.getRawTx(), rawTxInfo.getFidSigMap(), rawTxInfo.getP2sh(), mainnetwork);
    }

    public static String buildSchnorrMultiSignTx(RawTxInfo rawTxInfo) {
        return buildSchnorrMultiSignTx(rawTxInfo.getRawTx(), rawTxInfo.getFidSigMap(), rawTxInfo.getP2sh(), com.fc.fc_ajdk.core.fch.FchMainNetwork.MAINNETWORK);
    }

    public static String buildSchnorrMultiSignTx(byte[] rawTx, Map<String, List<String>> sigListMap, P2SH p2sh, MainNetParams mainnetwork) {
        if (sigListMap.size() > p2sh.getM())
            sigListMap = dropRedundantSigs(sigListMap, p2sh.getM());

        Transaction transaction = new Transaction(mainnetwork, rawTx);

        for (int i = 0; i < transaction.getInputs().size(); i++) {
            List<byte[]> sigListByTx = new ArrayList<>();
            for (String fid : p2sh.getFids()) {
                try {
                    String sig = sigListMap.get(fid).get(i);
                    sigListByTx.add(Hex.fromHex(sig));
                } catch (Exception ignore) {
                }
            }

            Script inputScript = createSchnorrMultiSigInputScriptBytes(sigListByTx, Hex.fromHex(p2sh.getRedeemScript())); // Include all required signatures
            TransactionInput input = transaction.getInput(i);
            input.setScriptSig(inputScript);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    private static Map<String, List<String>> dropRedundantSigs(Map<String, List<String>> sigListMap, int m) {
        Map<String, List<String>> newMap = new HashMap<>();
        int i = 0;
        for (String key : sigListMap.keySet()) {
            newMap.put(key, sigListMap.get(key));
            i++;
            if (i == m) return newMap;
        }
        return newMap;
    }

    public static Script createSchnorrMultiSigInputScriptBytes(List<byte[]> signatures, byte[] multisigProgramBytes) {
        if (signatures.size() >= 16) return null;
        ScriptBuilder builder = new ScriptBuilder();
        builder.smallNum(0);
        Iterator<byte[]> var3 = signatures.iterator();
        byte[] sigHashAll = new byte[]{0x41};

        while (var3.hasNext()) {
            byte[] signature = (byte[]) var3.next();
            builder.data(BytesUtils.bytesMerger(signature, sigHashAll));
        }

        if (multisigProgramBytes != null) {
            builder.data(multisigProgramBytes);
        }

        return builder.build();
    }

    public static String createTimeLockedTransaction(List<Cash> inputs, byte[] prikey, List<SendTo> outputs, long lockUntil, String opReturn, MainNetParams mainnetwork) {

        String changeToFid = inputs.get(0).getOwner();

        long fee;
        if (opReturn != null) {
            fee = TxCreator.calcTxSize(inputs.size(), outputs.size(), opReturn.getBytes().length);
        } else fee = TxCreator.calcTxSize(inputs.size(), outputs.size(), 0);

        Transaction transaction = new Transaction(mainnetwork);
//        transaction.setLockTime(nLockTime);

        long totalMoney = 0;
        long totalOutput = 0;

        ECKey eckey = ECKey.fromPrivate(prikey);

        for (SendTo output : outputs) {
            long value = FchUtils.coinToSatoshi(output.getAmount());
            byte[] pubkeyHash = KeyTools.addrToHash160(output.getFid());
            totalOutput += value;

            ScriptBuilder builder = new ScriptBuilder();

            builder.number(lockUntil)
                    .op(ScriptOpCodes.OP_CHECKLOCKTIMEVERIFY)
                    .op(ScriptOpCodes.OP_DROP);

            builder.op(ScriptOpCodes.OP_DUP)
                    .op(ScriptOpCodes.OP_HASH160)
                    .data(pubkeyHash)
                    .op(ScriptOpCodes.OP_EQUALVERIFY)
                    .op(ScriptOpCodes.OP_CHECKSIG);

            Script cltvScript = builder.build();

            transaction.addOutput(Coin.valueOf(value), cltvScript);
        }

        if (opReturn != null && !opReturn.isEmpty()) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(opReturn.getBytes(StandardCharsets.UTF_8));
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (Cash input : inputs) {
            totalMoney += input.getValue();

            TransactionOutPoint outPoint = new TransactionOutPoint(mainnetwork, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(mainnetwork, null, new byte[0], outPoint,Coin.valueOf(input.getValue()));
            transaction.addInput(unsignedInput);
        }

        if ((totalOutput + fee) > totalMoney) {
            throw new RuntimeException("input is not enough");
        }

        long change = totalMoney - totalOutput - fee;
        if (change > Constants.DustInSatoshi) {
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(mainnetwork, changeToFid));
        }

        for (int i = 0; i < inputs.size(); ++i) {
            Cash input = inputs.get(i);
            Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
            SchnorrSignature signature = transaction.calculateSchnorrSignature(i, eckey, script.getProgram(), Coin.valueOf(input.getValue()), Transaction.SigHash.ALL, false);

            Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
            transaction.getInput(i).setScriptSig(schnorr);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    //Tools
    public static P2SH createP2sh(List<byte[]> pubkeyList, int m) {
        List<ECKey> keys = new ArrayList<>();
        for (byte[] bytes : pubkeyList) {
            ECKey ecKey = ECKey.fromPublicOnly(bytes);
            keys.add(ecKey);
        }

        Script multiSigScript = ScriptBuilder.createMultiSigOutputScript(m, keys);

        byte[] redeemScriptBytes = multiSigScript.getProgram();

        P2SH p2sh;
        try {
            p2sh = P2SH.parseP2shRedeemScript(Hex.toHex(redeemScriptBytes));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return p2sh;
    }

    public static long calcTxSize(int inputNum, int outputNum, int opReturnBytesLen) {

        long baseLength = 10;
        long inputLength = 141 * (long) inputNum;
        long outputLength = 34 * (long) (outputNum + 1); // Include change output

        int opReturnLen = 0;
        if (opReturnBytesLen != 0)
            opReturnLen = calcOpReturnLen(opReturnBytesLen);

        return baseLength + inputLength + outputLength + opReturnLen;
    }

    private static int calcOpReturnLen(int opReturnBytesLen) {
        int dataLen;
        if (opReturnBytesLen < 76) {
            dataLen = opReturnBytesLen + 1;
        } else if (opReturnBytesLen < 256) {
            dataLen = opReturnBytesLen + 2;
        } else dataLen = opReturnBytesLen + 3;
        int scriptLen;
        scriptLen = (dataLen + 1) + VarInt.sizeOf(dataLen + 1);
        int amountLen = 8;
        return scriptLen + amountLen;
    }

    public static long calcFee(long txSize, double feeRate) {
        long feeRateLong;
        if (feeRate != 0) {
            feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);
        } else feeRateLong = (long) (DEFAULT_FEE_RATE / 1000 * COIN_TO_SATOSHI);
        long fee = feeRateLong * txSize;
        return fee;
    }
    public static String decodeTx(String rawTx) {
        return decodeTx(Hex.fromHex(rawTx), com.fc.fc_ajdk.core.fch.FchMainNetwork.MAINNETWORK);
    }
    public static String decodeTx(String rawTx, MainNetParams mainNetParams) {
        byte[] rawTxBytes;
        try{
            if(Hex.isHexString(rawTx))rawTxBytes = Hex.fromHex(rawTx);
            else {
                rawTxBytes = Base64.getDecoder().decode(rawTx);
            }
        }catch (Exception e){
            return null;
        }
        return decodeTx(rawTxBytes,mainNetParams);
    }

    public static String decodeTxFch(byte[] rawTxBytes) {
        return decodeTx(rawTxBytes, FchMainNetwork.MAINNETWORK);
    }

    public static String decodeTx(byte[] rawTxBytes, MainNetParams mainnetwork) {
        if(rawTxBytes==null) return null;

        Transaction transaction;
            // Handle parsing of combined format with input values
            List<Long> inputValueList = new ArrayList<>();
            byte[] rawTx;
            try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(rawTxBytes)) {
                int flag = byteArrayInputStream.read();
                if(flag== OFF_LINE_TX_START_FLAG) {
                    byte[] b2 = new byte[2];
                    byteArrayInputStream.read(b2);
                    int size = BytesUtils.bytes2ToIntBE(b2);
                    byte[] b8 = new byte[8];
                    for (int i = 0; i < size; i++) {
                        byteArrayInputStream.read(b8);
                        inputValueList.add(BytesUtils.bytes8ToLong(b8, false));
                    }
                    rawTx = BytesUtils.readAllBytes(byteArrayInputStream);
                    transaction = new Transaction(mainnetwork, rawTx);
                }else {
                    transaction = new Transaction(mainnetwork, rawTxBytes);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }


        // Build JSON structure
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append(String.format("  \"txid\": \"%s\",\n", transaction.getTxId()));
        json.append(String.format("  \"hash\": \"%s\",\n", transaction.getHash()));
        json.append(String.format("  \"version\": %d,\n", transaction.getVersion()));
        json.append(String.format("  \"size\": %d,\n", transaction.getMessageSize()));
        json.append(String.format("  \"locktime\": %d,\n", transaction.getLockTime()));

        // Handle inputs
        json.append("  \"vin\": [\n");
        List<TransactionInput> inputs = transaction.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            TransactionInput input = inputs.get(i);
            json.append("    {\n");
            json.append(String.format("      \"txid\": \"%s\",\n", input.getOutpoint().getHash()));
            json.append(String.format("      \"vout\": %d,\n", input.getOutpoint().getIndex()));
            json.append("      \"scriptSig\": {\n");
            json.append(String.format("        \"asm\": \"%s\",\n", input.getScriptSig().toString()));
            json.append(String.format("        \"hex\": \"%s\"\n", Hex.toHex(input.getScriptSig().getProgram())));
            json.append("      },\n");
            json.append(String.format("      \"sequence\": %d\n", input.getSequenceNumber()));
            json.append("    }").append(i < inputs.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ],\n");

        // Handle outputs
        json.append("  \"vout\": [\n");
        List<TransactionOutput> outputs = transaction.getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            TransactionOutput output = outputs.get(i);
            json.append("    {\n");
            json.append(String.format("      \"value\": %.8f,\n", output.getValue().getValue() / 100000000.0));
            json.append(String.format("      \"n\": %d,\n", i));
            json.append("      \"scriptPubkey\": {\n");
            json.append(String.format("        \"asm\": \"%s\",\n", output.getScriptPubKey().toString()));
            json.append(String.format("        \"hex\": \"%s\",\n", Hex.toHex(output.getScriptPubKey().getProgram())));

            // Determine script type and addresses
            String type = getScriptType(output.getScriptPubKey());
            json.append(String.format("        \"type\": \"%s\"", type));

            if (!type.equals("nulldata")) {
                json.append(",\n        \"addresses\": [\n");
                try {
                    Address address = output.getScriptPubKey().getToAddress(mainnetwork);
                    json.append(String.format("          \"%s\"\n", address.toString()));
                } catch (Exception e) {
                    // Handle non-standard scripts
                }
                json.append("        ]");
            }
            json.append("\n      }\n");
            json.append("    }").append(i < outputs.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n");
        json.append("}");

        return json.toString();
    }

    private static String getScriptType(Script script) {
        if (script.isSentToAddress() || script.isSentToRawPubKey())
            return "pubkeyhash";
        else if (script.isPayToScriptHash())
            return "scripthash";
        else if (script.isOpReturn())
            return "nulldata";
        else
            return "nonstandard";
    }

    public static long calcSizeMultiSign(int inputNum, int outputNum, int opReturnBytesLen, int m, int n) {

        /*多签单个Input长度：
            基础字节40（preTxId 32，preIndex 4，sequence 4），
            可变脚本长度：？
            脚本：
                op_0    1
                签名：m * (1+64+1)     // length + pubkeyLength + sigHash ALL
                可变redeemScript 长度：？
                redeem script：
                    op_m    1
                    pubkeys    n * 33
                    op_n    1
                    OP_CHECKMULTISIG    1
         */

        long op_mLen =1;
        long op_nLen =1;
        long pubkeyLen = 33;
        long pubkeyLenLen = 1;
        long op_checkmultisigLen = 1;

        long redeemScriptLength = op_mLen + (n * (pubkeyLenLen + pubkeyLen)) + op_nLen + op_checkmultisigLen; //105 n=3
        long redeemScriptVarInt = VarInt.sizeOf(redeemScriptLength);//1 n=3

        long op_pushDataLen = 1;
        long sigHashLen = 1;
        long signLen=64;
        long signLenLen = 1;
        long zeroByteLen = 1;

        long mSignLen = m * (signLenLen + signLen + sigHashLen); //132 m=2

        long scriptLength = zeroByteLen + mSignLen + op_pushDataLen + redeemScriptVarInt + redeemScriptLength;//236 m=2
        long scriptVarInt = VarInt.sizeOf(scriptLength);

        long preTxIdLen = 32;
        long preIndexLen = 4;
        long sequenceLen = 4;

        long inputLength = preTxIdLen + preIndexLen + sequenceLen + scriptVarInt + scriptLength;//240 n=3,m=2


        long opReturnLen = 0;
        if (opReturnBytesLen != 0)
            opReturnLen = calcOpReturnLen(opReturnBytesLen);

        long outputValueLen=8;
        long unlockScriptLen = 25; //If sending to multiSignAddr, it will be 23.
        long unlockScriptLenLen =1;
        long outPutLen = outputValueLen + unlockScriptLenLen + unlockScriptLen;

        long inputCountLen=1;
        long outputCountLen=1;
        long txVerLen = 4;
        long nLockTimeLen = 4;
        long txFixedLen = inputCountLen + outputCountLen + txVerLen + nLockTimeLen;

        long length;
        length = txFixedLen + inputLength * inputNum + outPutLen * (outputNum + 1) + opReturnLen;

        return length;
    }

    public static String buildSignedTx(String[] signedDatas, MainNetParams mainnetwork) {
        ReplyBody replyBody = mergeMultisignTxData(signedDatas);
        if (replyBody == null) return null;
        if(replyBody.getCode()!=0){
            System.out.println(replyBody.getMessage());
            return null;
        }
        RawTxInfo finalRawTxInfo = (RawTxInfo) replyBody.getData();
        if (finalRawTxInfo == null) return null;
        return buildSchnorrMultiSignTx(finalRawTxInfo,mainnetwork);
    }

    @Nullable
    public static ReplyBody mergeMultisignTxData(String[] signedDatas) {
        Map<String, List<String>> fidSigListMap = new HashMap<>();
        RawTxInfo finalRawTxInfo = null;
        byte[] rawTx = null;
        P2SH p2sh = null;
        ReplyBody replyBody;
        for (String dataJson : signedDatas) {
            try {


                RawTxInfo multiSignData = com.fc.fc_ajdk.core.fch.RawTxInfo.fromJson(dataJson, RawTxInfo.class);

                if (p2sh == null
                        && multiSignData.getP2sh() != null) {
                    p2sh = multiSignData.getP2sh();
                }

                if (rawTx == null
                        && multiSignData.getRawTx() != null
                        && multiSignData.getRawTx().length > 0) {
                    rawTx = multiSignData.getRawTx();
                }

                for(String fid:multiSignData.getFidSigMap().keySet()){
                    List<String> sign = multiSignData.getFidSigMap().get(fid);
                    if(fidSigListMap.get(fid)==null){
                        replyBody = verifySig(fid, multiSignData);
                        if(replyBody.getCode()==0)
                            fidSigListMap.put(fid,sign);
                        else return replyBody;
                    }
                }
                finalRawTxInfo = multiSignData;
            } catch (Exception ignored) {
                replyBody= new ReplyBody();
                replyBody.set1020Other("Failed to parse the signed data.");
                return replyBody;
            }
        }
        if (rawTx == null || p2sh == null) return null;

        finalRawTxInfo.setP2sh(p2sh);
        finalRawTxInfo.setFidSigMap(fidSigListMap);

        replyBody = new ReplyBody();
        replyBody.set0Success();
        replyBody.setData(finalRawTxInfo);
        return replyBody;
    }

    private static ReplyBody verifySig(String fid, RawTxInfo multiSignData) {

        ReplyBody replyBody = new ReplyBody();
        try {
            if (!multiSignData.getP2sh().getFids().contains(fid)){
                replyBody.set1020Other("The FID is not a member of "+multiSignData.getP2sh().getId());
                return replyBody;
            }
            int putKeyIndex = multiSignData.getP2sh().getFids().indexOf(fid);
            String pubkey = multiSignData.getP2sh().getPubKeys().get(putKeyIndex);
            String redeemScript = multiSignData.getP2sh().getRedeemScript();
            for(int i = 0; i<multiSignData.getInputs().size(); i++){
                if(!rawTxSigVerify(multiSignData.getRawTx(), Hex.fromHex(pubkey), Hex.fromHex(multiSignData.getFidSigMap().get(fid).get(i)), i, multiSignData.getInputs().get(i).getValue(), Hex.fromHex(redeemScript), FchMainNetwork.MAINNETWORK)){
                    replyBody.set1020Other("The signature is invalid");
                    return replyBody;
                }
            }
        }catch (Exception e){
            replyBody.set1020Other("Failed to verify the signature.");
            return replyBody;
        }
        replyBody.set0Success();
        return replyBody;
    }

    //Unfinished
    public static Transaction buildLockedTx() {
        Transaction transaction = new Transaction(new com.fc.fc_ajdk.core.fch.FchMainNetwork());

        ScriptBuilder scriptBuilder = new ScriptBuilder();
        byte[] hash = KeyTools.addrToHash160("FKi3bRKUPUbUfQuzxT9CfbYwT7m4KEu13R");
        Script script = scriptBuilder.op(169).data(hash).op(135).build();
        return transaction;
    }

    public static Script createP2PKHOutputScript(byte[] hash) {
        Preconditions.checkArgument(hash.length == 20);
        ScriptBuilder builder = new ScriptBuilder();
        builder.op(118);
        builder.op(169);
        builder.data(hash);
        builder.op(136);
        builder.op(172);
        return builder.build();
    }
}
