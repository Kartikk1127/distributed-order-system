package com.twopc.coordinator.service;

import com.twopc.common.log.FileBasedWAL;
import com.twopc.common.log.WriteAheadLog;
import com.twopc.common.model.Transaction;
import com.twopc.common.protocol.MessageType;
import com.twopc.common.protocol.TransactionMessage;
import com.twopc.common.protocol.TransactionState;
import com.twopc.coordinator.model.OrderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// core coordinator logic for the Two-Phase commit protocol
// create transactions for incoming orders
// send prepare to all participants
// collect votes
// make COMMIT/ABORT decision
// send decision to all participants
@Service
public class TransactionCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(TransactionCoordinator.class);

    private final ParticipantClient participantClient;
    private final WriteAheadLog wal;
    private final String inventoryServiceUrl;
    private final String paymentServiceUrl;

    // active transactions in memory
    private final Map<String, Transaction> activeTransactions = new ConcurrentHashMap<>();

    public TransactionCoordinator(
            ParticipantClient participantClient,
            @Value("${coordinator.wal.base-dir}") String wal,
            @Value("${coordinator.participants.inventory-service}") String inventoryServiceUrl,
            @Value("${coordinator.participants.payment-service}") String paymentServiceUrl) {
        this.participantClient = participantClient;
        this.wal = new FileBasedWAL("coordinator-service", wal);
        this.inventoryServiceUrl = inventoryServiceUrl;
        this.paymentServiceUrl = paymentServiceUrl;
    }

    // process an order using the 2PC protocol
    // create transaction
    // send prepare to all participants
    // collect votes
    // make decision(commit if all yes, abort otherwise)
    // log decision to wal
    // send decision to all participants
    public Transaction processOrder(OrderRequest order) {
        String txnId = "TXN-" + UUID.randomUUID().toString().substring(0,8);

        logger.info("[{}] Starting 2PC for order: {}", txnId, order);

        Transaction transaction = new Transaction(txnId);
        transaction.addParticipant(inventoryServiceUrl);
        transaction.addParticipant(paymentServiceUrl);

        // store order details
        transaction.getOperationData().put("orderId", order.getOrderId());
        transaction.getOperationData().put("customerId", order.getCustomerId());
        transaction.getOperationData().put("productId", order.getProductId());
        transaction.getOperationData().put("quantity", order.getQuantity());
        transaction.getOperationData().put("amount", order.getAmount());

        activeTransactions.put(txnId, transaction);

        // transition to PREPARING state and send PREPARE to all participants
        transaction.setState(TransactionState.PREPARING);
        wal.writeLog(transaction);

        logger.info("[{}] Entering PREPARING phase", txnId);

        // send prepare to inventory service
        Map<String, Object> inventoryData = new HashMap<>();
        inventoryData.put("productId", order.getProductId());
        inventoryData.put("quantity", order.getQuantity());

        TransactionMessage inventoryVote = participantClient.sendPrepare(inventoryServiceUrl, txnId, inventoryData);
        transaction.recordVote(inventoryServiceUrl, inventoryVote.getMessageType() == MessageType.VOTE_YES ? "YES" : "NO");

        // send prepare to payment service
        Map<String, Object> paymentData = new HashMap<>();
        paymentData.put("customerId", order.getCustomerId());
        paymentData.put("amount", order.getAmount());

        TransactionMessage paymentVote = participantClient.sendPrepare(paymentServiceUrl, txnId, paymentData);
        transaction.recordVote(paymentServiceUrl, paymentVote.getMessageType() == MessageType.VOTE_YES ? "YES" : "NO");

        // make decision based on votes
        if (transaction.allParticipantsVotedYes()) {
            logger.info("[{}] All participants voted YES - COMMITTING", txnId);

            transaction.setState(TransactionState.COMMITTED);
            wal.writeLog(transaction);

            // send commit to all participants
            participantClient.sendCommit(inventoryServiceUrl,txnId);
            participantClient.sendCommit(paymentServiceUrl,txnId);

            logger.info("[{}] Transaction COMMITTED successfully", txnId);
        } else {
            String reason = getAbortReason(transaction);
            logger.warn("[{}] At least one participant voted NO - ABORTING. Reason: {}", txnId, reason);

            transaction.setState(TransactionState.ABORTED);
            wal.writeLog(transaction);

            // send abort to both
            participantClient.sendAbort(inventoryServiceUrl, txnId);
            participantClient.sendAbort(paymentServiceUrl, txnId);
            logger.info("[{}] Transaction ABORTED", txnId);
        }

        activeTransactions.remove(txnId);

        return transaction;
    }

    public Optional<Transaction> getTransaction(String txnId) {
        Transaction txn = activeTransactions.get(txnId);
        if (txn != null) {
            return Optional.of(txn);
        }
        return wal.readLog(txnId);
    }

    // helper to determine why transaction was aborted
    public String getAbortReason(Transaction transaction) {
        StringBuilder reason = new StringBuilder();

        for (Map.Entry<String, String> entry : transaction.getParticipantsVote().entrySet()) {
            if ("NO".equals(entry.getValue())) {
                String service = entry.getKey().contains("8081") ? "Inventory" : "Payment";
                reason.append(service).append(" voted NO; ");
            }
        }

        if (reason.isEmpty()) {
            reason.append("Timeout or communication failure");
        }

        return reason.toString();
    }
}
