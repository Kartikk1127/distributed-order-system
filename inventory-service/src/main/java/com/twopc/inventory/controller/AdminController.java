package com.twopc.inventory.controller;

import com.twopc.inventory.service.ResourceLockManager;
import com.twopc.inventory.storage.InventoryStore;
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

    private final InventoryStore inventoryStore;
    private final ResourceLockManager lockManager;

    public AdminController(InventoryStore inventoryStore, ResourceLockManager lockManager) {
        this.inventoryStore = inventoryStore;
        this.lockManager = lockManager;
    }

    /**
     * Get all products and their current inventory.
     */
    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Object>> getInventory() {
        return ResponseEntity.ok(Map.of(
                "products", inventoryStore.getAllProducts()
        ));
    }

    /**
     * Get all currently locked resources.
     */
    @GetMapping("/locks")
    public ResponseEntity<Map<String, Object>> getLocks() {
        return ResponseEntity.ok(Map.of(
                "lockedResources", lockManager.getLockedResources()
        ));
    }

    /**
     * Simulate a crash (for testing recovery).
     * In a real scenario, this would terminate the process.
     */
    @PostMapping("/crash")
    public ResponseEntity<Map<String, String>> crash() {
        logger.warn("CRASH endpoint called - simulating service crash");

        // In a real crash test, you'd call System.exit(1)
        // For now, just log it
        return ResponseEntity.ok(Map.of(
                "status", "crash simulation not implemented yet",
                "message", "Will be implemented in Phase 4 with recovery"
        ));
    }

    /**
     * Health check.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "inventory-service"
        ));
    }
}
