package com.joblens.api.job;

import com.joblens.api.common.exception.InvalidRequestException;
import com.joblens.api.job.domain.EmploymentType;
import com.joblens.api.job.domain.WorkMode;

import java.time.Instant;
import java.util.UUID;

/**
 * Every filter a job search can apply, in one value.
 *
 * <p>It exists because the alternative is a repository method with ten
 * parameters, which nobody can call correctly and which grows another parameter
 * every time a filter is added. A record keeps the call site readable and gives
 * the cross-field rules -- the ones Bean Validation cannot express -- somewhere
 * obvious to live.
 *
 * <p>Every field is nullable, and null means "do not filter on this". That is
 * what makes the filters compose: a search with nothing set returns the first
 * page of active jobs, and each supplied value narrows it further.
 *
 * @param search         keyword, matched against title OR description
 * @param companyId      restrict to one company
 * @param location       case-insensitive substring of the job's location text
 * @param employmentType exact match
 * @param workMode       exact match
 * @param experienceMin  lower bound of the experience the searcher is offering
 * @param experienceMax  upper bound of the same
 * @param active         defaults to true at the controller; see {@code JobController}
 * @param postedAfter    inclusive lower bound on {@code postedAt}
 * @param postedBefore   inclusive upper bound on {@code postedAt}
 */
public record JobSearchCriteria(
        String search,
        UUID companyId,
        String location,
        EmploymentType employmentType,
        WorkMode workMode,
        Integer experienceMin,
        Integer experienceMax,
        Boolean active,
        Instant postedAfter,
        Instant postedBefore
) {

    /**
     * Checks the rules that involve more than one field.
     *
     * <p>Bean Validation has already checked each value on its own -- that the
     * experience figures are not negative, that the dates parsed. It cannot
     * check that one is not greater than the other, because a constraint
     * annotation sees a single field. Left unchecked, an inverted range would
     * quietly return zero results and look like a data problem, so it is
     * rejected instead.
     *
     * @throws InvalidRequestException if a range is inverted
     */
    public void validate() {
        if (experienceMin != null && experienceMax != null && experienceMin > experienceMax) {
            throw new InvalidRequestException(
                    "experienceMin (%d) must not be greater than experienceMax (%d)"
                            .formatted(experienceMin, experienceMax));
        }
        if (postedAfter != null && postedBefore != null && postedAfter.isAfter(postedBefore)) {
            throw new InvalidRequestException("postedAfter must not be later than postedBefore");
        }
    }
}
