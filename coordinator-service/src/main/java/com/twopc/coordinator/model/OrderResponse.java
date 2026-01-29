package com.twopc.coordinator.model;

// response sent back to client after processing order
public class OrderResponse {
    private String orderId;
    private String transactionId;
    private String status; // COMMITTED or ABORTED
    private String message;

    public OrderResponse() {
    }

    public OrderResponse(String orderId, String transactionId, String status, String message) {
        this.orderId = orderId;
        this.transactionId = transactionId;
        this.status = status;
        this.message = message;
    }

    public static OrderResponse success(String orderId, String transactionId) {
        return new OrderResponse(orderId, transactionId, "COMMITTED", "Order processed successfully");
    }

    public static OrderResponse failure(String orderId, String transactionId, String reason) {
        return new OrderResponse(orderId, transactionId, "ABORTED", "Order failed: " + reason);
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
