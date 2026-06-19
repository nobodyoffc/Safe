package com.fc.safe.db;

import android.content.Context;

import com.fc.fc_ajdk.data.fchData.Cash;
import com.fc.fc_ajdk.data.fchData.PendingTx;
import com.fc.fc_ajdk.utils.TimberLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Singleton manager for the PendingTx database. Mirrors CashManager.
 *
 * A PendingTx is keyed by its pendingId (UUID), not the on-chain txId — because for multisig
 * we need to persist the record before the TX is fully built (and therefore before onChainTxId exists).
 */
public class PendingTxManager {
    private static final String TAG = "PendingTxManager";

    private static PendingTxManager instance;
    private LocalDB<PendingTx> pendingTxDB;

    private PendingTxManager() {}

    public static synchronized PendingTxManager getInstance(Context context) {
        if (instance == null) {
            instance = new PendingTxManager();
            instance.initialize(context.getApplicationContext());
        }
        return instance;
    }

    public void initialize(Context context) {
        DatabaseManager dbManager = DatabaseManager.getInstance(context);
        if (pendingTxDB != null) {
            try {
                pendingTxDB.close();
            } catch (Exception e) {
                TimberLogger.e(TAG, "Error closing database: " + e.getMessage());
            }
        }
        pendingTxDB = dbManager.getEntityDatabase(PendingTx.class);
    }

    public LocalDB<PendingTx> getDB() {
        return pendingTxDB;
    }

    public PendingTx getById(String pendingId) {
        if (pendingTxDB == null || pendingId == null) return null;
        return pendingTxDB.get(pendingId);
    }

    public Map<String, PendingTx> getAll() {
        if (pendingTxDB == null) return new HashMap<>();
        Map<String, PendingTx> all = pendingTxDB.getAll();
        return all != null ? all : new HashMap<>();
    }

    public List<PendingTx> getAllList() {
        return new ArrayList<>(getAll().values());
    }

    /** Find an existing pending TX by its unsigned fingerprint — used when a partial multisig TX is re-imported. */
    public PendingTx findByFingerprint(String fingerprint) {
        if (fingerprint == null) return null;
        for (PendingTx pendingTx : getAllList()) {
            if (fingerprint.equals(pendingTx.getUnsignedTxFingerprint())) {
                return pendingTx;
            }
        }
        return null;
    }

    public List<PendingTx> getByStatus(String status) {
        List<PendingTx> result = new ArrayList<>();
        for (PendingTx pendingTx : getAllList()) {
            if (status.equals(pendingTx.getStatus())) {
                result.add(pendingTx);
            }
        }
        return result;
    }

    /** Pending TXs sorted newest-first. */
    public List<PendingTx> getPendingList() {
        List<PendingTx> list = getByStatus(PendingTx.STATUS_PENDING);
        sortByUpdatedDesc(list);
        return list;
    }

    /** Terminal (CONFIRMED + CANCELED) records, newest-first. */
    public List<PendingTx> getArchivedList() {
        List<PendingTx> list = new ArrayList<>();
        for (PendingTx pendingTx : getAllList()) {
            if (!pendingTx.isPending()) list.add(pendingTx);
        }
        sortByUpdatedDesc(list);
        return list;
    }

    private void sortByUpdatedDesc(List<PendingTx> list) {
        Collections.sort(list, new Comparator<PendingTx>() {
            @Override
            public int compare(PendingTx a, PendingTx b) {
                long ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : 0L;
                long tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : 0L;
                return Long.compare(tb, ta);
            }
        });
    }

    public int pendingCount() {
        int count = 0;
        for (PendingTx pendingTx : getAllList()) {
            if (pendingTx.isPending()) count++;
        }
        return count;
    }

    public void put(PendingTx pendingTx) {
        if (pendingTx == null || pendingTx.getId() == null) {
            TimberLogger.w(TAG, "Cannot put PendingTx with null id");
            return;
        }
        pendingTx.touch();
        pendingTxDB.put(pendingTx.getId(), pendingTx);
    }

    public void remove(String pendingId) {
        if (pendingTxDB == null || pendingId == null) return;
        pendingTxDB.remove(pendingId);
    }

    public void commit() {
        if (pendingTxDB != null) pendingTxDB.commit();
    }

    /**
     * Lock the given input cashes by tagging them with pendingId. The cashes stay valid=true
     * (they exist on-chain) but are excluded from available balance / selectable UTXOs.
     * Only cashes already present in cashDB are touched; missing ones are skipped.
     *
     * @return the ids of cashes that were actually locked (excludes cashes already locked by a different pending TX)
     */
    public static List<String> lockInputCashes(Context context, List<String> cashIds, String pendingId) {
        List<String> locked = new ArrayList<>();
        if (cashIds == null || cashIds.isEmpty() || pendingId == null) return locked;
        CashManager cashManager = CashManager.getInstance(context);
        for (String cashId : cashIds) {
            if (cashId == null) continue;
            Cash cash = cashManager.getCashById(cashId);
            if (cash == null) continue;
            // Skip cashes already locked by another pending TX to avoid clobbering.
            if (cash.getPendingId() != null && !pendingId.equals(cash.getPendingId())) {
                TimberLogger.w(TAG, "Cash %s already locked by pending %s; skipping", cashId, cash.getPendingId());
                continue;
            }
            cash.setPendingId(pendingId);
            cashManager.getCashDB().put(cashId, cash);
            locked.add(cashId);
        }
        cashManager.commit();
        return locked;
    }

    /** Insert pending-incoming output cashes (valid=false, pendingId set). */
    public static void insertPendingOutputs(Context context, List<Cash> cashes, String pendingId) {
        if (cashes == null || cashes.isEmpty() || pendingId == null) return;
        CashManager cashManager = CashManager.getInstance(context);
        for (Cash cash : cashes) {
            if (cash.getId() == null) cash.makeId();
            if (cash.getId() == null) continue;
            cash.setValid(false);
            cash.setPendingId(pendingId);
            cashManager.addCash(cash);
        }
        cashManager.commit();
    }

    /**
     * Finalize a pending TX as CONFIRMED: spent inputs become valid=false/spendTxId set,
     * new outputs become valid=true, both lose their pendingId.
     */
    public void markConfirmed(Context context, PendingTx pendingTx) {
        if (pendingTx == null) return;
        CashManager cashManager = CashManager.getInstance(context);
        String onChainTxId = pendingTx.getOnChainTxId();

        if (pendingTx.getSpentCashIds() != null) {
            for (String cashId : pendingTx.getSpentCashIds()) {
                Cash cash = cashManager.getCashById(cashId);
                if (cash == null) continue;
                cash.setValid(false);
                cash.setPendingId(null);
                if (onChainTxId != null) cash.setSpendTxId(onChainTxId);
                cash.setSpendTime(System.currentTimeMillis() / 1000);
                cashManager.getCashDB().put(cashId, cash);
            }
        }

        if (pendingTx.getNewCashIds() != null) {
            for (String cashId : pendingTx.getNewCashIds()) {
                Cash cash = cashManager.getCashById(cashId);
                if (cash == null) continue;
                cash.setValid(true);
                cash.setPendingId(null);
                cashManager.getCashDB().put(cashId, cash);
            }
        }

        cashManager.commit();

        pendingTx.setStatus(PendingTx.STATUS_CONFIRMED);
        put(pendingTx);
        commit();
    }

    /**
     * Finalize a pending TX as CANCELED: spent inputs are released (pendingId cleared, remain valid=true),
     * new outputs are deleted entirely.
     */
    public void markCanceled(Context context, PendingTx pendingTx) {
        if (pendingTx == null) return;
        CashManager cashManager = CashManager.getInstance(context);

        if (pendingTx.getSpentCashIds() != null) {
            for (String cashId : pendingTx.getSpentCashIds()) {
                Cash cash = cashManager.getCashById(cashId);
                if (cash == null) continue;
                cash.setPendingId(null);
                cashManager.getCashDB().put(cashId, cash);
            }
        }

        if (pendingTx.getNewCashIds() != null) {
            for (String cashId : pendingTx.getNewCashIds()) {
                cashManager.removeCash(cashId);
            }
        }

        cashManager.commit();

        pendingTx.setStatus(PendingTx.STATUS_CANCELED);
        put(pendingTx);
        commit();
    }
}
