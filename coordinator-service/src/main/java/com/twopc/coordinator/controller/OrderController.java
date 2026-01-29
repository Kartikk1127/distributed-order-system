package com.twopc.coordinator.controller;

import com.twopc.common.model.Transaction;
import com.twopc.common.protocol.TransactionState;
import com.twopc.coordinator.model.OrderRequest;
import com.twopc.coordinator.model.OrderResponse;
import com.twopc.coordinator.service.TransactionCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for order processing.
 * Entry point for clients to create orders.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

    private final TransactionCoordinator coordinator;

    public OrderController(TransactionCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    /**
     * Create a new order.
     * Initiates the 2PC protocol across inventory and payment services.
     *
     * Request body:
     * {
     *   "orderId": "ORD-123",
     *   "customerId": "CUST-001",
     *   "productId": "LAPTOP-001",
     *   "quantity": 2,
     *   "amount": 2999.98
     * }
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest orderRequest) {
        logger.info("POST /api/orders - Order: {}", orderRequest.getOrderId());

        try {
            // Validate request
            if (orderRequest.getOrderId() == null || orderRequest.getCustomerId() == null ||
                    orderRequest.getProductId() == null || orderRequest.getQuantity() <= 0 ||
                    orderRequest.getAmount() <= 0) {
                return ResponseEntity.badRequest().body(
                        OrderResponse.failure(orderRequest.getOrderId(), null, "Invalid request parameters"));
            }

            // Process order using 2PC
            Transaction transaction = coordinator.processOrder(orderRequest);

            // Build response based on transaction outcome
            if (transaction.getState() == TransactionState.COMMITTED) {
                return ResponseEntity.ok(
                        OrderResponse.success(orderRequest.getOrderId(), transaction.getTransactionId()));
            } else {
                return ResponseEntity.ok(
                        OrderResponse.failure(orderRequest.getOrderId(), transaction.getTransactionId(),
                                "Transaction aborted"));
            }

        } catch (Exception e) {
            logger.error("Error processing order {}", orderRequest.getOrderId(), e);
            return ResponseEntity.internalServerError().body(
                    OrderResponse.failure(orderRequest.getOrderId(), null, "Internal error: " + e.getMessage()));
        }
    }

    /**
     * Get transaction status by transaction ID.
     */
    @GetMapping("/transaction/{txnId}")
    public ResponseEntity<Transaction> getTransactionStatus(@PathVariable String txnId) {
        logger.info("GET /api/orders/transaction/{}", txnId);

        return coordinator.getTransaction(txnId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
