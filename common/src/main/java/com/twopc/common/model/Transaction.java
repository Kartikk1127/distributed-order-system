package com.twopc.common.model;

import com.twopc.common.protocol.TransactionState;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Transaction {
    private String transactionId;
    private TransactionState state;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<String> participants;
    private Map<String, String> participantsVote;
    private Map<String, Object> operationData;
    private List<String> lockedResources;

    public Transaction() {
        this.state = TransactionState.INIT;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.participants = new ArrayList<>();
        this.participantsVote = new HashMap<>();
        this.operationData = new HashMap<>();
        this.lockedResources = new ArrayList<>();
    }

    public Transaction(String transactionId) {
        this();
        this.transactionId = transactionId;
    }

    public synchronized void setState(TransactionState newState) {
        if (this.state.canTransitionInto(newState)) {
            this.state = newState;
            this.updatedAt = LocalDateTime.now();
        } else {
            throw new IllegalStateException(
                    String.format("Invalid state transition from %s to %s for transaction %s",
                            this.state, newState, this.transactionId)
            );
        }
    }

    public synchronized void recordVote(String participantUrl, String vote) {
        participantsVote.put(participantUrl, vote);
    }

    public synchronized boolean allParticipantsVotedYes() {
        if (participantsVote.size() != participants.size()) return false;
        return participantsVote.values().stream().allMatch("YES"::equals);
    }

    public synchronized boolean anyParticipantVotedNo() {
        return participantsVote.values().stream().anyMatch("NO"::equals);
    }

    public synchronized boolean allVotesReceived() {
        return participantsVote.size()==participants.size();
    }

    public void addParticipant(String participantUrl) {
        if (!this.participants.contains(participantUrl)) {
            this.participants.add(participantUrl);
        }
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public TransactionState getState() {
        return state;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<String> getParticipants() {
        return participants;
    }

    public void setParticipants(List<String> participants) {
        this.participants = participants;
    }

    public Map<String, String> getParticipantsVote() {
        return participantsVote;
    }

    public void setParticipantsVote(Map<String, String> participantsVote) {
        this.participantsVote = participantsVote;
    }

    public Map<String, Object> getOperationData() {
        return operationData;
    }

    public void setOperationData(Map<String, Object> operationData) {
        this.operationData = operationData;
    }

    public List<String> getLockedResources() {
        return lockedResources;
    }

    public void setLockedResources(List<String> lockedResources) {
        this.lockedResources = lockedResources;
    }
}
