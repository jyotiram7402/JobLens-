package com.joblens.api.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a persistence-slice test: JPA, repositories and Flyway, but no web
 * layer and no services.
 *
 * <p>{@code @AutoConfigureTestDatabase(replace = NONE)} is the important part.
 * By default {@code @DataJpaTest} swaps the real datasource for an embedded
 * database, which would silently test against something that is not PostgreSQL
 * -- different types, different constraint behaviour, different SQL. These tests
 * exist precisely to check PostgreSQL behaviour, so the replacement is off.
 *
 * <p>Each test rolls back at the end, so they do not see each other's rows.
 * They do see the dev seed data only if the dev profile is active, which it is
 * not: the test profile uses the default migration location.
 *
 * <p>Requires a running database. See {@code docs/VERIFICATION.md}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
public @interface RepositoryTest {
}
