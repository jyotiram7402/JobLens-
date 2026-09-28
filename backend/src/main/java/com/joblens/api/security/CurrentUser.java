package com.joblens.api.security;

import org.springframework.util.Assert;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Reads the authenticated principal out of the security context.
 *
 * <p>Exists so controllers have one obvious way to answer "who is calling?" --
 * and so no controller is ever tempted to take that answer from a request body
 * or a path variable instead.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * @return the authenticated principal
     * @throws IllegalStateException if called on an unauthenticated request,
     *                               which would mean the endpoint is not
     *                               actually protected -- a configuration bug
     *                               that should be loud
     */
    public static AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Assert.state(authentication != null && authentication.isAuthenticated()
                        && authentication.getPrincipal() instanceof AuthenticatedUser,
                "No authenticated user in the security context. "
                        + "This endpoint should be behind authentication.");
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    /**
     * The authenticated principal, if there is one.
     *
     * <p>For endpoints that are public but behave differently for a signed-in
     * caller -- job search showing withdrawn positions, for instance. Returns
     * empty rather than throwing, because on those endpoints being anonymous is
     * a normal state and not a configuration bug.
     */
    public static Optional<AuthenticatedUser> optional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }
}
