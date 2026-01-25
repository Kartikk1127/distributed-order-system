package com.twopc.inventory.config;

import com.twopc.common.model.Product;
import com.twopc.inventory.storage.InventoryStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "inventory")
public class InventoryConfig {
    private List<ProductConfig> products = new ArrayList<>();

    public static class ProductConfig {
        private String id;
        private String name;
        private int quantity;
        private double price;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }
    }

    public List<ProductConfig> getProducts() {
        return products;
    }

    public void setProducts(List<ProductConfig> products) {
        this.products = products;
    }

    @Bean
    public CommandLineRunner initializeInventory(InventoryStore inventoryStore) {
        return args -> {
            List<Product> initialProducts = new ArrayList<>();
            for (ProductConfig config : products) {
                Product product = new Product(
                        config.id, config.name, config.quantity, config.price
                );
                initialProducts.add(product);
            }
            inventoryStore.initializeProducts(initialProducts);
        };
    }
}
