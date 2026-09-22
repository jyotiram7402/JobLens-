package com.joblens.api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that loads the full application context and talks to a real
 * PostgreSQL database.
 *
 * <p>Defined once so the profile and the context configuration cannot drift
 * between test classes -- and so the Spring context is cached and reused across
 * every test that carries this annotation, rather than being rebuilt per class.
 *
 * <p>Requires a running database. See {@code docs/VERIFICATION.md}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@ActiveProfiles("test")
public @interface IntegrationTest {
}
