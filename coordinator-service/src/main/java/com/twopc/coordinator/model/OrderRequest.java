package com.twopc.coordinator.model;

// incoming order request from client
public class OrderRequest {
    private String orderId;
    private String customerId;
    private String productId;
    private int quantity;
    private double amount;

    public OrderRequest() {
    }

    public OrderRequest(String orderId, String customerId, String productId, int quantity, double amount) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return String.format("OrderRequest{orderId=%s, customerId=%s, productId=%s, quantity=%d, amount=%.2f",
                orderId, customerId, productId, quantity, amount);
    }
}
