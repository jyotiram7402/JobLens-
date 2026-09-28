package com.joblens.api.matching;

import com.joblens.api.job.domain.WorkMode;
import com.joblens.api.user.domain.RemotePreference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scoring rules.
 *
 * <p>These are the most valuable tests in the project. The engine is pure and
 * deterministic, so every rule can be pinned exactly — and because the score is
 * shown to users with an explanation attached, a rule that quietly changes is a
 * rule that starts lying to them.
 */
class JobMatchingEngineTest {

    private final JobMatchingEngine engine = new JobMatchingEngine(MatchingWeights.defaults());

    // --- fixtures ---------------------------------------------------------

    private static Map<String, String> skills(String... displayNames) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : displayNames) {
            map.put(name.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim(), name);
        }
        return map;
    }

    private static MatchProfile profile(Map<String, String> skills, Integer years,
                                        Set<String> roles, Set<String> locations,
                                        RemotePreference preference) {
        return new MatchProfile(skills, years, roles, locations, preference);
    }

    /** A profile with something in every category. */
    private static MatchProfile fullProfile() {
        return profile(skills("Java", "Spring Boot", "Docker", "React"), 3,
                Set.of("java backend developer"), Set.of("pune"), RemotePreference.HYBRID);
    }

    private static MatchJob job(Map<String, String> skills, Integer expMin, Integer expMax,
                                String locationNormalized, WorkMode workMode, String titleNormalized) {
        return new MatchJob(UUID.randomUUID(), titleNormalized, skills, expMin, expMax,
                locationNormalized, workMode);
    }

    /** A job that matches {@link #fullProfile()} perfectly. */
    private static MatchJob perfectJob() {
        return job(skills("Java", "Spring Boot", "Docker"), 2, 5, "pune maharashtra india",
                WorkMode.HYBRID, "senior java backend developer");
    }

    private static double fractionOf(MatchOutcome outcome, MatchCriterion criterion) {
        return outcome.criterion(criterion).orElseThrow().fraction();
    }

    private static boolean applicable(MatchOutcome outcome, MatchCriterion criterion) {
        return outcome.criterion(criterion).orElseThrow().applicable();
    }

    // --- the invariant ----------------------------------------------------

    @Test
    void scoreIsAlwaysWithinZeroAndOneHundred() {
        // Every shape of input that could plausibly push the arithmetic out of
        // range: nothing matching, everything matching, and absurd experience.
        MatchOutcome worst = engine.score(
                profile(skills("COBOL"), 0, Set.of("welder"), Set.of("reykjavik"),
                        RemotePreference.ONSITE),
                job(skills("Java", "Rust"), 20, 30, "sydney", WorkMode.REMOTE, "rust engineer"));
        MatchOutcome best = engine.score(fullProfile(), perfectJob());

        assertThat(worst.score()).isBetween(0, 100);
        assertThat(best.score()).isBetween(0, 100);
    }

    @Test
    void aPerfectMatchScoresOneHundred() {
        MatchOutcome outcome = engine.score(fullProfile(), perfectJob());

        assertThat(outcome.scored()).isTrue();
        assertThat(outcome.score()).isEqualTo(100);
    }

    @Test
    void nothingMatchingScoresZeroRatherThanFailing() {
        MatchOutcome outcome = engine.score(
                profile(skills("COBOL"), 0, Set.of("welder"), Set.of("reykjavik"),
                        RemotePreference.ONSITE),
                job(skills("Java"), 10, 15, "sydney", WorkMode.REMOTE, "java architect"));

        assertThat(outcome.scored()).isTrue();
        assertThat(outcome.score()).isZero();
    }

    // --- skills -----------------------------------------------------------

    @Nested
    class Skills {

        @Test
        void allRequiredSkillsHeldScoresFull() {
            MatchOutcome outcome = engine.score(fullProfile(),
                    job(skills("Java", "Spring Boot"), null, null, null, WorkMode.HYBRID, "engineer"));

            assertThat(fractionOf(outcome, MatchCriterion.SKILLS)).isEqualTo(1.0);
        }

        @Test
        void partialMatchScoresTheFraction() {
            // Job asks for 4, user has 3 of them.
            MatchOutcome outcome = engine.score(fullProfile(),
                    job(skills("Java", "Spring Boot", "Docker", "PostgreSQL"), null, null,
                            null, WorkMode.HYBRID, "engineer"));

            assertThat(fractionOf(outcome, MatchCriterion.SKILLS)).isEqualTo(0.75);
        }

        @Test
        void noOverlapScoresZeroButStillCounts() {
            MatchOutcome outcome = engine.score(fullProfile(),
                    job(skills("COBOL", "Fortran"), null, null, null, WorkMode.HYBRID, "engineer"));

            assertThat(applicable(outcome, MatchCriterion.SKILLS)).isTrue();
            assertThat(fractionOf(outcome, MatchCriterion.SKILLS)).isZero();
        }

        @Test
        void reportsWhichSkillsMatchedAndWhichAreMissing() {
            MatchOutcome outcome = engine.score(fullProfile(),
                    job(skills("Java", "Spring Boot", "PostgreSQL"), null, null, null,
                            WorkMode.HYBRID, "engineer"));

            CriterionOutcome skills = outcome.criterion(MatchCriterion.SKILLS).orElseThrow();
            assertThat(skills.matched()).containsExactlyInAnyOrder("Java", "Spring Boot");
            assertThat(skills.missing()).containsExactly("PostgreSQL");
        }

        @Test
        void extraSkillsTheJobDoesNotWantAreNeitherRewardedNorPenalised() {
            // The profile has React; the job never mentions it.
            MatchOutcome outcome = engine.score(fullProfile(),
                    job(skills("Java"), null, null, null, WorkMode.HYBRID, "engineer"));

            assertThat(fractionOf(outcome, MatchCriterion.SKILLS)).isEqualTo(1.0);
            assertThat(outcome.criterion(MatchCriterion.SKILLS).orElseThrow().missing()).isEmpty();
        }

        @Test
        void caseAndSpacingDifferencesStillMatch() {
            // Comparison is on the normalized key, so these are one skill.
            Map<String, String> jobSkills = new LinkedHashMap<>();
            jobSkills.put("spring boot", "SPRING BOOT");

            MatchOutcome outcome = engine.score(fullProfile(),
                    job(jobSkills, null, null, null, WorkMode.HYBRID, "engineer"));

            assertThat(fractionOf(outcome, MatchCriterion.SKILLS)).isEqualTo(1.0);
        }

        @Test
        void explanationUsesTheUsersOwnSpellingOfASkill() {
            Map<String, String> jobSkills = new LinkedHashMap<>();
            jobSkills.put("spring boot", "SPRING BOOT");

            MatchOutcome outcome = engine.score(fullProfile(),
                    job(jobSkills, null, null, null, WorkMode.HYBRID, "engineer"));

            assertThat(outcome.criterion(MatchCriterion.SKILLS).orElseThrow().matched())
                    .containsExactly("Spring Boot");
        }

        @Test
        void jobWithNoSkillsIsNotApplicableRatherThanAPerfectScore() {
            // Awarding full marks for absent data would make every
            // under-described job look ideal.
            MatchOutcome outcome = engine.score(fullProfile(),
                    job(Map.of(), 2, 5, "pune", WorkMode.HYBRID, "java backend developer"));

            assertThat(applicable(outcome, MatchCriterion.SKILLS)).isFalse();
        }

        @Test
        void userWithNoSkillsIsNotApplicableRatherThanZero() {
            MatchProfile noSkills = profile(Map.of(), 3, Set.of("java backend developer"),
                    Set.of("pune"), RemotePreference.HYBRID);

            MatchOutcome outcome = engine.score(noSkills, perfectJob());

            assertThat(applicable(outcome, MatchCriterion.SKILLS)).isFalse();
            // And the other four still produce a score.
            assertThat(outcome.scored()).isTrue();
            assertThat(outcome.score()).isEqualTo(100);
        }
    }

    // --- experience -------------------------------------------------------

    @Nested
    class Experience {

        private MatchOutcome withYearsAgainst(Integer years, Integer min, Integer max) {
            MatchProfile profile = profile(skills("Java"), years, Set.of(), Set.of(),
                    RemotePreference.ANY);
            return engine.score(profile, job(skills("Java"), min, max, null, WorkMode.HYBRID,
                    "engineer"));
        }

        @ParameterizedTest
        @CsvSource({"1,1,4", "2,1,4", "4,1,4", "3,3,3"})
        void insideTheRangeScoresFull(int years, int min, int max) {
            assertThat(fractionOf(withYearsAgainst(years, min, max), MatchCriterion.EXPERIENCE))
                    .isEqualTo(1.0);
        }

        @Test
        void openEndedRangesAreSatisfiedFromTheirOneBound() {
            assertThat(fractionOf(withYearsAgainst(10, 3, null), MatchCriterion.EXPERIENCE))
                    .isEqualTo(1.0);
            assertThat(fractionOf(withYearsAgainst(1, null, 4), MatchCriterion.EXPERIENCE))
                    .isEqualTo(1.0);
        }

        @ParameterizedTest
        @CsvSource({
                // years, min, max, expected fraction: 1 - shortfall/3
                "3, 4, 8, 0.6666666666666667",
                "2, 4, 8, 0.33333333333333337",
                "1, 4, 8, 0.0",
                "0, 4, 8, 0.0"
        })
        void belowTheMinimumFallsAwayOverThreeYears(int years, int min, int max, double expected) {
            assertThat(fractionOf(withYearsAgainst(years, min, max), MatchCriterion.EXPERIENCE))
                    .isCloseTo(expected, org.assertj.core.data.Offset.offset(0.0001));
        }

        @ParameterizedTest
        @CsvSource({
                // Over-qualification is penalised gently and never below 0.5:
                // having too much experience does not stop you doing the work.
                "5, 1, 4, 0.9",
                "8, 1, 4, 0.6",
                "20, 1, 4, 0.5",
                "60, 1, 4, 0.5"
        })
        void aboveTheMaximumIsPenalisedGentlyAndFloorsAtHalf(int years, int min, int max,
                                                             double expected) {
            assertThat(fractionOf(withYearsAgainst(years, min, max), MatchCriterion.EXPERIENCE))
                    .isCloseTo(expected, org.assertj.core.data.Offset.offset(0.0001));
        }

        @Test
        void zeroExperienceAgainstAZeroMinimumIsAFullMatch() {
            assertThat(fractionOf(withYearsAgainst(0, 0, 2), MatchCriterion.EXPERIENCE))
                    .isEqualTo(1.0);
        }

        @Test
        void jobWithNoRangeIsNotApplicableRatherThanFullMarks() {
            assertThat(applicable(withYearsAgainst(3, null, null), MatchCriterion.EXPERIENCE))
                    .isFalse();
        }

        @Test
        void userWithNoStatedExperienceIsNotApplicableRatherThanZero() {
            assertThat(applicable(withYearsAgainst(null, 2, 5), MatchCriterion.EXPERIENCE))
                    .isFalse();
        }
    }

    // --- location ---------------------------------------------------------

    @Nested
    class Location {

        private MatchOutcome withLocations(Set<String> preferred, String jobLocation,
                                           WorkMode mode) {
            MatchProfile profile = profile(skills("Java"), null, Set.of(), preferred,
                    RemotePreference.ANY);
            return engine.score(profile,
                    job(skills("Java"), null, null, jobLocation, mode, "engineer"));
        }

        @Test
        void exactMatch() {
            assertThat(fractionOf(withLocations(Set.of("pune"), "pune", WorkMode.ONSITE),
                    MatchCriterion.LOCATION)).isEqualTo(1.0);
        }

        @Test
        void matchesAWordInsideALongerLocation() {
            assertThat(fractionOf(
                    withLocations(Set.of("pune"), "pune maharashtra india", WorkMode.ONSITE),
                    MatchCriterion.LOCATION)).isEqualTo(1.0);
            assertThat(fractionOf(
                    withLocations(Set.of("pune"), "hybrid pune", WorkMode.HYBRID),
                    MatchCriterion.LOCATION)).isEqualTo(1.0);
        }

        @Test
        void matchesAMultiWordPlaceName() {
            assertThat(fractionOf(
                    withLocations(Set.of("new york"), "new york ny united states", WorkMode.ONSITE),
                    MatchCriterion.LOCATION)).isEqualTo(1.0);
        }

        @Test
        void anyOneOfSeveralPreferencesIsEnough() {
            assertThat(fractionOf(
                    withLocations(Set.of("mumbai", "pune", "remote"), "pune india", WorkMode.ONSITE),
                    MatchCriterion.LOCATION)).isEqualTo(1.0);
        }

        @Test
        void doesNotMatchAWordThatMerelyStartsTheSame() {
            // Substring matching would call this a match. Whole-word matching
            // does not, which is the point.
            assertThat(fractionOf(
                    withLocations(Set.of("pune"), "punegar district", WorkMode.ONSITE),
                    MatchCriterion.LOCATION)).isZero();
        }

        @Test
        void noMatchScoresZeroButStillCounts() {
            MatchOutcome outcome = withLocations(Set.of("pune"), "berlin germany", WorkMode.ONSITE);

            assertThat(applicable(outcome, MatchCriterion.LOCATION)).isTrue();
            assertThat(fractionOf(outcome, MatchCriterion.LOCATION)).isZero();
        }

        @Test
        void wantingRemoteMatchesARemoteJobWhateverItsAddressSays() {
            assertThat(fractionOf(
                    withLocations(Set.of("remote"), "head office berlin", WorkMode.REMOTE),
                    MatchCriterion.LOCATION)).isEqualTo(1.0);
        }

        @Test
        void userWithNoPreferenceIsNotPenalised() {
            assertThat(applicable(withLocations(Set.of(), "pune india", WorkMode.ONSITE),
                    MatchCriterion.LOCATION)).isFalse();
        }

        @Test
        void jobWithNoLocationIsNotTreatedAsAMatch() {
            assertThat(applicable(withLocations(Set.of("pune"), null, WorkMode.ONSITE),
                    MatchCriterion.LOCATION)).isFalse();
            assertThat(applicable(withLocations(Set.of("pune"), "   ", WorkMode.ONSITE),
                    MatchCriterion.LOCATION)).isFalse();
        }
    }

    // --- role -------------------------------------------------------------

    @Nested
    class Role {

        private MatchOutcome withRoles(Set<String> preferred, String jobTitle) {
            MatchProfile profile = profile(skills("Java"), null, preferred, Set.of(),
                    RemotePreference.ANY);
            return engine.score(profile,
                    job(skills("Java"), null, null, null, WorkMode.HYBRID, jobTitle));
        }

        @Test
        void identicalTitleScoresFull() {
            assertThat(fractionOf(withRoles(Set.of("java backend developer"),
                    "java backend developer"), MatchCriterion.ROLE)).isEqualTo(1.0);
        }

        @Test
        void extraWordsInTheJobTitleDoNotSpoilTheMatch() {
            // "Senior", team names and level suffixes are free.
            assertThat(fractionOf(withRoles(Set.of("java backend developer"),
                    "senior java backend developer platform team"),
                    MatchCriterion.ROLE)).isEqualTo(1.0);
        }

        @Test
        void partialOverlapAboveTheFloorScoresProportionally() {
            // 2 of the 3 words the user asked for.
            assertThat(fractionOf(withRoles(Set.of("java backend developer"), "java developer"),
                    MatchCriterion.ROLE))
                    .isCloseTo(0.6667, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        void weakOverlapIsTreatedAsCoincidenceAndScoresZero() {
            // One shared word in three. Nearly every engineering title shares
            // "developer" with nearly every other, so this must not count.
            assertThat(fractionOf(withRoles(Set.of("java backend developer"), "frontend developer"),
                    MatchCriterion.ROLE)).isZero();
        }

        @Test
        void noOverlapScoresZero() {
            assertThat(fractionOf(withRoles(Set.of("java backend developer"), "chef de partie"),
                    MatchCriterion.ROLE)).isZero();
        }

        @Test
        void theBestOfSeveralPreferredRolesIsUsed() {
            assertThat(fractionOf(
                    withRoles(Set.of("chef de partie", "java backend developer"),
                            "java backend developer"),
                    MatchCriterion.ROLE)).isEqualTo(1.0);
        }

        @Test
        void userWithNoPreferredRoleIsNotPenalised() {
            assertThat(applicable(withRoles(Set.of(), "java backend developer"),
                    MatchCriterion.ROLE)).isFalse();
        }
    }

    // --- work mode --------------------------------------------------------

    @Nested
    class WorkModeMatching {

        private MatchOutcome with(RemotePreference preference, WorkMode mode) {
            MatchProfile profile = profile(skills("Java"), null, Set.of(), Set.of(), preference);
            return engine.score(profile,
                    job(skills("Java"), null, null, null, mode, "engineer"));
        }

        @ParameterizedTest
        @CsvSource({"REMOTE,REMOTE", "HYBRID,HYBRID", "ONSITE,ONSITE"})
        void exactMatchScoresFull(RemotePreference preference, WorkMode mode) {
            assertThat(fractionOf(with(preference, mode), MatchCriterion.WORK_MODE))
                    .isEqualTo(1.0);
        }

        @ParameterizedTest
        @CsvSource({"REMOTE,HYBRID", "ONSITE,HYBRID", "HYBRID,REMOTE", "HYBRID,ONSITE"})
        void anythingInvolvingHybridScoresHalf(RemotePreference preference, WorkMode mode) {
            assertThat(fractionOf(with(preference, mode), MatchCriterion.WORK_MODE))
                    .isEqualTo(0.5);
        }

        @ParameterizedTest
        @CsvSource({"REMOTE,ONSITE", "ONSITE,REMOTE"})
        void theOnlyRealContradictionScoresZero(RemotePreference preference, WorkMode mode) {
            assertThat(fractionOf(with(preference, mode), MatchCriterion.WORK_MODE))
                    .isZero();
        }

        @Test
        void anyMeansNoPreferenceAndIsNotScored() {
            // Full marks for indifference would advantage users who skipped the
            // question over users who answered it.
            assertThat(applicable(with(RemotePreference.ANY, WorkMode.ONSITE),
                    MatchCriterion.WORK_MODE)).isFalse();
        }
    }

    // --- normalization across missing data --------------------------------

    @Nested
    class MissingDataNormalization {

        @Test
        void aCriterionThatCannotBeJudgedIsExcludedRatherThanScoredZero() {
            // Skills, experience and location all match; the user expressed no
            // work-mode preference and no preferred role. Those two drop out,
            // and the score stays 100 rather than being dragged down by data
            // the user never supplied.
            MatchProfile partial = profile(skills("Java"), 3, Set.of(), Set.of("pune"),
                    RemotePreference.ANY);
            MatchJob job = job(skills("Java"), 2, 5, "pune india", WorkMode.ONSITE,
                    "java developer");

            MatchOutcome outcome = engine.score(partial, job);

            assertThat(applicable(outcome, MatchCriterion.ROLE)).isFalse();
            assertThat(applicable(outcome, MatchCriterion.WORK_MODE)).isFalse();
            assertThat(outcome.score()).isEqualTo(100);
        }

        @Test
        void normalizesAgainstTheAvailableMaximum() {
            // Applicable: skills (50) and experience (20) -> available 70.
            // Skills half matched (25), experience full (20) -> earned 45.
            // 45 / 70 = 0.642857 -> 64.
            MatchProfile partial = profile(skills("Java"), 3, Set.of(), Set.of(),
                    RemotePreference.ANY);
            MatchJob job = job(skills("Java", "Rust"), 2, 5, null, WorkMode.ONSITE, "engineer");

            MatchOutcome outcome = engine.score(partial, job);

            assertThat(outcome.score()).isEqualTo(64);
        }

        @Test
        void nothingJudgeableProducesNoScoreRatherThanZero() {
            // Zero would read as "terrible match" when it means "we cannot
            // tell", and the two lead a user to do completely different things.
            MatchProfile empty = profile(Map.of(), null, Set.of(), Set.of(),
                    RemotePreference.ANY);
            MatchJob bare = job(Map.of(), null, null, null, WorkMode.ONSITE, "engineer");

            MatchOutcome outcome = engine.score(empty, bare);

            assertThat(outcome.scored()).isFalse();
            assertThat(outcome.sortableScore()).isEqualTo(-1);
        }
    }

    // --- weights ----------------------------------------------------------

    @Test
    void weightsAreConfigurableAndChangeTheScore() {
        // Skills matched, work mode mismatched. With the default weights the
        // skills dominate; with skills worth less, the same inputs score lower.
        MatchProfile profile = profile(skills("Java"), null, Set.of(), Set.of(),
                RemotePreference.REMOTE);
        MatchJob job = job(skills("Java"), null, null, null, WorkMode.ONSITE, "engineer");

        int defaultScore = engine.score(profile, job).score();
        int reweighted = new JobMatchingEngine(new MatchingWeights(10, 20, 15, 10, 50))
                .score(profile, job).score();

        assertThat(defaultScore).isGreaterThan(reweighted);
    }

    @Test
    void defaultWeightsSumToOneHundred() {
        MatchingWeights weights = MatchingWeights.defaults();

        int total = weights.skills() + weights.experience() + weights.location()
                + weights.role() + weights.workMode();

        assertThat(total).isEqualTo(100);
    }
}
