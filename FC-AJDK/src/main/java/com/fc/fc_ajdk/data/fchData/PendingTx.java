package com.fc.fc_ajdk.data.fchData;

import com.fc.fc_ajdk.data.fcData.FcObject;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a locally pending transaction — a TX that has been signed (or partially signed, for multisig)
 * on this device but has not yet been confirmed as broadcast/canceled by the user.
 *
 * While PENDING, the referenced input cashes are locked (pendingId set, valid=true) and any outputs
 * owned locally are inserted as pendingId-tagged, valid=false.
 */
public class PendingTx extends FcObject {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELED = "CANCELED";

    // Primary key (UUID). Used as Cash.pendingId.
    // pendingId is stored in FcEntity.id.

    // Filled once the TX is fully built (all multisig sigs collected, or single-key signed).
    private String onChainTxId;

    // Fully-signed / fully-built TX hex. Null for a partial multisig TX.
    private String signedTxHex;

    // Serialized RawTxInfo for re-export / re-viewing / resuming partial multisig signing.
    private String rawTxInfoJson;

    // Deterministic fingerprint of the unsigned TX (inputs + outputs), stable across partial-sign round trips.
    // Used to find an existing PendingTx record when a partial-signed RawTxInfo is re-imported.
    private String unsignedTxFingerprint;

    // PENDING | CONFIRMED | CANCELED
    private String status;

    private Long createdAt;
    private Long updatedAt;

    // Input cash IDs locked by this pending TX.
    private List<String> spentCashIds;

    // Output cash IDs (only those owned locally) inserted as pending-incoming.
    // Populated when the TX is fully built.
    private List<String> newCashIds;

    private Boolean isMultisig;

    // Sender FID (single-key sender or multisig id).
    private String ownerFid;

    public PendingTx() {
        this.spentCashIds = new ArrayList<>();
        this.newCashIds = new ArrayList<>();
    }

    public static PendingTx create(String rawTxInfoJson, String unsignedTxFingerprint, boolean isMultisig, String ownerFid) {
        PendingTx pendingTx = new PendingTx();
        pendingTx.setId(UUID.randomUUID().toString());
        pendingTx.setRawTxInfoJson(rawTxInfoJson);
        pendingTx.setUnsignedTxFingerprint(unsignedTxFingerprint);
        pendingTx.setStatus(STATUS_PENDING);
        pendingTx.setMultisig(isMultisig);
        pendingTx.setOwnerFid(ownerFid);
        long now = System.currentTimeMillis() / 1000;
        pendingTx.setCreatedAt(now);
        pendingTx.setUpdatedAt(now);
        return pendingTx;
    }

    public String getPendingId() {
        return getId();
    }

    public void setPendingId(String pendingId) {
        setId(pendingId);
    }

    public String getOnChainTxId() {
        return onChainTxId;
    }

    public void setOnChainTxId(String onChainTxId) {
        this.onChainTxId = onChainTxId;
    }

    public String getSignedTxHex() {
        return signedTxHex;
    }

    public void setSignedTxHex(String signedTxHex) {
        this.signedTxHex = signedTxHex;
    }

    public String getRawTxInfoJson() {
        return rawTxInfoJson;
    }

    public void setRawTxInfoJson(String rawTxInfoJson) {
        this.rawTxInfoJson = rawTxInfoJson;
    }

    public String getUnsignedTxFingerprint() {
        return unsignedTxFingerprint;
    }

    public void setUnsignedTxFingerprint(String unsignedTxFingerprint) {
        this.unsignedTxFingerprint = unsignedTxFingerprint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getSpentCashIds() {
        return spentCashIds;
    }

    public void setSpentCashIds(List<String> spentCashIds) {
        this.spentCashIds = spentCashIds;
    }

    public List<String> getNewCashIds() {
        return newCashIds;
    }

    public void setNewCashIds(List<String> newCashIds) {
        this.newCashIds = newCashIds;
    }

    public Boolean getMultisig() {
        return isMultisig;
    }

    public void setMultisig(Boolean multisig) {
        isMultisig = multisig;
    }

    public String getOwnerFid() {
        return ownerFid;
    }

    public void setOwnerFid(String ownerFid) {
        this.ownerFid = ownerFid;
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isConfirmed() {
        return STATUS_CONFIRMED.equals(status);
    }

    public boolean isCanceled() {
        return STATUS_CANCELED.equals(status);
    }

    public boolean isFullyBuilt() {
        return signedTxHex != null && onChainTxId != null;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis() / 1000;
    }

    @Override
    public String toJson() {
        return new Gson().toJson(this);
    }

    public static PendingTx fromJson(String json) {
        return new Gson().fromJson(json, PendingTx.class);
    }
}
