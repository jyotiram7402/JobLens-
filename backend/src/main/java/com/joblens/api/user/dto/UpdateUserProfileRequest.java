package com.joblens.api.user.dto;

import com.joblens.api.user.domain.RemotePreference;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body of {@code PUT /api/v1/users/me/profile}.
 *
 * <p><b>There is no {@code userId} field, and there never will be.</b> The
 * profile being updated is always the one belonging to the authenticated
 * principal, taken from the security context. A client-supplied id here would
 * be an invitation to edit somebody else's profile, and no amount of checking it
 * afterwards is as safe as not accepting it.
 *
 * <p>Full replacement, PUT semantics: an omitted field is cleared. A null
 * collection is treated as empty, which is how a skill gets removed.
 *
 * <p>The collection size limits are not decoration. Without them a single
 * request could insert an unbounded number of rows, which on a 0.5 GB free-tier
 * database is a cheap way to fill it.
 */
public record UpdateUserProfileRequest(

        @Size(max = 200, message = "must be at most 200 characters")
        String headline,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String summary,

        @Min(value = 0, message = "must not be negative")
        @Max(value = 60, message = "must be at most 60")
        Integer yearsOfExperience,

        @Size(max = 150, message = "must be at most 150 characters")
        String currentRole,

        /* Null means "no preference", stored as ANY. */
        RemotePreference remotePreference,

        @Size(max = 20, message = "must contain at most 20 preferred roles")
        List<
                @NotBlank(message = "must not be blank")
                @Size(max = 150, message = "must be at most 150 characters")
                String> preferredRoles,

        @Size(max = 20, message = "must contain at most 20 preferred locations")
        List<
                @NotBlank(message = "must not be blank")
                @Size(max = 150, message = "must be at most 150 characters")
                String> preferredLocations,

        @Size(max = 50, message = "must contain at most 50 skills")
        List<
                @NotBlank(message = "must not be blank")
                @Size(max = 80, message = "must be at most 80 characters")
                String> skills
) {
}
