package com.joblens.api.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/v1/companies/{id}}.
 *
 * <p>A full replacement of the editable fields, which is what PUT means: an
 * omitted optional field is cleared, not left alone. Partial updates would be
 * PATCH, and PATCH needs a way to distinguish "absent" from "set to null" --
 * worth doing when something actually needs it, not before.
 *
 * <p>Same field set as {@link CreateCompanyRequest} and for the same reason:
 * {@code id}, {@code slug}, {@code normalizedName}, {@code active} and the
 * timestamps are system-managed and have nowhere to be supplied.
 *
 * <p>Renaming a company recomputes its normalized name, so it can collide with
 * another company and be rejected with 409. It does not change the slug: that
 * is a public identifier already present in links.
 */
public record UpdateCompanyRequest(

        @NotBlank(message = "must not be blank")
        @Size(max = 200, message = "must be at most 200 characters")
        String name,

        @Size(max = 2000, message = "must be at most 2000 characters")
        String description,

        @Size(max = 500, message = "must be at most 500 characters")
        @Pattern(regexp = UrlPatterns.HTTP_URL, message = UrlPatterns.MESSAGE)
        String websiteUrl,

        @Size(max = 500, message = "must be at most 500 characters")
        @Pattern(regexp = UrlPatterns.HTTP_URL, message = UrlPatterns.MESSAGE)
        String careersUrl,

        @Size(max = 500, message = "must be at most 500 characters")
        @Pattern(regexp = UrlPatterns.HTTP_URL, message = UrlPatterns.MESSAGE)
        String logoUrl,

        @Size(max = 120, message = "must be at most 120 characters")
        String industry,

        @Size(max = 200, message = "must be at most 200 characters")
        String location
) {
}
