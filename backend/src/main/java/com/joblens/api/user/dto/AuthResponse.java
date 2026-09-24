package com.joblens.api.user.dto;

/**
 * Body of a successful login.
 *
 * @param accessToken the JWT, to be sent back as {@code Authorization: Bearer <token>}
 * @param tokenType   always {@code Bearer}; included so a client can build the
 *                    header without hard-coding the scheme
 * @param expiresIn   lifetime in seconds, so a client can refresh or warn before
 *                    it lapses rather than discovering expiry through a 401
 * @param user        enough to render a header without a second request
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    public static AuthResponse of(String accessToken, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
