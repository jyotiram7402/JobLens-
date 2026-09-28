package com.joblens.api.matching;

import com.joblens.api.job.domain.WorkMode;

import java.util.Map;
import java.util.UUID;

/**
 * Everything the engine needs to know about a job. See {@link MatchProfile} for
 * why this is a value object rather than the entity.
 *
 * @param skillsByNormalizedName normalized name -> display name of the skills
 *                               the opening asks for; empty when none are
 *                               recorded
 * @param experienceMin          null means unspecified, not zero
 * @param experienceMax          null means unspecified, not unbounded-at-zero
 * @param locationNormalized     null or blank when the opening does not say
 */
public record MatchJob(
        UUID id,
        String titleNormalized,
        Map<String, String> skillsByNormalizedName,
        Integer experienceMin,
        Integer experienceMax,
        String locationNormalized,
        WorkMode workMode
) {
}
