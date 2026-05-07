package com.example.ordermgmt.service;

import com.example.ordermgmt.dto.request.OrderRequest;
import com.example.ordermgmt.dto.response.OrderResponse;
import com.example.ordermgmt.dto.response.PagedResponse;
import com.example.ordermgmt.enums.OrderStatus;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ORDER SERVICE INTERFACE
 *
 * CONCEPT: Program to interface, not implementation
 *
 * INTERVIEW POINTS:
 *
 * Why use interface + impl pattern?
 * 1. Testability: mock the interface in unit tests
 *    @MockBean OrderService orderService;
 *    No need to spin up real DB for controller tests
 *
 * 2. Multiple implementations possible
 *    OrderServiceImpl (real DB)
 *    CachedOrderServiceImpl (with redis cache)
 *    MockOrderServiceImpl (for integration tests)
 *
 * 3. Spring AOP proxies work cleanly on interfaces
 *    @Transactional, @Async, @Cacheable all use AOP proxies
 *
 * 4. Dependency Injection via interface type:
 *    @Autowired OrderService orderService
 *    Spring injects the implementing Bean
 *
 * 5. Separation of contract from implementation
 *    Interface = what the service does (contract)
 *    Impl = how it does it (implementation detail)
 */

public interface OrderService {

    // CREATE -- POST /orders
    OrderResponse createOrder(OrderRequest request);

    // READ ONE -- GET /order/{id}
    OrderResponse getOrderById(Long id);

    // REAL ALL with pagination and filters -- GET /orders
    PagedResponse<OrderResponse> getAllOrders(
            String status,
            String email,
            int page,
            int size,
            String sortBy,
            String sortDir
    );

    // FULL UPDATE -- PUT /orders/{id}
    OrderResponse updateOrder(Long id, OrderRequest request);

    // PARTIAL UPDATE -- PATCH /orders/{id}
    OrderResponse patchOrder(Long id, Map<String, Object> fields);

    // DELETE -- DELETE /orders/{id}
    void deleteOrder(Long id);

    // STATUS UPDATE -- PATCH /orders/{id}/status
    OrderResponse updateOrderStatus(Long id, OrderStatus newStatus);

    // ASYNC -- background notification
    // returns CompletableFuture for non-blocking operation
    CompletableFuture<Void> sendOrderConfirmationAsync(Long orderId);

}
