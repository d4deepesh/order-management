package com.example.ordermgmt.warmup;

import com.example.ordermgmt.dto.request.OrderRequest;
import com.example.ordermgmt.dto.response.OrderResponse;
import com.example.ordermgmt.entity.Order;
import com.example.ordermgmt.mapper.OrderMapper;
import com.example.ordermgmt.repository.OrderRepository;
import com.example.ordermgmt.service.OrderService;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile("prod")               // ONLY active in PROD profile
public class ApplicationWarmUp implements ApplicationRunner {

    private final EntityManagerFactory entityManagerFactory;
    private final DataSource dataSource;
    private final OrderService orderService;
    private final JwtDecoder jwtDecoder;
    private final FilterChainProxy filterChainProxy;
    private final OrderMapper orderMapper;
    private final OrderRepository orderRepository;
    private final PlatformTransactionManager transactionManager;
    private final ServerProperties serverProperties;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting application warm-up...");
        long start = System.currentTimeMillis();

        warmUpJwt();                  // fixes JWK fetch on first request
        warmUpSecurityFilterChain();  // fixes 330ms filter init
        warmUpHikari();               // ensures connections ready
        warmUpOrderService();         // warms read path + transaction proxy
        warmUpWritePath();            // warms mapper + INSERT plan

        log.info("Warm-up complete in {}ms",
                System.currentTimeMillis() - start);
    }

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${keycloak.warmup.token-uri}")   // add to yml
    private String tokenUri;

    @Value("${keycloak.warmup.client-id}")
    private String clientId;

    @Value("${keycloak.warmup.client-secret}")
    private String clientSecret;

    @Value("${keycloak.warmup.username}")
    private String username;

    @Value("${keycloak.warmup.password}")
    private String password;

    private void warmUpJwt() {
        try {
            // fetch REAL token from Keycloak — has actual kid
            RestTemplate rest = new RestTemplate();
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type",    "password");
            form.add("client_id",     clientId);
            form.add("client_secret", clientSecret);
            form.add("username",      username);
            form.add("password",      password);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            Map<String, Object> response = rest.postForObject(
                    tokenUri,
                    new HttpEntity<>(form, headers),
                    Map.class
            );

            String realToken = (String) response.get("access_token");

            // decode real token — Nimbus fetches JWK set with actual kid
            // caches key by kid — all subsequent requests hit cache
            jwtDecoder.decode(realToken);
            log.info("JWT decoder warmed up with real kid-indexed key");

        } catch (Exception e) {
            log.warn("JWT warmup failed: {}", e.getMessage());
        }
    }

    private void warmUpSecurityFilterChain() {
        try {
            int port = serverProperties.getPort() != null
                    ? serverProperties.getPort() : 8080;

            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getForObject(
                    "http://localhost:" + port + "/api/public/health",
                    String.class
            );
            log.info("Security filter chain warmed up via HTTP");
        } catch (Exception e) {
            log.warn("Filter chain warmup: {}", e.getMessage());
        }
    }

    private void warmUpHikari() {
        try (Connection c = dataSource.getConnection()) {
            c.isValid(1);
            log.info("HikariCP warmed up");
        } catch (Exception e) {
            log.warn("HikariCP warmup error: {}", e.getMessage());
        }
    }

    private void warmUpOrderService() {
        try {
            orderService.getAllOrders(null, null, 0, 1, "createdAt", "desc");
            log.info("OrderService read path warmed up");
        } catch (Exception e) {
            log.warn("OrderService warmup: {}", e.getMessage());
        }
    }

    private void warmUpWritePath() {
        Order savedOrder = null;
        try {
            OrderRequest dummy = OrderRequest.builder()
                    .item("__warmup__")
                    .qty(1)
                    .price(999999.00)          // high value to pass min-order rule
                    .customerEmail("warmup@internal.com")
                    .deliveryAddress("warmup")
                    .build();

            // go through FULL service proxy — warms @Transactional + @CachePut + @CacheEvict
            OrderResponse response = orderService.createOrder(dummy);

            // immediately delete — no trace left in DB or cache
            orderService.deleteOrder(response.getId());

            log.info("Write path warmed up — proxy, cache interceptors, INSERT plan");

        } catch (Exception e) {
            // cleanup if delete failed
            if (savedOrder != null) {
                try { orderRepository.deleteById(savedOrder.getId()); }
                catch (Exception ignored) {}
            }
            log.warn("Write path warmup error: {}", e.getMessage());
        }
    }
}