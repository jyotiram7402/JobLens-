package com.joblens.api.user.dto;

/**
 * Body of a successful registration.
 *
 * <p>Carries no token. Registration does not log the user in -- see
 * {@code AuthService} for why -- so the client sends the credentials it already
 * has to {@code /auth/login} next.
 */
public record RegistrationResponse(
        UserResponse user,
        String message
) {

    public static RegistrationResponse of(UserResponse user) {
        return new RegistrationResponse(user, "Registration successful");
    }
}
