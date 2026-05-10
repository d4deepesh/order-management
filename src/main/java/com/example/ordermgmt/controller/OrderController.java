package com.example.ordermgmt.controller;

import com.example.ordermgmt.dto.request.OrderRequest;
import com.example.ordermgmt.dto.response.OrderResponse;
import com.example.ordermgmt.dto.response.PagedResponse;
import com.example.ordermgmt.enums.OrderStatus;
import com.example.ordermgmt.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

/**
 * ORDER CONTROLLER
 *
 * CONCEPT: HTTP Layer -- handles all HTTP concerns ONLY
 *
 * RULES for CONTROLLER:
 * 1. NO Business Logic -- delegate immediately to service
 * 2. NO database interaction -- only service calls
 * 3. NO exception handling -- delegate to GlobalExceptionHandler
 * 4. Handles: HTTP method, URL, request parsing, response status
 *
 * INTERVIEW POINTS:
 *
 * @RestController = @Controller + @ResponseBody
 *    @Controller -- marks as Spring MVC controller
 *    @ResponseBody -- return value serialized to HTTP response body
 *                     no view resolution (no JSP/Thymeleaf)
 *
 * @RequestMapping("/api/orders") -- base path for all methods
 *
 * @Validated -- enables @Min/@Max/@Pattern on @RequestParam
 * @Validated is method-parameter-focused (Spring AOP feature). Without this, parameter-level constraints are ignored
 *
 * @Valid is object-focused
 *
 * @RequiredArgsConstructor -- constructor injection for OrderService
 * Preferred over @Autowired field injection
 * Makes dependencies explicit, easier to unit test
 *
 * HTTP ANNOTATIONS:
 *   @GetMapping    -- HTTP GET
 *   @PostMapping   -- HTTP POST
 *   @PutMapping    -- HTTP PUT
 *   @PatchMapping  -- HTTP PATCH
 *   @DeleteMapping -- HTTP DELETE
 *
 * PARAMETER ANNOTATIONS:
 *   @PathVariable -- from URI path (/orders/{id})
 *   @RequestParam -- from query string (?page=0&size=10)
 *   @RequestBody -- from HTTP request body (JSON)
 *   @Valid -- triggers bean validation on @RequestBody
 *
 * CONTENT NEGOTIATION:
 * produces = {APPLICATION_JSON_VALUE, APPLICATION_XML_VALUE}
 *   Client sends Accept: application/json --> JSON response
 *   Client sends Accept: application/xml  --> XML response
 *   No match                              --> 406 Not Acceptable
 *
 * consumes = APPLICATION_JSON_VALUE
 *   Client must send Content-Type: application/json
 *   Mismatch --> 415 Unsupported Media Type
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@Validated    // enables constraint annotations on @RequestParam and @PathVariable
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ================================================================
    // POST /api/orders
    // CREATE a new order
    // Request:  JSON body with order details
    // Response: 201 Created + Location header + created order
    // ===============================================================

    /**
     * @RequestBody -- Jackson reads JSON body --> OrderRequest POJO
     * @Valid       -- triggers JSR-303 validation on OrderRequest DTO
     *                 if validation fails --> MethodArgumentNotValidException
     *                 GlobalExceptionHandler returns 400 with field errors
     *
     * 201 Created:
     *   - Correct status for successful resource creation (not 200)
     *   - Location header: URI of the created resource (HTTP spec requirement)
     *   - Client can use Location to GET the created resource
     *
     * Content negotiation:
     *   consumes = JSON only (cannot accept XML request bodies in this demo)
     *   produces = JSON or XML (client chooses via Accept header)
     */

    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.APPLICATION_XML_VALUE
            }
    )
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody @Valid OrderRequest request) {

        log.debug("POST /api/orders | item={}", request.getItem());

        // create order SYNCHRONOUSLY -- client waits for this
        OrderResponse created = orderService.createOrder(request);

        // fire-and-forget async notification -- goes through Spring proxy
        // controller does NOT wait -- returns 201 immediately
        // notification runs in background thread (taskExecutor pool)
        orderService.sendOrderConfirmationAsync(created.getId());

        // 201 Created with Location header
        // Location header tells client where to find the new resource
        URI location = URI.create("/api/orders/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }


    // ================================================================
    // GET /api/orders/{id}
    // READ a single order by ID
    // Response: 200 OK + order details, or 404 if not found
    // ================================================================

    /**
     * @PathVariable Long id:
     *    Extracts {id} from URI path
     *    Spring auto-converts "101" (String from URL) --> Long
     *    Conversion failure --> MethodArgumentTypeMismatchException --> 400
     *
     * @Positive:
     *    @Validated on class enables this on @PathVariable
     *    id must be > 0 (no negative IDs)
     *    Failure --> ConstraintViolationException --> 400
     *
     * 200 OK: ResponseEntity.ok(body) is shorthand for
     *         ResponseEntity.status(200).body(body)
     *
     * If order not found: service throws OrderNotFoundException
     * GlobalExceptionHandler catches --> 404 Not Found
     */
    @GetMapping(
            value = "/{id}",
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.APPLICATION_XML_VALUE
            }
    )
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable
            @Min(value = 1, message = "id must be a positive number")
            Long id) {
        log.debug("GET /api/orders/{}", id);
        return ResponseEntity.ok(orderService.getOrderById(id));
    }


    // ================================================================
    // GET /api/orders
    // READ all orders with pagination, filtering, sorting
    // Response: 200 OK + paginated list
    // ================================================================

    /**
     * PAGINATION PARAMETERS:
     * page  -- page index, 0-bases (page=0 = first page)
     * size  -- records per page (capped at 50 in service)
     * sortBy-- field to sort by (whitelist validated in service)
     * sortDir -- sort direction "ASC" or "DESC"
     *
     * FILTER PARAMETERS:
     * status -- filter by OrderStatus enum value
     * email  -- filter by customer email
     *
     * @RequestParam(defaultValue="0"):
     *    If not provided by client, defaults to 0
     *    No MissingServletRequestParameterException
     *
     * @Min(0) on page:
     *   page cannot be negative
     *   @Validated on class makes this work on @RequestParam
     *
     * @Min(1) @Max(50) on size:
     *   Prevent client from requesting 0 or 10,000 records
     *   Service also enforces max from AppProperties
     *
     * EMPTY LIST CASE:
     *   Return 200 with empty content[] -- never 404 for collections
     *   Empty collection is a valid result, not an error
     */
    @GetMapping(
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.APPLICATION_XML_VALUE
            }
    )
    public ResponseEntity<PagedResponse<OrderResponse>> getAllOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must >= 0")
            int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 50, message = "size must be <= 50")
            int size,
            @RequestParam(defaultValue = "createdAt")
            @Pattern(
                    regexp = "^(id|createdAt|price|status)$",
                    message = "sortBy must be: id, createdAt, price, or status"
            )
            String sortBy,
            @RequestParam(defaultValue = "desc")
            @Pattern(
                    regexp = "asc|desc",
                    message = "sortDir must be 'asc' or 'desc'"
            )
            String sortDir) {

        log.debug("GET /api/orders | status={}, page={}, size={}",
                status, page, size);

        return ResponseEntity.ok(orderService.getAllOrders(
                status, email, page, size, sortBy, sortDir));
    }


    // ================================================================
    // PUT /api/orders/{id}
    // FULL UPDATE -- replace entire order
    // Response: 200 OK + updated order
    // ================================================================

    /**
     * PUT semantics: FULL REPLACE
     * Client sends complete representation of the resource.
     * All fields in request body replace all fields in entity.
     * Fields not in request become null in DB.
     *
     * Idempotent: calling PUT with same body 10 times = same result.
     * Different from POST which creates a new resource each time.
     *
     * 200 OK: returns updated resource body
     * Alternative: 204 No Content (no body returned)
     */
    @PutMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.APPLICATION_XML_VALUE
            }
    )
    public ResponseEntity<OrderResponse> updateOrder(
            @PathVariable
            @Min(value = 1, message = "id must be positive") Long id,
            @RequestBody @Valid OrderRequest request) {
        log.debug("PUT /api/orders/{}", id);

        return ResponseEntity.ok(orderService.updateOrder(id, request));
    }

    // ================================================================
    // PATCH /api/orders/{id}
    // PARTIAL UPDATE -- update only provided fields
    // Response: 200 OK + updated order
    // ================================================================

    /**
     * PATCH semantics: PARTIAL UPDATE
     * Client sends ONLY the fields they want to change.
     * Fields not in request body remain unchanged in DB.
     *
     * Map<String, Object> approach:
     * - Allows any subset of fields
     * - Service checks which keys are present and updates only those
     *
     * Different from PUT:
     * PUT:   { "item": "Laptop", "qty": 5, "price": 75000 }  (ALL fields)
     * PATCH: { "qty": 5 }  (ONLY qty changes, item and price unchanged)
     */
    @PatchMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.APPLICATION_XML_VALUE
            }
    )
    public ResponseEntity<OrderResponse> patchOrder(
            @PathVariable
            @Min(value = 1, message = "id must be positive")
            Long id,
            @RequestBody Map<String, Object> fields) {

        log.debug("PATCH /api/orders/{} | fields: {}",
                id, fields.keySet());

        return ResponseEntity.ok(orderService.patchOrder(id, fields));
    }

    // ================================================================
    // PATCH /api/orders/{id}/status
    // UPDATE order status (state machine)
    // Response: 200 OK + updated order
    // ================================================================

    /**
     * Separate endpoint for status updates.
     * Status transitions follow state machine rules:
     * PENDING --> CONFIRMED --> SHIPPED --> DELIVERED
     * PENDING/CONFIRMED --> CANCELLED
     *
     * Invalid transition throws OrderStatusException --> 409 Conflict
     */
    @PatchMapping(
            value = "/{id}/status",
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.APPLICATION_XML_VALUE
            }
    )
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable
            @Min(value = 1, message = "id must be positive")
            Long id,
            @RequestParam String status) {

        log.debug("PATCH /api/orders/{}/status | newStatus={}",
                id, status);

        OrderStatus newStatus;
        try {
            newStatus = OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            // invalid enum value -- return 400 immediately
            return ResponseEntity.badRequest()
                    .body(null);
        }

        return ResponseEntity.ok(
                orderService.updateOrderStatus(id, newStatus));
    }


    // ================================================================
    // DELETE /api/orders/{id}
    // DELETE an order
    // Response: 204 No Content (no body)
    // ================================================================

    /**
     * 204 No Content:
     * Correct status for successful DELETE (not 200).
     * Response MUST have no body (HTTP spec).
     * ResponseEntity.noContent().build() ensures empty body.
     *
     * Idempotent: DELETE same resource twice should not fail
     * (second call returns 404, which is acceptable behavior)
     *
     * If order not found: service throws OrderNotFoundException
     *   GlobalExceptionHandler catches --> 404 Not Found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(
            @PathVariable
            @Min(value = 1, message = "id must be positive")
            Long id) {

        log.debug("DELETE /api/orders/{}", id);

        orderService.deleteOrder(id);

        // 204 No Content -- no body, no response entity
        return ResponseEntity.noContent().build();
    }

    // ================================================================
    // GET /api/orders/{id}/confirm-async
    // Demonstrates @Async + CompletableFuture in controller
    // Response: 202 Accepted immediately, task runs in background
    // ================================================================

    /**
     * ASYNC endpoint pattern:
     * 1. Controller starts async task (returns CompletableFuture)
     * 2. Servlet thread released immediately
     * 3. Spring MVC writes response when CompletableFuture completes
     *    OR controller returns 202 Accepted for true fire-and-forget
     *
     * 202 Accepted:
     * "I received your request and started processing it.
     *  The result is not yet available."
     * Client should poll a status endpoint or use webhooks.
     */
    @PostMapping("/{id}/notify")
    public ResponseEntity<Void> sendNotification(
            @PathVariable
            @Min(value = 1, message = "id must be positive")
            Long id) {

        log.debug("POST /api/orders/{}/notify", id);

        // verify order exists first
        orderService.getOrderById(id);

        // fire async task -- does NOT block
        orderService.sendOrderConfirmationAsync(id);

        // 202 Accepted -- processing started, result not yet ready
        return ResponseEntity.accepted().build();
    }
}

