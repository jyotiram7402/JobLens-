package com.joblens.api.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How much each matching criterion is worth.
 *
 * <p>Every number the scoring engine uses lives here and nowhere else. Weights
 * scattered through scoring code are impossible to review, impossible to tune,
 * and turn "why did this score 72?" into an archaeology exercise.
 *
 * <p>Bound from {@code joblens.matching.weights.*}, so they can be adjusted per
 * environment without a rebuild -- useful when we start looking at real
 * profiles and find the balance wrong.
 *
 * <h2>Why these values</h2>
 *
 * <pre>
 * Skills       50   Can you do the job? Everything else is a preference;
 *                   this is the only criterion about capability, so it is
 *                   worth as much as the other four together.
 * Experience   20   A proxy for seniority. It matters, but it is a crude
 *                   one -- two years somewhere demanding beats five
 *                   somewhere idle -- so it does not outweigh skills.
 * Location     15   A hard blocker in practice when it is wrong, but it is
 *                   about circumstances rather than suitability.
 * Role         10   Title matching is the weakest signal here: titles are
 *                   inconsistent between companies, and a good match can be
 *                   called almost anything. Low weight reflects low trust.
 * Work mode     5   Real but narrow: three values, and hybrid is partially
 *                   compatible with both of the others.
 * ------------------
 * Total       100
 * </pre>
 *
 * <p>The total is not enforced to be 100. Scores are normalized against the
 * criteria that could actually be evaluated, so the weights only need to be
 * sensible relative to each other -- see {@code JobMatchingEngine}.
 */
@ConfigurationProperties(prefix = "joblens.matching.weights")
public record MatchingWeights(
        int skills,
        int experience,
        int location,
        int role,
        int workMode
) {

    /**
     * The documented defaults, used by tests and as the fallback if the
     * properties are absent.
     */
    public static MatchingWeights defaults() {
        return new MatchingWeights(50, 20, 15, 10, 5);
    }

    public int weightOf(MatchCriterion criterion) {
        return switch (criterion) {
            case SKILLS -> skills;
            case EXPERIENCE -> experience;
            case LOCATION -> location;
            case ROLE -> role;
            case WORK_MODE -> workMode;
        };
    }
}
