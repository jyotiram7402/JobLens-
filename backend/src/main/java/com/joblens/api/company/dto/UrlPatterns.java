package com.joblens.api.company.dto;

/**
 * Shared URL validation for the company DTOs.
 *
 * <p>The check is intentionally shallow: the value must be absent, or an
 * {@code http}/{@code https} URL. It does not try to prove the address exists or
 * that the host resolves -- that would mean a network call during validation,
 * which is slow, flaky and a way to have the API fetch arbitrary URLs on a
 * caller's behalf.
 *
 * <p>Restricting the scheme is the part that matters. Without it a client could
 * store {@code javascript:...} or {@code data:...} in {@code websiteUrl}, and
 * the frontend would later render it as a link.
 *
 * <p>An empty string passes, because Bean Validation applies {@code @Pattern}
 * only to non-null values and the fields are optional. The service normalizes
 * blank strings to {@code null} before saving.
 */
final class UrlPatterns {

    static final String HTTP_URL = "^$|^https?://[^\s]+$";

    static final String MESSAGE = "must be an http or https URL";

    private UrlPatterns() {
    }
}
