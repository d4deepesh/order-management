package com.example.ordermgmt.config;

import com.example.ordermgmt.entity.Order;
import com.example.ordermgmt.enums.OrderStatus;
import com.example.ordermgmt.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DATA INITIALIZER
 *
 * CONCEPT: Seeds sample data into the H2 in-memory DB on startup
 *
 * INTERVIEW POINTS:
 * CommandLineRunner:
 * - Functional interface with run(String... args) method
 * - Spring Boot calls run() AFTER ApplicationContext is fully loaded
 * - Used for startup tasks: data seeding, cache warming, health checks
 *
 * Alternative: ApplicationRunner -- similar but receives ApplicationArguments
 *
 * @Component -- Spring manages this bean and calls run() automatically
 */
@Slf4j
@Profile("dev")
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final OrderRepository orderRepository;

    @Override
    public void run(String... args) {
        log.info("Seeding sample order data...");

        List<Order> sampleOrders = List.of(
            Order.builder()
                .item("Laptop - Dell XPS 15")
                .qty(1)
                .price(95000.00)
                .customerEmail("deepesh@example.com")
                .customerPhone("9876543210")
                .status(OrderStatus.DELIVERED)
                .deliveryAddress("Bhopal, MP 462001")
                .createdAt(LocalDateTime.now().minusDays(10))
                .build(),

            Order.builder()
                .item("Mechanical Keyboard - Keychron K2")
                .qty(2)
                .price(8500.00)
                .customerEmail("deepesh@example.com")
                .customerPhone("9876543210")
                .status(OrderStatus.CONFIRMED)
                .deliveryAddress("Bhopal, MP 462001")
                .createdAt(LocalDateTime.now().minusDays(3))
                .build(),

            Order.builder()
                .item("USB-C Hub 7-in-1")
                .qty(1)
                .price(3200.00)
                .customerEmail("test@example.com")
                .status(OrderStatus.PENDING)
                .deliveryAddress("Indore, MP 452001")
                .createdAt(LocalDateTime.now().minusHours(5))
                .build(),

            Order.builder()
                .item("Monitor - LG 27 inch 4K")
                .qty(1)
                .price(45000.00)
                .customerEmail("test@example.com")
                .status(OrderStatus.SHIPPED)
                .deliveryAddress("Indore, MP 452001")
                .createdAt(LocalDateTime.now().minusDays(1))
                .build(),

            Order.builder()
                .item("Wireless Mouse - Logitech MX Master 3")
                .qty(3)
                .price(6500.00)
                .customerEmail("admin@example.com")
                .status(OrderStatus.CANCELLED)
                .deliveryAddress("Delhi 110001")
                .remarks("Customer cancelled -- out of stock")
                .createdAt(LocalDateTime.now().minusDays(7))
                .build()
        );

        orderRepository.saveAll(sampleOrders);
        log.info("Seeded {} sample orders successfully",
            sampleOrders.size());
        log.info("=================================================");
        log.info("App ready. Try these endpoints:");
        log.info("Health:  GET  http://localhost:8080/api/public/health");
        log.info("List:    GET  http://localhost:8080/api/orders" +
                 "  (Basic auth: admin/admin123)");
        log.info("Get one: GET  http://localhost:8080/api/orders/1");
        log.info("H2 DB:        http://localhost:8080/h2-console");
        log.info("=================================================");
    }
}