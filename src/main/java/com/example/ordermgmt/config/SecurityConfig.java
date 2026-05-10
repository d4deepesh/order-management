package com.example.ordermgmt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SECURITY CONFIGURATION
 *
 * Concept: Configures Spring Security filter chain
 *
 * INTERVIEW POINTS:
 * - Spring Security runs as FILTERS (not interceptors)
 * - Filters run BEFORE DispatcherServlet
 * - If auth fails, controller is NEVER reached
 * - 401 = not authenticated (no/invalid token)
 * - 403 = authenticated but not authorized (wrong role, no permission)
 *
 * DelegatingFilterProxy bridges servlet filter chain to
 * Spring Security's SecurityFilterChain
 *
 * For this demo:
 * - Public endpoints: /api/public/**, /h2-console/**, /actuator/**
 * - All other endpoints require authentication
 * - Using HTTP Basic auth (simplified for learning)
 * - In production: use JWT / OAuth2/ Okta
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // disable CSRF for REST APIS (stateless, JWT tokens, OAuth2)
                // CSRF protection is for browser-based session authentication (cookies) form submission
                .csrf(AbstractHttpConfigurer::disable)

                // DISABLE form login -- this stops /login redirect
                // form login is for browser-based apps with login pages
                // REST APIs use Basic Auth or JWT -- never form login
                //.formLogin(AbstractHttpConfigurer::disable)

                // DISABLE logout page -- not needed for REST APIs
                //.logout(AbstractHttpConfigurer::disable)

                // authorization rules
                .authorizeHttpRequests(auth -> auth
                        // public endpoints -- no auth required
                        .requestMatchers(
                                "/api/public/**",
                                "/h2-console/**",
                                "/actuator/**",
                                "/actuator/health",
                                "/error"
                        ).permitAll()
                        // all other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // HTTP Basic auth for demo (use JWT in production)
                .httpBasic(Customizer.withDefaults())
                // allow H2 console frames (H2 uses iframes)
                .headers(headers -> headers
                        .frameOptions(
                                HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                );

        return http.build();
    }
}