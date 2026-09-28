package com.joblens.api.matching;

import com.joblens.api.common.response.PageResponse;
import com.joblens.api.common.web.ApiRoutes;
import com.joblens.api.matching.dto.MatchResponse;
import com.joblens.api.matching.dto.RecommendedJob;
import com.joblens.api.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Matching endpoints: how one job scores for the signed-in user, and which
 * recent openings score best.
 *
 * <p><b>Both require authentication, and neither accepts a user id.</b> The
 * profile being matched is always the one the verified token points at. There
 * is no {@code userId} parameter to tamper with, so one user cannot ask for
 * another's results — the same structural defence the {@code /users/me}
 * endpoints use.
 *
 * <p>These live under {@code /api/v1/jobs} because that is where a client looks
 * for them, but in the {@code matching} module because that is what owns the
 * logic. A URL path and a package are answers to different questions.
 *
 * <p>Note for {@code SecurityConfig}: {@code GET /api/v1/jobs/**} is public for
 * discovery, so these two routes are listed <em>before</em> that rule and
 * marked {@code authenticated()}. Order matters there; get it wrong and a
 * user's match results become anonymous reads.
 */
@RestController
@RequestMapping(ApiRoutes.API_V1 + "/jobs")
@Validated
public class MatchController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Same ceiling as job search, so the API behaves consistently. */
    private static final int MAX_PAGE_SIZE = 50;

    private final JobMatchingService jobMatchingService;

    public MatchController(JobMatchingService jobMatchingService) {
        this.jobMatchingService = jobMatchingService;
    }

    /**
     * How this job scores for the signed-in user, with the full breakdown and a
     * readable explanation.
     *
     * <p>{@code 401} without a token, {@code 404} if the job does not exist,
     * {@code 422} if the profile has nothing to match on.
     */
    @GetMapping("/{jobId}/match")
    public MatchResponse match(@PathVariable UUID jobId) {
        return jobMatchingService.match(currentUserId(), jobId);
    }

    /**
     * Recent active openings, best match first.
     *
     * <p>Scored over a bounded set of recent jobs rather than the whole table —
     * see {@code JobMatchingService} for what that does and does not guarantee.
     */
    @GetMapping("/recommended")
    public PageResponse<RecommendedJob> recommended(

            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "must be 0 or greater")
            int page,

            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "must be at most " + MAX_PAGE_SIZE)
            int size) {

        // Unsorted: the ordering is by match score, which the service applies
        // after scoring. Passing a Sort here would be ignored and misleading.
        Pageable pageable = PageRequest.of(page, size);
        return jobMatchingService.recommend(currentUserId(), pageable);
    }

    /** The one source of the caller's identity in this controller. */
    private static UUID currentUserId() {
        return CurrentUser.require().id();
    }
}
