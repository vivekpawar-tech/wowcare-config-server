package com.atoconn.config;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.netflix.eureka.EurekaInstanceConfigBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

/**
 * Eureka Client Configuration
 * 
 * Configures the Config Server's integration with Netflix Eureka for service discovery.
 * This allows other microservices to discover and communicate with the Config Server
 * dynamically without hardcoded URLs.
 * 
 * Key Features:
 * - Custom metadata for service identification
 * - Health check endpoint configuration
 * - Actuator endpoint exposure
 * - Environment-specific instance configuration
 * 
 * @author WowCare Platform Engineering Team
 * @version 1.0.0
 * @since 2026-01-29
 */
@Configuration
@ConditionalOnProperty(name = "eureka.client.enabled", havingValue = "true", matchIfMissing = true)
public class EurekaClientConfig {
    
    private final Environment environment;
    private final EurekaInstanceConfigBean eurekaInstanceConfig;
    
    public EurekaClientConfig(Environment environment, EurekaInstanceConfigBean eurekaInstanceConfig) {
        this.environment = environment;
        this.eurekaInstanceConfig = eurekaInstanceConfig;
    }
    
    /**
     * Initialize Eureka instance metadata after application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void customizeEurekaMetadata() {
        // Add custom metadata
        Map<String, String> metadata = eurekaInstanceConfig.getMetadataMap();
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        
        // Service information
        metadata.put("service-type", "config-server");
        metadata.put("service-version", "1.0.0");
        metadata.put("spring-boot-version", "3.2.5");
        metadata.put("spring-cloud-version", "2023.0.4");
        
        // Actuator endpoints
        metadata.put("management.context-path", "/actuator");
        metadata.put("health.path", "/actuator/health");
        metadata.put("info.path", "/actuator/info");
        metadata.put("metrics.path", "/actuator/metrics");
        metadata.put("prometheus.path", "/actuator/prometheus");
        
        // Environment
        String[] activeProfiles = environment.getActiveProfiles();
        metadata.put("active-profiles", String.join(",", activeProfiles));
        metadata.put("environment", activeProfiles.length > 0 ? activeProfiles[0] : "default");
        
        // Config Server specific metadata
        metadata.put("config.refresh.enabled", "true");
        metadata.put("config.git.enabled", "true");
        
        eurekaInstanceConfig.setMetadataMap(metadata);
    }
    
    /**
     * Add Config Server information to the /actuator/info endpoint
     * 
     * Contributes additional information about the Config Server to the
     * Actuator info endpoint for monitoring and troubleshooting.
     * 
     * @return InfoContributor with Config Server details
     */
    @Bean
    public InfoContributor configServerInfoContributor() {
        return builder -> {
            Map<String, Object> configServerInfo = new HashMap<>();
            configServerInfo.put("service", "wowcare-config-server");
            configServerInfo.put("description", "Centralized Configuration Management Server");
            configServerInfo.put("version", "1.0.0");
            configServerInfo.put("gitEnabled", true);
            configServerInfo.put("eurekaEnabled", true);
            configServerInfo.put("securityEnabled", true);
            
            String[] profiles = environment.getActiveProfiles();
            configServerInfo.put("activeProfiles", profiles);
            configServerInfo.put("environment", profiles.length > 0 ? profiles[0] : "default");
            
            builder.withDetail("configServer", configServerInfo);
        };
    }
}
