package com.joblens.api.matching;

import com.joblens.api.job.domain.WorkMode;
import com.joblens.api.user.domain.RemotePreference;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores one job against one profile.
 *
 * <p><b>Pure.</b> No database, no clock, no randomness, no network. The same
 * inputs always give the same score, which is what makes the result something a
 * user can be shown and argue with, and what makes the rules testable in
 * milliseconds.
 *
 * <p>No AI. Every rule below is a few lines of arithmetic that can be read,
 * disputed and changed. A model would score better on average and would not be
 * able to say why, and "why did this job score 72?" is a question JobLens has
 * to answer.
 *
 * <h2>The formula</h2>
 *
 * <pre>
 *   for each of the five criteria:
 *       if there is enough data on both sides:
 *           earned    += fraction(0..1) * weight
 *           available += weight
 *
 *   score = round(earned / available * 100)
 * </pre>
 *
 * <p>The normalization is the important part. A criterion that cannot be judged
 * is removed from the denominator instead of scoring zero, so a user who has not
 * filled in their preferred locations is not punished for it — they are simply
 * matched on the other four. Without this, an empty profile would score near
 * zero against every job and the number would mean nothing.
 *
 * <p>The cost of that choice, stated plainly: two scores are only strictly
 * comparable when they were computed over the same set of criteria. A 90 earned
 * on skills alone is a thinner claim than a 90 earned on all five. The response
 * therefore always reports which criteria applied, and the recommendation
 * endpoint sorts within one profile, where the applicable set rarely varies.
 */
@Component
@EnableConfigurationProperties(MatchingWeights.class)
public class JobMatchingEngine {

    /**
     * Below this, a role title overlap is treated as coincidence rather than a
     * match. "Java Backend Developer" against "Frontend Developer" shares one
     * word in three, and calling that a match would make role scoring noise.
     */
    private static final double ROLE_MATCH_FLOOR = 0.5;

    /** Years short of a job's minimum at which experience scores nothing. */
    private static final int EXPERIENCE_SHORTFALL_LIMIT = 3;

    /** The least an overqualified candidate can score on experience. */
    private static final double OVERQUALIFIED_FLOOR = 0.5;

    /** How much each year above a job's maximum costs. */
    private static final double OVERQUALIFIED_STEP = 0.1;

    private final MatchingWeights weights;

    public JobMatchingEngine(MatchingWeights weights) {
        this.weights = weights;
    }

    public MatchingWeights weights() {
        return weights;
    }

    /**
     * Scores a job against a profile.
     *
     * @return the outcome of every criterion, the normalized score, and whether
     *         a score could be produced at all
     */
    public MatchOutcome score(MatchProfile profile, MatchJob job) {
        List<CriterionOutcome> outcomes = List.of(
                scoreSkills(profile, job),
                scoreExperience(profile, job),
                scoreLocation(profile, job),
                scoreRole(profile, job),
                scoreWorkMode(profile, job));

        double earned = 0.0;
        int available = 0;
        for (CriterionOutcome outcome : outcomes) {
            int weight = weights.weightOf(outcome.criterion());
            earned += outcome.earnedPoints(weight);
            available += outcome.availablePoints(weight);
        }

        if (available == 0) {
            // Nothing could be judged. Reporting 0 would read as "terrible
            // match" when it means "we cannot tell", so no score is produced.
            return MatchOutcome.unscored(job.id(), outcomes);
        }

        int score = (int) Math.round(earned / available * 100.0);
        return new MatchOutcome(job.id(), true, score, outcomes);
    }

    // --- skills -----------------------------------------------------------

    /**
     * The fraction of the job's required skills the user has.
     *
     * <pre>
     *   job asks for 5, user has 4  ->  4/5 = 0.8  ->  40 of 50 points
     * </pre>
     *
     * <p>Not applicable when either side has no skills recorded. A job with no
     * structured skills is common — nothing extracts them from a description
     * yet — and awarding a perfect score for absent data would make every such
     * job look like a perfect match. Awarding zero would be just as wrong: the
     * job has not failed, it simply has not been described. It drops out.
     *
     * <p>Skills the user has that the job does not ask for are neither rewarded
     * nor penalised. Knowing Kubernetes does not make you a better fit for a job
     * that never mentions it.
     */
    private CriterionOutcome scoreSkills(MatchProfile profile, MatchJob job) {
        Map<String, String> required = job.skillsByNormalizedName();
        Map<String, String> held = profile.skillsByNormalizedName();

        if (required.isEmpty()) {
            return CriterionOutcome.notApplicable(MatchCriterion.SKILLS,
                    "This job does not list the skills it needs, so skills could not be compared.");
        }
        if (held.isEmpty()) {
            return CriterionOutcome.notApplicable(MatchCriterion.SKILLS,
                    "You have not added any skills to your profile, so skills could not be compared.");
        }

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, String> requirement : required.entrySet()) {
            if (held.containsKey(requirement.getKey())) {
                // The user's own spelling, so the explanation reflects how they
                // described themselves.
                matched.add(held.get(requirement.getKey()));
            } else {
                missing.add(requirement.getValue());
            }
        }

        double fraction = (double) matched.size() / required.size();
        String reason = "You have %d of the %d skills this job asks for."
                .formatted(matched.size(), required.size());

        return CriterionOutcome.scoredSkills(fraction, reason, matched, missing);
    }

    // --- experience -------------------------------------------------------

    /**
     * How well the user's years fit the job's stated range.
     *
     * <pre>
     *   inside the range              -> 1.0
     *   short of the minimum          -> 1 - (shortfall / 3), floored at 0
     *   above the maximum             -> 1 - (excess * 0.1), floored at 0.5
     * </pre>
     *
     * <p>The two directions are deliberately asymmetric. Being under-qualified
     * is a real obstacle and closes quickly: three years short of the minimum
     * scores nothing. Being over-qualified is not an obstacle to doing the work
     * at all — it is a risk that the employer passes or the salary does not fit
     * — so it never falls below half marks however senior the candidate.
     *
     * <p>Not applicable when the job states no range or the user has not given
     * their experience. An unstated range is not "any experience welcome", and
     * treating it as a match would hand full marks to every job that skipped
     * the field.
     */
    private CriterionOutcome scoreExperience(MatchProfile profile, MatchJob job) {
        Integer years = profile.yearsOfExperience();
        Integer min = job.experienceMin();
        Integer max = job.experienceMax();

        if (min == null && max == null) {
            return CriterionOutcome.notApplicable(MatchCriterion.EXPERIENCE,
                    "This job does not state an experience range, so experience could not be compared.");
        }
        if (years == null) {
            return CriterionOutcome.notApplicable(MatchCriterion.EXPERIENCE,
                    "You have not added your years of experience, so experience could not be compared.");
        }

        if (min != null && years < min) {
            int shortfall = min - years;
            double fraction = Math.max(0.0, 1.0 - (double) shortfall / EXPERIENCE_SHORTFALL_LIMIT);
            return CriterionOutcome.scored(MatchCriterion.EXPERIENCE, fraction,
                    "This job asks for at least %d years and you have %d."
                            .formatted(min, years));
        }

        if (max != null && years > max) {
            int excess = years - max;
            double fraction = Math.max(OVERQUALIFIED_FLOOR, 1.0 - excess * OVERQUALIFIED_STEP);
            return CriterionOutcome.scored(MatchCriterion.EXPERIENCE, fraction,
                    "You have %d years, more than the %d this job is aimed at."
                            .formatted(years, max));
        }

        return CriterionOutcome.scored(MatchCriterion.EXPERIENCE, 1.0,
                "Your %d years of experience fit what this job is asking for.".formatted(years));
    }

    // --- location ---------------------------------------------------------

    /**
     * Whether any of the user's preferred locations appears in the job's.
     *
     * <p>Matching is on whole words, not raw substrings: {@code "pune"} matches
     * {@code "pune maharashtra india"} and {@code "hybrid pune"}, but would not
     * match a longer word that merely starts with those letters. Substring
     * matching is what produces the classic false positive where a three-letter
     * city matches half the table.
     *
     * <p>One special case: a user who prefers "remote" matches a remote job
     * whatever its location text says, because a remote job's location is
     * usually an office address that is beside the point.
     *
     * <p>Binary — either somewhere they want or not. Partial geographic scoring
     * needs real distances, which is a later step.
     *
     * <p>Not applicable when either side is silent. A user with no stated
     * preference has not failed a location test, and a job with no location has
     * not either.
     */
    private CriterionOutcome scoreLocation(MatchProfile profile, MatchJob job) {
        Set<String> preferred = profile.preferredLocationsNormalized();
        String jobLocation = job.locationNormalized();

        if (jobLocation == null || jobLocation.isBlank()) {
            return CriterionOutcome.notApplicable(MatchCriterion.LOCATION,
                    "This job does not say where it is based, so location could not be compared.");
        }
        if (preferred.isEmpty()) {
            return CriterionOutcome.notApplicable(MatchCriterion.LOCATION,
                    "You have not added any preferred locations, so location could not be compared.");
        }

        boolean remoteJobAndRemoteWanted =
                job.workMode() == WorkMode.REMOTE && preferred.contains("remote");

        for (String preference : preferred) {
            if (remoteJobAndRemoteWanted || containsPhrase(jobLocation, preference)) {
                return CriterionOutcome.scored(MatchCriterion.LOCATION, 1.0,
                        "This job is in a location you are looking for.");
            }
        }

        return CriterionOutcome.scored(MatchCriterion.LOCATION, 0.0,
                "This job is not in any of the locations you listed.");
    }

    // --- role -------------------------------------------------------------

    /**
     * How much of a preferred job title the job's title covers.
     *
     * <pre>
     *   preferred "java backend developer"
     *   title     "senior java backend developer"   -> 3/3 words -> 1.0
     *   title     "java developer"                  -> 2/3 words -> 0.67
     *   title     "frontend developer"              -> 1/3 words -> below floor -> 0.0
     * </pre>
     *
     * <p>Word overlap rather than substring containment, and only in one
     * direction: what fraction of the words the <em>user</em> asked for appear
     * in the title. Extra words in the title are free, which is what lets
     * "Senior" and "II" and a team name not spoil a match.
     *
     * <p>Below {@link #ROLE_MATCH_FLOOR} the overlap is treated as coincidence
     * and scores nothing, because almost every engineering title shares the
     * word "developer" or "engineer" with almost every other.
     *
     * <p>Not applicable when the user has listed no preferred roles.
     */
    private CriterionOutcome scoreRole(MatchProfile profile, MatchJob job) {
        Set<String> preferredRoles = profile.preferredRolesNormalized();

        if (preferredRoles.isEmpty()) {
            return CriterionOutcome.notApplicable(MatchCriterion.ROLE,
                    "You have not added any preferred roles, so the job title could not be compared.");
        }

        Set<String> titleWords = wordsOf(job.titleNormalized());
        double best = 0.0;
        for (String role : preferredRoles) {
            Set<String> roleWords = wordsOf(role);
            if (roleWords.isEmpty()) {
                continue;
            }
            long overlap = roleWords.stream().filter(titleWords::contains).count();
            best = Math.max(best, (double) overlap / roleWords.size());
        }

        if (best < ROLE_MATCH_FLOOR) {
            return CriterionOutcome.scored(MatchCriterion.ROLE, 0.0,
                    "This job title does not look like the roles you are after.");
        }

        String reason = best == 1.0
                ? "This job title matches a role you are looking for."
                : "This job title partly matches a role you are looking for.";
        return CriterionOutcome.scored(MatchCriterion.ROLE, best, reason);
    }

    // --- work mode --------------------------------------------------------

    /**
     * How well the job's working arrangement fits the user's preference.
     *
     * <pre>
     *            job REMOTE   job HYBRID   job ONSITE
     *   REMOTE       1.0          0.5          0.0
     *   HYBRID       0.5          1.0          0.5
     *   ONSITE       0.0          0.5          1.0
     * </pre>
     *
     * <p>Hybrid is the middle ground in both directions, so every pairing
     * involving it scores half rather than zero — someone who wants hybrid can
     * usually live with either extreme, and a hybrid job partly satisfies
     * someone who wanted one or the other. Remote against onsite is the only
     * genuine contradiction.
     *
     * <p>Not applicable when the user's preference is {@code ANY}, which means
     * they have not expressed one. Awarding full marks for indifference would
     * quietly advantage users who skipped the question over users who answered
     * it.
     */
    private CriterionOutcome scoreWorkMode(MatchProfile profile, MatchJob job) {
        RemotePreference preference = profile.remotePreference();

        if (preference == null || preference == RemotePreference.ANY) {
            return CriterionOutcome.notApplicable(MatchCriterion.WORK_MODE,
                    "You have not said how you want to work, so the working arrangement could not be compared.");
        }

        WorkMode mode = job.workMode();
        double fraction;
        if (matchesExactly(preference, mode)) {
            fraction = 1.0;
        } else if (preference == RemotePreference.HYBRID || mode == WorkMode.HYBRID) {
            fraction = 0.5;
        } else {
            fraction = 0.0;
        }

        String reason = switch ((int) (fraction * 2)) {
            case 2 -> "This job's working arrangement is the one you prefer.";
            case 1 -> "This job's working arrangement is close to the one you prefer.";
            default -> "This job's working arrangement is not the one you prefer.";
        };
        return CriterionOutcome.scored(MatchCriterion.WORK_MODE, fraction, reason);
    }

    private static boolean matchesExactly(RemotePreference preference, WorkMode mode) {
        return switch (preference) {
            case REMOTE -> mode == WorkMode.REMOTE;
            case HYBRID -> mode == WorkMode.HYBRID;
            case ONSITE -> mode == WorkMode.ONSITE;
            case ANY -> false;
        };
    }

    // --- helpers ----------------------------------------------------------

    /**
     * Whole-word phrase containment. Both arguments are already normalized, so
     * splitting on a single space is enough and no further cleaning is needed.
     */
    private static boolean containsPhrase(String haystack, String needle) {
        if (needle.isBlank()) {
            return false;
        }
        // Padding with spaces turns "contains substring" into "contains whole
        // words", without a regular expression to get wrong.
        return (" " + haystack + " ").contains(" " + needle + " ")
                || haystack.equals(needle);
    }

    private static Set<String> wordsOf(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.asList(normalizedText.split(" ")));
    }
}
