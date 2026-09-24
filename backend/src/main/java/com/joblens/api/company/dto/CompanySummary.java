package com.joblens.api.company.dto;

import com.joblens.api.company.domain.Company;

import java.util.UUID;

/**
 * Compact company representation for search results.
 *
 * <p>A separate type from {@link CompanyResponse} because a results page does
 * not need 2000-character descriptions: on a list of 20 that is most of the
 * payload, for text no list UI displays. The client fetches the full record when
 * the user opens one.
 */
public record CompanySummary(
        UUID id,
        String slug,
        String name,
        String industry,
        String location,
        String logoUrl
) {

    public static CompanySummary from(Company company) {
        return new CompanySummary(
                company.getId(),
                company.getSlug(),
                company.getName(),
                company.getIndustry(),
                company.getLocation(),
                company.getLogoUrl());
    }
}
