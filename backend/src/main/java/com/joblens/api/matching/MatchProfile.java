package com.joblens.api.matching;

import com.joblens.api.user.domain.RemotePreference;

import java.util.Map;
import java.util.Set;

/**
 * Everything the engine needs to know about a user, and nothing else.
 *
 * <p>A value object rather than the {@code UserProfile} entity, so the engine
 * is a pure function: no JPA, no lazy loading, no database. That is what makes
 * the scoring rules testable in milliseconds and reviewable without tracing
 * through a persistence layer.
 *
 * @param skillsByNormalizedName    normalized name -> display name, so the
 *                                  explanation can say "Spring Boot" while the
 *                                  comparison uses "spring boot"
 * @param yearsOfExperience         null when the user has not said
 * @param preferredRolesNormalized  normalized preferred job titles
 * @param preferredLocationsNormalized normalized preferred locations
 * @param remotePreference          never null; {@code ANY} means no preference
 */
public record MatchProfile(
        Map<String, String> skillsByNormalizedName,
        Integer yearsOfExperience,
        Set<String> preferredRolesNormalized,
        Set<String> preferredLocationsNormalized,
        RemotePreference remotePreference
) {
}
