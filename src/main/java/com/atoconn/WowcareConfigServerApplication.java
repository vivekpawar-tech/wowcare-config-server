package com.atoconn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * WowCare Config Server Application
 * 
 * This is the main entry point for the Spring Cloud Config Server.
 * It provides centralized external configuration management for all microservices
 * in the WowCare platform.
 * 
 * Features:
 * - Centralized configuration management via Git repository
 * - Service discovery integration with Netflix Eureka
 * - Environment-specific configuration profiles
 * - OAuth2/JWT security integration with Keycloak
 * - Health monitoring and metrics via Actuator
 * 
 * @author WowCare Platform Engineering Team
 * @version 1.0.0
 * @since 2026-01-29
 */
@EnableConfigServer
@EnableDiscoveryClient
@SpringBootApplication
public class WowcareConfigServerApplication {
    
    /**
     * Main method to start the Config Server application
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(WowcareConfigServerApplication.class, args);
    }
}
