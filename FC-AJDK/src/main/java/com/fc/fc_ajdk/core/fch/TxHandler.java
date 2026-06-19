package com.fc.fc_ajdk.core.fch;

import static com.fc.fc_ajdk.constants.Constants.CHANGE_OUTPUT_FEE;
import static com.fc.fc_ajdk.constants.Constants.CHANGE_P2SH_OUTPUT_FEE;
import static com.fc.fc_ajdk.constants.Constants.COIN_TO_SATOSHI;
import static com.fc.fc_ajdk.core.crypto.KeyTools.prikeyToFid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fc.fc_ajdk.constants.Constants;
import com.fc.fc_ajdk.data.fcData.ReplyBody;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.Multisig;
import com.fc.fc_ajdk.data.fchData.P2SH;
import com.fc.fc_ajdk.utils.BytesUtils;
import com.fc.fc_ajdk.utils.FchUtils;
import com.fc.fc_ajdk.utils.Hex;
import com.fc.fc_ajdk.utils.TimberLogger;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VarInt;
import org.bitcoinj.crypto.SchnorrSignature;
import org.bitcoinj.fch.FchMainNetwork;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;

/**
 * Transaction handler for creating, signing and broadcasting general transactions.
 * Supports P2PKH, P2SH, CLTV (time-locked) and multisig transactions.
 */
public class TxHandler {
    private static final String TAG = "TxHandler";
    public static final double DEFAULT_FEE_RATE = 0.00001;
    private final MainNetParams mainNetwork;

    static {
        fixKeyLength();
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
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
    

    public TxHandler() {
        mainNetwork = FchMainNetwork.MAINNETWORK;
    }

    public TxHandler(MainNetParams mainNetwork) {
        this.mainNetwork = mainNetwork;
    }


    public static boolean isLockTimeUnlocked(long lockTime, long bestHeight) {
        long currentTimestamp = System.currentTimeMillis() / 1000;
        return isLockTimeUnlocked(lockTime, bestHeight, currentTimestamp);
    }

    /**
     * 判断一个 Bitcoin lockTime 是否已解锁
     *
     * @param lockTime         要检查的锁定值
     * @param bestHeight    当前区块高度
     * @param currentTimestamp 当前区块时间戳（秒）
     * @return true 表示已解锁，可以被打包；false 表示仍被锁定
     */
    private static boolean isLockTimeUnlocked(long lockTime, long bestHeight, long currentTimestamp) {
        final long LOCKTIME_THRESHOLD = 500_000_000L;

        if (lockTime == 0) {
            // 0 表示没有时间锁
            return true;
        }

        if (lockTime < LOCKTIME_THRESHOLD) {
            // 区块高度锁
            return bestHeight >= lockTime;
        } else {
            // 时间戳锁（单位为秒）
            return currentTimestamp >= lockTime;
        }
    }

    public static long getChangeFee(String changeToFid) {
        long changeFee;
        if(changeToFid.startsWith("3"))
            changeFee = CHANGE_P2SH_OUTPUT_FEE;
        else changeFee = CHANGE_OUTPUT_FEE;
        return changeFee;
    }

//Create TX

    public String createTxHex(RawTxInfo rawTxInfo) {
        byte[] txBytes = createTx(rawTxInfo);
        if (txBytes == null) return null;
        return Hex.toHex(txBytes);
    }

    public byte[] createTx(RawTxInfo rawTxInfo) {
        Transaction tx = createTx(rawTxInfo, this.mainNetwork);
        if(tx==null) return null;
        return tx.bitcoinSerialize();
    }

    public Transaction createTx(RawTxInfo rawTxInfo, MainNetParams mainNetwork) {
        if (rawTxInfo.getInputs() == null || rawTxInfo.getInputs().isEmpty()) {
            TimberLogger.e("The sender is absent.");
            return null;
        }

        // Track CLTV outputs to append to opReturn

        if(rawTxInfo.getChangeTo()==null) {
            if(rawTxInfo.getSender()!=null)rawTxInfo.setChangeTo(rawTxInfo.getSender());
            else rawTxInfo.setChangeTo(rawTxInfo.getInputs().get(0).getOwner());
            if(rawTxInfo.getChangeTo()==null) return null;
        }
//        boolean isMultiSign = false;
        if(rawTxInfo.getSenderMultisig()!=null){
//            isMultiSign=true;
            String multisignFid = rawTxInfo.getSenderMultisig().getId();
            if(rawTxInfo.getSender()==null)rawTxInfo.setSender(multisignFid);
            else if(!rawTxInfo.getSender().equals(multisignFid))return null;
        }

        if(rawTxInfo.getFeeRate()==null || rawTxInfo.getFeeRate()==0)
            rawTxInfo.setFeeRate(DEFAULT_FEE_RATE);
//        long fee;

        FeeResult feeResult = calcFee(rawTxInfo);
        if(feeResult.fee==null)return null;

        Transaction transaction = new Transaction(mainNetwork);

        // Check if any inputs have lockTime requirements (spending CLTV UTXOs)
        // When spending CLTV outputs, transaction lockTime MUST be set to >= the CLTV value
        // Note: This is different from creating CLTV outputs, where we don't set transaction lockTime
        long maxInputLockTime = 0;
        for (Cash input : rawTxInfo.getInputs()) {
            if (input.getLockTime() != null && input.getLockTime() > maxInputLockTime) {
                maxInputLockTime = input.getLockTime();
            }
        }
        if (maxInputLockTime > 0) {
            transaction.setLockTime(maxInputLockTime);
            rawTxInfo.setLockTime(maxInputLockTime);
            TimberLogger.d(TAG,"Set transaction lockTime to " + maxInputLockTime + " for spending CLTV UTXO");
        }

        long totalOutput = 0;

        long totalMoney = addInputToTx(rawTxInfo.getInputs(), transaction);

        if(rawTxInfo.getOutputs() !=null && !rawTxInfo.getOutputs().isEmpty()){
//            int p2shIndex = 0; // Index to track which P2SH info to use
            for (Cash output : rawTxInfo.getOutputs()) {
                long value = FchUtils.coinToSatoshi(output.getAmount());
                totalOutput += value;

                Script redeemScript;

            if(output.getRedeemScript()!=null){
                redeemScript = new Script( Hex.fromHex(output.getRedeemScript()));
                // Create P2SH output from the redeemScript
                Script p2shScript = ScriptBuilder.createP2SHOutputScript(redeemScript);
                transaction.addOutput(Coin.valueOf(value), p2shScript);

            } else {
                // Regular P2PKH output (no time lock, no multisig)
                transaction.addOutput(Coin.valueOf(value), Address.fromBase58(mainNetwork, output.getOwner()));
            }

            }
        }
        // CRITICAL FIX: Simplified change output logic
        // The fee calculation (calcFee) now already determines if change output will exist
        // Just check if there's enough input and create change if applicable
        if ((totalOutput + feeResult.fee()) > totalMoney) {
            TimberLogger.e(TAG, "Input is not enough: input=" + totalMoney +
                ", output=" + totalOutput + ", fee=" + feeResult.fee());
            return null;
        }

        long change = totalMoney - totalOutput - feeResult.fee();
        if (change > Constants.DustInSatoshi) {
            // Change output is ALWAYS regular P2PKH (not time-locked), sent back to sender immediately
            // This ensures the sender can spend the change immediately, even in CLTV transactions
            transaction.addOutput(Coin.valueOf(change), Address.fromBase58(mainNetwork, rawTxInfo.getChangeTo()));
            TimberLogger.d(TAG, "Added change output: " + change + " satoshis to " + rawTxInfo.getChangeTo());
        } else {
            TimberLogger.d(TAG, "No change output: change=" + change + " satoshis (below dust threshold)");
        }

        // Add opReturn to transaction (finalOpReturnBytes was already calculated earlier)
        if (feeResult.finalOpReturnBytes() != null && feeResult.finalOpReturnBytes().length > 0) {
            try {
                Script opreturnScript = ScriptBuilder.createOpReturnScript(feeResult.finalOpReturnBytes());
                transaction.addOutput(Coin.ZERO, opreturnScript);
            } catch (Exception e) {
                TimberLogger.e("Failed to create opreturn script: "+e.getMessage());
                return null;
            }
        }

        return transaction;
    }

    @NonNull
    public static FeeResult calcFee(RawTxInfo rawTxInfo) {
        // Get fee rate, use default if not set
        double feeRate = (rawTxInfo.getFeeRate() != null && rawTxInfo.getFeeRate() > 0)
            ? rawTxInfo.getFeeRate() : DEFAULT_FEE_RATE;
        long feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);

        // Process outputs to determine P2SH outputs and build opReturn
        byte[] finalOpReturnBytes = (rawTxInfo.getOpReturn() != null && !rawTxInfo.getOpReturn().isEmpty())
            ? rawTxInfo.getOpReturn().getBytes() : new byte[0];

        List<P2SH> p2SHOutputs = new ArrayList<>();
        long totalOutputSize = 0;
        long totalOutputValue = 0;

        if (rawTxInfo.getOutputs() != null && !rawTxInfo.getOutputs().isEmpty()) {
            for (Cash output : rawTxInfo.getOutputs()) {
                totalOutputValue += output.getValue();
                if (output.getRedeemScript()!=null ) {
                    // P2SH output: 8 value + 1 scriptLen + 23 P2SH script = 32 bytes
                    totalOutputSize += 32;
                    P2SH p2SHInfo = new P2SH(output.getRedeemScript());//P2SH.p2shFromSendTo(output);
                    p2SHOutputs.add(p2SHInfo);
                } else if(output.getOwner().startsWith("3")){
                    totalOutputSize += 32;
                }
                else {
                    // Regular P2PKH output: 8 value + 1 scriptLen + 25 P2PKH script = 34 bytes
                    totalOutputSize += 34;
                }
            }
        }

        // Build final opReturn bytes if there are P2SH outputs
        if (!p2SHOutputs.isEmpty()) {
            String redeemScriptListJson = P2SH.makeRedeemScriptListJsonForOpReturn(p2SHOutputs);
            finalOpReturnBytes = redeemScriptListJson.getBytes(StandardCharsets.UTF_8);
        }

        // Calculate opReturn size
        int opReturnLen = (finalOpReturnBytes.length > 0)
            ? calcOpReturnLen(finalOpReturnBytes.length) : 0;

        // Calculate input sizes - parse each input's redeemScript to determine its type
        long totalInputSize = 0;
        long totalInputValue = 0;

        for (Cash input : rawTxInfo.getInputs()) {
            totalInputValue += input.getValue();
            if(rawTxInfo.getSenderMultisig()!=null || input.getLockTime()!=null){
                if( input.getRedeemScript()==null || input.getRedeemScript().isEmpty()){
                    // Extract m and n from redeemScript
                    // Note: Caller must ensure senderMultisig is set in RawTxInfo before calling calcFee
                    Multisig multisig = rawTxInfo.getSenderMultisig();
                    if (multisig != null) {
                        int n = multisig.getN();
                        int m = multisig.getM();

                        // CRITICAL FIX: Calculate correct redeemScript length based on whether input has CLTV
                        int redeemScriptLen = 0;
                        if (input.getLockTime() != null && input.getLockTime() > 0) {
                            // CLTV+multisig: Build the actual redeemScript to get accurate size
                            Script cltvMultisigScript = P2SH.makeMultisigLockTimeRedeemScript(
                                input.getLockTime(),
                                multisig.getPubKeys(),
                                multisig.getM(),
                                multisig.getN()
                            );
                            redeemScriptLen = cltvMultisigScript.getProgram().length;
                        } else {
                            // Plain multisig: Use stored redeemScript length or calculate
                            if (multisig.getRedeemScript() != null) {
                                redeemScriptLen = Hex.fromHex(multisig.getRedeemScript()).length;
                            }
                            // else: multisigInputSize will calculate the length when redeemScriptLen=0
                        }

                        totalInputSize += multisigInputSize(n, m, redeemScriptLen);
                    }else
                        return new FeeResult(null,null,null);
                } else {
                    // P2SH input - parse redeemScript to determine type
                    String redeemScriptHex = input.getRedeemScript();
                    P2SH p2SH = new P2SH(redeemScriptHex);
                    if(p2SH.getId()==null)
                        return new FeeResult(null,null,null);

                    // Use improved P2SH input size calculation
                    totalInputSize += calculateP2SHInputSize(p2SH);
                }
            } else {
                // Regular P2PKH input: ~141 bytes
                totalInputSize += 141;
            }
        }

        // Calculate total transaction size
        long baseLength = 10; // Version(4) + input count(1) + output count(1) + locktime(4)

        // CRITICAL FIX: Determine change output size based on changeTo address type
        // P2PKH (1xxx/Fxxx): 8 (value) + 1 (scriptLen) + 25 (P2PKH script) = 34 bytes
        // P2SH (3xxx): 8 (value) + 1 (scriptLen) + 23 (P2SH script) = 32 bytes
        // If changeTo is not set, use sender address to determine change output type
        String changeAddress = rawTxInfo.getChangeTo();
        if (changeAddress == null || changeAddress.isEmpty()) {
            changeAddress = rawTxInfo.getSender();
        }
//        long changeOutputSize = Constants.CHANGE_OUTPUT_FEE; // Default to P2PKH
//        if (changeAddress != null && changeAddress.startsWith("3")) {
//            changeOutputSize = Constants.CHANGE_P2SH_OUTPUT_FEE; // P2SH is 2 bytes smaller
//        }
        long changeOutputSize = getChangeFee(changeAddress);
        // CRITICAL FIX: Calculate fee correctly by checking if change output will actually be created
        // First, calculate size and fee WITHOUT change output
        long txSizeWithoutChange = baseLength + totalInputSize + totalOutputSize + opReturnLen;
        long feeWithoutChange = feeRateLong * txSizeWithoutChange;

        // Calculate what the change amount would be
        long potentialChange = totalInputValue - totalOutputValue - feeWithoutChange;

        // Determine if change output will be created
        boolean willHaveChange = false;
        long finalFee;

        if (potentialChange > Constants.DustInSatoshi) {
            // Change output will be created, so we need to account for its size
            long changeOutputFeeSize = feeRateLong * changeOutputSize;

            // Recalculate with change output included
            long txSizeWithChange = txSizeWithoutChange + changeOutputSize;
            long feeWithChange = feeRateLong * txSizeWithChange;

            // Check if there's still enough for change after accounting for its own fee
            long actualChange = totalInputValue - totalOutputValue - feeWithChange;

            if (actualChange > Constants.DustInSatoshi) {
                // Yes, change output will be created
                willHaveChange = true;
                finalFee = feeWithChange;
            } else {
                // No, the change would be too small even accounting for its fee
                willHaveChange = false;
                finalFee = feeWithoutChange;
            }
        } else {
            // Change is already below dust threshold, no change output
            willHaveChange = false;
            finalFee = feeWithoutChange;
        }

        TimberLogger.d(TAG, "Fee calculation: totalInput=" + totalInputValue +
            ", totalOutput=" + totalOutputValue +
            ", feeWithoutChange=" + feeWithoutChange +
            ", potentialChange=" + potentialChange +
            ", willHaveChange=" + willHaveChange +
            ", finalFee=" + finalFee);

        return new FeeResult(finalFee, finalOpReturnBytes, p2SHOutputs);
    }

    /**
     * Calculate the input size for a multisig P2SH transaction
     * ScriptSig format: OP_0 <sig1> <sig2> ... <sigM> <redeemScript>
     *
     * @param n Total number of public keys in the multisig
     * @param m Required number of signatures
     * @param redeemScriptLen Length of the redeemScript in bytes (0 to auto-calculate for plain multisig)
     * @return Input size in bytes
     */
    private static long multisigInputSize(int n, int m, long redeemScriptLen) {
        // If redeemScriptLen not provided, calculate it for a standard multisig (no CLTV)
        // Format: <m> <pubkey1> ... <pubkeyN> <n> OP_CHECKMULTISIG
        if(redeemScriptLen <= 0) {
            long op_mLen = 1;
            long op_nLen = 1;
            long pubkeyLen = 33; // compressed pubkey
            long pubkeyLenLen = 1; // length byte for each pubkey
            long op_checkmultisigLen = 1;
            redeemScriptLen = op_mLen + (n * (pubkeyLenLen + pubkeyLen)) + op_nLen + op_checkmultisigLen;
        }

        // Calculate the push operation size for the redeemScript
        // Bitcoin script push operations:
        // - For data < 76 bytes: 1 byte (implicit OP_PUSHDATA)
        // - For 76-255 bytes: 2 bytes (OP_PUSHDATA1 + 1 byte length)
        // - For 256-65535 bytes: 3 bytes (OP_PUSHDATA2 + 2 byte length)
        long redeemScriptPushLen;
        if (redeemScriptLen < 76) {
            redeemScriptPushLen = 1; // length byte only
        } else if (redeemScriptLen < 256) {
            redeemScriptPushLen = 2; // OP_PUSHDATA1 + 1 length byte
        } else {
            redeemScriptPushLen = 3; // OP_PUSHDATA2 + 2 length bytes
        }

        // ScriptSig components:
        // 1. OP_0 (1 byte) - required for multisig due to off-by-one bug
        long zeroByteLen = 1;

        // 2. m signatures, each with: length byte + 64 bytes signature + 1 byte sighash
        long sigHashLen = 1;
        long signLen = 64; // Schnorr signature
        long signLenLen = 1; // length byte for each signature
        long mSignLen = m * (signLenLen + signLen + sigHashLen);

        // 3. RedeemScript: push operation + redeemScript bytes
        long redeemScriptTotalLen = redeemScriptPushLen + redeemScriptLen;

        // Total scriptSig length
        long scriptLength = zeroByteLen + mSignLen + redeemScriptTotalLen;

        // scriptSig length is encoded as VarInt
        long scriptVarInt = VarInt.sizeOf(scriptLength);

        // Total input size: txid(32) + vout(4) + scriptSig length VarInt + scriptSig + sequence(4)
        return 32 + 4 + scriptVarInt + scriptLength + 4;
    }

    public record FeeResult(Long fee, byte[] finalOpReturnBytes, List<P2SH> p2SHOutputs) {
    }

    private long addInputToTx(List<Cash> valueTxIdIndexCashList, Transaction transaction) {
        long totalMoney=0;
        for (Cash input : valueTxIdIndexCashList) {
            totalMoney += input.getValue();
            TransactionOutPoint outPoint = new TransactionOutPoint(this.mainNetwork, input.getBirthIndex(), Sha256Hash.wrap(input.getBirthTxId()));
            TransactionInput unsignedInput = new TransactionInput(this.mainNetwork, null, new byte[0], outPoint, Coin.valueOf(input.getValue()));

            // CRITICAL: For CLTV inputs, sequence MUST be < 0xFFFFFFFF to enable lockTime validation
            // If sequence = 0xFFFFFFFF (default), OP_CHECKLOCKTIMEVERIFY will fail and the transaction will be rejected
            if (input.getLockTime() != null && input.getLockTime() > 0) {
                unsignedInput.setSequenceNumber(0xFFFFFFFEL); // Enable lockTime checking
                TimberLogger.d(TAG,"Set sequence to 0xFFFFFFFE for CLTV input: " + input.getBirthTxId() + ":" + input.getBirthIndex());
            }
            // else: default sequence 0xFFFFFFFF is fine for regular inputs
            transaction.addInput(unsignedInput);
        }

        return totalMoney;
    }

//Parse TX
    public RawTxInfo parseTx(Transaction tx){
        return RawTxInfo.fromTransaction(tx);
    }

    public RawTxInfo parseTx(String rawTx){
        Transaction tx = new Transaction(this.mainNetwork,Hex.fromHex(rawTx));
        return RawTxInfo.fromTransaction(tx);
    }

//Calculate Fee

    /**
     * Helper method to calculate fee from transaction size
     * @param txSize Transaction size in bytes
     * @param feeRate Fee rate in FCH/KB
     * @return Fee in satoshis
     */
    public static long calcFee(long txSize, double feeRate) {
        long feeRateLong;
        if (feeRate != 0) {
            feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);
        } else feeRateLong = (long) (DEFAULT_FEE_RATE / 1000 * COIN_TO_SATOSHI);
        return feeRateLong * txSize;
    }

    /**
     * Calculate fee for a transaction with the given parameters.
     * This is a convenience method for simple fee calculation.
     *
     * @param inputSize Number of inputs
     * @param outputSize Number of outputs
     * @param opReturnBytesLen Length of OP_RETURN data in bytes
     * @param feeRate Fee rate in FCH/KB
     * @param isMultiSig Whether this is a multisig transaction
     * @param multisig Multisig configuration (required if isMultiSig is true)
     * @return Fee in satoshis
     */
    public static long calcFee(int inputSize, int outputSize, int opReturnBytesLen, double feeRate, boolean isMultiSig, Multisig multisig) {
        long fee;
        if(isMultiSig && multisig != null) {
            long feeRateLong = (long) (feeRate / 1000 * COIN_TO_SATOSHI);
            fee = feeRateLong * calcSizeMultiSign(inputSize, outputSize, opReturnBytesLen, multisig.getM(), multisig.getN());
        } else {
            long txSize = calcTxSize(inputSize, outputSize, opReturnBytesLen);
            fee = calcFee(txSize, feeRate);
        }
        return fee;
    }

    /**
     * Calculate standard P2PKH transaction size
     */
    public static long calcTxSize(int inputNum, int outputNum, int opReturnBytesLen) {
        long baseLength = 10;
        long inputLength = 141 * (long) inputNum;
        long outputLength = 34 * (long) (outputNum + 1); // Include change output

        int opReturnLen = 0;
        if (opReturnBytesLen != 0)
            opReturnLen = calcOpReturnLen(opReturnBytesLen);

        return baseLength + inputLength + outputLength + opReturnLen;
    }

    /**
     * Calculate multisig transaction size
     */
    public static long calcSizeMultiSign(int inputNum, int outputNum, int opReturnBytesLen, int m, int n) {
        long op_mLen = 1;
        long op_nLen = 1;
        long pubkeyLen = 33;
        long pubkeyLenLen = 1;
        long op_checkmultisigLen = 1;

        long redeemScriptLength = op_mLen + (n * (pubkeyLenLen + pubkeyLen)) + op_nLen + op_checkmultisigLen;
        long redeemScriptVarInt = VarInt.sizeOf(redeemScriptLength);

        long op_pushDataLen = 1;
        long sigHashLen = 1;
        long signLen = 64;
        long signLenLen = 1;
        long zeroByteLen = 1;

        long mSignLen = m * (signLenLen + signLen + sigHashLen);

        long scriptLength = zeroByteLen + mSignLen + op_pushDataLen + redeemScriptVarInt + redeemScriptLength;
        long scriptVarInt = VarInt.sizeOf(scriptLength);

        long preTxIdLen = 32;
        long preIndexLen = 4;
        long sequenceLen = 4;

        long inputLength = preTxIdLen + preIndexLen + sequenceLen + scriptVarInt + scriptLength;

        long opReturnLen = 0;
        if (opReturnBytesLen != 0)
            opReturnLen = calcOpReturnLen(opReturnBytesLen);

        long outputValueLen = 8;
        long unlockScriptLen = 25;
        long unlockScriptLenLen = 1;
        long outPutLen = outputValueLen + unlockScriptLenLen + unlockScriptLen;

        long inputCountLen = 1;
        long outputCountLen = 1;
        long txVerLen = 4;
        long nLockTimeLen = 4;
        long txFixedLen = inputCountLen + outputCountLen + txVerLen + nLockTimeLen;

        return txFixedLen + inputLength * inputNum + outPutLen * (outputNum + 1) + opReturnLen;
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

    //Sign TX

    public String signTx(RawTxInfo rawTxInfo, byte[] prikey){
        Transaction transaction = createTx(rawTxInfo, FchMainNetwork.MAINNETWORK);
        if(transaction==null)return null;
        return signTx(prikey, transaction, rawTxInfo.getInputs());
    }

    /**
     * Sign a transaction with support for both regular P2PKH and P2SH inputs (including CLTV)
     * @param prikey Private key for signing
     * @param transaction Transaction to sign
     * @param inputs List of Cash inputs with redeemScript information
     * @return Signed transaction in hex format
     */
    public String signTx(byte[] prikey, Transaction transaction, List<Cash> inputs) {
        if(prikey==null){
            return null;
        }

        ECKey eckey = ECKey.fromPrivate(prikey);

        List<TransactionInput> txInputs = transaction.getInputs();
        for (int i = 0; i < txInputs.size(); ++i) {
            TransactionInput input = txInputs.get(i);
            Coin value = input.getValue();
            if(value==null)continue;

            // Check if this input has a redeemScript (P2SH: multisig, CLTV, etc.)
            Cash cashInput = (inputs != null && i < inputs.size()) ? inputs.get(i) : null;

            // DEBUG: Log input details
            if (cashInput != null) {
                TimberLogger.d(TAG,"Input " + i + ": txId=" + cashInput.getBirthTxId() +
                    ":" + cashInput.getBirthIndex() +
                    ", type=" + cashInput.getType() +
                    ", lockTime=" + cashInput.getLockTime() +
                    ", redeemScript=" + (cashInput.getRedeemScript() != null ?
                        cashInput.getRedeemScript().substring(0, Math.min(40, cashInput.getRedeemScript().length())) + "..." : "null"));
            }

            if (cashInput != null && cashInput.getRedeemScript() != null && !cashInput.getRedeemScript().isEmpty()) {
                // P2SH input - use redeemScript for signing
                // Note: redeemScript is always stored in hex format (Cash.setRedeemScript handles conversion)
                String redeemScriptStr = cashInput.getRedeemScript();
                byte[] redeemScriptBytes;
                try {
                    if(!Hex.isHexString(redeemScriptStr)){
                        redeemScriptStr = P2SH.scriptAsmToHex(redeemScriptStr);
                        TimberLogger.d(TAG,"Input " + i + ": Converted P2SH redeemScript to" + redeemScriptStr );
                    }
                    if(redeemScriptStr==null)return null;
                    redeemScriptBytes = Hex.fromHex(redeemScriptStr);
                    TimberLogger.d(TAG,"Input " + i + ": Using P2SH redeemScript (length: " + redeemScriptBytes.length + " bytes)");
                } catch (Exception e) {
                    TimberLogger.e("Failed to parse redeemScript hex for input " + i + ": " + e.getMessage());
                    return null;
                }

                Script redeemScript = new Script(redeemScriptBytes);

                // Calculate signature using the redeemScript
                SchnorrSignature signature = transaction.calculateSchnorrSignature(
                    i, eckey, redeemScript.getProgram(), value, Transaction.SigHash.ALL, false);

                // For P2SH (including CLTV): scriptSig = <signature> <pubkey> <redeemScript>
                ScriptBuilder builder = new ScriptBuilder();
                builder.data(signature.encodeToBitcoin());
                builder.data(eckey.getPubKey());
                builder.data(redeemScriptBytes);

                transaction.getInput(i).setScriptSig(builder.build());
                TimberLogger.d(TAG,"Signed P2SH input " + i + " with redeemScript (type=" + cashInput.getType() + ")");
            } else {
                // Regular P2PKH input
                Script script = ScriptBuilder.createP2PKHOutputScript(eckey);
                SchnorrSignature signature = transaction.calculateSchnorrSignature(
                    i, eckey, script.getProgram(), value, Transaction.SigHash.ALL, false);
                Script schnorr = ScriptBuilder.createSchnorrInputScript(signature, eckey);
                transaction.getInput(i).setScriptSig(schnorr);
                TimberLogger.d(TAG,"Signed regular P2PKH input " + i + (cashInput != null ? " (type=" + cashInput.getType() + ")" : ""));
            }
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    /**
     * Legacy method - signs transaction without P2SH support
     * Use signTx(byte[], Transaction, List<Cash>) for P2SH/CLTV support
     */
    public String signTx(byte[] prikey, Transaction transaction) {
        return signTx(prikey, transaction, null);
    }

//Decode TX
    public String decodeTx(String rawTx) {
        return decodeTx(Hex.fromHex(rawTx), FchMainNetwork.MAINNETWORK);
    }
    public String decodeTx(String rawTx, MainNetParams mainNetParams) {
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

    public String decodeTx(byte[] rawTxBytes, MainNetParams mainNetwork) {
        if(rawTxBytes==null) return null;

        Transaction transaction;
        // Handle parsing of combined format with input values
        List<Long> inputValueList = new ArrayList<>();
        byte[] rawTx;
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(rawTxBytes)) {
            int flag = byteArrayInputStream.read();
            transaction = new Transaction(mainNetwork, rawTxBytes);
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
                    Address address = output.getScriptPubKey().getToAddress(mainNetwork);
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

    private String getScriptType(Script script) {
        if (script.isSentToAddress() || script.isSentToRawPubKey())
            return "pubkeyhash";
        else if (script.isPayToScriptHash())
            return "scripthash";
        else if (script.isOpReturn())
            return "nulldata";
        else
            return "nonstandard";
    }

//Multisig

    public Multisig createMultisign(List<byte[]> pubkeyList, int m) {
        List<ECKey> keys = new ArrayList<>();
        for (byte[] bytes : pubkeyList) {
            ECKey ecKey = ECKey.fromPublicOnly(bytes);
            keys.add(ecKey);
        }

        Script multiSigScript = ScriptBuilder.createMultiSigOutputScript(m, keys);

        byte[] redeemScriptBytes = multiSigScript.getProgram();

        Multisig multisig;
        try {
            multisig = Multisig.parseMultisignRedeemScript(Hex.toHex(redeemScriptBytes));
        } catch (Exception e) {
            TimberLogger.d(TAG,e.getMessage());
            return null;
        }
        return multisig;
    }

    /**
     * Sign multisig transaction with support for CLTV inputs
     *
     * @param rawMultisignTx Raw transaction info with multisig configuration
     * @param prikey         Private key for signing
     */
    public void signSchnorrMultiSignTx(RawTxInfo rawMultisignTx, byte[] prikey) {
        byte[] rawTx = createTx(rawMultisignTx);
        List<Cash> cashList = rawMultisignTx.getInputs();
        Multisig multisig = rawMultisignTx.getSenderMultisig();

        Transaction transaction = new Transaction(this.mainNetwork, rawTx);
        List<TransactionInput> inputs = transaction.getInputs();

        // CRITICAL: Handle CLTV inputs - set sequence and lockTime for time-locked multisig UTXOs
        // When spending CLTV outputs, transaction lockTime MUST be >= the CLTV value
        // AND input sequence MUST be < 0xFFFFFFFF to enable lockTime validation
        long maxInputLockTime = 0;
        for (int i = 0; i < inputs.size(); i++) {
            Cash cashInput = cashList.get(i);
            if (cashInput.getLockTime() != null && cashInput.getLockTime() > 0) {
                inputs.get(i).setSequenceNumber(0xFFFFFFFEL); // Enable lockTime checking
                if (cashInput.getLockTime() > maxInputLockTime) {
                    maxInputLockTime = cashInput.getLockTime();
                }
                TimberLogger.d(TAG,"Set sequence to 0xFFFFFFFE for CLTV multisig input: "
                    + cashInput.getBirthTxId() + ":" + cashInput.getBirthIndex());
            }
        }

        // Set transaction lockTime if any input requires it
        if (maxInputLockTime > 0) {
            transaction.setLockTime(maxInputLockTime);
            TimberLogger.d(TAG,"Set transaction lockTime to " + maxInputLockTime + " for CLTV multisig");
        }

        ECKey ecKey = ECKey.fromPrivate(prikey);
        BigInteger prikeyBigInteger = ecKey.getPrivKey();
        List<String> sigList = new ArrayList<>();

        // CRITICAL FIX: Sign each input with the correct redeemScript
        // Use input's stored redeemScript if available, otherwise determine from lockTime
        for (int i = 0; i < inputs.size(); ++i) {
            byte[] redeemScript;
            Cash cashInput = cashList.get(i);

            // Determine correct redeemScript for this specific input
            if (cashInput.getRedeemScript() != null && !cashInput.getRedeemScript().isEmpty()) {
                // Use the input's stored redeemScript directly (already correct format)
                redeemScript = Hex.fromHex(cashInput.getRedeemScript());
                TimberLogger.d(TAG, "Signing input " + i + " with stored redeemScript:"+cashInput.getRedeemScript());
            } else if (cashInput.getLockTime() != null && cashInput.getLockTime() > 0) {
                // Build full CLTV+multisig redeemScript for this input
                Script cltvMultisigScript = P2SH.makeMultisigLockTimeRedeemScript(
                    cashInput.getLockTime(),
                    multisig.getPubKeys(),
                    multisig.getM(),
                    multisig.getN()
                );
                redeemScript = cltvMultisigScript.getProgram();
                TimberLogger.d(TAG, "Signing input " + i + " with CLTV+multisig redeemScript, lockTime: " + cashInput.getLockTime()+". RedeemScript: "+ Hex.toHex(redeemScript));
            } else {
                // Plain multisig (no CLTV)
                redeemScript = Hex.fromHex(multisig.getRedeemScript());
                TimberLogger.d(TAG, "Signing input " + i + " with plain multisig redeemScript:"+multisig.getRedeemScript());
            }
            if(redeemScript==null) {
                TimberLogger.d(TAG, "Failed to sign multisig input. RedeemScript is null.");
                return;
            }
            Script script = new Script(redeemScript);
            Sha256Hash hash = transaction.hashForSignatureWitness(i, script, Coin.valueOf(cashInput.getValue()), Transaction.SigHash.ALL, false);
            byte[] sig = SchnorrSignature.schnorr_sign(hash.getBytes(), prikeyBigInteger);
            sigList.add(Hex.toHex(sig));
        }

        String fid = prikeyToFid(prikey);
        if (rawMultisignTx.getFidSigMap() == null) {
            Map<String, List<String>> fidSigListMap = new HashMap<>();
            rawMultisignTx.setFidSigMap(fidSigListMap);
        }
        rawMultisignTx.getFidSigMap().put(fid, sigList);
    }

    /**
     * Build complete multisig transaction from signatures with support for CLTV
     * IMPORTANT: For CLTV multisig, transaction lockTime and input sequences must be properly set
     * @param rawTxInfo Raw transaction info with signatures in fidSigMap
     * @return Complete signed transaction in hex format
     */
    public String buildSchnorrMultiSignTx(RawTxInfo rawTxInfo) {

        Map<String, List<String>> sigListMap = rawTxInfo.getFidSigMap();
        Multisig multisig = rawTxInfo.getSenderMultisig();
        byte[] rawTx = createTx(rawTxInfo);
        if(rawTx==null){
            String rawTxHex = createTxHex(rawTxInfo);
            if(rawTxHex!=null) rawTx = Hex.fromHex(rawTxHex);
        }
        if (sigListMap.size() > multisig.getM())
            sigListMap = dropRedundantSigs(sigListMap, multisig.getM());

        Transaction transaction = new Transaction(this.mainNetwork, rawTx);

        // CRITICAL: For CLTV multisig inputs, ensure proper lockTime and sequence settings
        // These should already be set from createTx or signSchnorrMultiSignTx, but verify
        List<Cash> cashList = rawTxInfo.getInputs();
        if (cashList != null) {
            long maxInputLockTime = 0;
            for (int i = 0; i < transaction.getInputs().size() && i < cashList.size(); i++) {
                Cash cashInput = cashList.get(i);
                if (cashInput.getLockTime() != null && cashInput.getLockTime() > 0) {
                    // Ensure sequence is set for CLTV validation
                    transaction.getInput(i).setSequenceNumber(0xFFFFFFFEL);
                    if (cashInput.getLockTime() > maxInputLockTime) {
                        maxInputLockTime = cashInput.getLockTime();
                    }
                }
            }
            // Set transaction lockTime if needed
            if (maxInputLockTime > 0) {
                transaction.setLockTime(maxInputLockTime);
                TimberLogger.d(TAG,"Set transaction lockTime to " + maxInputLockTime + " for CLTV multisig build");
            }
        }

        // Build scriptSig for each input with m signatures
        for (int i = 0; i < transaction.getInputs().size(); i++) {
            List<byte[]> sigListByTx = new ArrayList<>();
            for (String fid : multisig.getFids()) {
                try {
                    String sig = sigListMap.get(fid).get(i);
                    sigListByTx.add(Hex.fromHex(sig));
                } catch (Exception ignore) {
                }
            }

            // CRITICAL FIX: Determine correct redeemScript based on input's actual redeemScript
            // The input's redeemScript field (if set) should be used, otherwise fall back to checking lockTime
            byte[] redeemScriptForBuild;
            Cash cashInput = (cashList != null && i < cashList.size()) ? cashList.get(i) : null;

            if (cashInput != null && cashInput.getRedeemScript() != null && !cashInput.getRedeemScript().isEmpty()) {
                // Use the input's stored redeemScript directly (already correct format)
                redeemScriptForBuild = Hex.fromHex(cashInput.getRedeemScript());
                TimberLogger.d(TAG, "Building scriptSig with stored redeemScript for input " + i);
            } else if (cashInput != null && cashInput.getLockTime() != null && cashInput.getLockTime() > 0) {
                // Build CLTV+multisig redeemScript (for newly created CLTV+multisig outputs)
                Script cltvMultisigScript = P2SH.makeMultisigLockTimeRedeemScript(
                    cashInput.getLockTime(),
                    multisig.getPubKeys(),
                    multisig.getM(),
                    multisig.getN()
                );
                redeemScriptForBuild = cltvMultisigScript.getProgram();
                TimberLogger.d(TAG, "Building scriptSig with CLTV+multisig redeemScript for input " + i);
            } else {
                // Plain multisig (no CLTV, no stored redeemScript)
                redeemScriptForBuild = Hex.fromHex(multisig.getRedeemScript());
                TimberLogger.d(TAG, "Building scriptSig with plain multisig redeemScript for input " + i);
            }

            // Create scriptSig: OP_0 <sig1> <sig2> ... <sigM> <redeemScript>
            Script inputScript = createSchnorrMultiSigInputScriptBytes(sigListByTx, redeemScriptForBuild);
            TransactionInput input = transaction.getInput(i);
            input.setScriptSig(inputScript);
        }

        byte[] signResult = transaction.bitcoinSerialize();
        return Hex.toHex(signResult);
    }

    public String buildSignedMultisignTx(String[] signedData) {
        ReplyBody replyBody = mergeMultisignTxData(signedData);
        if (replyBody == null) return null;
        if(replyBody.getCode()!=0){
            System.out.println(replyBody.getMessage());
            return null;
        }
        RawTxInfo finalRawTxInfo = (RawTxInfo) replyBody.getData();
        if (finalRawTxInfo == null) return null;
        return buildSchnorrMultiSignTx(finalRawTxInfo);
    }

    @Nullable
    public ReplyBody mergeMultisignTxData(String[] signedDatas) {
        Map<String, List<String>> fidSigListMap = new HashMap<>();
        RawTxInfo finalRawTxInfo = null;
        byte[] rawTx = null;
        Multisig multisig = null;
        ReplyBody replyBody;
        for (String dataJson : signedDatas) {
            try {

                RawTxInfo multiSignData = RawTxInfo.fromJson(dataJson, RawTxInfo.class);

                if (multisig == null
                        && multiSignData.getSenderMultisig() != null) {
                    multisig = multiSignData.getSenderMultisig();
                }

                if (rawTx == null) {
                    rawTx = createTx(multiSignData);
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
        if (rawTx == null || rawTx.length==0 || multisig == null) return null;

        finalRawTxInfo.setSenderMultisig(multisig);
        finalRawTxInfo.setFidSigMap(fidSigListMap);

        replyBody = new ReplyBody();
        replyBody.set0Success();
        replyBody.setData(finalRawTxInfo);
        return replyBody;
    }

    private ReplyBody verifySig(String fid, RawTxInfo multiSignData) {

        ReplyBody replyBody = new ReplyBody();
        try {
            if (!multiSignData.getSenderMultisig().getFids().contains(fid)){
                replyBody.set1020Other("The FID is not a member of "+multiSignData.getSenderMultisig().getId());
                return replyBody;
            }
            int putKeyIndex = multiSignData.getSenderMultisig().getFids().indexOf(fid);
            String pubkey = multiSignData.getSenderMultisig().getPubKeys().get(putKeyIndex);
            String redeemScript = multiSignData.getSenderMultisig().getRedeemScript();
            for(int i = 0; i<multiSignData.getInputs().size(); i++){
                if(!rawTxSigVerify(createTx(multiSignData), Hex.fromHex(pubkey), Hex.fromHex(multiSignData.getFidSigMap().get(fid).get(i)), i, multiSignData.getInputs().get(i).getValue(), Hex.fromHex(redeemScript), FchMainNetwork.MAINNETWORK)){
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


    public Script createSchnorrMultiSigInputScriptBytes(List<byte[]> signatures, byte[] multisigProgramBytes) {
        if (signatures.size() >= 16) return null;
        ScriptBuilder builder = new ScriptBuilder();
        builder.smallNum(0);
        Iterator<byte[]> var3 = signatures.iterator();
        byte[] sigHashAll = new byte[]{0x41};

        while (var3.hasNext()) {
            byte[] signature = var3.next();
            builder.data(BytesUtils.bytesMerger(signature, sigHashAll));
        }

        if (multisigProgramBytes != null) {
            builder.data(multisigProgramBytes);
        }

        return builder.build();
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

    //Verify TX
    public boolean rawTxSigVerify(byte[] rawTx, byte[] pubkey, byte[] sig, int inputIndex, long inputValue, byte[] redeemScript, MainNetParams mainnetwork) {
        if(mainnetwork==null)mainnetwork=this.mainNetwork;
        Transaction transaction = new Transaction(mainnetwork, rawTx);
        Script script = new Script(redeemScript);
        Sha256Hash hash = transaction.hashForSignatureWitness(inputIndex, script, Coin.valueOf(inputValue), Transaction.SigHash.ALL, false);
        return SchnorrSignature.schnorr_verify(hash.getBytes(), pubkey, sig);
    }

    /**
     * Calculate the transaction input size for a P2SH (Pay-to-Script-Hash) output
     * Different P2SH types have different sizes:
     * - CLTV (single-sig time lock): signature + pubkey + redeemScript
     * - MULTISIG: m signatures + redeemScript
     * - MULTISIG_CLTV: m signatures + redeemScript (CLTV+multisig combined)
     *
     * @param p2sh The P2SH information (type, m, n, redeemScript)
     * @return The estimated input size in bytes
     */
    private static long calculateP2SHInputSize(P2SH p2sh) {
        int redeemScriptLen = p2sh.getRedeemScript().length() / 2; // hex to bytes

        P2SH.P2shType inputType = p2sh.getType();

        return switch (inputType) {
            case CLTV ->
                // Single-sig CLTV P2SH
                // scriptSig format: <signature(64)+sighash(1)> <pubkey(33)> <redeemScript>
                // Signature with sighash flag: 64 + 1 + 1 (length byte) = 66 bytes
                // Compressed pubkey: 33 + 1 (length byte) = 34 bytes
                // RedeemScript: variable length + length byte
                    calculateSingleSigP2SHInputSize(redeemScriptLen);
            case MULTISIG ->
                // Multisig P2SH (no CLTV)
                // scriptSig format: OP_0 <sig1> <sig2> ... <sigM> <redeemScript>
                    multisigInputSize(p2sh.getN(), p2sh.getM(), redeemScriptLen);
            case MULTISIG_CLTV ->
                // Multisig + CLTV P2SH
                // scriptSig format: OP_0 <sig1> <sig2> ... <sigM> <redeemScript>
                // The redeemScript contains both CLTV and multisig, so redeemScriptLen is accurate
                    multisigInputSize(p2sh.getN(), p2sh.getM(), redeemScriptLen);
            default -> {
                // Fallback to generic calculation
                TimberLogger.w(TAG, "Unknown P2SH type: " + inputType + ", using generic calculation");
                yield calculateSingleSigP2SHInputSize(redeemScriptLen);
            }
        };
    }

    /**
     * Calculate size for single-sig P2SH input (CLTV or other single-sig scripts)
     * @param redeemScriptLen Length of the redeemScript in bytes
     * @return Input size in bytes
     */
    private static long calculateSingleSigP2SHInputSize(int redeemScriptLen) {
        // scriptSig components:
        // - Signature: 64 bytes (Schnorr) + 1 byte sighash flag + 1 byte length = 66 bytes
        // - Pubkey: 33 bytes (compressed) + 1 byte length = 34 bytes
        // - RedeemScript: redeemScriptLen + VarInt length

        long redeemScriptVarInt = VarInt.sizeOf(redeemScriptLen);
        long signLen = 64; // Schnorr signature
        long sigHashLen = 1; // sighash flag
        long signLenLen = 1; // length byte for signature
        long pubkeyLen = 33; // compressed pubkey
        long pubkeyLenLen = 1; // length byte for pubkey

        long scriptLength = signLenLen + signLen + sigHashLen + pubkeyLenLen + pubkeyLen + redeemScriptVarInt + redeemScriptLen;
        long scriptVarInt = VarInt.sizeOf(scriptLength);

        // Total: txid(32) + index(4) + scriptSig varint + scriptSig + sequence(4)
        return 32 + 4 + scriptVarInt + scriptLength + 4;
    }

    /**
     * Get a list of Cash outputs from a signed transaction for a specific FID.
     * This is useful for updating local cash database after broadcasting a transaction.
     *
     * @param signedTx The signed transaction in hex format
     * @param fid The FID to filter outputs for
     * @return List of Cash objects belonging to the specified FID
     */
    public static List<Cash> getIssuedCashListForFid(String signedTx, String fid) {
        if (signedTx == null || fid == null) {
            return new ArrayList<>();
        }

        List<Cash> cashList = new ArrayList<>();

        try {
            Transaction transaction = new Transaction(FchMainNetwork.MAINNETWORK, Hex.fromHex(signedTx));
            String txId = transaction.getTxId().toString();
            List<TransactionOutput> outputs = transaction.getOutputs();

            for (int i = 0; i < outputs.size(); i++) {
                TransactionOutput output = outputs.get(i);

                try {
                    Address address = output.getScriptPubKey().getToAddress(FchMainNetwork.MAINNETWORK);

                    if (fid.equals(address.toString())) {
                        Cash cash = new Cash();
                        cash.setBirthTxId(txId);
                        cash.setBirthIndex(i);
                        cash.setOwner(fid);
                        cash.setValue(output.getValue().getValue());
                        cash.setValid(true);
                        cash.makeId();
                        cashList.add(cash);
                    }
                } catch (Exception e) {
                    // Skip outputs that can't be converted to addresses (e.g., OP_RETURN)
                    TimberLogger.d(TAG, "Skipping output " + i + " - cannot convert to address: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            TimberLogger.e(TAG, "Failed to parse signed transaction: " + e.getMessage());
            return new ArrayList<>();
        }

        return cashList;
    }

}

