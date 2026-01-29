# Two-Phase Commit (2PC) Protocol

## Project Overview

Built a complete distributed transaction system implementing the Two-Phase Commit (2PC) protocol with:
- **Coordinator Service** (Port 8080) - Orchestrates distributed transactions
- **Inventory Service** (Port 8081) - Participant managing product inventory
- **Payment Service** (Port 8082) - Participant managing customer payments

**Use Case**: Process customer orders atomically across inventory and payment systems.

---

## Table of Contents
1. [Core Concepts](#core-concepts)
2. [Architecture & Design](#architecture--design)
3. [Implementation Details](#implementation-details)
4. [Critical Insights](#critical-insights)
5. [Common Pitfalls & Solutions](#common-pitfalls--solutions)
6. [Testing & Scenarios](#testing--scenarios)
7. [Limitations of 2PC](#limitations-of-2pc)

---

## Core Concepts

### What is Two-Phase Commit?

2PC is a distributed algorithm that ensures **atomic commitment** across multiple services:
- Either ALL services commit the transaction, OR
- ALL services abort the transaction
- NO partial commits allowed

### The Two Phases

#### Phase 1: PREPARE (Voting Phase)
```
Coordinator → Participants: "Can you commit?"
Participants → Coordinator: "YES" or "NO"
```

**Participant Actions:**
1. Check if operation is possible (sufficient resources)
2. Lock required resources
3. Log PREPARED state to WAL (Write-Ahead Log)
4. Vote YES (promise to commit) or NO (cannot commit)

**Key Point:** Participants do NOT apply changes yet, just prepare and promise.

#### Phase 2: COMMIT/ABORT (Decision Phase)
```
If all voted YES:
    Coordinator → Participants: "COMMIT"
    Participants apply changes, release locks
Else:
    Coordinator → Participants: "ABORT"
    Participants release locks without applying changes
```

**Coordinator Actions:**
1. Collect all votes
2. Make decision (COMMIT if all YES, ABORT if any NO)
3. Log decision to WAL **BEFORE** sending to participants (critical!)
4. Send decision to all participants
5. Transaction complete

---

## Architecture & Design

### State Machines

#### Coordinator State Machine
```
INIT
  ↓ (send PREPARE)
PREPARING (waiting for votes)
  ↓
  ├─ All YES → COMMITTED
  └─ Any NO  → ABORTED
```

#### Participant State Machine
```
INIT
  ↓ (receive PREPARE, lock resources)
PREPARED (uncertain state - waiting for decision)
  ↓
  ├─ Receive COMMIT → COMMITTED
  └─ Receive ABORT  → ABORTED
```

### Message Flow

```
Client → Coordinator: Order Request
Coordinator → Inventory: PREPARE {productId, quantity}
Coordinator → Payment: PREPARE {customerId, amount}
Inventory → Coordinator: VOTE_YES
Payment → Coordinator: VOTE_YES
Coordinator → Inventory: COMMIT
Coordinator → Payment: COMMIT
Coordinator → Client: Success
```

### Project Structure

```
2pc-distributed-order-system/
├── common/                      # Shared components
│   ├── protocol/                # TransactionState, MessageType, TransactionMessage
│   ├── model/                   # Transaction, Product
│   └── log/                     # WriteAheadLog interface & FileBasedWAL
├── coordinator-service/         # Orchestrator (Port 8080)
│   ├── controller/              # REST endpoints
│   ├── service/                 # TransactionCoordinator, ParticipantClient
│   └── model/                   # OrderRequest, OrderResponse
├── inventory-service/           # Participant (Port 8081)
│   ├── controller/              # ParticipantController, AdminController
│   ├── service/                 # TransactionParticipant, ResourceLockManager
│   └── storage/                 # InventoryStore
└── payment-service/             # Participant (Port 8082)
    ├── controller/              # ParticipantController, AdminController
    ├── service/                 # TransactionParticipant, ResourceLockManager
    └── storage/                 # PaymentStore
```

---

## Implementation Details

### 1. Write-Ahead Log (WAL)

**Purpose:** Enable crash recovery by persisting transaction state to disk.

**Format:** JSON, one transaction per line (append-only)
```json
{"transactionId":"TXN-001","state":"PREPARED","timestamp":"2025-01-25T10:30:00",...}
{"transactionId":"TXN-001","state":"COMMITTED","timestamp":"2025-01-25T10:30:05",...}
```

**Critical Logging Points:**

| Who | When | Why |
|-----|------|-----|
| **Participant** | BEFORE voting YES | Promises to commit - must survive crash |
| **Coordinator** | BEFORE sending COMMIT | Decision must survive crash to replay |

**Thread Safety:**
- Uses `ReadWriteLock` (multiple readers, exclusive writer)
- Atomic file operations (write to temp file, then atomic move)

**Code Pattern:**
```java
// Participant MUST log before voting YES
transaction.setState(TransactionState.PREPARED);
wal.writeLog(transaction);  // ← CRITICAL
return TransactionMessage.voteYes(txnId, serviceId);

// Coordinator MUST log before sending COMMIT
transaction.setState(TransactionState.COMMITTED);
wal.writeLog(transaction);  // ← CRITICAL
sendCommitToAll();
```

---

### 2. Resource Locking

**Purpose:** Prevent concurrent transactions from modifying the same resources.

**Simplified Design (Used in Payment Service):**
```java
// One resource, one owner
Map<String, String> resourceLocks;  // resourceId → transactionId
Map<String, String> transactionResource;  // transactionId → resourceId
```

**Why Synchronized Methods:**
```java
public synchronized boolean acquireLock(String txnId, String resourceId) {
    // Only ONE thread can execute this at a time
    // Prevents race conditions when checking/acquiring locks
}
```

**Two Types of "Locks":**
1. **Java synchronized lock** - Prevents concurrent method execution (thread-level)
2. **Resource locks** - Tracks which transaction owns which resource (business-level)

**Lock Lifecycle:**
```
PREPARE Phase:
  - Acquire lock
  - If already locked by different transaction → Vote NO
  
COMMIT/ABORT Phase:
  - Release lock
  - Allow other transactions to proceed
```

---

### 3. Transaction State Management

**State Validation:**
```java
public boolean canTransitionTo(TransactionState newState) {
    switch (this) {
        case INIT:
            return newState == PREPARING || newState == PREPARED || newState == ABORTED;
        case PREPARING:
        case PREPARED:
            return newState == COMMITTED || newState == ABORTED;
        case COMMITTED:
        case ABORTED:
            return false; // Terminal states
    }
}
```

**Why Validation Matters:**
- Prevents invalid state transitions
- Catches bugs early (e.g., trying to transition from COMMITTED back to INIT)
- Documents expected flow

**Thread Safety:**
```java
public synchronized void setState(TransactionState newState) {
    if (this.state.canTransitionTo(newState)) {
        this.state = newState;
        this.updatedAt = LocalDateTime.now();
    } else {
        throw new IllegalStateException("Invalid transition");
    }
}
```

---

### 4. Vote Aggregation (Coordinator)

**Collecting Votes:**
```java
Map<String, String> participantVotes = new ConcurrentHashMap<>();

// Record each participant's vote
transaction.recordVote(participantUrl, vote);  // "YES" or "NO"

// Decision logic
if (transaction.allParticipantsVotedYes()) {
    // All voted YES → COMMIT
} else {
    // Any voted NO → ABORT
}
```

**Thread-Safe Implementation:**
- Uses `ConcurrentHashMap` for vote storage
- `synchronized` methods for vote checking
- Prevents race conditions during vote collection

---

### 5. HTTP Communication (Coordinator ↔ Participants)

**ParticipantClient Pattern:**
```java
public TransactionMessage sendPrepare(String participantUrl, String txnId, Map<String, Object> data) {
    try {
        // 1. Create message
        TransactionMessage msg = TransactionMessage.prepare(txnId, "coordinator", data);
        
        // 2. Serialize to JSON
        String json = objectMapper.writeValueAsString(msg);
        
        // 3. Send HTTP POST
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(participantUrl + "/api/transaction/prepare"))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, ...);
        
        // 4. Deserialize response
        return objectMapper.readValue(response.body(), TransactionMessage.class);
        
    } catch (Exception e) {
        // Communication failure = Vote NO
        return TransactionMessage.voteNo(txnId, participantUrl, "Error: " + e.getMessage());
    }
}
```

**Error Handling:**
- Network failures treated as NO votes
- Timeouts trigger ABORT
- Retries can be added for COMMIT/ABORT messages (idempotent)

---

## Critical Insights

### 1. The "Uncertain" State (PREPARED)

**The Problem:**
When a participant votes YES and enters PREPARED state:
- Resources are locked
- Waiting for coordinator's decision
- **If coordinator crashes now → Participant is BLOCKED indefinitely!**

This is called the **blocking problem** of 2PC.

**Why It Happens:**
```
Participant: "I voted YES, locked resources, waiting for decision..."
Coordinator: *crashes*
Participant: "I can't commit on my own (others might have voted NO)"
Participant: "I can't abort on my own (coordinator might have decided COMMIT)"
Participant: "I'm stuck... resources remain locked... system is blocked!"
```

**Real-World Impact:**
- Resources unavailable to other transactions
- System throughput degraded
- Manual intervention may be needed

**Mitigation:**
- Recovery mechanisms (read WAL, complete transaction)
- Timeout with coordinator query
- Three-Phase Commit (3PC) eliminates this but adds complexity

---

### 2. Why Log BEFORE Sending?

**Coordinator Crash Scenarios:**

**Scenario A - Logged BEFORE sending:**
```
1. Coordinator decides COMMIT
2. Logs COMMIT to WAL ✓
3. *CRASHES*
4. Recovery: Reads WAL → sees COMMIT → resends to participants ✓
Result: Transaction completes successfully
```

**Scenario B - Sent BEFORE logging:**
```
1. Coordinator decides COMMIT
2. Sends COMMIT to Inventory ✓
3. *CRASHES*
4. Recovery: No log entry → doesn't know decision was COMMIT
5. Inventory committed, Payment doesn't know → INCONSISTENT! ✗
Result: Data corruption - some participants committed, others didn't
```

**The Rule:** Log the decision BEFORE sending to ANY participant.

**Participant Logging:**
Similarly, participants MUST log PREPARED before voting YES:
- If they crash after voting but before logging, they forgot their promise
- Recovery won't know they need to commit

---

### 3. PREPARE ≠ Apply Changes

**Common Misconception:**
"PREPARE phase should deduct inventory/payment"

**Correct Understanding:**
```
PREPARE Phase:
  ✓ Check if operation is possible
  ✓ Lock resources
  ✗ Do NOT apply changes yet

COMMIT Phase:
  ✓ Now apply the actual changes
  ✓ Release locks
```

**Why This Design:**
- PREPARE is reversible (just unlock)
- COMMIT is irreversible (changes applied)
- If any participant can't prepare → abort is easy (no changes to undo)
- Clean separation of concerns

**In Our Code:**
```java
// PREPARE - Check and lock only
if (!inventoryStore.hasAvailableQuantity(productId, quantity)) {
    return VOTE_NO;
}
lockManager.acquireLock(txnId, resourceId);  // Just lock, don't change inventory
return VOTE_YES;

// COMMIT - Actually apply changes
inventoryStore.reserveInventory(productId, quantity);  // Now modify inventory
lockManager.releaseLocks(txnId);
```

---

### 4. Idempotency of Messages

**Problem:** Network issues can cause duplicate messages.

**Solution:** Make operations idempotent.

**Example:**
```java
// Participant receives duplicate PREPARE
if (lockManager.alreadyHoldsLock(txnId, resourceId)) {
    // Already locked by this transaction
    return VOTE_YES;  // Safe to vote YES again
}
```

**In Our Implementation:**
```java
public synchronized boolean acquireLock(String txnId, String resourceId) {
    String currentOwner = resourceLocks.get(resourceId);
    
    // If already locked by THIS transaction, it's idempotent
    if (currentOwner != null && currentOwner.equals(txnId)) {
        return true;  // Already have the lock
    }
    
    // ... rest of logic
}
```

---

### 5. Concurrency Control

**Challenge:** Multiple orders might want the same product simultaneously.

**Solution:** Resource-level locking (not service-level).

**Example:**
```
Transaction A: Wants LAPTOP-001 × 2
Transaction B: Wants PHONE-001 × 1
Transaction C: Wants LAPTOP-001 × 1

A and B can proceed concurrently (different products)
A and C cannot (same product) - one must wait
```

**Implementation:**
```java
// Lock at product granularity
String resourceId = productId;  // Not service-wide lock
if (!lockManager.acquireLock(txnId, resourceId)) {
    return VOTE_NO;  // Resource already locked by another transaction
}
```

---

## Common Pitfalls & Solutions

### 1. State Transition Validation

**Problem:**
```java
Transaction txn = new Transaction(txnId);  // Constructor sets state to INIT
txn.setState(TransactionState.INIT);  // Trying to set INIT again
// ERROR: Invalid transition from INIT to INIT
```

**Solution:**
```java
Transaction txn = new Transaction(txnId);  // Already in INIT state
// Don't set INIT again, just use it
```

**Lesson:** Constructor already initializes state - don't redundantly set it.

---

### 2. Over-Engineering Resource Locks

**Initial Design (Over-Complicated):**
```java
Map<String, Set<String>> resourceLocks;  // Why a Set? One owner only!
ConcurrentHashMap + synchronized methods  // Redundant thread-safety
```

**Simplified Design:**
```java
Map<String, String> resourceLocks;  // One resource, one owner
HashMap + synchronized methods  // Simple and sufficient
```

**Lesson:** Don't use `ConcurrentHashMap` if all access is within `synchronized` methods. Simple `HashMap` is sufficient and clearer.

---

### 3. JSON Serialization Issues

**Problem:** ObjectMapper not configured properly caused null fields.

**Solution:**
```java
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.registerModule(new JavaTimeModule());  // For LocalDateTime
objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

**Lesson:** When using Jackson manually (not Spring auto-config), explicitly register modules for types like `LocalDateTime`.

---

### 4. Forgetting to Log Before Actions

**Dangerous Pattern:**
```java
sendCommit(participant);  // Send first
wal.writeLog(transaction);  // Log second - WRONG!
```

**Correct Pattern:**
```java
wal.writeLog(transaction);  // Log first - CRITICAL!
sendCommit(participant);  // Then send
```

**Lesson:** Always log durable decisions BEFORE communicating them. The log is the source of truth for recovery.

---

### 5. Not Handling Refunds in ABORT

**Question:** Should we refund in ABORT?

**Answer:** NO, because we never deducted in the first place!

**Flow:**
```
PREPARE: Check balance, lock account (NO deduction)
COMMIT: Actually deduct money
ABORT: Release lock (nothing to refund, we never deducted)
```

**Lesson:** In optimistic 2PC, PREPARE is non-destructive. Only COMMIT applies actual changes.

---

## Testing & Scenarios

### Happy Path
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "orderId": "ORD-001",
    "customerId": "CUST-001",
    "productId": "LAPTOP-001",
    "quantity": 2,
    "amount": 2999.98
  }'
```

**Expected:**
- Inventory: 10 → 8 laptops
- Payment: $5000 → $2000.02
- Status: COMMITTED

---

### Insufficient Inventory
```bash
curl -X POST http://localhost:8080/api/orders \
  -d '{
    "productId": "LAPTOP-001",
    "quantity": 100,  # Only 10 available
    ...
  }'
```

**Expected:**
- Inventory votes NO
- Payment never deducts (ABORT)
- Status: ABORTED
- Reason: "Insufficient inventory"

---

### Insufficient Balance
```bash
curl -X POST http://localhost:8080/api/orders \
  -d '{
    "amount": 10000.00,  # Customer only has $5000
    ...
  }'
```

**Expected:**
- Payment votes NO
- Inventory unlocks (ABORT)
- Status: ABORTED
- Reason: "Insufficient balance"

---

### Concurrent Transactions (Same Product)

**Terminal 1:**
```bash
# Order 2 LAPTOP-001
curl -X POST http://localhost:8080/api/orders -d '{...}'
```

**Terminal 2 (immediately):**
```bash
# Order 3 more LAPTOP-001 (only 10 total)
curl -X POST http://localhost:8080/api/orders -d '{...}'
```

**Expected:**
- First transaction locks LAPTOP-001
- Second transaction cannot acquire lock → votes NO
- First transaction commits: 10 → 8 laptops
- Second transaction aborts

---

### Idempotent PREPARE

Send PREPARE 10 times for same transaction:
```bash
for i in {1..10}; do
  curl -X POST http://localhost:8081/api/transaction/prepare -d '{...}'
done
```

**Expected:**
- First PREPARE: Lock acquired, vote YES
- Subsequent PREPAREs: Already locked by same transaction, vote YES again
- No errors, graceful handling

---

## Limitations of 2PC

### 1. Blocking Problem
- Participants in PREPARED state can be blocked indefinitely if coordinator crashes
- Resources remain locked, reducing system throughput
- Requires manual intervention or recovery mechanisms

### 2. Single Point of Failure
- Coordinator is critical - if it crashes, system halts
- Mitigated by: Coordinator replication, recovery mechanisms

### 3. Synchronous & Slow
- All participants must respond before proceeding
- Slowest participant determines transaction latency
- Not suitable for high-throughput systems

### 4. No Partition Tolerance
- Network partitions can cause indefinite blocking
- CAP theorem: 2PC chooses Consistency over Availability
- In partition: System blocks rather than making progress

### 5. Scalability Challenges
- Adding more participants increases coordination overhead
- More participants = higher chance of failure/timeout
- Coordinator becomes bottleneck

---

## Alternatives to 2PC

### Saga Pattern
- Long-running transactions with compensating actions
- Each service commits locally, compensation if needed
- Example: Reserve inventory → Charge payment → If payment fails, release inventory
- Trade-off: Eventual consistency, more complex error handling

### Three-Phase Commit (3PC)
- Adds PRECOMMIT phase between PREPARE and COMMIT
- Non-blocking in some failure scenarios
- Trade-off: More messages, higher latency, still has edge cases

### Event Sourcing + CQRS
- Store events instead of state
- Eventual consistency through event replay
- Trade-off: Complexity, eventual consistency

---

## Key Takeaways

1. **2PC guarantees atomicity** across distributed services - all commit or all abort
2. **Write-Ahead Logging is critical** for crash recovery - log before sending
3. **PREPARED state is uncertain** - participants are blocked waiting for decision
4. **Resource locking prevents conflicts** - but adds contention in high-concurrency scenarios
5. **PREPARE phase is non-destructive** - only COMMIT applies actual changes
6. **Coordinator is single point of failure** - needs recovery mechanisms
7. **2PC is blocking and slow** - not suitable for all use cases
8. **Thread safety requires careful design** - synchronized methods, proper lock granularity
9. **Idempotency matters** - network can duplicate messages
10. **Testing failure scenarios is essential** - happy path is easy, edge cases reveal bugs

---

## Technologies & Patterns Used

### Technologies
- **Java 17** - Modern Java features (switch expressions, records candidate)
- **Spring Boot 3.2** - REST APIs, dependency injection, auto-configuration
- **Gradle** - Multi-module build system
- **Jackson** - JSON serialization/deserialization
- **Java HttpClient** - HTTP communication between services
- **SLF4J + Logback** - Logging

### Design Patterns
- **State Machine Pattern** - Transaction state management
- **Write-Ahead Logging** - Durability and crash recovery
- **Command Pattern** - TransactionMessage as commands
- **Template Method** - Common participant logic
- **Repository Pattern** - InventoryStore, PaymentStore
- **Client Pattern** - ParticipantClient for HTTP communication

### Concurrency Patterns
- **Pessimistic Locking** - Lock resources during PREPARE
- **synchronized** - Java thread synchronization
- **ConcurrentHashMap** - Thread-safe vote collection
- **ReadWriteLock** - WAL concurrency control

---

## Project Metrics

- **Lines of Code**: ~2500 (excluding tests)
- **Modules**: 4 (common, coordinator, inventory, payment)
- **Classes**: ~25
- **Services**: 3 (coordinator + 2 participants)
- **REST Endpoints**: ~15
- **Transaction States**: 5
- **Message Types**: 7

---

## Further Learning

### Next Steps
1. **Implement Recovery** - Handle coordinator/participant crashes
2. **Add Timeouts** - PREPARE phase timeout, participant query timeout
3. **Concurrent Load Testing** - Stress test with 100+ concurrent orders
4. **Metrics & Monitoring** - Transaction success rate, latency, lock contention
5. **Distributed Tracing** - Track transactions across services

### Advanced Topics
- **Paxos/Raft** - Consensus algorithms for coordinator replication
- **Saga Pattern** - Alternative to 2PC with compensations
- **Event Sourcing** - Store events instead of state
- **CQRS** - Command Query Responsibility Segregation
- **Distributed Locks** - Redis, Zookeeper for coordination

### Related Reading
- "Designing Data-Intensive Applications" by Martin Kleppmann
- "Database Internals" by Alex Petrov
- "Distributed Systems" by Maarten van Steen & Andrew Tanenbaum

---

## Conclusion

This project provided hands-on experience with:
- Distributed transaction coordination
- Crash recovery mechanisms
- Concurrency control
- Network communication patterns
- Real-world trade-offs in distributed systems

**Key Insight:** 2PC is theoretically sound but practically challenging. Understanding its limitations helps in choosing the right consistency model for your use case.

**Remember:** In distributed systems, there are no perfect solutions - only trade-offs. 2PC trades availability and performance for strong consistency.

---