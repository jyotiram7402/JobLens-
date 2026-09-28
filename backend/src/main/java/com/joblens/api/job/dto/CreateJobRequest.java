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
import java.util.List;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/jobs}.
 *
 * <p>No {@code id}, {@code active} or timestamps: those are system-managed and
 * have nowhere to be supplied. {@code companyId} <em>is</em> accepted, because
 * a job genuinely belongs to a company the caller chooses -- unlike a profile,
 * where accepting an owner id would be the whole IDOR problem. The service
 * verifies the company exists.
 *
 * <p>{@code postedAt} is optional and defaults to now. It is accepted because a
 * job imported from a careers page was posted when the employer posted it, not
 * when JobLens noticed, and the default sort depends on that being honest.
 */
public record CreateJobRequest(

        @NotNull(message = "must not be null")
        UUID companyId,

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

        /*
         * The skills the opening asks for, by name. Resolved against the shared
         * vocabulary, so "Spring Boot" here and "spring boot" on a profile are
         * the same skill. Capped for the same reason profile skills are: one
         * request should not be able to insert unbounded rows.
         */
        @Size(max = 30, message = "must contain at most 30 skills")
        List<
                @NotBlank(message = "must not be blank")
                @Size(max = 80, message = "must be at most 80 characters")
                String> skills,

        /** Optional; defaults to now. */
        Instant postedAt
) {
}
