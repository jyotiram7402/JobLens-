package com.joblens.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the Spring context starts and Flyway migrations apply cleanly
 * against a real PostgreSQL instance.
 */
@SpringBootTest
@ActiveProfiles("test")
class JobLensApplicationTests {

    @Test
    void contextLoads() {
    }
}
