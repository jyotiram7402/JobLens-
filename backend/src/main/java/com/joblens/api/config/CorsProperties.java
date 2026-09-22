package com.joblens.api.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Cross-origin settings, bound from {@code joblens.cors.*}.
 *
 * <p>Typed configuration rather than scattered {@code @Value} lookups: a typo in
 * a property name fails at startup instead of silently leaving an empty
 * allowlist, and {@code @NotEmpty} guarantees a deployment can never come up
 * with CORS accidentally unconfigured.
 *
 * @param allowedOrigins exact origins permitted to call the API, scheme
 *                       included and no trailing slash
 * @param allowedMethods HTTP methods permitted on those origins
 * @param maxAgeSeconds  how long a browser may cache the preflight response
 */
@Validated
@ConfigurationProperties(prefix = "joblens.cors")
public record CorsProperties(
        @NotEmpty List<String> allowedOrigins,
        @NotEmpty List<String> allowedMethods,
        long maxAgeSeconds
) {
}
