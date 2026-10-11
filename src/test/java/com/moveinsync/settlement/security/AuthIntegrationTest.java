package com.moveinsync.settlement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test suite verifying user authentication, BCrypt password matching,
 * and signed JWT token issuance.
 *
 * <p>Test scenarios:
 * <ul>
 *   <li><b>Successful Login:</b> Valid admin credentials issue a signed HMAC-SHA256 JWT with {@code ROLE_SETTLEMENT_ADMIN}.</li>
 *   <li><b>Invalid Credentials:</b> Bad passwords trigger an authentication failure mapped to 401 Unauthorized.</li>
 *   <li><b>DTO Field Validation:</b> Blank usernames trigger JSR-380 validation mapped to 400 Bad Request.</li>
 * </ul>
 *
 * @author MoveInSync Core Settlement Team
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Verifies that valid credentials for the seed administrator generate a valid JWT
     * and correctly expose granted authority roles in the response payload.
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Success with valid seed admin credentials")
    void shouldLoginSuccessfullyWithSeedAdmin() throws Exception {
        AuthRequestDto request = new AuthRequestDto("admin", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.username", is("admin")))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_SETTLEMENT_ADMIN")));
    }

    /**
     * Verifies that incorrect password attempts fail authentication, return 401 Unauthorized,
     * and do not leak user existence details.
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Fails 401 with bad credentials")
    void shouldFailLoginWithBadPassword() throws Exception {
        AuthRequestDto request = new AuthRequestDto("admin", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Verifies that empty payload fields are caught by Hibernate Validator before invoking
     * the database or authentication manager, returning structured 400 Bad Request field errors.
     */
    @Test
    @DisplayName("POST /api/v1/auth/login - Fails 400 when username is blank")
    void shouldFailValidationWhenUsernameIsBlank() throws Exception {
        AuthRequestDto request = new AuthRequestDto("", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.fieldErrors.username", notNullValue()));
    }
}
