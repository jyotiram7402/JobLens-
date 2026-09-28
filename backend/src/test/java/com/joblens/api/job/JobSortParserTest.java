package com.joblens.api.job;

import com.joblens.api.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobSortParserTest {

    @Test
    void defaultsToNewestFirst() {
        Sort sort = JobSortParser.parse(null);

        assertThat(sort.getOrderFor("postedAt")).isNotNull();
        assertThat(sort.getOrderFor("postedAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void blankSortFallsBackToTheDefault() {
        assertThat(JobSortParser.parse("   ")).isEqualTo(JobSortParser.DEFAULT_SORT);
    }

    @Test
    void parsesFieldAndDirection() {
        Sort sort = JobSortParser.parse("title,asc");

        assertThat(sort.getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void alwaysAppendsIdAsATiebreaker() {
        // Without this, two jobs posted in the same second can swap places
        // between pages, so one row appears twice and another is never seen.
        assertThat(JobSortParser.parse("postedAt,desc").getOrderFor("id")).isNotNull();
        assertThat(JobSortParser.parse(null).getOrderFor("id")).isNotNull();
    }

    @Test
    void datesDefaultToDescendingAndTitlesToAscending() {
        assertThat(JobSortParser.parse("postedAt").getOrderFor("postedAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(JobSortParser.parse("title").getOrderFor("title").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "salary",
            "description",
            "company.name",
            "id",
            // A property that exists on the entity but is not on the allowlist:
            // the allowlist is the rule, not "does this field exist".
            "normalizedTitle",
            "'; DROP TABLE jobs; --"
    })
    void rejectsFieldsOutsideTheAllowlist(String field) {
        assertThatThrownBy(() -> JobSortParser.parse(field))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void rejectsUnknownDirection() {
        assertThatThrownBy(() -> JobSortParser.parse("title,sideways"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("direction");
    }
}
