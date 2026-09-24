package com.joblens.api.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/companies}.
 *
 * <p>Only the fields a client is allowed to set appear here. There is no
 * {@code id}, {@code slug}, {@code normalizedName}, {@code active} or timestamp:
 * those are system-managed, and the safest way to stop a client supplying one is
 * to have nowhere for it to go. Unknown JSON properties are rejected rather than
 * ignored, so a caller sending {@code "id"} is told, not quietly obeyed.
 *
 * <p>Every length limit matches its database column, so an oversized value fails
 * validation with a readable message instead of a constraint violation at
 * insert time.
 */
public record CreateCompanyRequest(

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
