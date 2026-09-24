package com.joblens.api.user.dto;

import com.joblens.api.user.domain.RemotePreference;
import com.joblens.api.user.domain.UserProfile;

import java.util.List;

/**
 * A user's career profile as returned to them.
 *
 * <p>Collections are always present, empty rather than null, so the frontend can
 * iterate without a null check on every render.
 */
public record UserProfileResponse(
        String headline,
        String summary,
        Integer yearsOfExperience,
        String currentRole,
        RemotePreference remotePreference,
        List<String> preferredRoles,
        List<String> preferredLocations,
        List<String> skills
) {

    public static UserProfileResponse from(UserProfile profile) {
        return new UserProfileResponse(
                profile.getHeadline(),
                profile.getSummary(),
                profile.getYearsOfExperience(),
                profile.getCurrentJobTitle(),
                profile.getRemotePreference(),
                profile.getPreferredRoles(),
                profile.getPreferredLocations(),
                profile.getSkills());
    }
}
