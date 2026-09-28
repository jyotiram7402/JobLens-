package com.joblens.api.matching;

import java.util.List;

/**
 * What one criterion concluded.
 *
 * <p>Three states, and the third is the one that matters:
 *
 * <ul>
 *   <li><b>Scored</b> — there was enough data on both sides, and the criterion
 *       earned some fraction of its weight.</li>
 *   <li><b>Scored zero</b> — there was enough data, and it did not match.</li>
 *   <li><b>Not applicable</b> — there was not enough data to judge. This is
 *       <em>not</em> a zero: the criterion drops out of the total entirely,
 *       taking its weight with it.</li>
 * </ul>
 *
 * <p>Collapsing "no data" into "no match" is the single easiest way to make a
 * matching engine feel broken. A user who has not listed a preferred location
 * has not failed a location test; they have not taken one. Scoring them zero
 * would quietly punish an incomplete profile and make every score
 * incomparable with every other.
 *
 * @param criterion   which of the five
 * @param applicable  whether it could be judged at all
 * @param fraction    0.0–1.0 of the criterion's weight, meaningless when not
 *                    applicable
 * @param reason      why it is not applicable, or a short factual note about
 *                    how it matched; shown to the user
 * @param matched     skills the user has that the job asks for (skills only)
 * @param missing     skills the job asks for that the user does not have
 */
public record CriterionOutcome(
        MatchCriterion criterion,
        boolean applicable,
        double fraction,
        String reason,
        List<String> matched,
        List<String> missing
) {

    public static CriterionOutcome notApplicable(MatchCriterion criterion, String reason) {
        return new CriterionOutcome(criterion, false, 0.0, reason, List.of(), List.of());
    }

    public static CriterionOutcome scored(MatchCriterion criterion, double fraction, String reason) {
        return new CriterionOutcome(criterion, true, clamp(fraction), reason, List.of(), List.of());
    }

    public static CriterionOutcome scoredSkills(double fraction, String reason,
                                                List<String> matched, List<String> missing) {
        return new CriterionOutcome(MatchCriterion.SKILLS, true, clamp(fraction), reason,
                List.copyOf(matched), List.copyOf(missing));
    }

    /**
     * Guards the invariant the whole score depends on. A fraction outside 0–1
     * would let a criterion earn more than its weight and push the total past
     * 100, so it is clamped here rather than trusted from five call sites.
     */
    private static double clamp(double fraction) {
        return Math.max(0.0, Math.min(1.0, fraction));
    }

    /** Points earned, given the configured weight for this criterion. */
    public double earnedPoints(int weight) {
        return applicable ? fraction * weight : 0.0;
    }

    /** Points this criterion contributed to the achievable maximum. */
    public int availablePoints(int weight) {
        return applicable ? weight : 0;
    }
}
