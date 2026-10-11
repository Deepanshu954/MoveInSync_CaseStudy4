package com.moveinsync.settlement.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service orchestrating user login authentication and security token dispatch.
 *
 * <p>Authentication Workflow:
 * <ol>
 *   <li>Receives raw credentials from {@link AuthController}.</li>
 *   <li>Delegates credential validation to Spring Security's {@link AuthenticationManager},
 *       which verifies the plaintext password against the BCrypt hash stored in the database.</li>
 *   <li>Extracts the authenticated {@link CustomUserDetails} principal and authority roles.</li>
 *   <li>Invokes {@link JwtService} to construct and sign an HMAC-SHA256 JWT containing identity claims.</li>
 *   <li>Returns an {@link AuthResponseDto} containing the token, TTL expiry, username, and role set.</li>
 * </ol>
 *
 * @author MoveInSync Core Settlement Team
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Authenticates credentials and issues a signed JSON Web Token.
     *
     * @param request login payload containing username and raw password
     * @return response DTO containing token, validity duration in milliseconds, and granted roles
     * @throws org.springframework.security.core.AuthenticationException if credentials are invalid or account is disabled
     */
    public AuthResponseDto login(AuthRequestDto request) {
        log.info("Attempting authentication for username: {}", request.getUsername());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String jwtToken = jwtService.generateToken(userDetails);

        Set<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        log.info("Authentication successful for user: {} with roles: {}", userDetails.getUsername(), roles);

        return new AuthResponseDto(
                jwtToken,
                jwtService.getExpirationMs(),
                userDetails.getUsername(),
                roles
        );
    }
}
