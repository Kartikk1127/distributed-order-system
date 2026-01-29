package com.twopc.payment.config;

import com.twopc.payment.model.Account;
import com.twopc.payment.storage.PaymentStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentConfig {
    private List<AccountConfig> accounts = new ArrayList<>();

    public static class AccountConfig {
        private String customerId;
        private String name;
        private double balance;

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
    }

    public List<AccountConfig> getAccounts() {
        return accounts;
    }

    @Bean
    public CommandLineRunner initializeAccounts(PaymentStore paymentStore) {
        return args -> {
            List<Account> initialAccounts = new ArrayList<>();

            for (AccountConfig accountConfig : accounts) {
                initialAccounts.add(new Account(accountConfig.customerId, accountConfig.name, accountConfig.balance));
            }
            paymentStore.initializeAccounts(initialAccounts);
        };
    }
}
