/**
 * Scoring a job against a user's career profile, and explaining the result.
 *
 * <p>Implemented in roadmap step 7.
 *
 * <pre>
 * matching/
 *   MatchController.java          HTTP: /jobs/{id}/match, /jobs/recommended
 *   JobMatchingService.java       loads data, orchestrates, paginates
 *   JobMatchingEngine.java        the rules -- pure, no database
 *   MatchExplanationWriter.java   outcome -> sentences
 *   MatchingWeights.java          every number, in one place
 *   MatchCriterion.java           the five things judged
 *   MatchProfile / MatchJob       the engine's inputs, as values not entities
 *   CriterionOutcome / MatchOutcome  the engine's output
 *   dto/                          response records
 *   exception/                    ProfileNotReadyException
 * </pre>
 *
 * <p>The split between engine and service is the important one. The engine is a
 * pure function: same inputs, same score, no database, no clock, no randomness.
 * That is what makes the rules testable in milliseconds and reviewable without
 * reading a persistence layer -- and it is why the engine takes
 * {@code MatchProfile} and {@code MatchJob} value objects rather than entities.
 *
 * <p><b>No AI, deliberately.</b> Every rule is arithmetic a person can read and
 * argue with. A model would probably rank better on average and would not be
 * able to say why, and "why did this job score 72?" is a question this product
 * has to answer. AI-assisted matching is a later step, and it will sit beside
 * this rather than replace it.
 *
 * <p>Depends on {@code job}, {@code user} and {@code skill} through their
 * repositories and domain types. Nothing depends on this module.
 */
package com.joblens.api.matching;
