package com.joblens.api.user.dto;

import com.joblens.api.user.domain.User;
import com.joblens.api.user.domain.UserProfile;

import java.util.UUID;

/**
 * Body of {@code GET /api/v1/users/me}: the account plus its profile in one
 * response, because that is what a client needs on load and two round trips to
 * a sleeping free-tier backend is two cold starts.
 */
public record CurrentUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserProfileResponse profile
) {

    public static CurrentUserResponse of(User user, UserProfile profile) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                UserProfileResponse.from(profile));
    }
}
