package com.twopc.common.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class TransactionMessage {
    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("messageType")
    private MessageType messageType;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    @JsonProperty("payload")
    private Map<String, Object> payload;

    @JsonProperty("senderId")
    private String senderId;

    @JsonProperty("reason")
    private String reason;

    public TransactionMessage() {
        this.timestamp = LocalDateTime.now();
        this.payload = new HashMap<>();
    }

    public TransactionMessage(String transactionId, MessageType messageType) {
        this();
        this.timestamp = LocalDateTime.now();
        this.payload = new HashMap<>();
    }

    // factory methods for creating messages
    public static TransactionMessage prepare(String transactionId, String senderId, Map<String, Object> operationData) {
        TransactionMessage msg = new TransactionMessage(transactionId, MessageType.PREPARE);
        msg.transactionId = transactionId;
        msg.senderId = senderId;
        msg.payload = operationData;
        return msg;
    }

    public static TransactionMessage voteYes(String transactionId, String senderId, Map<String, Object> payload) {
        TransactionMessage msg = new TransactionMessage(transactionId, MessageType.VOTE_YES);
        msg.transactionId = transactionId;
        msg.messageType = MessageType.VOTE_YES;
        msg.senderId = senderId;
        msg.payload = payload;
        return msg;
    }

    public static TransactionMessage voteNo(String transactionId, String senderId, String reason) {
        TransactionMessage msg = new TransactionMessage(transactionId, MessageType.VOTE_NO);
        msg.senderId = senderId;
        msg.reason = reason;
        return msg;
    }

    public static TransactionMessage commit(String transactionId, String senderId) {
        TransactionMessage msg = new TransactionMessage(transactionId, MessageType.COMMIT);
        msg.transactionId = transactionId;
        msg.senderId = senderId;
        return msg;
    }

    public static TransactionMessage abort(String transactionId, String senderId, String reason) {
        TransactionMessage msg = new TransactionMessage(transactionId, MessageType.ABORT);
        msg.senderId = senderId;
        msg.reason = reason;
        return msg;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return String.format("TransactionMessage{txnId=%s, type=%s, sender=%s, reason=%s", transactionId, messageType, senderId, reason);
    }
}
