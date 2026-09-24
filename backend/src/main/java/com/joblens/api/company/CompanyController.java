package com.joblens.api.company;

import com.joblens.api.common.response.PageResponse;
import com.joblens.api.common.web.ApiRoutes;
import com.joblens.api.company.dto.CompanyResponse;
import com.joblens.api.company.dto.CompanySummary;
import com.joblens.api.company.dto.CreateCompanyRequest;
import com.joblens.api.company.dto.UpdateCompanyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import java.util.UUID;

/**
 * HTTP layer for companies.
 *
 * <p>Thin by design: it maps a request to a service call and a result to a
 * status code. There is no business logic here, no repository access and no
 * entity in sight. Failures are thrown by the service and rendered by
 * {@code GlobalExceptionHandler}, so there is nothing to catch.
 *
 * <p>There is no DELETE endpoint, deliberately -- see the class javadoc note
 * below and ARCHITECTURE.md.
 */
@RestController
@RequestMapping(ApiRoutes.API_V1 + "/companies")
@Validated
public class CompanyController {

    /**
     * Big enough that a client rarely needs a second request, small enough that
     * one page is a cheap query and a small response.
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * A ceiling, not a suggestion. Without one, {@code ?size=100000} is a free
     * denial-of-service against a 512 MB container.
     */
    private static final int MAX_PAGE_SIZE = 50;

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /**
     * Creates a company.
     *
     * <p>201 with a {@code Location} header pointing at the new resource.
     * 409 if a company with the same normalized name already exists.
     */
    @PostMapping
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CreateCompanyRequest request) {
        CompanyResponse created = companyService.create(request);
        URI location = URI.create(ApiRoutes.API_V1 + "/companies/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Searches active companies by name, newest-agnostic and ordered by name so
     * that paging is stable.
     *
     * <p>Sorting is fixed rather than client-supplied. An open {@code sort}
     * parameter lets a caller order by any mapped property, including ones that
     * have no index, and turns a typo into a 500. Configurable sorting arrives
     * in step 6 with an explicit allowlist.
     */
    @GetMapping
    public PageResponse<CompanySummary> search(

            @RequestParam(name = "search", required = false)
            @Size(max = 200, message = "must be at most 200 characters")
            String search,

            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "must be 0 or greater")
            int page,

            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE)
            @Min(value = 1, message = "must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "must be at most " + MAX_PAGE_SIZE)
            int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return companyService.search(search, pageable);
    }

    /**
     * Fetches one company by id. 404 if it does not exist.
     */
    @GetMapping("/{id}")
    public CompanyResponse getById(@PathVariable UUID id) {
        return companyService.getById(id);
    }

    /**
     * Fetches one company by its URL slug, for frontend routes such as
     * {@code /companies/tata-consultancy-services}. 404 if it does not exist.
     */
    @GetMapping("/by-slug/{slug}")
    public CompanyResponse getBySlug(
            @PathVariable
            @Size(max = 220, message = "must be at most 220 characters")
            String slug) {
        return companyService.getBySlug(slug);
    }

    /**
     * Replaces a company's editable fields. 404 if it does not exist, 409 if the
     * new name collides with a different company.
     */
    @PutMapping("/{id}")
    public CompanyResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateCompanyRequest request) {
        return companyService.update(id, request);
    }
}
