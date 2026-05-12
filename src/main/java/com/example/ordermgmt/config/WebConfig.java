package com.example.ordermgmt.config;

import com.example.ordermgmt.interceptor.AuditInterceptor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


/**
 * WEB MVC CONFIGURATION (this file is NOT mandatory in a normal Spring Boot project. Your application can work perfectly without it.)
 *
 * This class exists because someone wants to customize Spring MVC + Jackson behavior explicitly.
 *
 * CONCEPT: Customizes Spring MVC Behavior. “Customize how Spring converts HTTP ↔ Java objects.”
 *
 * This config file customizes:
 * | Feature             | Why customize?                  |
 * | ------------------- | ------------------------------- |
 * | JSON formatting     | pretty print, date formatting   |
 * | Unknown fields      | strict vs lenient APIs          |
 * | XML support         | JSON + XML APIs                 |
 * | Content negotiation | `Accept` header / `?format=`    |
 * | ObjectMapper        | centralized serialization rules |
 *
 * INTERVIEW POINTS:
 *
 * HttpMessageConverter:
 * - Converts Java object <--> HTP body (JSON, XML, etc)
 * - MappingJackson2HttpMessageConverter    handles application/json
 * - MappingJackson2XmlHttpMessageConverter handles application/xml
 * - Selection based on Content-Type (read) and Accept header (write)
 *
 * Content Negotiation:
 * - Process of selecting response format based on client preference
 * - Client sends: Accept: application/json or Accept: application/xml
 * - Server checks: what formats can this endpoint produce?
 * - Intersection determines format
 * - No match --> 406 Not Acceptable
 *
 * ObjectMapper(@Primary)
 * - Central Jackson class for JSON serialization/deserialization
 * - Expensive to create -- always singleton (Spring manages this)
 * - @Primary marks as the default ObjectMapper when multiple exist
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Primary ObjectMapper for JSON
     *
     * @Primary ensures this is used when Spring injects ObjectMapper
     * without qualifier. The XML ObjectMapper is the secondary one.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {  // custom Jackson ObjectMapper
        ObjectMapper mapper = new ObjectMapper();

        // Register JavaTimeModule for LocalDate, LocalDateTime etc.
        // Without this: LocalDateTime throws InvalidDefinitionException
        mapper.registerModule(new JavaTimeModule());

        // Write dates as "2026-05-06" not [2026, 5, 6]
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Silently ignore unknown JSON fields from client
        // strict APIs set this to true to reject unexpected fields
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // false --> Ignore unknown fields. // true --> Throw error

        // Pretty print JSON (disable in production for performance)
        // Useful for: development, debugging
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        return mapper;
    }


    /**
     * Content Negotiation Configuration
     *
     * INTERVIEW POINTS:
     *
     * Strategy 1: Accept Header (DEFAULT, recommended)
     * GET /orders/101 + Accept: application/xml --> XML response
     *
     * Strategy 2: URL Parameter
     * GET /orders/101?format=json --> JSON
     * GET /orders/101?format=xml  --> XML
     * Useful when client cannot set headers
     *
     * Strategy 3: URL Extension (.json, .xml) -- DEPRECATED in Spring 5.3
     * Security risk (RFD attacks) -- do NOT use in new projects
     *
     * defaultContentType: used when no Accept header and no format param
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                // URL parameter strategy: ?format=json or ?format=xml
                .favorParameter(true).parameterName("format")

                // map parameter values to media types
                .mediaType("json", MediaType.APPLICATION_JSON).mediaType("xml", MediaType.APPLICATION_XML)

                // default when neither Accept header nor format param given
                .defaultContentType(MediaType.APPLICATION_JSON);
    }
}