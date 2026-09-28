package com.joblens.api.job.dto;

import com.joblens.api.company.domain.Company;

import java.util.UUID;

/**
 * The company, as it appears nested inside a job.
 *
 * <p>Three fields, because that is what rendering a result row needs: the id to
 * link by, the name to show, the slug for a readable URL. Embedding the full
 * company -- description, careers URL, timestamps -- would multiply the payload
 * of a 20-job page for data nothing on that page displays.
 *
 * <p>This is a projection for display, not denormalized storage. The company
 * data still lives in exactly one table.
 */
public record JobCompanyRef(
        UUID id,
        String name,
        String slug
) {

    public static JobCompanyRef from(Company company) {
        return new JobCompanyRef(company.getId(), company.getName(), company.getSlug());
    }
}
