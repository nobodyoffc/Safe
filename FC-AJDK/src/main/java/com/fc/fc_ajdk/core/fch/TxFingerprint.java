package com.fc.fc_ajdk.core.fch;

import com.fc.fc_ajdk.core.crypto.Hash;
import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.Multisig;
import com.fc.fc_ajdk.utils.Hex;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes a deterministic fingerprint of a RawTxInfo that ignores signatures — so a partial multisig TX
 * and its fully-signed successor produce the same fingerprint.
 *
 * Used to find an existing PendingTx record when an accumulating partial-signed RawTxInfo is re-imported.
 */
public final class TxFingerprint {

    private static final String SEP = "|";

    private TxFingerprint() {}

    public static String of(RawTxInfo rawTxInfo) {
        if (rawTxInfo == null) return null;
        StringBuilder sb = new StringBuilder();

        sb.append("inputs=");
        List<String> inputKeys = new ArrayList<>();
        if (rawTxInfo.getInputs() != null) {
            for (Cash in : rawTxInfo.getInputs()) {
                String id = in.getId();
                if (id == null) id = in.makeId();
                inputKeys.add((id != null ? id : "null") + ":" + (in.getValue() != null ? in.getValue() : 0L));
            }
        }
        Collections.sort(inputKeys);
        for (String k : inputKeys) sb.append(k).append(SEP);

        sb.append(";outputs=");
        // Output order matters for TX identity, so do not sort.
        if (rawTxInfo.getOutputs() != null) {
            for (Cash out : rawTxInfo.getOutputs()) {
                sb.append(out.getOwner() == null ? "" : out.getOwner())
                  .append(":")
                  .append(out.getValue() == null ? 0L : out.getValue())
                  .append(":")
                  .append(out.getLockTime() == null ? 0L : out.getLockTime())
                  .append(SEP);
            }
        }

        sb.append(";opReturn=").append(rawTxInfo.getOpReturn() == null ? "" : rawTxInfo.getOpReturn());
        sb.append(";lockTime=").append(rawTxInfo.getLockTime() == null ? 0L : rawTxInfo.getLockTime());
        sb.append(";changeTo=").append(rawTxInfo.getChangeTo() == null ? "" : rawTxInfo.getChangeTo());
        sb.append(";sender=").append(rawTxInfo.getSender() == null ? "" : rawTxInfo.getSender());

        Multisig m = rawTxInfo.getMultisign();
        if (m != null) {
            sb.append(";multisig=").append(m.getId() == null ? "" : m.getId());
        }

        byte[] hash = Hash.sha256x2(sb.toString().getBytes(StandardCharsets.UTF_8));
        return Hex.toHex(hash);
    }
}
