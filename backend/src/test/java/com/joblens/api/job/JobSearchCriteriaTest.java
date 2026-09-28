package com.joblens.api.job;

import com.joblens.api.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cross-field rules Bean Validation cannot express, because a constraint
 * annotation only ever sees one field.
 */
class JobSearchCriteriaTest {

    private static JobSearchCriteria experience(Integer min, Integer max) {
        return new JobSearchCriteria(null, null, null, null, null, min, max, true, null, null);
    }

    private static JobSearchCriteria dates(Instant after, Instant before) {
        return new JobSearchCriteria(null, null, null, null, null, null, null, true,
                after, before);
    }

    @Test
    void acceptsAnEmptyCriteria() {
        assertThatCode(() -> experience(null, null).validate()).doesNotThrowAnyException();
    }

    @Test
    void acceptsAnOrderedExperienceRange() {
        assertThatCode(() -> experience(2, 5).validate()).doesNotThrowAnyException();
    }

    @Test
    void acceptsEqualExperienceBounds() {
        assertThatCode(() -> experience(3, 3).validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvertedExperienceRange() {
        // Left unchecked this returns zero results and looks like a data
        // problem, so it is rejected rather than silently answered.
        assertThatThrownBy(() -> experience(5, 2).validate())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("experienceMin");
    }

    @Test
    void acceptsOneSidedExperienceBounds() {
        assertThatCode(() -> experience(2, null).validate()).doesNotThrowAnyException();
        assertThatCode(() -> experience(null, 5).validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvertedDateWindow() {
        assertThatThrownBy(() -> dates(Instant.parse("2026-09-20T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z")).validate())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("postedAfter");
    }

    @Test
    void acceptsAnOrderedDateWindow() {
        assertThatCode(() -> dates(Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-20T00:00:00Z")).validate()).doesNotThrowAnyException();
    }
}
