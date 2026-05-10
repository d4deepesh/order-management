package com.example.ordermgmt.controller;

import com.example.ordermgmt.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PUBLIC CONTROLLER
 *
 * CONCEPT: Endpoints that require NO authentication
 *
 * INTERVIEW POINTS:
 * - /api/public/** is excluded from security in SecurityConfig
 * - Useful for health checks, API info, app version
 * - Demonstrates @ConfigurationProperties usage in controller
 *   (via AppProperties bean injection)
 *
 * CONTENT NEGOTIATION:
 * These endpoints produce both JSON and XML.
 * Test with:
 *   curl http://localhost:8080/api/public/health
 *   curl -H "Accept: application/xml" http://localhost:8080/api/public/health
 *   curl "http://localhost:8080/api/public/health?format=xml"
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final AppProperties appProperties;

    /**
     * Health check endpoint -- no auth required
     * Returns basic app status and config values
     * Demonstrates @ConfigurationProperties values in action
     */
    @GetMapping(
            value = "/health",
            produces = {
                    MediaType.APPLICATION_JSON_VALUE,
                    MediaType.APPLICATION_XML_VALUE
            }
    )
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "order-management-service");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("maxPageSize",
                String.valueOf(appProperties.getOrder().getMaxPageSize()));
        response.put("defaultPageSize",
                String.valueOf(appProperties.getOrder().getDefaultPageSize()));
        response.put("minOrderValue",
                String.valueOf(appProperties.getOrder().getMinOrderValue()));
        response.put("asyncCoreThreads",
                String.valueOf(appProperties.getAsync().getCorePoolSize()));

        return ResponseEntity.ok(response);
    }

    /**
     * API info endpoint
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, String>> info() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("application", "Order Management System");
        response.put("version", "1.0.0");
        response.put("h2Console",
                "http://localhost:8080/h2-console");
        response.put("apiBase",
                "http://localhost:8080/api/orders");

        return ResponseEntity.ok(response);
    }
}