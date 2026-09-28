package com.joblens.api.matching;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The engine's verdict on one job.
 *
 * <p>{@code scored} is false when not one criterion could be evaluated -- an
 * empty profile against a sparsely described job. That is reported as "no score"
 * rather than as zero, because zero reads as "terrible match" when the truth is
 * "we cannot tell", and the difference matters to a user deciding whether to
 * fill in their profile.
 *
 * @param score 0-100, meaningful only when {@code scored} is true
 */
public record MatchOutcome(
        UUID jobId,
        boolean scored,
        int score,
        List<CriterionOutcome> criteria
) {

    public static MatchOutcome unscored(UUID jobId, List<CriterionOutcome> criteria) {
        return new MatchOutcome(jobId, false, 0, criteria);
    }

    public Optional<CriterionOutcome> criterion(MatchCriterion criterion) {
        return criteria.stream().filter(c -> c.criterion() == criterion).findFirst();
    }

    /** Sort key for recommendations: unscored jobs rank below every scored one. */
    public int sortableScore() {
        return scored ? score : -1;
    }
}
