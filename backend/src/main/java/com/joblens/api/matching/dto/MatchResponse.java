package com.joblens.api.matching.dto;

import com.joblens.api.matching.CriterionOutcome;
import com.joblens.api.matching.MatchOutcome;
import com.joblens.api.matching.MatchingWeights;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Body of {@code GET /api/v1/jobs/{jobId}/match}.
 *
 * <p>Never just a number. The breakdown and the explanation are the product:
 * a bare "88%" is not something a user can act on, argue with, or learn from,
 * and JobLens is meant to tell people why a job suits them.
 *
 * @param scored      false when nothing could be compared; {@code score} is
 *                    then meaningless and should not be shown
 * @param score       0-100
 * @param maxScore    always 100; the per-criterion maxima sum to the achievable
 *                    total, which is lower when criteria were skipped
 * @param breakdown   keyed by criterion name
 * @param explanation sentences, in reading order
 */
public record MatchResponse(
        UUID jobId,
        boolean scored,
        int score,
        int maxScore,
        Map<String, CriterionScore> breakdown,
        List<String> explanation
) {

    public static MatchResponse of(MatchOutcome outcome, MatchingWeights weights,
                                   List<String> explanation) {
        // LinkedHashMap so the criteria always appear in the same order --
        // skills first, because that is what people look at.
        Map<String, CriterionScore> breakdown = new LinkedHashMap<>();
        for (CriterionOutcome criterion : outcome.criteria()) {
            breakdown.put(criterion.criterion().jsonName(),
                    CriterionScore.from(criterion, weights.weightOf(criterion.criterion())));
        }

        return new MatchResponse(outcome.jobId(), outcome.scored(), outcome.score(), 100,
                breakdown, explanation);
    }
}
