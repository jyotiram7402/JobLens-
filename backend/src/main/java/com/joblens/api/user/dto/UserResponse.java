package com.joblens.api.user.dto;

import com.joblens.api.user.domain.User;

import java.util.UUID;

/**
 * The safe public view of an account: identity and display name, nothing else.
 *
 * <p>No password hash, obviously, but also no {@code role} and no {@code active}
 * flag. Neither is information the frontend acts on in V1, and an authorization
 * decision the client can see is an authorization decision someone will try to
 * change.
 */
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(),
                user.getLastName());
    }
}
