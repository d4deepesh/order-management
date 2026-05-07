package com.example.ordermgmt.dto.response;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ERROR RESPONSE DTO
 *
 * CONCEPT: Standardized error format returned for ALL error cases
 *
 * INTERVIEW POINTS:
 * - Never return raw exception messages to client (security risk)
 * - Never return 200 OK with error body (defeats HTTP protocol)
 * - Always use correct HTTP status code + structured error body
 * - fieldErrors map contains per-field validation errors (400 only)
 *
 * Example JSON (400 Bad Request):
 * {
 *   "status": 400,
 *   "error": "VALIDATION_FAILED",
 *   "message": "Input validation failed",
 *   "fieldErrors": {
 *     "item": "item cannot be blank",
 *     "qty": "qty must be at least 1"
 *   },
 *   "timestamp": "2026-05-06T10:30:00"
 * }
 *
 * Example JSON (404 Not Found):
 * {
 *   "status": 404,
 *   "error": "NOT_FOUND",
 *   "message": "Order not found with id: 999",
 *   "timestamp": "2026-05-06T10:30:00"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "error")
public class ErrorResponse {

    // HTTP status code (mirrors response status)
    private int status;

    // machine-readable error code
    private String error;

    // human-readable message
    private String message;

    // per-field validation errors (populated for 400 only)
    private Map<String, String> fieldErrors;

    // request path that caused the error
    private String path;

    // when the error occurred
    private LocalDateTime timestamp;

    // convenience factory -- most common usage
    public static ErrorResponse of(int status,
                                   String error,
                                   String message) {
        return ErrorResponse.builder()
                .status(status)
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
