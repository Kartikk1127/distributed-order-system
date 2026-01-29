package com.twopc.coordinator.controller;

import com.twopc.common.log.FileBasedWAL;
import com.twopc.common.log.WriteAheadLog;
import com.twopc.common.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for debugging and monitoring.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final WriteAheadLog wal;

    public AdminController(@Value("${coordinator.wal.base-dir}") String walBaseDir) {
        this.wal = new FileBasedWAL("coordinator-service", walBaseDir);
    }

    /**
     * Get all transactions from the WAL.
     */
    @GetMapping("/transactions")
    public ResponseEntity<Map<String, Object>> getAllTransactions() {
        List<Transaction> transactions = wal.readAllLogs();

        return ResponseEntity.ok(Map.of(
                "count", transactions.size(),
                "transactions", transactions
        ));
    }

    /**
     * Get transaction logs by state.
     */
    @GetMapping("/transactions/state/{state}")
    public ResponseEntity<Map<String, Object>> getTransactionsByState(@PathVariable String state) {
        try {
            com.twopc.common.protocol.TransactionState txnState =
                    com.twopc.common.protocol.TransactionState.valueOf(state.toUpperCase());

            List<Transaction> transactions = wal.readLogsByState(txnState);

            return ResponseEntity.ok(Map.of(
                    "state", state,
                    "count", transactions.size(),
                    "transactions", transactions
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid state: " + state,
                    "validStates", "INIT, PREPARING, PREPARED, COMMITTED, ABORTED"
            ));
        }
    }

    /**
     * Get the WAL file path.
     */
    @GetMapping("/wal/path")
    public ResponseEntity<Map<String, String>> getWalPath() {
        return ResponseEntity.ok(Map.of(
                "path", wal.getLogFilePath()
        ));
    }

    /**
     * Health check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "coordinator-service"
        ));
    }

    /**
     * Simulate a crash (for testing recovery).
     */
    @PostMapping("/crash")
    public ResponseEntity<Map<String, String>> crash() {
        logger.warn("CRASH endpoint called - simulating service crash");

        return ResponseEntity.ok(Map.of(
                "status", "crash simulation not implemented yet",
                "message", "Will be implemented in Phase 4 with recovery"
        ));
    }
}
