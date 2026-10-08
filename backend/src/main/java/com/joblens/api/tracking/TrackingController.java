package com.joblens.api.tracking;

import com.joblens.api.common.response.PageResponse;
import com.joblens.api.common.web.ApiRoutes;
import com.joblens.api.security.CurrentUser;
import com.joblens.api.tracking.dto.TrackedCompanyResponse;
import com.joblens.api.tracking.dto.TrackingStatusResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Company tracking. Every route requires authentication.
 *
 * <p><b>No route accepts a user id.</b> The user is always the one the verified
 * token belongs to, read from the security context. There is no parameter to
 * tamper with, so one user cannot read or change another's tracking -- the same
 * structural defence {@code /users/me} uses.
 *
 * <p>The paths are split across two prefixes on purpose. Tracking <em>a
 * company</em> is an action on that company, so it lives at
 * {@code /companies/{id}/track}. The list belongs to the user, so it lives at
 * {@code /users/me/tracked-companies}, next to the rest of what is "mine". One
 * controller, because it is one feature.
 *
 * <p>Security note: {@code GET /api/v1/companies/**} is public for discovery,
 * so {@code SecurityConfig} carves {@code GET /companies/*\/track} out of that
 * rule explicitly and ahead of it. The other methods fall through to the
 * authenticated default.
 */
@RestController
@RequestMapping(ApiRoutes.API_V1)
@Validated
public class TrackingController {

    /** Twelve fills a three- or four-column card grid evenly. */
    private static final int DEFAULT_PAGE_SIZE = 12;

    /** Same ceiling as every other list endpoint. */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * Most recently tracked first, with id as a tiebreaker so two companies
     * tracked in the same instant cannot swap places between pages.
     */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));

    private final TrackingService trackingService;

    public TrackingController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    /**
     * Starts tracking a company.
     *
     * <p>200 rather than 201, and the same response whether the row was just
     * created or already existed: the request is idempotent, and the client's
     * only question is "is it tracked now?", which the body answers. There is
     * also no addressable resource for a {@code Location} header to point at --
     * the relationship is addressed by company.
     *
     * <p>404 if the company does not exist.
     */
    @PostMapping("/companies/{companyId}/track")
    public TrackingStatusResponse track(@PathVariable UUID companyId) {
        return trackingService.track(currentUserId(), companyId);
    }

    /**
     * Stops tracking a company.
     *
     * <p>204 whether or not it was tracked: the requested state -- not tracked
     * -- now holds either way. 404 if the company does not exist, matching the
     * other two operations.
     */
    @DeleteMapping("/companies/{companyId}/track")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void untrack(@PathVariable UUID companyId) {
        trackingService.untrack(currentUserId(), companyId);
    }

    /**
     * Whether the signed-in user tracks this company.
     *
     * <p>A dedicated endpoint rather than a {@code tracked} field on the company
     * response. {@code GET /companies/{id}} is public and identical for every
     * caller; adding per-user state would make the same URL return different
     * bodies to different people, couple the company module to tracking, and
     * cost a tracking query on every anonymous company view. The price is one
     * small extra request, made only when someone is signed in.
     */
    @GetMapping("/companies/{companyId}/track")
    public TrackingStatusResponse status(@PathVariable UUID companyId) {
        return trackingService.status(currentUserId(), companyId);
    }

    /**
     * The signed-in user's tracked companies, most recently tracked first.
     */
    @GetMapping("/users/me/tracked-companies")
    public PageResponse<TrackedCompanyResponse> list(

            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "must be 0 or greater")
            int page,

            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "must be at most " + MAX_PAGE_SIZE)
            int size) {

        return trackingService.list(currentUserId(), PageRequest.of(page, size, NEWEST_FIRST));
    }

    /** The one source of the caller's identity in this controller. */
    private static UUID currentUserId() {
        return CurrentUser.require().id();
    }
}
