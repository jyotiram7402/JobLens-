package com.joblens.api.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings, bound from {@code joblens.jwt.*}.
 *
 * <p>The secret comes from the {@code JWT_SECRET} environment variable and has
 * no default anywhere, in any profile. A committed default is the single most
 * common way a JWT implementation ends up forgeable: every deployment that
 * forgot to set the variable would share a secret that is also in a public Git
 * history, and anyone could mint a token for any user.
 *
 * @param secret     HMAC signing key. Validated for length in {@code JwtService}.
 * @param expiration how long an issued token stays valid
 * @param issuer     the {@code iss} claim, so tokens from another system are
 *                   rejected even if they happen to be signed correctly
 */
@Validated
@ConfigurationProperties(prefix = "joblens.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration expiration,
        @NotBlank String issuer
) {
}
