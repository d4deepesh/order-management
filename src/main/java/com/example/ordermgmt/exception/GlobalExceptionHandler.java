package com.example.ordermgmt.exception;

import com.example.ordermgmt.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GLOBAL EXCEPTION HANDLER
 *
 * CONCEPT: Centralized exception handling for ALL controllers
 *
 * INTERVIEW POINTS:
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * - Applies to ALL @RestController classes
 * - @ExceptionHandler methods handle specific exception types
 * - Spring searches for most specific handler first
 *
 * WHY CENTRALIZED HANDLING?
 * - Controller stays thin -- no try/catch in controllers
 * - Consistent error format across entire API
 * - HTTP status code logic in one place
 * - Prevents stack trace leakage to client
 *
 * EXCEPTION --> HTTP STATUS MAPPING:
 * MethodArgumentNotValidException    --> 400 (@RequestBody @Valid failure)
 * ConstraintViolationException       --> 400 (@RequestParam @Min failure)
 * HttpMessageNotReadableException    --> 400 (malformed JSON)
 * MissingServletRequestParameterException --> 400 (required param missing)
 * MethodArgumentTypeMismatchException --> 400 (String where Long expected)
 * OrderNotFoundException             --> 404 (resource not found)
 * NoHandlerFoundException            --> 404 (wrong URI)
 * HttpRequestMethodNotSupportedException --> 405 (POST on GET endpoint)
 * HttpMediaTypeNotAcceptableException --> 406 (unsupported Accept header)
 * OrderStatusException               --> 409 (invalid state transition)
 * HttpMediaTypeNotSupportedException --> 415 (unsupported Content-Type)
 * BusinessRuleException              --> 422 (valid input, business rule violated)
 * Exception                          --> 500 (catch-all, unhandled)
 *
 * NEVER return 200 with error body.
 * HTTP clients, API gateways, circuit breakers use STATUS CODE not body
 * to determine success or failure.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ----------------------------------------------------------------
    // 400 BAD REQUEST
    // ----------------------------------------------------------------

    /**
     * @RequestBody @Valid failure
     * Jackson parsed JSON successfully, but bean validation failed
     * e.g. @NotBlank failed, @Min failed, @Email failed
     * Returns per-field error messages in fieldErrors map
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        // extract per-field error messages from BindingResult
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(
                        error.getField(),
                        error.getDefaultMessage()));

        log.warn("Validation failed for {}: {}",
                request.getRequestURI(), fieldErrors);

        ErrorResponse response = ErrorResponse.builder()
                .status(400)
                .error("VALIDATION_FAILED")
                .message("Input validation failed")
                .fieldErrors(fieldErrors)
                .path(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * @RequestParam / @PathVariable constraint violation
     * @Validated on class + @Min/@Max on method parameters
     * Different exception from MethodArgumentNotValidException
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(cv -> {
            String field = cv.getPropertyPath().toString();
            // strip method name prefix from path
            if (field.contains(".")) {
                field = field.substring(field.lastIndexOf('.') + 1);
            }
            errors.put(field, cv.getMessage());
        });

        log.warn("Constraint violation for {}: {}",
                request.getRequestURI(), errors);

        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder()
                        .status(400)
                        .error("CONSTRAINT_VIOLATION")
                        .message("Parameter validation failed")
                        .fieldErrors(errors)
                        .path(request.getRequestURI())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    /**
     * Malformed JSON in request body
     * JSON syntax error: missing comma, wrong brackets etc.
     * Jackson cannot even parse it
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("Malformed request body for {}: {}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400,
                        "MALFORMED_REQUEST_BODY",
                        "Request body is malformed or invalid JSON"));
    }

    /**
     * Required @RequestParam is missing from URL
     * e.g. @RequestParam String status without required=false
     * and client does not send ?status=...
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.warn("Missing parameter '{}' for {}",
                ex.getParameterName(), request.getRequestURI());

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400,
                        "MISSING_PARAMETER",
                        "Required parameter '" + ex.getParameterName()
                                + "' is missing"));
    }

    /**
     * Type mismatch in path variable or request param
     * e.g. GET /orders/abc where id is Long
     * "abc" cannot be converted to Long
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String message = String.format(
                "Parameter '%s' should be of type '%s' but got value '%s'",
                ex.getName(),
                ex.getRequiredType() != null
                        ? ex.getRequiredType().getSimpleName() : "unknown",
                ex.getValue());

        log.warn("Type mismatch for {}: {}", request.getRequestURI(),
                message);

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(400, "TYPE_MISMATCH", message));
    }

    /**
     * Business rule violation
     * Request is valid JSON, passes bean validation
     * but violates domain rules (min order value, invalid state etc.)
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException ex,
            HttpServletRequest request) {

        log.warn("Business rule violation for {}: {}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(422,
                        "BUSINESS_RULE_VIOLATION", ex.getMessage()));
    }

    // ----------------------------------------------------------------
    // 404 NOT FOUND
    // ----------------------------------------------------------------

    /**
     * Custom domain exception -- order not found in DB
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Order not found: {} for request {}",
                ex.getMessage(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "NOT_FOUND",
                        ex.getMessage()));
    }

    /*
     * No controller method found for the URI
     * Wrong URL -- endpoint does not exist
     * Requires spring.mvc.throw-exception-if-no-handler-found=true
     * in application.yml
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(
            NoHandlerFoundException ex,
            HttpServletRequest request) {

        log.warn("No handler found: {} {}",
                ex.getHttpMethod(), ex.getRequestURL());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(404, "ENDPOINT_NOT_FOUND",
                        "No endpoint: " + ex.getHttpMethod()
                                + " " + ex.getRequestURL()));
    }

    // ----------------------------------------------------------------
    // 405 METHOD NOT ALLOWED
    // ----------------------------------------------------------------

    /**
     * Wrong HTTP method used on existing endpoint
     * e.g. POST /orders/{id} when only GET, PUT, DELETE are defined
     * MUST include Allow header showing supported methods (HTTP spec)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {

        String allowedMethods = ex.getSupportedMethods() != null
                ? String.join(", ", ex.getSupportedMethods())
                : "unknown";

        log.warn("Method not allowed: {} {} | Allowed: {}",
                ex.getMethod(), request.getRequestURI(), allowedMethods);

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                // Allow header is REQUIRED by HTTP spec for 405
                .header("Allow", allowedMethods)
                .body(ErrorResponse.of(405, "METHOD_NOT_ALLOWED",
                        "HTTP method " + ex.getMethod()
                                + " is not supported. Allowed: " + allowedMethods));
    }

    // ----------------------------------------------------------------
    // 406 NOT ACCEPTABLE
    // ----------------------------------------------------------------

    /**
     * Client's Accept header cannot be satisfied
     * e.g. Accept: text/plain but endpoint only produces JSON/XML
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request) {

        log.warn("Not acceptable media type for {}: {}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(ErrorResponse.of(406, "NOT_ACCEPTABLE",
                        "Requested media type cannot be produced. " +
                                "Use Accept: application/json or application/xml"));
    }

    // ----------------------------------------------------------------
    // 409 CONFLICT
    // ----------------------------------------------------------------

    /**
     * Invalid state machine transition
     * e.g. DELIVERED --> CANCELLED is not allowed
     */
    @ExceptionHandler(OrderStatusException.class)
    public ResponseEntity<ErrorResponse> handleOrderStatus(
            OrderStatusException ex,
            HttpServletRequest request) {

        log.warn("Order status conflict for {}: {}",
                request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, "STATUS_CONFLICT",
                        ex.getMessage()));
    }


    // ----------------------------------------------------------------
    // 415 UNSUPPORTED MEDIA TYPE
    // ----------------------------------------------------------------

    /**
     * Client sent Content-Type that endpoint cannot read
     * e.g. Content-Type: text/plain but endpoint consumes JSON
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMedia(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {

        log.warn("Unsupported media type '{}' for {}",
                ex.getContentType(), request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(415,
                        "UNSUPPORTED_MEDIA_TYPE",
                        "Content-Type '" + ex.getContentType()
                                + "' is not supported. Use application/json"));
    }

    // ----------------------------------------------------------------
    // 500 INTERNAL SERVER ERROR -- catch-all
    // ----------------------------------------------------------------

    /**
     * Catch-all for any unhandled exception
     * Returns 500 with generic message
     *
     * IMPORTANT: Never expose stack trace or internal details to client
     * Log the full exception server-side for debugging
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        // log full stack trace server-side
        log.error("Unhandled exception for {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                ex.getMessage(), ex);

        // return generic message to client (no stack trace)
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of(500,
                        "INTERNAL_SERVER_ERROR",
                        "An unexpected error occurred. " +
                                "Please try again or contact support."));
    }
}