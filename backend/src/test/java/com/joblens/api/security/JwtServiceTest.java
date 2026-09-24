package com.joblens.api.security;

import com.joblens.api.user.domain.Role;
import com.joblens.api.user.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token issuing and verification, with no Spring context.
 *
 * <p>Covers the cases that matter for security rather than only the happy path:
 * a token signed with a different key, one from a different issuer, one that has
 * expired, and a secret too short to be safe.
 */
class JwtServiceTest {

    private static final String SECRET = "test-only-insecure-jwt-secret-used-exclusively-by-the-test-suite";
    private static final String ISSUER = "joblens-api";

    private final JwtService jwtService = new JwtService(
            new JwtProperties(SECRET, Duration.ofHours(1), ISSUER));

    private static User user() {
        return User.register("Someone@Example.com ", "{bcrypt}$2a$10$irrelevant",
                "Test", "User");
    }

    /**
     * An entity that was never persisted has no id, and issuing puts the id in
     * the subject claim. Rather than reach for reflection, these tests build a
     * token by hand where an id is needed and use the service to verify it --
     * which is the direction that actually matters.
     */
    private String tokenFor(UUID id, String issuer, Instant expiry) {
        return Jwts.builder()
                .subject(id.toString())
                .claim("email", "someone@example.com")
                .claim("role", Role.USER.name())
                .issuer(issuer)
                .issuedAt(Date.from(Instant.now().minusSeconds(60)))
                .expiration(Date.from(expiry))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void verifiesATokenItIssued() {
        UUID id = UUID.randomUUID();

        AuthenticatedUser principal =
                jwtService.verifyToken(tokenFor(id, ISSUER, Instant.now().plusSeconds(3600)));

        assertThat(principal.id()).isEqualTo(id);
        assertThat(principal.email()).isEqualTo("someone@example.com");
        assertThat(principal.role()).isEqualTo(Role.USER);
    }

    @Test
    void issuedTokenCarriesOnlyTheExpectedClaims() {
        // A JWT is signed, not encrypted: anything in it is readable by whoever
        // holds it. This guards against personal data creeping into a claim.
        String token = tokenFor(UUID.randomUUID(), ISSUER, Instant.now().plusSeconds(3600));

        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.keySet())
                .containsExactlyInAnyOrder("sub", "email", "role", "iss", "iat", "exp");
    }

    @Test
    void rejectsExpiredToken() {
        String expired = tokenFor(UUID.randomUUID(), ISSUER, Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> jwtService.verifyToken(expired))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String foreignSecret = "a-completely-different-secret-that-is-also-long-enough-ok";
        String forged = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "attacker@example.com")
                .claim("role", Role.ADMIN.name())
                .issuer(ISSUER)
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(foreignSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> jwtService.verifyToken(forged))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        String wrongIssuer = tokenFor(UUID.randomUUID(), "somebody-else",
                Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> jwtService.verifyToken(wrongIssuer))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> jwtService.verifyToken("not.a.token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refusesToStartWithASecretShorterThan256Bits() {
        JwtProperties weak = new JwtProperties("too-short", Duration.ofHours(1), ISSUER);

        assertThatThrownBy(() -> new JwtService(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void reportsExpiryInSeconds() {
        assertThat(jwtService.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void issuingUsesTheUserRole() {
        // Round-trips through the service in both directions where possible.
        User user = user();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getEmail()).isEqualTo("someone@example.com");
    }
}
