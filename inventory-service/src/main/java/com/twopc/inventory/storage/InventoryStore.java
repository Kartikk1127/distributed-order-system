package com.twopc.inventory.storage;

// in-memory storage for product inventory
// thread-safe using concurrent hashmap

import com.twopc.common.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InventoryStore {
    private static final Logger logger = LoggerFactory.getLogger(InventoryStore.class);

    private final Map<String, Product> products = new ConcurrentHashMap<>();

    public void initializeProducts(List<Product> initialProducts) {
        for (Product product : initialProducts) {
            products.put(product.getProductId(), product);
            logger.info("Initialized product: {}", product);
        }
    }

    // get a product by id
    public Optional<Product> getProduct(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    // check if sufficient quantity available
    public boolean hasAvailableQuantity(String productId, int requestedQuantity) {
        Product product = products.get(productId);
        if (product == null) {
            logger.warn("Product not found: {}", productId);
            return false;
        }
        return product.getQuantity() >= requestedQuantity;
    }

    // reserve inventory (decrease quantity)
    // called during commit phase
    public synchronized void reserveInventory(String productId, int quantity) {
        Product product = products.get(productId);
        if (product == null) {
            throw new IllegalStateException("Product not found: " + productId);
        }

        if (product.getQuantity() < quantity) {
            throw new IllegalStateException(
                    String.format("Insufficient inventory for %s. Available: %d, Requested: %d",
                            productId, product.getQuantity(), quantity)
            );
        }

        product.setQuantity(product.getQuantity() - quantity);
        logger.info("Reserved {} units of {}. Remaining: {}", quantity, productId, product.getQuantity());
    }

    // release inventory (increase quantity back)
    // called during ABORT phase
    public synchronized void releaseInventory(String productId, int quantity) {
        Product product = products.get(productId);
        if (product == null) {
            throw new IllegalStateException("Product not found: " + productId);
        }

        product.setQuantity(product.getQuantity() + quantity);
        logger.info("Released {} units of {}. New Quantity: {}", quantity, productId, product.getQuantity());
    }

    // get all products (for admin/debugging)
    public Map<String, Product> getAllProducts() {
        return new ConcurrentHashMap<>(products);
    }
}
