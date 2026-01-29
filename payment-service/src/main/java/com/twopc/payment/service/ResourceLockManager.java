package com.twopc.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

// manages resource locks for transactions
// Prevents concurrent transactions from modifying the same accounts
@Component
public class ResourceLockManager {
    private static final Logger logger = LoggerFactory.getLogger(ResourceLockManager.class);

    private final Map<String, String> resourceLocks = new HashMap<>();

    private final Map<String, String> transactionResources = new HashMap<>();

    // try to acquire lock on a customer account for a transaction
    public synchronized boolean acquireLock(String transactionId, String resourceId) {
        String currentOwner = resourceLocks.get(resourceId);

        if (currentOwner != null && !currentOwner.equals(transactionId)) {
            logger.warn("Resource {} already locked by transaction {}. Cannot lock for transaction {}", resourceId, currentOwner, transactionId);
            return false;
        }

        if (currentOwner != null && currentOwner.equals(transactionId)) {
            logger.debug("Transaction {} already holds lock on resource {}", transactionId, resourceId);
            return true;
        }

        resourceLocks.put(resourceId, transactionId);
        transactionResources.put(transactionId, resourceId);

        logger.info("Transaction {} acquired lock on resource {}", transactionId, resourceId);
        return true;
    }

    // release the lock held by a transaction
    public synchronized void releaseLocks(String transactionId) {
        String lockedResources = transactionResources.remove(transactionId);

        if (lockedResources == null) {
            logger.debug("No lock to release for transaction {}", transactionId);
            return;
        }

        resourceLocks.remove(lockedResources);
        logger.info("Released lock for transaction {}: {}", transactionId, lockedResources);
    }

    // check if a resource is currently locked
    public synchronized boolean isLocked(String resourceId) {
        return resourceLocks.containsKey(resourceId);
    }

    // get the transaction that owns a lock on resource
    public synchronized String getLockOwner(String resourceId) {
        return resourceLocks.get(resourceId);
    }

    // get all locked resources
    public synchronized Map<String, String> getResourceLocks() {
        return new HashMap<>(resourceLocks);
    }
}
