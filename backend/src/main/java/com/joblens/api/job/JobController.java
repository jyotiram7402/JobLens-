package com.joblens.api.job;

import com.joblens.api.common.exception.ApplicationException;
import com.joblens.api.common.exception.ErrorCode;
import com.joblens.api.common.response.PageResponse;
import com.joblens.api.common.web.ApiRoutes;
import com.joblens.api.job.domain.EmploymentType;
import com.joblens.api.job.domain.WorkMode;
import com.joblens.api.job.dto.CreateJobRequest;
import com.joblens.api.job.dto.JobResponse;
import com.joblens.api.job.dto.JobSummary;
import com.joblens.api.job.dto.UpdateJobRequest;
import com.joblens.api.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * Job discovery and management.
 *
 * <p>Reads are public -- discovery is the product, and someone should be able to
 * browse openings before signing up. Writes fall to the default-deny rule in
 * {@code SecurityConfig} and require a token.
 *
 * <p>Thin as ever: parameters in, criteria object out, service does the work.
 * The only logic here is turning request parameters into a validated
 * {@link JobSearchCriteria} and {@link Pageable}.
 */
@RestController
@RequestMapping(ApiRoutes.API_V1 + "/jobs")
@Validated
public class JobController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * A ceiling, not a suggestion. Without one, {@code ?size=1000000} is a free
     * denial-of-service against a 512 MB container. Matches the company search
     * limit so the API is consistent.
     */
    private static final int MAX_PAGE_SIZE = 50;

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Searches jobs.
     *
     * <p>All filters combine with AND; the keyword matches title OR description.
     * Every parameter is optional, so a bare {@code GET /api/v1/jobs} returns
     * the first page of active jobs, newest first.
     *
     * <p>An empty result is a {@code 200} with an empty {@code content} array,
     * never a {@code 404}. "No jobs match these filters" is a successful answer
     * to a well-formed question.
     */
    @GetMapping
    public PageResponse<JobSummary> search(

            @RequestParam(name = "search", required = false)
            @Size(max = 200, message = "must be at most 200 characters")
            String search,

            @RequestParam(name = "companyId", required = false)
            UUID companyId,

            @RequestParam(name = "location", required = false)
            @Size(max = 200, message = "must be at most 200 characters")
            String location,

            // Spring converts these from the enum name and raises a type
            // mismatch for anything else, which the global handler renders as a
            // 400. An unrecognised filter is never silently ignored.
            @RequestParam(name = "employmentType", required = false)
            EmploymentType employmentType,

            @RequestParam(name = "workMode", required = false)
            WorkMode workMode,

            @RequestParam(name = "experienceMin", required = false)
            @Min(value = 0, message = "must not be negative")
            @Max(value = 60, message = "must be at most 60")
            Integer experienceMin,

            @RequestParam(name = "experienceMax", required = false)
            @Min(value = 0, message = "must not be negative")
            @Max(value = 60, message = "must be at most 60")
            Integer experienceMax,

            /*
             * Defaults to true: job discovery means open positions, and a
             * closed one is noise. Asking for anything else requires
             * authentication -- see requireAuthenticationFor below.
             */
            @RequestParam(name = "active", required = false)
            Boolean active,

            // ISO-8601, parsed as an Instant, so the value is an absolute
            // moment in UTC and never depends on the server's time zone.
            // Anything unparseable is a 400 from the global handler.
            @RequestParam(name = "postedAfter", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant postedAfter,

            @RequestParam(name = "postedBefore", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant postedBefore,

            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "must be 0 or greater")
            int page,

            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "must be at most " + MAX_PAGE_SIZE)
            int size,

            @RequestParam(name = "sort", required = false)
            String sort) {

        Boolean effectiveActive = requireAuthenticationForNonActiveSearch(active);

        JobSearchCriteria criteria = new JobSearchCriteria(search, companyId, location,
                employmentType, workMode, experienceMin, experienceMax, effectiveActive,
                postedAfter, postedBefore);

        Pageable pageable = PageRequest.of(page, size, JobSortParser.parse(sort));

        return jobService.search(criteria, pageable);
    }

    /**
     * Public discovery only ever sees open positions. Listing withdrawn jobs is
     * an administrative view, so asking for {@code active=false} requires a
     * token -- otherwise anyone could enumerate positions an employer has
     * deliberately taken down.
     *
     * @return the value to filter on: {@code true} when nothing was requested
     */
    private Boolean requireAuthenticationForNonActiveSearch(Boolean requested) {
        if (requested == null) {
            return Boolean.TRUE;
        }
        if (!Boolean.TRUE.equals(requested) && CurrentUser.optional().isEmpty()) {
            throw new AuthenticationRequiredException();
        }
        return requested;
    }

    /**
     * Fetches one job. Public. {@code 404} if it does not exist, {@code 400} if
     * the id is not a UUID.
     */
    @GetMapping("/{id}")
    public JobResponse getById(@PathVariable UUID id) {
        return jobService.getById(id);
    }

    /**
     * Creates a job. Requires authentication. {@code 201} with a
     * {@code Location} header; {@code 404} if the company does not exist.
     */
    @PostMapping
    public ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request) {
        JobResponse created = jobService.create(request);
        URI location = URI.create(ApiRoutes.API_V1 + "/jobs/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Replaces a job's editable fields. Requires authentication.
     */
    @PutMapping("/{id}")
    public JobResponse update(@PathVariable UUID id,
                              @Valid @RequestBody UpdateJobRequest request) {
        return jobService.update(id, request);
    }

    /**
     * Withdraws a job from search. Requires authentication.
     *
     * <p>A named action rather than {@code DELETE}, because the row is kept:
     * tracking and scan history will reference it.
     */
    @PostMapping("/{id}/close")
    public JobResponse close(@PathVariable UUID id) {
        return jobService.close(id);
    }

    /**
     * Thrown when an anonymous caller asks for something only an authenticated
     * one may see. Rendered as 401 by the global handler, in the same shape the
     * security filter chain produces.
     */
    static class AuthenticationRequiredException extends ApplicationException {
        AuthenticationRequiredException() {
            super(ErrorCode.UNAUTHENTICATED,
                    "Searching inactive jobs requires authentication");
        }
    }
}
