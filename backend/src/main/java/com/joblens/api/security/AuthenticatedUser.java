package com.joblens.api.security;

import com.joblens.api.user.domain.Role;

import java.util.UUID;

/**
 * The authenticated principal, built entirely from the claims in a verified
 * token.
 *
 * <p>Controllers get this from the security context and never accept an id from
 * the request body, which is what makes it impossible to address another user's
 * data by guessing their id.
 *
 * <p>Carries no password hash and nothing beyond what authorization needs.
 */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
