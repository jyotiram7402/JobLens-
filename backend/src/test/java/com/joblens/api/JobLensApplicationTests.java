package com.joblens.api;

import com.joblens.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Smoke test for the whole application: the Spring context starts, every bean
 * resolves, Flyway applies its migrations, and Hibernate validates its mappings
 * against the resulting schema.
 *
 * <p>Small as it looks, this catches most wiring mistakes -- a missing
 * configuration property, an entity that does not match a migration, a
 * constructor Spring cannot satisfy -- and it is the one test that must pass
 * before anything is deployed.
 */
@IntegrationTest
class JobLensApplicationTests {

    @Test
    void contextLoads() {
    }
}
