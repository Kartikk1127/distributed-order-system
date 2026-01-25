package com.twopc.common.protocol;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class TransactionMessage {
    private String transactionId;
    private MessageType messageType;
    private LocalDateTime timestamp;
    private Map<String, Object> payload;
    private String senderId;
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
        msg.senderId = senderId;
        msg.payload = operationData;
        return msg;
    }

    public static TransactionMessage voteYes(String transactionId, String senderId) {
        TransactionMessage msg = new TransactionMessage(transactionId, MessageType.VOTE_YES);
        msg.senderId = senderId;
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
        return "TransactionMessage{" +
                "transactionId='" + transactionId + '\'' +
                ", messageType=" + messageType +
                ", timestamp=" + timestamp +
                ", payload=" + payload +
                ", senderId='" + senderId + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
