package com.twopc.payment.storage;

import com.twopc.payment.model.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentStore {
    private static final Logger logger = LoggerFactory.getLogger(PaymentStore.class);

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    // initialize the store with accounts from configuration

    public void initializeAccounts(List<Account> initialAccounts) {
        for (Account account : initialAccounts) {
            accounts.put(account.getCustomerId(), account);
            logger.info("Initialized account: {}", account);
        }
    }

    // get an account from customer id
    public Optional<Account> getAccount(String customerId) {
        return Optional.ofNullable(accounts.get(customerId));
    }

    // check if account has sufficient balance
    public boolean hasSufficientBalance(String customerId, double amount) {
        Account account = accounts.get(customerId);
        if (account == null) {
            logger.warn("Account not found for customerId: {}", customerId);
            return false;
        }
        return account.getBalance() >= amount;
    }

    // deduct amount from account(decrease balance)
    // called during COMMIT phase
    public synchronized void deductAmount(String customerId, double amount) {
        Account account = accounts.get(customerId);

        if (account == null) {
            throw new IllegalStateException("Account not found for customerId: " + customerId);
        }
        if (!hasSufficientBalance(customerId, amount)) {
            throw new IllegalStateException(
                    String.format("Insufficient balance for %s. Available: %.2f, Requested: %.2f", customerId, account.getBalance(), amount)
            );
        }
        account.setBalance(account.getBalance() - amount);
        logger.info("Deducted {} from {}. New balance: {}",
                amount, customerId, account.getBalance());
    }

    // refund amount to account (increase balance)
    // called during ABORT phase
    public synchronized void refundAmount(String customerId, double amount) {
        Account account = accounts.get(customerId);

        if (account == null) {
            throw new IllegalStateException("Account not found for customerId: " + customerId);
        }

        account.setBalance(account.getBalance() + amount);
        logger.info("Refunded {} to {}. Current balance: {}",
                amount, customerId, account.getBalance());
    }

    // get all accounts (for admin/debugging)
    public Map<String, Account> getAllAccounts() {
        return new ConcurrentHashMap<>(accounts);
    }
}
