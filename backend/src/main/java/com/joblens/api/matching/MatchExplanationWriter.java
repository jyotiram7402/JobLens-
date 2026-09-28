package com.joblens.api.matching;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link MatchOutcome} into sentences a person can read.
 *
 * <p>Every sentence is derived from the outcome it is given. Nothing is
 * invented, nothing is softened, and in particular the explanation can never
 * claim a skill the user does not have — the missing list comes from the same
 * comparison the score did. If the score says PostgreSQL is missing, the prose
 * says PostgreSQL is missing.
 *
 * <p>No language model. A template here is auditable, instant, free, and cannot
 * hallucinate a qualification the user never claimed — which in a product about
 * job applications is not a small property.
 *
 * <p>The tone is factual. JobLens does not tell someone a job is "great for
 * you"; it says which of their skills the job asks for and which it does not,
 * and lets them decide.
 */
@Component
public class MatchExplanationWriter {

    /**
     * Builds the explanation.
     *
     * <p>Order is deliberate: what fits first, then what does not, then what
     * could not be judged. A user reading only the first sentence should get
     * the most useful thing.
     */
    public List<String> write(MatchOutcome outcome) {
        if (!outcome.scored()) {
            return List.of("There is not enough information to compare you with this job yet. "
                    + "Adding your skills, experience and preferences to your profile will "
                    + "let JobLens score it.");
        }

        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> unknowns = new ArrayList<>();

        for (CriterionOutcome criterion : outcome.criteria()) {
            if (!criterion.applicable()) {
                unknowns.add(criterion.reason());
            } else if (criterion.criterion() == MatchCriterion.SKILLS) {
                addSkillSentences(criterion, strengths, gaps);
            } else if (criterion.fraction() >= 0.5) {
                strengths.add(criterion.reason());
            } else {
                gaps.add(criterion.reason());
            }
        }

        List<String> sentences = new ArrayList<>();
        sentences.add(headline(outcome));
        sentences.addAll(strengths);
        sentences.addAll(gaps);
        sentences.addAll(unknowns);
        return List.copyOf(sentences);
    }

    /**
     * States the score and what it was measured against.
     *
     * <p>Naming the number of criteria matters: a score computed from two
     * criteria is a thinner claim than one computed from five, and the user
     * should be able to see that rather than having to trust a bare percentage.
     */
    private String headline(MatchOutcome outcome) {
        long applicable = outcome.criteria().stream().filter(CriterionOutcome::applicable).count();
        return "This job scores %d out of 100, based on %d of the %d things JobLens compares."
                .formatted(outcome.score(), applicable, outcome.criteria().size());
    }

    /**
     * Skills get their own handling because they are the only criterion that can
     * be a strength and a gap at once, and because naming the actual skills is
     * the most useful thing the explanation does.
     */
    private void addSkillSentences(CriterionOutcome skills, List<String> strengths,
                                   List<String> gaps) {
        if (!skills.matched().isEmpty()) {
            strengths.add("You have %s, which this job asks for."
                    .formatted(joinNaturally(skills.matched())));
        }
        if (!skills.missing().isEmpty()) {
            gaps.add(skills.missing().size() == 1
                    ? "The one skill gap is %s.".formatted(skills.missing().get(0))
                    : "The skill gaps are %s.".formatted(joinNaturally(skills.missing())));
        }
        if (skills.matched().isEmpty() && skills.missing().isEmpty()) {
            gaps.add(skills.reason());
        }
    }

    /**
     * "Java", "Java and Spring Boot", "Java, Spring Boot and Docker".
     *
     * <p>Long lists are truncated: naming fifteen skills in a sentence stops
     * being an explanation and starts being a data dump. The full lists are in
     * the structured breakdown for anyone who wants them.
     */
    private String joinNaturally(List<String> items) {
        int limit = 4;
        if (items.size() > limit) {
            String head = String.join(", ", items.subList(0, limit));
            return "%s and %d more".formatted(head, items.size() - limit);
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        String head = String.join(", ", items.subList(0, items.size() - 1));
        return "%s and %s".formatted(head, items.get(items.size() - 1));
    }
}
