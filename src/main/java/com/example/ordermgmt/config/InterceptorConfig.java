package com.example.ordermgmt.config;

import com.example.ordermgmt.interceptor.AuditInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * INTERCEPTOR REGISTRATION CONFIG
 *
 * CONCEPT: Registers HandlerInterceptors with URL patterns
 *
 * INTERVIEW POINTS:
 * - addPathPatterns()     --> apply interceptor to these URLs
 * - excludePathPatterns() --> skip these URLs
 * - order()               --> controls execution order when multiple interceptors
 *
 * Interceptors execute in ORDER for preHandle()
 * Interceptors execute in REVERSE ORDER for postHandle() and afterCompletion()
 *
 * Example with 2 interceptors (order 1 and 2):
 * preHandle:       Interceptor1 --> Interceptor2
 * controller runs
 * postHandle:      Interceptor2 --> Interceptor1  (reverse)
 * afterCompletion: Interceptor2 --> Interceptor1  (reverse)
 */
@Configuration
@RequiredArgsConstructor
public class InterceptorConfig implements WebMvcConfigurer {

    private final AuditInterceptor auditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor) // Adds your interceptor into request processing chain.

                // apply to all API endpoints
                .addPathPatterns("/api/**")

                // skip interceptor for: health check and public endpoints
                .excludePathPatterns(
                        "/api/public/**",
                        "/actuator/**"
                )
                .order(1); //Controls execution order when multiple interceptors exist.
        // Lower number = earlier execution.
    }
}

/**
 * ✅ Common interceptor use cases
 *
 * | Use case           | Example             |
 * | ------------------ | ------------------- |
 * | Audit logging      | who called API      |
 * | Request timing     | performance metrics |
 * | Correlation IDs    | distributed tracing |
 * | Authentication     | custom auth         |
 * | Rate limiting      | throttle requests   |
 * | API usage tracking | analytics           |
 *
 * ⚠️ Interceptor vs Filter
 * | Filter                   | Interceptor                |
 * | ------------------------ | -------------------------- |
 * | Servlet level            | Spring MVC level           |
 * | Before DispatcherServlet | After DispatcherServlet    |
 * | Works for all requests   | Mainly controller requests |
 * | Lower level              | Higher level               |
 */