package com.joblens.api.security;

import com.joblens.api.user.domain.Role;
import com.joblens.api.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

/**
 * Issues and verifies access tokens.
 *
 * <p>All JWT handling lives here. Controllers and filters deal in
 * {@link AuthenticatedUser}, never in claims, so there is exactly one place
 * where a signature is checked and exactly one place to audit.
 *
 * <h2>Claims</h2>
 *
 * <pre>
 * sub    user id
 * email  so the principal can be built without a database read
 * role   so authorization works without a database read
 * iss    rejects correctly-signed tokens minted by something else
 * iat    issued at
 * exp    expiry
 * </pre>
 *
 * <p>Nothing else. A JWT is signed but not encrypted -- anyone holding it can
 * read every claim -- so it carries the minimum needed to authorize a request
 * and no personal data beyond the email that identifies the account.
 *
 * <h2>Stateless verification</h2>
 *
 * <p>Verifying a token touches no database. That is the point of a stateless
 * token and it is what keeps a 0.1 CPU container able to serve requests.
 *
 * <p>The cost is honest: a token stays valid until it expires, so deactivating
 * an account does not cut off a session already in progress. The mitigation is a
 * short lifetime -- one hour by default. Immediate revocation needs a
 * denylist or per-request lookups, which is a real feature with real
 * infrastructure behind it, not something to bolt on here.
 */
@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    /**
     * HS256 requires a key of at least 256 bits. A shorter secret would either
     * be rejected by the library at signing time -- a startup failure is far
     * better than that -- or, worse, silently weaken every token.
     */
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least %d bytes (%d supplied). Generate one with: openssl rand -base64 48"
                            .formatted(MINIMUM_SECRET_BYTES, keyBytes.length));
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.properties = properties;
    }

    /**
     * Issues a token for a user who has just proved who they are.
     */
    public String issueToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.expiration());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies a token and turns it into a principal.
     *
     * <p>Signature, issuer and expiry are all checked by the parser. A token
     * that fails any of them throws, and the caller reports 401 -- a token is
     * either trustworthy or it is not.
     *
     * @throws TokenExpiredException if the token was valid but has lapsed
     * @throws InvalidTokenException for anything else: bad signature, wrong
     *                               issuer, malformed, unparseable claims
     */
    public AuthenticatedUser verifyToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    claims.get("email", String.class),
                    Role.valueOf(claims.get("role", String.class)));

        } catch (ExpiredJwtException ex) {
            throw new TokenExpiredException();
        } catch (JwtException | IllegalArgumentException ex) {
            // Deliberately does not include the token or the library message in
            // anything the caller sees.
            throw new InvalidTokenException();
        }
    }

    /**
     * Token lifetime in seconds, for the {@code expiresIn} field of a login
     * response.
     */
    public long expiresInSeconds() {
        return properties.expiration().toSeconds();
    }
}
