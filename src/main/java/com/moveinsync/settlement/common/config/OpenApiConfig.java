package com.moveinsync.settlement.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Global OpenAPI 3.1 & Swagger UI configuration.
 *
 * <p>Registers JWT Bearer authentication scheme and API metadata.
 *
 * @author MoveInSync Core Settlement Team
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MoveInSync Vendor & Driver Settlement Engine API",
                version = "1.0.0",
                description = "Enterprise settlement calculation, reconciliation, dispute delta resolution, and transactional outbox engine.",
                contact = @Contact(name = "MoveInSync Core Settlement Team", email = "settlement@moveinsync.com"),
                license = @License(name = "MoveInSync Proprietary", url = "https://www.moveinsync.com")
        ),
        servers = {
                @Server(url = "http://localhost:8081", description = "Local Settlement Engine Instance")
        },
        security = {
                @SecurityRequirement(name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter JWT Bearer token obtained from /api/auth/login"
)
public class OpenApiConfig {
}
