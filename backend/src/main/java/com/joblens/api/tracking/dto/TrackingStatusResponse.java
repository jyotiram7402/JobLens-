package com.joblens.api.tracking.dto;

import com.joblens.api.tracking.domain.TrackedCompany;

import java.time.Instant;
import java.util.UUID;

/**
 * Whether the signed-in user tracks a company.
 *
 * <p>Returned by both {@code GET} and {@code POST}
 * {@code /companies/{companyId}/track}, so a client that has just tracked a
 * company receives exactly the shape it would get by asking afterwards, and can
 * update its state from the response without a second request.
 *
 * @param trackedAt null when not tracked
 */
public record TrackingStatusResponse(
        UUID companyId,
        boolean tracked,
        Instant trackedAt
) {

    public static TrackingStatusResponse trackedSince(TrackedCompany tracking) {
        return new TrackingStatusResponse(tracking.getCompany().getId(), true,
                tracking.getTrackedAt());
    }

    public static TrackingStatusResponse notTracked(UUID companyId) {
        return new TrackingStatusResponse(companyId, false, null);
    }
}
