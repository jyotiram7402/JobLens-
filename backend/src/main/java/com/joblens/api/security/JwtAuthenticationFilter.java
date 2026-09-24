package com.joblens.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Turns a {@code Authorization: Bearer <token>} header into an authenticated
 * security context.
 *
 * <p>The sequence is the standard one: read the header, check the scheme, verify
 * the token, build the principal, put it in the context, carry on.
 *
 * <p>Two decisions worth stating:
 *
 * <ol>
 *   <li><b>No database read.</b> The principal is built from the token's claims.
 *       Every authenticated request would otherwise cost a query before it did
 *       any work, which on a 0.1 CPU free-tier container is most of the request
 *       budget. The trade-off -- a deactivated account keeps working until its
 *       token expires -- is covered by the short token lifetime, and documented
 *       in {@link JwtService}.</li>
 *   <li><b>A bad token does not fail the request here.</b> The failure is
 *       recorded as a request attribute and the chain continues unauthenticated,
 *       so authorization decides what happens next. A bad token on a public
 *       endpoint is then simply ignored rather than turning a working request
 *       into a 401, and {@link RestAuthenticationEntryPoint} still has the
 *       reason available when the endpoint does require authentication.</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String AUTHORIZATION_HEADER = "Authorization";
    static final String BEARER_PREFIX = "Bearer ";

    /** Set when a token was present but unusable; read by the entry point. */
    static final String FAILURE_ATTRIBUTE = "joblens.authFailure";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request);

        if (token != null) {
            try {
                AuthenticatedUser principal = jwtService.verifyToken(token);

                var authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority(principal.role().authority())));
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (TokenExpiredException | InvalidTokenException ex) {
                SecurityContextHolder.clearContext();
                request.setAttribute(FAILURE_ATTRIBUTE, ex);
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
