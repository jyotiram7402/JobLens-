package com.joblens.api.job.dto;

/**
 * URL validation for the job DTOs.
 *
 * <p>Same shallow check the company DTOs use, and for the same reason:
 * restricting the scheme to {@code http}/{@code https} is the part that matters.
 * Without it a client could store {@code javascript:...} in {@code applyUrl},
 * and the frontend would render it as the "Apply" button.
 *
 * <p>It does not verify the address resolves. That would mean a network call
 * during validation -- slow, flaky, and a way to have the API fetch arbitrary
 * URLs on a caller's behalf.
 */
final class JobUrlPatterns {

    static final String HTTP_URL = "^$|^https?://[^\s]+$";

    static final String MESSAGE = "must be an http or https URL";

    private JobUrlPatterns() {
    }
}
