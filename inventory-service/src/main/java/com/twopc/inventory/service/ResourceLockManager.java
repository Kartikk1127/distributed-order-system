package com.twopc.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// manages resource locks for transactions
// prevents concurrent transactions from modifying the same resources.
@Component
public class ResourceLockManager {
    private static final Logger logger = LoggerFactory.getLogger(ResourceLockManager.class);

    // map: resourceId -> transaction id that has locked it
    private final Map<String, String> resourceLocks = new HashMap<>();

    // map: transactionId -> set of resource ids that have locked it
    private final Map<String, Set<String>> transactionResources = new HashMap<>();

    // try to acquire a lock on a resource for a transaction
    // return true if lock acquired, false if already locked by another transaction
    public synchronized boolean acquireLock(String transactionId, String resourceId) {
        String currentOwner = resourceLocks.get(resourceId);

        // check if resource is already locked by a different transaction
        if (currentOwner != null && !currentOwner.equals(transactionId)) {
            logger.warn("Resource {} already locked by transaction: {}. Cannot lock for {}",
                    resourceId, currentOwner, transactionId);
            return false;
        }

        if (currentOwner != null) {
            logger.debug("Transaction {} already holds lock on resource {}", transactionId, resourceId);
            return true;
        }

        //acquire the lock
        resourceLocks.put(resourceId, transactionId);
        // track what this transaction has locked
        transactionResources.computeIfAbsent(transactionId, k -> new HashSet<>())
                .add(resourceId);

        logger.info("Transaction {} acquired lock on resource {}", transactionId, resourceId);
        return true;
    }

    // release all locks held by a transaction
    public synchronized void releaseLocks(String transactionId) {
        Set<String> lockedResources = transactionResources.remove(transactionId);

        if (lockedResources == null || lockedResources.isEmpty()) {
            logger.debug("No locks to release for transaction {}", transactionId);
            return;
        }
        for (String resourceId : lockedResources) {
            resourceLocks.remove(resourceId);
        }

        logger.info("Released {} locks for transaction {}", lockedResources.size(), transactionId);
    }

    // check if a resource is currently locked
    public synchronized boolean isLocked(String resourceId) {
        return resourceLocks.containsKey(resourceId);
    }

    // get the transaction that owns the lock
    public synchronized String lockOwner(String resourceId) {
        return resourceLocks.get(resourceId);
    }

    // get all locked resources
    public Map<String, String> getLockedResources() {
        return new HashMap<>(resourceLocks);
    }
}
