package com.joblens.api.tracking.dto;

import com.joblens.api.company.domain.Company;
import com.joblens.api.tracking.domain.TrackedCompany;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the tracked-companies list.
 *
 * <p>Flat rather than nesting a {@code CompanyResponse}: the list needs a card's
 * worth of company information plus when it was tracked, not the company's
 * description and audit timestamps. {@code companyId} rather than {@code id},
 * because the tracking row's own id is an internal detail nobody needs -- the
 * relationship is addressed by company, as the endpoints are.
 */
public record TrackedCompanyResponse(
        UUID companyId,
        String name,
        String slug,
        String logoUrl,
        String industry,
        String location,
        String websiteUrl,
        String careersUrl,
        Instant trackedAt
) {

    public static TrackedCompanyResponse from(TrackedCompany tracking) {
        Company company = tracking.getCompany();
        return new TrackedCompanyResponse(
                company.getId(),
                company.getName(),
                company.getSlug(),
                company.getLogoUrl(),
                company.getIndustry(),
                company.getLocation(),
                company.getWebsiteUrl(),
                company.getCareersUrl(),
                tracking.getTrackedAt());
    }
}
