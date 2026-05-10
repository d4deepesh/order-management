package com.example.ordermgmt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SECURITY CONFIG -- PROD PROFILE ONLY
 *
 * @Profile("prod") -- Spring loads this ONLY when
 * active profile is "prod"
 *
 * PROD uses JWT OAuth2:
 * - Bearer token from Keycloak
 * - No username/password in our app
 * - Keycloak handles authentication
 * - Our app only validates JWT signature
 * - Stateless -- no session, no cookie
 *
 * INTERVIEW POINTS:
 * Resource Server flow:
 * 1. Client gets JWT from Keycloak
 * 2. Client sends JWT in Authorization: Bearer header
 * 3. Spring validates JWT using Keycloak public keys
 * 4. Valid --> request proceeds
 * 5. Invalid/expired --> 401
 */
@Configuration
@EnableWebSecurity
@Profile("prod")               // ONLY active in PROD profile
public class ProdSecurityConfig {

    @Bean
    public SecurityFilterChain prodSecurityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/public/**",
                    "/actuator/health",
                    "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )

            // JWT Resource Server
            // reads issuer-uri and jwk-set-uri from
            // application-prod.yml automatically
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
            );

        return http.build();
    }
}