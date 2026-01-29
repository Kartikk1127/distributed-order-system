package com.twopc.payment.service;

import com.twopc.common.log.FileBasedWAL;
import com.twopc.common.log.WriteAheadLog;
import com.twopc.common.model.Transaction;
import com.twopc.common.protocol.TransactionMessage;
import com.twopc.common.protocol.TransactionState;
import com.twopc.payment.storage.PaymentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TransactionParticipant {
    private static final Logger logger = LoggerFactory.getLogger(TransactionParticipant.class);

    private final PaymentStore paymentStore;
    private final ResourceLockManager lockManager;
    private final WriteAheadLog wal;

    private final Map<String, Transaction> activeTransactions = new ConcurrentHashMap<>();

    public TransactionParticipant(PaymentStore paymentStore, ResourceLockManager lockManager, @Value("${payment.wal.base-dir}") String wal) {
        this.paymentStore = paymentStore;
        this.lockManager = lockManager;
        this.wal = new FileBasedWAL("payment-service", wal);
    }

    // handle prepare request from coordinator
    public TransactionMessage handlePrepare(TransactionMessage prepareMsg) {
        String txnId = prepareMsg.getTransactionId();
        logger.info("[{}] Received PREPARE request", txnId);

        try {
            // extract operation data
            Map<String, Object> payload = prepareMsg.getPayload();
            String customerId = (String) payload.get("customerId");

            // handle both integer and double for amount
            Number amountNumber = (Number) payload.get("amount");
            Double amount = amountNumber != null ? amountNumber.doubleValue() : null;

            if (customerId == null || amount == null) {
                return TransactionMessage.voteNo(txnId, "payment-service", "Invalid request: missing customer id or amount");
            }

            // create a transaction
            Transaction transaction = new Transaction(txnId);
            transaction.getOperationData().put("customerId", customerId);
            transaction.getOperationData().put("amount", amount);

            // check if account exists and has sufficient balance
            if (!paymentStore.hasSufficientBalance(customerId, amount)) {
                logger.warn("[{}] Insufficient balance for customer {}. Requested: {}", txnId, customerId, amount);
                return TransactionMessage.voteNo(txnId, "payment-service", "Insufficient Balance");
            }

            // try to acquire lock on the account
            if (!lockManager.acquireLock(txnId, customerId)) {
                logger.warn("[{}] Failed to acquire lock on resource {}", txnId, customerId);
                return TransactionMessage.voteNo(txnId, "payment-service", "Resource already locked");
            }

            // log prepared state to wal
            transaction.setState(TransactionState.PREPARED);
            transaction.getLockedResources().add(customerId);
            wal.writeLog(transaction);

            activeTransactions.put(txnId, transaction);

            logger.info("[{}] PREPARED - Locked resource {} and logged to WAL", txnId, customerId);
            return TransactionMessage.voteYes(txnId, "payment-service", prepareMsg.getPayload());
        } catch (Exception e) {
            logger.error("[{}] Error during PREPARE", txnId, e);
            lockManager.releaseLocks(txnId);
            return TransactionMessage.voteNo(txnId, "payment-service", "Internal error: " + e.getMessage());
        }
    }

    // handle commit request from coordinator
    public void handleCommit(String txnId) {
        logger.info("[{}] Received COMMIT request", txnId);

        Transaction transaction = activeTransactions.get(txnId);
        if (transaction == null) {
            Optional<Transaction> walTxn = wal.readLog(txnId);
            if (walTxn.isEmpty()) {
                logger.warn("[{}] Transaction not found in memory or WAL", txnId);
                return;
            }
            transaction = walTxn.get();
        }

        try {
            // apply payment changes
            String customerId = (String) transaction.getOperationData().get("customerId");
            Double amount = (Double) transaction.getOperationData().get("amount");

            paymentStore.deductAmount(customerId, amount);

            // log committed state
            transaction.setState(TransactionState.COMMITTED);
            wal.writeLog(transaction);

            logger.info("[{}] COMMITTED - Deducted {} from {}", txnId, amount, customerId);
        } catch (Exception e) {
            logger.error("[{}] Error during COMMIT", txnId, e);
        } finally {
            lockManager.releaseLocks(txnId);
            activeTransactions.remove(txnId);
        }
    }

    // handle abort request from coordinator
    public void handleAbort(String txnId) {
        logger.info("[{}] Received ABORT request", txnId);

        Transaction transaction = activeTransactions.get(txnId);
        if (transaction == null) {
            Optional<Transaction> walTxn = wal.readLog(txnId);
            if (walTxn.isEmpty()) {
                logger.warn("[{}] Transaction not found in memory or WAL", txnId);
                return;
            }
            transaction = walTxn.get();
        }

        try {
            transaction.setState(TransactionState.ABORTED);
            wal.writeLog(transaction);

            logger.info("[{}] ABORTED - No changes applied", txnId);
        } finally {
            lockManager.releaseLocks(txnId);
            activeTransactions.remove(txnId);
        }
    }

    // get transaction status
    public Optional<Transaction> getTransaction(String txnId) {
        Transaction transaction = activeTransactions.get(txnId);
        if (transaction != null) {
            return Optional.of(transaction);
        }
        return wal.readLog(txnId);
    }
}
