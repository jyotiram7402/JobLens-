package com.joblens.api.job.dto;

import com.joblens.api.job.domain.EmploymentType;
import com.joblens.api.job.domain.Job;
import com.joblens.api.job.domain.WorkMode;
import com.joblens.api.skill.domain.Skill;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full job representation, returned by create, fetch and update.
 *
 * <p>Absent on purpose: {@code normalizedTitle} and {@code version}. The
 * normalized title is an internal matching key, and publishing it invites
 * clients to depend on normalization rules we intend to keep changing.
 */
public record JobResponse(
        UUID id,
        String title,
        JobCompanyRef company,
        String description,
        String location,
        EmploymentType employmentType,
        WorkMode workMode,
        Integer experienceMin,
        Integer experienceMax,
        String applyUrl,
        /** Display names of the skills this opening asks for. */
        List<String> skills,
        Instant postedAt,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                JobCompanyRef.from(job.getCompany()),
                job.getDescription(),
                job.getLocation(),
                job.getEmploymentType(),
                job.getWorkMode(),
                job.getExperienceMin(),
                job.getExperienceMax(),
                job.getApplyUrl(),
                job.getSkills().stream().map(Skill::getName).toList(),
                job.getPostedAt(),
                job.isActive(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
