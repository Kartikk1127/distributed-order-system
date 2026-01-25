package com.twopc.inventory.controller;

import com.twopc.common.protocol.TransactionMessage;
import com.twopc.inventory.service.TransactionParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for 2PC participant operations.
 * Called by the coordinator during 2PC protocol*/
@RestController
@RequestMapping("/api/transaction")
public class ParticipantController {
    private static final Logger logger = LoggerFactory.getLogger(ParticipantController.class);

    private final TransactionParticipant participant;
    public ParticipantController(TransactionParticipant participant) {
        this.participant = participant;
    }

    /**
     * Coordinator sends PREPARE, participant votes YES/NO.
     */
    @PostMapping("/prepare")
    public ResponseEntity<TransactionMessage> prepare(@RequestBody TransactionMessage message) {
        logger.info("POST /api/transaction/prepare - Transaction: {}", message.getTransactionId());
        TransactionMessage transactionMessage = participant.handlePrepare(message);
        return ResponseEntity.ok(transactionMessage);
    }

    /**
     * Coordinator sends COMMIT, participant applies changes.
     */
    @PostMapping("/commit")
    public ResponseEntity<Void> commit(@RequestBody TransactionMessage commitMsg) {
        logger.info("POST /api/transaction/commit - Transaction: {}",
                commitMsg.getTransactionId());

        participant.handleCommit(commitMsg.getTransactionId());
        return ResponseEntity.ok().build();
    }

    /**
     * Coordinator sends ABORT, participant releases locks.
     */
    @PostMapping("/abort")
    public ResponseEntity<Void> abort(@RequestBody TransactionMessage abortMsg) {
        logger.info("POST /api/transaction/abort - Transaction: {}",
                abortMsg.getTransactionId());

        participant.handleAbort(abortMsg.getTransactionId());
        return ResponseEntity.ok().build();
    }

    /**
     * Get transaction status (for debugging).
     */
    @GetMapping("/{txnId}/status")
    public ResponseEntity<Object> getStatus(@PathVariable String txnId) {
        return participant.getTransaction(txnId)
                .map(txn -> ResponseEntity.ok((Object) txn))
                .orElse(ResponseEntity.notFound().build());
    }
}
