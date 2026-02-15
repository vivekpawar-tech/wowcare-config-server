package com.atoconn.config;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Security configuration properties for WowCare Config Server
 * 
 * <p>This class holds externalized security configuration from application.yml
 * under the prefix "config.security". It supports:
 * <ul>
 *   <li>Enable/disable security features</li>
 *   <li>JWT issuer and JWK Set URIs</li>
 *   <li>Role-based access control configuration</li>
 *   <li>Public endpoint definitions</li>
 *   <li>CORS settings</li>
 * </ul>
 * 
 * <p><b>Configuration Example (application.yml):</b>
 * <pre>
 * config:
 *   security:
 *     enabled: true
 *     admin-role: ADMIN
 *     public-endpoints:
 *       - /actuator/health
 *       - /actuator/info
 *     cors:
 *       enabled: true
 *       allowed-origins:
 *         - http://localhost:3000
 *         - https://admin.wowcare.com
 *       allowed-methods:
 *         - GET
 *         - POST
 *         - PUT
 *         - DELETE
 * </pre>
 * 
 * @author WowCare Development Team
 * @version 1.0
 * @since 2026-01-29
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "config.security")
public class SecurityProperties {

    /**
     * Enable/disable security for Config Server endpoints
     * 
     * <p>When disabled, all endpoints will be publicly accessible.
     * This should only be disabled in local development environments.
     * 
     * <p><b>Default:</b> true
     */
    private boolean enabled = true;

    /**
     * Required role for accessing admin endpoints
     * 
     * <p>This role is required for endpoints like:
     * <ul>
     *   <li>/actuator/env</li>
     *   <li>/actuator/configprops</li>
     *   <li>/actuator/refresh</li>
     *   <li>/monitor/**</li>
     * </ul>
     * 
     * <p><b>Default:</b> ADMIN
     */
    private String adminRole = "ADMIN";

    /**
     * Public endpoints that don't require authentication
     * 
     * <p>These endpoints are accessible without JWT token.
     * Typically includes health checks and monitoring endpoints.
     * 
     * <p><b>Default endpoints:</b>
     * <ul>
     *   <li>/actuator/health</li>
     *   <li>/actuator/health/liveness</li>
     *   <li>/actuator/health/readiness</li>
     *   <li>/actuator/info</li>
     *   <li>/actuator/prometheus</li>
     * </ul>
     */
    private List<String> publicEndpoints = new ArrayList<>(List.of(
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/prometheus"
    ));

    /**
     * CORS (Cross-Origin Resource Sharing) configuration
     * 
     * <p>Controls which domains can access the Config Server API
     * from web browsers.
     */
    private Cors cors = new Cors();

    /**
     * CORS configuration properties
     */
    @Data
    public static class Cors {
        
        /**
         * Enable/disable CORS
         * 
         * <p><b>Default:</b> false
         */
        private boolean enabled = false;

        /**
         * Allowed origins for CORS requests
         * 
         * <p>List of origins (domains) that are allowed to make
         * cross-origin requests to the Config Server.
         * 
         * <p><b>Examples:</b>
         * <ul>
         *   <li>http://localhost:3000 - Local development</li>
         *   <li>https://admin.wowcare.com - Production admin UI</li>
         *   <li>* - Allow all origins (not recommended for production)</li>
         * </ul>
         * 
         * <p><b>Default:</b> ["http://localhost:3000"]
         */
        @NotEmpty
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:3000",
                "http://localhost:4200",
                "http://localhost:8080"
        ));

        /**
         * Allowed HTTP methods for CORS requests
         * 
         * <p><b>Default:</b> ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
         */
        @NotEmpty
        private List<String> allowedMethods = new ArrayList<>(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));

        /**
         * Allowed headers for CORS requests
         * 
         * <p>Use "*" to allow all headers.
         * 
         * <p><b>Default:</b> ["*"]
         */
        @NotEmpty
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

        /**
         * Allow credentials (cookies, authorization headers) in CORS requests
         * 
         * <p>When true, the browser will include credentials in cross-origin requests.
         * 
         * <p><b>Default:</b> true
         */
        private boolean allowCredentials = true;

        /**
         * Maximum age for preflight request caching (in seconds)
         * 
         * <p>Browsers will cache the preflight response for this duration.
         * 
         * <p><b>Default:</b> 3600 (1 hour)
         */
        private long maxAge = 3600;
    }
}
