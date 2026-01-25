package com.twopc.common.protocol;

// represents the state of a transaction in the two-phase commit protocol
// state transactions:
// coordinator: INIT -> PREPARING -> (COMMITTED | ABORTED)
// Participant: INIT -> PREPARED -> (COMMITTED | ABORTED)
public enum TransactionState {
    INIT,
    PREPARING, // coordinator waiting for votes
    PREPARED, // participant voted yes, waiting for decision (uncertain state)
    COMMITTED, // terminal state
    ABORTED; // terminal state cannot transition into anything else

    public boolean isTerminalState() {
        return this == COMMITTED || this == ABORTED;
    }

    public boolean canTransitionInto(TransactionState newState) {
        return switch (this) {
            case INIT -> newState == PREPARING || newState == ABORTED || newState == PREPARED;
            case PREPARING, PREPARED -> newState == COMMITTED || newState == ABORTED;
            default -> false;
        };
    }
}
