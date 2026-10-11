package com.moveinsync.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Bootstrap smoke test validating that the Spring Boot ApplicationContext,
 * Flyway database migrations, and JPA entity mappings initialize without errors.
 *
 * @author MoveInSync Core Settlement Team
 */
@SpringBootTest
@ActiveProfiles("test")
class SettlementEngineApplicationTests {

    /**
     * Asserts that all Spring beans, Hibernate entities, and security filters
     * assemble and wire together cleanly against PostgreSQL.
     */
    @Test
    @DisplayName("Application context loads cleanly with test profile")
    void contextLoads() {
        // Assertion implicit in clean context bootstrap
    }
}
