package com.twopc.payment.controller;
import com.twopc.common.protocol.TransactionMessage;
import com.twopc.payment.service.TransactionParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transaction")
public class ParticipantController {
    private static final Logger logger = LoggerFactory.getLogger(ParticipantController.class);

    private final TransactionParticipant participant;

    public ParticipantController(TransactionParticipant participant) {
        this.participant = participant;
    }

    @PostMapping("/prepare")
    public ResponseEntity<TransactionMessage> prepare(@RequestBody TransactionMessage prepareMsg) {
        logger.info("POST /api/transaction/prepare - Transaction: {}",
                prepareMsg.getTransactionId());

        TransactionMessage response = participant.handlePrepare(prepareMsg);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/commit")
    public ResponseEntity<Void> commit(@RequestBody TransactionMessage commitMsg) {
        logger.info("POST /api/transaction/commit - Transaction: {}",
                commitMsg.getTransactionId());

        participant.handleCommit(commitMsg.getTransactionId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/abort")
    public ResponseEntity<Void> abort(@RequestBody TransactionMessage abortMsg) {
        logger.info("POST /api/transaction/abort - Transaction: {}",
                abortMsg.getTransactionId());

        participant.handleAbort(abortMsg.getTransactionId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{txnId}/status")
    public ResponseEntity<Object> getStatus(@PathVariable String txnId) {
        return participant.getTransaction(txnId)
                .map(txn -> ResponseEntity.ok((Object) txn))
                .orElse(ResponseEntity.notFound().build());
    }
}
