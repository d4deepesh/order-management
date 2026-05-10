package com.example.ordermgmt.service.impl;

import com.example.ordermgmt.config.AppProperties;
import com.example.ordermgmt.dto.request.OrderRequest;
import com.example.ordermgmt.dto.response.OrderResponse;
import com.example.ordermgmt.dto.response.PagedResponse;
import com.example.ordermgmt.entity.Order;
import com.example.ordermgmt.enums.OrderStatus;
import com.example.ordermgmt.exception.BusinessRuleException;
import com.example.ordermgmt.exception.OrderNotFoundException;
import com.example.ordermgmt.exception.OrderStatusException;
import com.example.ordermgmt.mapper.OrderMapper;
import com.example.ordermgmt.repository.OrderRepository;
import com.example.ordermgmt.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/*
 * ORDER SERVICE IMPLEMENTATION
 *
 * CONCEPT: Business Logic Layer
 *
 * INTERVIEW POINTS:
 *
 * @Service -- marks as Spring service bean
 *   Functionally same as @Component
 *   Semantic marker -- signals business logic layer
 *
 * @Transactional at CLASS level:
 *   All public methods get READ-WRITE transaction by default
 *   Override individual methods with @Transactional(readOnly=true)
 *   for read operations (better performance, no dirty checking)
 *
 * @Transactional rules:
 *   - Lives on SERVICE layer, NOT controller or repository
 *   - Transaction starts when method is called via Spring proxy
 *   - Transaction commits on successful return
 *   - Transaction rolls back on RuntimeException (unchecked)
 *   - CheckedException does NOT trigger rollback by default
 *     use @Transactional(rollbackFor = Exception.class) for that
 *   - @Transactional does NOT propagate to @Async threads
 *
 * Self-invocation problem:
 *   @Async works via AOP proxy
 *   Calling this.sendOrderConfirmationAsync() from same class
 *   BYPASSES the proxy --> @Async is IGNORED
 *   Solution: call via injected self reference or separate bean
 *
 * @RequiredArgsConstructor:
 *   Generates constructor for all final fields
 *   Spring uses constructor injection (preferred over @Autowired field injection)
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final AppProperties appProperties;

    // ----------------------------------------------------------------
    // CREATE
    // ----------------------------------------------------------------

    /*
     * POST /orders
     *
     * FLOW:
     * 1. Business rule validation (min order value)
     * 2. Duplicate check
     * 3. Map DTO --> Entity
     * 4. Save via repository
     * 5. Map saved Entity --> Response DTO
     * 6. Return DTO (never Entity)
     *
     * @Transactional (inherited from class): begins here, commits on return
     */
    @Override
    public OrderResponse createOrder(OrderRequest request) {
        log.debug("createOrder called for item: {}", request.getItem());

        // BUSINESS RULE: minimum order value check
        double orderValue = request.getPrice() * request.getQty();
        double minValue = appProperties.getOrder().getMinOrderValue();

        if (orderValue < minValue) {
            throw new BusinessRuleException(
                    String.format(
                            "Order total %.2f is below minimum allowed value %.2f",
                            orderValue, minValue));
        }

        // MAP: DTO --> Entity
        // Never pass DTO directly to repository
        Order order = orderMapper.toEntity(request);

        // status is set by mapper and @PrePersist
        // service can override if needed
        order.setStatus(OrderStatus.PENDING);

        // SAVE: repository handles INSERT
        // @Transactional ensures this is within a transaction
        Order savedOrder = orderRepository.save(order);

        log.info("Order created successfully with id: {}",
                savedOrder.getId());

        // MAP: Entity --> Response DTO
        // Never return Entity to controller
        return orderMapper.toResponse(savedOrder);
    }

    // ----------------------------------------------------------------
    // READ ONE
    // ----------------------------------------------------------------

    /*
     * GET /orders/{id}
     *
     * @Transactional(readOnly=true):
     * - Disables Hibernate dirty checking (no tracking of changes)
     * - Faster: no flush needed before query
     * - Allows DB to optimize for read-only connection
     * - Route to read replica in primary-replica setups
     */
    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        log.debug("getOrderById called for id: {}", id);

        // findById returns Optional<Order>
        // orElseThrow: if empty, throw 404 exception
        // GlobalExceptionHandler maps this to 404 response
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        return orderMapper.toResponse(order);
    }

    // ----------------------------------------------------------------
    // READ ALL WITH PAGINATION
    // ----------------------------------------------------------------

    /*
     * GET /orders?status=PENDING&page=0&size=10&sortBy=createdAt&sortDir=desc
     *
     * PAGINATION INTERNALS:
     * PageRequest.of(page, size, sort) --> Pageable object
     * Passed to repository --> Hibernate generates:
     *   SELECT * FROM orders LIMIT ? OFFSET ?       (data query)
     *   SELECT COUNT(*) FROM orders                  (count query)
     * Returns Page<Order> with both data and metadata
     *
     * SORT FIELD WHITELIST:
     * Never pass client-supplied sortBy directly to Sort.by()
     * Malicious client can send: sortBy=password (data leak)
     * Always validate against whitelist first
     */
    @Override
    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getAllOrders(
            String status,
            String email,
            int page,
            int size,
            String sortBy,
            String sortDir) {

        log.debug("getAllOrders: status={}, email={}, page={}, " +
                        "size={}, sortBy={}, sortDir={}",
                status, email, page, size, sortBy, sortDir);

        // VALIDATE sort field against whitelist
        // prevents client from sorting on sensitive columns
        List<String> allowedFields =
                appProperties.getOrder().getAllowedSortFieldList();

        if (!allowedFields.contains(sortBy)) {
            log.warn("Invalid sortBy field: {}. Defaulting to createdAt",
                    sortBy);
            sortBy = "createdAt";
        }

        // ENFORCE max page size from config
        // prevents client from requesting 10,000 records
        int maxSize = appProperties.getOrder().getMaxPageSize();
        if (size > maxSize) {
            log.warn("Requested size {} exceeds max {}. Capping to {}",
                    size, maxSize, maxSize);
            size = maxSize;
        }

        // BUILD sort direction
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        // BUILD Pageable -- encapsulates page, size, sort
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(direction, sortBy));

        // PARSE status to enum if provided
        OrderStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = OrderStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessRuleException(
                        "Invalid status value: " + status);
            }
        }

        // QUERY repository -- returns Page<Order>
        // Page<Order> contains:
        //   getContent()       --> List<Order> for this page
        //   getTotalElements() --> total matching records
        //   getTotalPages()    --> total pages
        //   isFirst()          --> is this page 0?
        //   isLast()           --> no more pages?
        Page<Order> orderPage = orderRepository
                .findByFilters(statusEnum, email, pageable);

        // MAP each Entity --> Response DTO using stream
        List<OrderResponse> content = orderPage.getContent()
                .stream()
                .map(orderMapper::toResponse)
                .collect(Collectors.toList());

        // BUILD PagedResponse with metadata
        return PagedResponse.<OrderResponse>builder()
                .content(content)
                .page(orderPage.getNumber())
                .size(orderPage.getSize())
                .totalElements(orderPage.getTotalElements())
                .totalPages(orderPage.getTotalPages())
                .first(orderPage.isFirst())
                .last(orderPage.isLast())
                .hasNext(orderPage.hasNext())
                .hasPrevious(orderPage.hasPrevious())
                .build();
    }

    // ----------------------------------------------------------------
    // FULL UPDATE (PUT)
    // ----------------------------------------------------------------

    /*
     * PUT /orders/{id}
     *
     * PUT semantics: FULL REPLACE
     * All fields in request replace all fields in existing entity
     * Missing fields become null (full overwrite)
     *
     * @Transactional dirty checking:
     * After findById(), the entity is MANAGED by Hibernate.
     * Changes to it are detected automatically (dirty checking).
     * No explicit save() call needed -- Hibernate flushes on commit.
     */
    @Override
    public OrderResponse updateOrder(Long id, OrderRequest request) {
        log.debug("updateOrder called for id: {}", id);

        // find or throw 404
        Order existing = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        // FULL REPLACE -- all fields updated
        // mapper applies all fields from request to existing entity
        orderMapper.updateEntityFromRequest(existing, request);

        // no explicit save() needed
        // entity is managed -- dirty checking auto-detects changes
        // @Transactional commit triggers Hibernate flush --> UPDATE SQL

        log.info("Order {} updated successfully", id);
        return orderMapper.toResponse(existing);
    }

    // ----------------------------------------------------------------
    // PARTIAL UPDATE (PATCH)
    // ----------------------------------------------------------------

    /*
     * PATCH /orders/{id}
     *
     * PATCH semantics: PARTIAL UPDATE
     * Only fields present in the request body are updated.
     * Absent fields remain unchanged.
     *
     * Map<String, Object> approach:
     * Client sends only the fields they want to change.
     * We check each key and update only if present.
     * Type casting needed (Object --> correct type).
     *
     * Production alternative: use JsonPatch or JsonMergePatch
     * for more formal PATCH semantics.
     */
    @Override
    public OrderResponse patchOrder(Long id,
                                    Map<String, Object> fields) {
        log.debug("patchOrder called for id: {} with fields: {}",
                id, fields.keySet());

        Order existing = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        // only update fields that are explicitly provided
        if (fields.containsKey("item")) {
            existing.setItem((String) fields.get("item"));
        }
        if (fields.containsKey("qty")) {
            existing.setQty(((Number) fields.get("qty")).intValue());
        }
        if (fields.containsKey("price")) {
            existing.setPrice(
                    ((Number) fields.get("price")).doubleValue());
        }
        if (fields.containsKey("deliveryAddress")) {
            existing.setDeliveryAddress(
                    (String) fields.get("deliveryAddress"));
        }
        if (fields.containsKey("remarks")) {
            existing.setRemarks((String) fields.get("remarks"));
        }

        // dirty checking handles the UPDATE SQL
        log.info("Order {} partially updated", id);
        return orderMapper.toResponse(existing);
    }

    // ----------------------------------------------------------------
    // DELETE
    // ----------------------------------------------------------------

    /*
     * DELETE /orders/{id}
     *
     * Returns void -- controller sends 204 No Content
     * Check existence first to return proper 404 if not found
     * (deleteById silently does nothing if ID not found)
     */
    @Override
    public void deleteOrder(Long id) {
        log.debug("deleteOrder called for id: {}", id);

        // check existence first -- deleteById won't throw if not found
        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException(id);
        }

        orderRepository.deleteById(id);
        log.info("Order {} deleted successfully", id);
    }

    // ----------------------------------------------------------------
    // STATUS UPDATE
    // ----------------------------------------------------------------

    /*
     * PATCH /orders/{id}/status
     *
     * State machine validation:
     * PENDING    --> CONFIRMED, CANCELLED
     * CONFIRMED  --> SHIPPED, CANCELLED
     * SHIPPED    --> DELIVERED
     * DELIVERED  --> (terminal -- no further transitions)
     * CANCELLED  --> (terminal -- no further transitions)
     */
    @Override
    public OrderResponse updateOrderStatus(Long id,
                                           OrderStatus newStatus) {
        log.debug("updateOrderStatus: id={}, newStatus={}", id, newStatus);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        OrderStatus currentStatus = order.getStatus();

        // validate state machine transition
        if (!isValidTransition(currentStatus, newStatus)) {
            throw new OrderStatusException(
                    String.format(
                            "Invalid status transition from %s to %s",
                            currentStatus, newStatus));
        }

        order.setStatus(newStatus);
        log.info("Order {} status changed: {} --> {}",
                id, currentStatus, newStatus);

        return orderMapper.toResponse(order);
    }

    /*
     * Validate state machine transitions
     * DELIVERED and CANCELLED are terminal states
     */
    private boolean isValidTransition(OrderStatus current,
                                      OrderStatus next) {
        return switch (current) {
            case PENDING -> Set.of(
                            OrderStatus.CONFIRMED, OrderStatus.CANCELLED)
                    .contains(next);
            case CONFIRMED -> Set.of(
                            OrderStatus.SHIPPED, OrderStatus.CANCELLED)
                    .contains(next);
            case SHIPPED -> Set.of(
                    OrderStatus.DELIVERED).contains(next);
            case DELIVERED,
                 CANCELLED,
                 FAILED -> false; // terminal states
        };
    }

    // ----------------------------------------------------------------
    // ASYNC -- Background processing
    // ----------------------------------------------------------------

    /*
     * @Async -- runs in separate thread from taskExecutor pool
     *
     * INTERVIEW POINTS:
     *
     * @Async rules:
     * 1. Method must be PUBLIC (AOP proxy cannot intercept private)
     * 2. Must be called from OUTSIDE this class (self-invocation bypasses proxy)
     *    Wrong: this.sendOrderConfirmationAsync(id)  --> @Async ignored
     *    Right: called from controller or another bean
     * 3. Return type must be void or CompletableFuture<T>
     * 4. @Transactional does NOT propagate to async thread
     *    Each async method manages its own transaction
     *
     * CompletableFuture:
     * - Allows caller to chain operations: .thenApply(), .exceptionally()
     * - Allows caller to wait: .get() or .join()
     * - Allows parallel execution: CompletableFuture.allOf(f1, f2, f3)
     *
     * "taskExecutor" refers to the bean defined in AsyncConfig
     */
    @Override
    @Async("taskExecutor")
    public CompletableFuture<Void> sendOrderConfirmationAsync(
            Long orderId) {

        log.info("sendOrderConfirmationAsync started for order: {} " +
                        "on thread: {}",
                orderId, Thread.currentThread().getName());

        try {
            // simulate email/SMS sending delay
            Thread.sleep(2000);

            // In production: call EmailService, SmsService, etc.
            log.info("Order confirmation sent for order: {}", orderId);

            return CompletableFuture.completedFuture(null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        } catch (Exception e) {
            log.error("Failed to send confirmation for order: {}",
                    orderId, e);
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }
}