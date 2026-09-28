package com.joblens.api.job.dto;

import com.joblens.api.job.domain.EmploymentType;
import com.joblens.api.job.domain.WorkMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Body of {@code PUT /api/v1/jobs/{id}}.
 *
 * <p>A full replacement of the editable fields, PUT semantics: an omitted
 * optional field is cleared.
 *
 * <p>There is no {@code companyId}. Moving an opening between employers is not
 * an edit, it is a different job -- and the entity maps the column
 * {@code updatable = false} so it agrees.
 *
 * <p>No {@code id}, {@code active} or timestamps either: those are
 * system-managed. Closing a job is a separate action, not something smuggled
 * into an update.
 */
public record UpdateJobRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String title,

        /*
         * Capped here rather than in the database. The column is TEXT because
         * real descriptions vary wildly, but an API that accepts unbounded text
         * accepts a request body designed to exhaust memory.
         */
        @Size(max = 20000, message = "must be at most 20000 characters")
        String description,

        @Size(max = 200, message = "must be at most 200 characters")
        String location,

        @NotNull(message = "must not be null")
        EmploymentType employmentType,

        @NotNull(message = "must not be null")
        WorkMode workMode,

        /*
         * Both optional. Null means the employer did not state a range, which
         * search treats as an open bound rather than as zero -- a job is never
         * filtered out for lacking data nobody supplied.
         */
        @Min(value = 0, message = "must not be negative")
        @Max(value = 60, message = "must be at most 60")
        Integer experienceMin,

        @Min(value = 0, message = "must not be negative")
        @Max(value = 60, message = "must be at most 60")
        Integer experienceMax,

        @Size(max = 1000, message = "must be at most 1000 characters")
        @Pattern(regexp = JobUrlPatterns.HTTP_URL, message = JobUrlPatterns.MESSAGE)
        String applyUrl,

        /** Optional; defaults to now. */
        Instant postedAt
) {
}
