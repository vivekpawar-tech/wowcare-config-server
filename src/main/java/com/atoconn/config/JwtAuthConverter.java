package com.atoconn.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Custom JWT authentication converter for Keycloak tokens
 * 
 * <p>This converter extracts roles and authorities from Keycloak JWT tokens
 * and converts them into Spring Security GrantedAuthority objects. It supports:
 * <ul>
 *   <li>Realm-level roles from realm_access claim</li>
 *   <li>Client-specific roles from resource_access claim</li>
 *   <li>Default scope-based authorities</li>
 * </ul>
 * 
 * <p><b>Keycloak JWT Token Structure:</b>
 * <pre>
 * {
 *   "realm_access": {
 *     "roles": ["admin", "user"]
 *   },
 *   "resource_access": {
 *     "wowcare-config-server": {
 *       "roles": ["config-admin", "config-viewer"]
 *     }
 *   },
 *   "scope": "openid profile email",
 *   "sub": "user-id",
 *   "preferred_username": "john.doe"
 * }
 * </pre>
 * 
 * <p><b>Authority Mapping:</b>
 * <ul>
 *   <li>Realm role "admin" → "ROLE_ADMIN"</li>
 *   <li>Resource role "config-admin" → "ROLE_CONFIG-ADMIN"</li>
 *   <li>Scope "profile" → "SCOPE_profile"</li>
 * </ul>
 * 
 * @author WowCare Development Team
 * @version 1.0
 * @since 2026-01-29
 */
@Slf4j
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String CLIENT_ID = "wowcare-config-server";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtGrantedAuthoritiesConverter defaultGrantedAuthoritiesConverter = 
            new JwtGrantedAuthoritiesConverter();

    /**
     * Converts a JWT token into an authentication token with extracted authorities
     * 
     * @param jwt the JWT token to convert
     * @return JwtAuthenticationToken with all extracted authorities
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Combine default scope-based authorities with extracted roles
        Collection<GrantedAuthority> authorities = Stream.concat(
                defaultGrantedAuthoritiesConverter.convert(jwt).stream(),
                extractRoles(jwt).stream()
        ).collect(Collectors.toSet());

        String username = jwt.getClaim("preferred_username");
        log.debug("JWT token converted for user: {} with {} authorities", 
                username, authorities.size());
        log.trace("Authorities: {}", authorities);

        return new JwtAuthenticationToken(jwt, authorities, username);
    }

    /**
     * Extracts roles from Keycloak JWT token
     * 
     * <p>Supports both realm_access and resource_access claims.
     * Realm roles are available across all applications, while resource
     * roles are client-specific.
     * 
     * @param jwt the JWT token containing role claims
     * @return collection of GrantedAuthority objects
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRoles(Jwt jwt) {
        // Extract realm-level roles
        Collection<String> realmRoles = extractRealmRoles(jwt);
        log.debug("Extracted {} realm roles", realmRoles.size());
        
        // Extract client-specific roles
        Collection<String> resourceRoles = extractResourceRoles(jwt);
        log.debug("Extracted {} resource roles for client: {}", resourceRoles.size(), CLIENT_ID);

        // Combine and convert to GrantedAuthority with ROLE_ prefix
        List<GrantedAuthority> authorities = Stream.concat(
                realmRoles.stream(),
                resourceRoles.stream()
        )
        .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase()))
        .collect(Collectors.toList());

        log.debug("Total authorities created: {}", authorities.size());
        return authorities;
    }

    /**
     * Extracts realm-level roles from realm_access claim
     * 
     * @param jwt the JWT token
     * @return collection of realm role names
     */
    @SuppressWarnings("unchecked")
    private Collection<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim(REALM_ACCESS_CLAIM);
        
        if (realmAccess == null) {
            log.trace("No realm_access claim found in JWT");
            return Collections.emptyList();
        }
        
        Collection<String> roles = (Collection<String>) realmAccess.get(ROLES_CLAIM);
        if (roles == null) {
            log.trace("No roles found in realm_access claim");
            return Collections.emptyList();
        }
        
        log.trace("Realm roles: {}", roles);
        return roles;
    }

    /**
     * Extracts client-specific roles from resource_access claim
     * 
     * @param jwt the JWT token
     * @return collection of resource role names
     */
    @SuppressWarnings("unchecked")
    private Collection<String> extractResourceRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaim(RESOURCE_ACCESS_CLAIM);
        
        if (resourceAccess == null) {
            log.trace("No resource_access claim found in JWT");
            return Collections.emptyList();
        }
        
        // Try to get roles from wowcare-config-server client
        Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(CLIENT_ID);
        if (clientAccess == null) {
            log.trace("No resource access found for client: {}", CLIENT_ID);
            return Collections.emptyList();
        }
        
        Collection<String> roles = (Collection<String>) clientAccess.get(ROLES_CLAIM);
        if (roles == null) {
            log.trace("No roles found for client: {}", CLIENT_ID);
            return Collections.emptyList();
        }
        
        log.trace("Resource roles for {}: {}", CLIENT_ID, roles);
        return roles;
    }
}
