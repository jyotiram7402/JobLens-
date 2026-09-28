package com.joblens.api.matching;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The explanation must say what the score says.
 *
 * <p>These tests exist because the prose is the part a user actually reads. A
 * breakdown that is right and an explanation that contradicts it is worse than
 * no explanation: it tells someone they have a skill they do not have.
 */
class MatchExplanationWriterTest {

    private final MatchExplanationWriter writer = new MatchExplanationWriter();

    private static CriterionOutcome skills(List<String> matched, List<String> missing) {
        double fraction = matched.size() + missing.size() == 0
                ? 0.0
                : (double) matched.size() / (matched.size() + missing.size());
        return CriterionOutcome.scoredSkills(fraction,
                "You have %d of the %d skills this job asks for."
                        .formatted(matched.size(), matched.size() + missing.size()),
                matched, missing);
    }

    private static MatchOutcome outcome(int score, CriterionOutcome... criteria) {
        return new MatchOutcome(UUID.randomUUID(), true, score, List.of(criteria));
    }

    private static String joined(List<String> sentences) {
        return String.join(" ", sentences);
    }

    @Test
    void namesTheSkillsTheUserHas() {
        List<String> explanation = writer.write(outcome(88,
                skills(List.of("Java", "Spring Boot", "Docker"), List.of("PostgreSQL"))));

        assertThat(joined(explanation)).contains("Java", "Spring Boot", "Docker");
    }

    @Test
    void neverClaimsASkillTheUserIsMissing() {
        // The single most important assertion here. If PostgreSQL is missing,
        // the prose must not read as though the user has it.
        List<String> explanation = writer.write(outcome(88,
                skills(List.of("Java"), List.of("PostgreSQL"))));

        assertThat(joined(explanation))
                .contains("The one skill gap is PostgreSQL.")
                .doesNotContain("You have PostgreSQL");
    }

    @Test
    void listsSeveralGapsTogether() {
        List<String> explanation = writer.write(outcome(40,
                skills(List.of("Java"), List.of("PostgreSQL", "Kubernetes"))));

        assertThat(joined(explanation)).contains("The skill gaps are PostgreSQL and Kubernetes.");
    }

    @Test
    void doesNotMentionMatchedSkillsWhenThereAreNone() {
        List<String> explanation = writer.write(outcome(10,
                skills(List.of(), List.of("Rust", "Go"))));

        assertThat(joined(explanation)).doesNotContain("which this job asks for");
    }

    @Test
    void truncatesVeryLongSkillLists() {
        List<String> many = List.of("Java", "Spring Boot", "Docker", "React", "Kafka", "Go");

        List<String> explanation = writer.write(outcome(90, skills(many, List.of())));

        assertThat(joined(explanation)).contains("and 2 more");
    }

    @Test
    void reportsTheScoreAndHowMuchWasCompared() {
        // A score from two criteria is a thinner claim than one from five, and
        // the user should be able to see that rather than trust a bare number.
        List<String> explanation = writer.write(outcome(75,
                skills(List.of("Java"), List.of()),
                CriterionOutcome.notApplicable(MatchCriterion.LOCATION, "no location"),
                CriterionOutcome.scored(MatchCriterion.WORK_MODE, 0.5, "close to your preference")));

        assertThat(explanation.get(0)).contains("75 out of 100").contains("2 of the 3");
    }

    @Test
    void strengthsComeBeforeGaps() {
        List<String> explanation = writer.write(outcome(70,
                skills(List.of("Java"), List.of("Rust")),
                CriterionOutcome.scored(MatchCriterion.LOCATION, 0.0, "not in your locations")));

        String all = joined(explanation);
        assertThat(all.indexOf("You have Java")).isLessThan(all.indexOf("The one skill gap"));
        assertThat(all.indexOf("The one skill gap")).isLessThan(all.indexOf("not in your locations"));
    }

    @Test
    void explainsWhyACriterionWasSkipped() {
        List<String> explanation = writer.write(outcome(80,
                skills(List.of("Java"), List.of()),
                CriterionOutcome.notApplicable(MatchCriterion.EXPERIENCE,
                        "This job does not state an experience range, so experience could not be compared.")));

        assertThat(joined(explanation)).contains("does not state an experience range");
    }

    @Test
    void anUnscoredMatchSaysSoAndSaysWhatToDo() {
        MatchOutcome unscored = MatchOutcome.unscored(UUID.randomUUID(),
                List.of(CriterionOutcome.notApplicable(MatchCriterion.SKILLS, "no skills")));

        List<String> explanation = writer.write(unscored);

        assertThat(joined(explanation))
                .contains("not enough information")
                .contains("profile");
        // No number, because there is no score to report.
        assertThat(joined(explanation)).doesNotContain("out of 100");
    }
}
