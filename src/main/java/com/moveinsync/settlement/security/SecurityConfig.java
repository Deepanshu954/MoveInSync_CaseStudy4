package com.moveinsync.settlement.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Enterprise Spring Security Configuration for MoveInSync Settlement Engine.
 *
 * <p>Architectural Principles:
 * <ul>
 *   <li><b>Stateless Architecture:</b> HTTP sessions are strictly disabled ({@code STATELESS})
 *       to support zero-downtime horizontal pod autoscaling in Kubernetes.</li>
 *   <li><b>HMAC-SHA256 JWT Authentication:</b> Every incoming request must present a cryptographically
 *       signed Bearer token in the {@code Authorization} header, verified per-request.</li>
 *   <li><b>Defense-in-Depth RBAC:</b> Global route rules protect the perimeter while fine-grained
 *       method security ({@code @PreAuthorize}) guards service and controller mutation methods.</li>
 *   <li><b>RFC 7807 Error Responses:</b> Authentication failures (401) and authorization rejections (403)
 *       emit machine-readable JSON payloads rather than default HTML error pages.</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    /**
     * Explicit constructor injection ensuring all security components are non-null and testable.
     *
     * @param jwtAuthFilter            filter intercepting requests to validate Bearer JWTs
     * @param userDetailsService       service loading user accounts from the database
     * @param authenticationEntryPoint 401 unauthorized challenge handler
     * @param accessDeniedHandler      403 forbidden access rejection handler
     */
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthFilter,
            CustomUserDetailsService userDetailsService,
            JwtAuthenticationEntryPoint authenticationEntryPoint,
            CustomAccessDeniedHandler accessDeniedHandler
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * Configures the main HTTP security filter chain.
     *
     * <p>Permits public access to auth endpoints, Swagger/OpenAPI docs, and Actuator health probes.
     * All remaining endpoints require an authenticated user with valid authorities.
     *
     * @param http Spring Security HTTP builder
     * @return constructed {@link SecurityFilterChain}
     * @throws Exception if security filter configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF since API is purely stateless REST using Bearer tokens
                .csrf(AbstractHttpConfigurer::disable)
                // Guarantee no server-side HTTP session state is persisted
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Route authorization whitelist and protected perimeter
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/auth/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // Register custom DAO authentication provider
                .authenticationProvider(authenticationProvider())
                // Place JWT filter before standard username/password authentication filter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Custom error handlers emitting structured JSON payloads
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }

    /**
     * Configures the database-backed {@link AuthenticationProvider}.
     *
     * @return configured {@link DaoAuthenticationProvider}
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Exposes the Spring {@link AuthenticationManager} bean for programmatic login authentication.
     *
     * @param config authentication configuration
     * @return manager instance
     * @throws Exception on lookup failure
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Configures the BCrypt password encoder with 12 hashing rounds (recommended industry strength).
     *
     * @return BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
