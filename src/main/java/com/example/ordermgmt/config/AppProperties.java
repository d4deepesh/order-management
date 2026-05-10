package com.example.ordermgmt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * APPLICATION CONFIGURATION PROPERTIES
 *
 * CONCEPT: @ConfigurationProperties -- type-safe binding of
 * application.yml properties to a Java POJO
 *
 * INTERVIEW POINTS vs @Value;
 *
 * @Value("${app.order.max-page-size}")  <-- one property at a time
 *                                           no relaxed binding
 *                                           no validation support
 *
 * @ConfigurationProperties(prefix="app") <-- entire group at once
 *                                            relaxed binding:
 *                                            max-page-size, maxPageSize,
 *                                            MAX_PAGE_SIZE all work
 *                                            supports @Validated
 *                                            IDE autocomplete
 *                                            unit testable as plain POJO
 *
 * Maps from application.yml:
 * app:
 *   order:
 *     max-page-size: 50       --> maxPageSize = 50
 *     default-page-size: 10   --> defaultPageSize = 10
 *   async:
 *     core-pool-size: 5       --> corePoolSize = 5
 */
@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Order order = new Order();
    private Async async = new Async();
    private Notification notification = new Notification();


    @Data
    public static class Order {
        private int maxPageSize = 50;
        private int defaultPageSize = 10;
        private String allowedSortFields =
                "id,createdAt,price,status";
        private double minOrderValue = 100.0;

        // convenience -- returns whitelist as List
        public List<String> getAllowedSortFieldList() {
            return Arrays.asList(allowedSortFields.split(","));
        }
    }

    @Data
    public static class Async {
        private int corePoolSize = 5;
        private int maxPoolSize = 20;
        private int queueCapacity = 100;
        private String threadNamePrefix = "order-async-";
    }

    @Data
    public static class Notification {
        private boolean emailEnabled = true;
        private int retryAttempts = 3;
    }
}
