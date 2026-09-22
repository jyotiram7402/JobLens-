package com.joblens.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Instant;
import java.util.Optional;

/**
 * Enables Spring Data JPA auditing, which populates the {@code createdAt} and
 * {@code updatedAt} fields on
 * {@link com.joblens.api.common.domain.BaseEntity}.
 *
 * <p>Repository and entity scanning are left to Spring Boot's auto-configuration:
 * both default to the application's base package, which is exactly what the
 * domain modules sit under. Declaring them explicitly would only create a second
 * place to update every time a module is added.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    /**
     * Audit timestamps are UTC instants. Without this, auditing would use the
     * server's default time zone, which differs between a laptop and a
     * container and would make timestamps incomparable across environments.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now());
    }
}
