package com.moveinsync.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SettlementEngineApplicationTests {

    @Test
    @DisplayName("Application context loads cleanly with test profile")
    void contextLoads() {
    }
}
