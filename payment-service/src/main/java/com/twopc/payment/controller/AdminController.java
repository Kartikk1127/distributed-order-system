package com.twopc.payment.controller;

import com.twopc.payment.service.ResourceLockManager;
import com.twopc.payment.storage.PaymentStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final PaymentStore paymentStore;
    private final ResourceLockManager lockManager;

    public AdminController(PaymentStore paymentStore, ResourceLockManager lockManager) {
        this.paymentStore = paymentStore;
        this.lockManager = lockManager;
    }

    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> getAccounts() {
        return ResponseEntity.ok(Map.of(
                "accounts", paymentStore.getAllAccounts()
        ));
    }

    @GetMapping("/locks")
    public ResponseEntity<Map<String, Object>> getLocks() {
        return ResponseEntity.ok(Map.of(
                "lockedResources", lockManager.getResourceLocks()
        ));
    }

    @PostMapping("/crash")
    public ResponseEntity<Map<String, String>> crash() {
        logger.warn("CRASH endpoint called - simulating service crash");
        return ResponseEntity.ok(Map.of(
                "status", "crash simulation not implemented yet",
                "message", "Will be implemented in Phase 4 with recovery"
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "payment-service"
        ));
    }
}
