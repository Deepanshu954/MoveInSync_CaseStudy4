package com.moveinsync.settlement.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication REST endpoint for MoveInSync Settlement Engine.
 *
 * <p>Provides token acquisition capabilities for frontend dashboards, operational CLI tools,
 * and automated banking integration workers.
 *
 * @author MoveInSync Core Settlement Team
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Operations for user authentication and Bearer JWT token generation")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates user credentials and returns a cryptographically signed Bearer JWT token.
     *
     * @param request credentials DTO containing username and password
     * @return 200 OK with {@link AuthResponseDto} containing token, expiry, and user roles
     */
    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT", description = "Validates username and password against database BCrypt hash and returns signed JWT")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "400", description = "Validation failure (blank username or password)"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody AuthRequestDto request) {
        AuthResponseDto response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
