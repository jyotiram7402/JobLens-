package com.joblens.api.matching.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.joblens.api.matching.CriterionOutcome;

import java.util.List;

/**
 * One criterion's contribution, as it appears in a response.
 *
 * <p>{@code applicable: false} is a first-class state, not an absence. It means
 * there was not enough data to judge and the criterion was excluded from the
 * total -- along with its weight -- rather than scored zero. {@code maxScore}
 * is therefore 0 for those, which is what makes the arithmetic add up when a
 * client sums the breakdown.
 *
 * <p>{@code matched} and {@code missing} apply to skills only and are omitted
 * elsewhere.
 *
 * @param score     points earned, rounded for display
 * @param maxScore  points this criterion contributed to the achievable total
 * @param matched   whether it counted as a match (score at or above half)
 * @param reason    a short factual sentence, also used in the explanation
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CriterionScore(
        int score,
        int maxScore,
        boolean applicable,
        boolean matched,
        String reason,
        List<String> matchedSkills,
        List<String> missingSkills
) {

    public static CriterionScore from(CriterionOutcome outcome, int weight) {
        boolean isSkills = !outcome.matched().isEmpty() || !outcome.missing().isEmpty();
        return new CriterionScore(
                (int) Math.round(outcome.earnedPoints(weight)),
                outcome.availablePoints(weight),
                outcome.applicable(),
                outcome.applicable() && outcome.fraction() >= 0.5,
                outcome.reason(),
                isSkills ? outcome.matched() : null,
                isSkills ? outcome.missing() : null);
    }
}
