package com.atoconn.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Security configuration for WowCare Config Server
 * 
 * <p>This configuration implements OAuth2 Resource Server with JWT authentication
 * using Keycloak as the identity provider. It provides:
 * <ul>
 *   <li>JWT-based authentication for all endpoints</li>
 *   <li>Role-based access control (RBAC)</li>
 *   <li>Public endpoints for health checks and monitoring</li>
 *   <li>Protected endpoints for configuration management</li>
 *   <li>CORS configuration for cross-origin requests</li>
 *   <li>Security headers (CSP, HSTS, etc.)</li>
 * </ul>
 * 
 * <p><b>Endpoint Security Model:</b>
 * <ul>
 *   <li><b>Public:</b> /actuator/health, /actuator/info, /actuator/prometheus</li>
 *   <li><b>Authenticated:</b> /{application}/{profile}, /encrypt, /decrypt</li>
 *   <li><b>Admin Only:</b> /actuator/env, /actuator/configprops, /actuator/refresh</li>
 * </ul>
 * 
 * @author WowCare Development Team
 * @version 1.0
 * @since 2026-01-29
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "config.security.enabled", havingValue = "true", matchIfMissing = true)
@Profile("!test")
public class SecurityConfig {

    private final SecurityProperties securityProperties;

    /**
     * Configures the security filter chain with OAuth2 Resource Server
     * 
     * @param http HttpSecurity builder for configuring web-based security
     * @return SecurityFilterChain configured with OAuth2 JWT authentication
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Security Filter Chain for Config Server");
        log.info("Security enabled: {}", securityProperties.isEnabled());
        log.info("Public endpoints: {}", securityProperties.getPublicEndpoints());

        http
                // CSRF disabled for stateless REST API with JWT authentication
                .csrf(AbstractHttpConfigurer::disable)
                
                // Session management - stateless (JWT tokens, no sessions)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                
                // CORS configuration
                .cors(cors -> {
                    if (securityProperties.getCors().isEnabled()) {
                        cors.configurationSource(corsConfigurationSource());
                        log.info("CORS enabled with origins: {}", 
                                securityProperties.getCors().getAllowedOrigins());
                    } else {
                        cors.disable();
                        log.info("CORS disabled");
                    }
                })
                
                // Authorization rules
                .authorizeHttpRequests(auth -> {
                    // Public endpoints - no authentication required
                    auth.requestMatchers(
                            "/actuator/health",
                            "/actuator/health/**",
                            "/actuator/info",
                            "/actuator/prometheus"
                    ).permitAll();
                    
                    log.info("Public endpoints configured: health, info, prometheus");
                    
                    // Admin-only endpoints - require ADMIN role
                    auth.requestMatchers(
                            "/actuator/env",
                            "/actuator/env/**",
                            "/actuator/configprops",
                            "/actuator/configprops/**",
                            "/actuator/refresh",
                            "/actuator/beans",
                            "/actuator/beans/**",
                            "/monitor/**"
                    ).hasRole(securityProperties.getAdminRole());
                    
                    log.info("Admin endpoints configured - require role: {}", 
                            securityProperties.getAdminRole());
                    
                    // Config Server endpoints - require authentication
                    auth.requestMatchers(
                            "/{application}/{profile}",
                            "/{application}/{profile}/{label}",
                            "/{application}-{profile}.yml",
                            "/{application}-{profile}.yaml",
                            "/{application}-{profile}.json",
                            "/{application}-{profile}.properties",
                            "/{label}/{application}-{profile}.yml",
                            "/{label}/{application}-{profile}.yaml",
                            "/{label}/{application}-{profile}.json",
                            "/{label}/{application}-{profile}.properties",
                            "/encrypt",
                            "/encrypt/**",
                            "/decrypt",
                            "/decrypt/**"
                    ).authenticated();
                    
                    log.info("Config Server endpoints require authentication");
                    
                    // All other requests require authentication
                    auth.anyRequest().authenticated();
                })
                
                // OAuth2 Resource Server with JWT
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(new JwtAuthConverter())
                        )
                )
                
                // Security headers
                .headers(headers -> headers
                        // Allow same-origin framing (for Spring Boot Admin)
                        .frameOptions(frame -> frame.sameOrigin())
                        // Content Security Policy
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; " +
                                        "script-src 'self' 'unsafe-inline'; " +
                                        "style-src 'self' 'unsafe-inline'; " +
                                        "img-src 'self' data:; " +
                                        "font-src 'self' data:")
                        )
                        // HTTP Strict Transport Security (HSTS)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000) // 1 year
                        )
                );

        log.info("Security configuration completed - OAuth2 Resource Server with JWT enabled");
        return http.build();
    }

    /**
     * CORS configuration source bean
     * 
     * <p>Configures Cross-Origin Resource Sharing (CORS) based on properties.
     * Allows specified origins, methods, and headers for API access from
     * different domains.
     * 
     * @return CorsConfigurationSource with configured CORS settings
     */
    @Bean
    @ConditionalOnProperty(name = "config.security.cors.enabled", havingValue = "true")
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(securityProperties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(securityProperties.getCors().getAllowedMethods());
        configuration.setAllowedHeaders(securityProperties.getCors().getAllowedHeaders());
        configuration.setAllowCredentials(securityProperties.getCors().isAllowCredentials());
        configuration.setMaxAge(securityProperties.getCors().getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        log.info("CORS configuration registered for all paths");
        log.debug("CORS details - Origins: {}, Methods: {}, Headers: {}",
                configuration.getAllowedOrigins(),
                configuration.getAllowedMethods(),
                configuration.getAllowedHeaders());
        
        return source;
    }
}
