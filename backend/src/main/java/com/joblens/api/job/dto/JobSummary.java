package com.joblens.api.job.dto;

import com.joblens.api.job.domain.EmploymentType;
import com.joblens.api.job.domain.Job;
import com.joblens.api.job.domain.WorkMode;

import java.time.Instant;
import java.util.UUID;

/**
 * A job as it appears in a search result row.
 *
 * <p>No description. On a page of 20 that would be most of the response for
 * text no list UI shows; the client fetches the full record when a user opens
 * one.
 */
public record JobSummary(
        UUID id,
        String title,
        JobCompanyRef company,
        String location,
        EmploymentType employmentType,
        WorkMode workMode,
        Integer experienceMin,
        Integer experienceMax,
        Instant postedAt,
        boolean active
) {

    public static JobSummary from(Job job) {
        return new JobSummary(
                job.getId(),
                job.getTitle(),
                JobCompanyRef.from(job.getCompany()),
                job.getLocation(),
                job.getEmploymentType(),
                job.getWorkMode(),
                job.getExperienceMin(),
                job.getExperienceMax(),
                job.getPostedAt(),
                job.isActive());
    }
}
