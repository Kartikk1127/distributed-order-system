package com.twopc.payment.model;

public class Account {
    private String customerId;
    private String name;
    private double balance;

    public Account() {}

    public Account(String customerId, String name, double balance) {
        this.customerId = customerId;
        this.name = name;
        this.balance = balance;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    @Override
    public String toString() {
        return String.format("Account{customerId=%s, name=%s, balance=%.2f}", customerId, name, balance);
    }
}
