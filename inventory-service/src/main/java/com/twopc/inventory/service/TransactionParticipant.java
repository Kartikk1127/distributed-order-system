package com.twopc.inventory.service;

import com.twopc.common.log.FileBasedWAL;
import com.twopc.common.log.WriteAheadLog;
import com.twopc.common.model.Transaction;
import com.twopc.common.protocol.TransactionMessage;
import com.twopc.common.protocol.TransactionState;
import com.twopc.inventory.storage.InventoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// implements the participant side of the 2pc protocol
// handles PREPARE, COMMIT, ABORT requests from the coordinator
@Service
public class TransactionParticipant {
    private static final Logger logger = LoggerFactory.getLogger(TransactionParticipant.class);

    private final InventoryStore inventoryStore;
    private final ResourceLockManager lockManager;
    private final WriteAheadLog wal;

    // active transactions in memory
    private final Map<String, Transaction> activeTransactions = new ConcurrentHashMap<>();

    public TransactionParticipant(InventoryStore inventoryStore, ResourceLockManager lockManager,
                                  @Value("${inventory.wal.base-dir}") String walBaseDir) {
        this.inventoryStore = inventoryStore;
        this.lockManager = lockManager;
        this.wal = new FileBasedWAL("inventory-service", walBaseDir);
    }

    /**
     * Handle PREPARE request from coordinator
     *
     * Steps:
     * 1. Check if resource is available
     * 2. Try to acquire locks
     * 3. Log PREPARED state to WAL
     * 4. Vote YES or NO
     * */
    public TransactionMessage handlePrepare(TransactionMessage prepareMsg) {
        String txnId = prepareMsg.getTransactionId();
        logger.info("[{}] Received PREPARE request", txnId);

        try {
            // extract operation data
            Map<String, Object> payload = prepareMsg.getPayload();
            String productId = (String) payload.get("productId");
            Integer quantity = (Integer) payload.get("quantity");

            if (productId == null || quantity == null) {
                return TransactionMessage.voteNo(txnId, "inventory-service", "Invalid request: missing productId or quantity");
            }

            // create a transaction
            Transaction transaction = new Transaction(txnId);
            transaction.getOperationData().put("productId", productId);
            transaction.getOperationData().put("quantity", quantity);

            // check if product exists and has sufficient quantity
            if (!inventoryStore.hasAvailableQuantity(productId, quantity)) {
                logger.warn("[{}], Insufficient inventory for product {}. Requested: {}",
                        txnId, productId, quantity);
                return TransactionMessage.voteNo(txnId, "inventory-service", "Insufficient memory");
            }

            // try to acquire lock on the resource
            String resourceId = productId;
            if (!lockManager.acquireLock(txnId, resourceId)) {
                logger.warn("[{}] Failed to acquire lock on resource {}", txnId, resourceId);
                return TransactionMessage.voteNo(txnId, "inventory-service", "Resource already locked");
            }

            // log PREPARED state to WAL
            transaction.setState(TransactionState.PREPARED);
            transaction.getLockedResources().add(resourceId);
            wal.writeLog(transaction);

            // store in memory
            activeTransactions.put(txnId, transaction);

            logger.info("[{}] PREPARED - Locked resource {} and logged to WAL", txnId, resourceId);

            return TransactionMessage.voteYes(txnId, "inventory-service", prepareMsg.getPayload());
        } catch (Exception e) {
            logger.error("[{}] Error during PREPARE", txnId, e);
            lockManager.releaseLocks(txnId);
            return TransactionMessage.voteNo(txnId,"inventory-service", "Internal error: " + e.getMessage());
        }
    }

    /**
     * Handle COMMIT request from coordinator
     *
     * Steps:
     * 1. apply the inventory changes
     * 2. log COMMITTED state to wal
     * 3. release locks
     * */
    public void handleCommit(String txnId) {
        logger.info("[{}] Received COMMIT request", txnId);

        Transaction transaction = activeTransactions.get(txnId);
        if (transaction == null) {
            // try to load from wal
            Optional<Transaction> walTxn = wal.readLog(txnId);
            if (walTxn.isEmpty()) {
                logger.warn("[{}] Transaction not found in memory or wal", txnId);
                return;
            }
            transaction = walTxn.get();
        }

        try {
            // apply inventory changes
            String productId = (String) transaction.getOperationData().get("productId");
            Integer quantity = (Integer) transaction.getOperationData().get("quantity");

            inventoryStore.reserveInventory(productId,quantity);

            // log committed state
            transaction.setState(TransactionState.COMMITTED);
            wal.writeLog(transaction);

            logger.info("[{}] COMMITTED - Reserved {} units of {}", txnId, quantity, productId);
        } catch (Exception e) {
            logger.error("[{}] Error during COMMIT", txnId, e);
            // in a real system, there should be a retry logic
        } finally {
            // release locks
            lockManager.releaseLocks(txnId);
            activeTransactions.remove(txnId);
        }
    }

    /**
     * Handle ABORT request from coordinatory
     *
     * Steps:
     * 1. Log ABORTED state to wal
     * 2. Release lock (no inventory changes needed)
     * */
    public void handleAbort(String txnId) {

        logger.info("[{}] Received ABORT request", txnId);
        Transaction transaction = activeTransactions.get(txnId);
        if (transaction == null) {
            Optional<Transaction> walTxn = wal.readLog(txnId);
            if (walTxn.isEmpty()) {
                logger.warn("[{}] Transaction not found in memory or wal", txnId);
                return;
            }
            transaction = walTxn.get();
        }

        try {
            // log aborted state
            transaction.setState(TransactionState.ABORTED);
            wal.writeLog(transaction);

            logger.info("[{}] ABORTED - No changes applied", txnId);
        } finally {
            lockManager.releaseLocks(txnId);
            activeTransactions.remove(txnId);
        }
    }

    // get transaction status for admin/debugging
    public Optional<Transaction> getTransaction(String txnId) {
        Transaction txn = activeTransactions.get(txnId);
        if (txn!=null) {
            return Optional.of(txn);
        }
        return wal.readLog(txnId);
    }
}
