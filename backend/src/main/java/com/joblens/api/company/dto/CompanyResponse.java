package com.joblens.api.company.dto;

import com.joblens.api.company.domain.Company;

import java.time.Instant;
import java.util.UUID;

/**
 * Full company representation, returned by the create, fetch and update
 * endpoints.
 *
 * <p>Note what is absent: {@code normalizedName} and {@code version}.
 * The normalized name is an internal matching key, not information about the
 * company -- exposing it would invite clients to depend on our normalization
 * rules, which we intend to keep changing. The version is an optimistic-locking
 * detail that only matters once a client can send it back, which it cannot yet.
 */
public record CompanyResponse(
        UUID id,
        String slug,
        String name,
        String description,
        String websiteUrl,
        String careersUrl,
        String logoUrl,
        String industry,
        String location,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getSlug(),
                company.getName(),
                company.getDescription(),
                company.getWebsiteUrl(),
                company.getCareersUrl(),
                company.getLogoUrl(),
                company.getIndustry(),
                company.getLocation(),
                company.isActive(),
                company.getCreatedAt(),
                company.getUpdatedAt());
    }
}
