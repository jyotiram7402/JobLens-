package com.joblens.api.company;

import com.joblens.api.company.domain.Company;
import com.joblens.api.company.dto.CompanyResponse;
import com.joblens.api.company.dto.CompanySummary;
import com.joblens.api.company.dto.CreateCompanyRequest;
import com.joblens.api.company.dto.UpdateCompanyRequest;
import com.joblens.api.company.exception.CompanyAlreadyExistsException;
import com.joblens.api.company.exception.CompanyNotFoundException;
import com.joblens.api.company.exception.IllegalCompanyNameException;
import com.joblens.api.common.response.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Company business rules: normalization, slug assignment, duplicate detection
 * and the not-found cases.
 *
 * <p>This is where the transaction boundary sits. The controller above it does
 * HTTP and nothing else; the repository below it does persistence and nothing
 * else.
 *
 * <p>Entities never leave this class. Every method returns a DTO, so a caller
 * cannot accidentally serialise a {@code Company} or mutate one outside a
 * transaction.
 */
@Service
public class CompanyService {

    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * Creates a company.
     *
     * <p>Duplicates are caught twice, on purpose. The explicit lookup produces a
     * good error message naming the company that already exists. The unique
     * index is what actually guarantees correctness: between that lookup and the
     * insert, a concurrent request can create the same company, and only the
     * database can settle that race. Catching the violation turns a 500 into the
     * 409 the caller deserves.
     *
     * @throws CompanyAlreadyExistsException if a company with the same
     *                                       normalized name exists
     */
    @Transactional
    public CompanyResponse create(CreateCompanyRequest request) {
        String normalizedName = CompanyNameNormalizer.normalize(request.name());
        requireMeaningfulName(request.name(), normalizedName);

        companyRepository.findByNormalizedName(normalizedName).ifPresent(existing -> {
            throw CompanyAlreadyExistsException.forName(request.name(), existing.getSlug());
        });

        Company company = Company.create(
                request.name().trim(),
                SlugGenerator.generate(request.name(), companyRepository::existsBySlug),
                trimToNull(request.description()),
                trimToNull(request.websiteUrl()),
                trimToNull(request.careersUrl()),
                trimToNull(request.logoUrl()),
                trimToNull(request.industry()),
                trimToNull(request.location()));

        Company saved = saveHandlingDuplicates(company, request.name());
        log.info("Created company {} with slug '{}'", saved.getId(), saved.getSlug());
        return CompanyResponse.from(saved);
    }

    /**
     * @throws CompanyNotFoundException if no company has this id
     */
    @Transactional(readOnly = true)
    public CompanyResponse getById(UUID id) {
        return CompanyResponse.from(findById(id));
    }

    /**
     * Lookup by the public URL identifier, for frontend routes such as
     * {@code /companies/tata-consultancy-services}.
     *
     * @throws CompanyNotFoundException if no company has this slug
     */
    @Transactional(readOnly = true)
    public CompanyResponse getBySlug(String slug) {
        Company company = companyRepository.findBySlug(slug)
                .orElseThrow(() -> CompanyNotFoundException.withSlug(slug));
        return CompanyResponse.from(company);
    }

    /**
     * Searches active companies by name.
     *
     * <p>The term is normalized with the same rules as the stored value, which
     * is what makes the search insensitive to case, spacing, accents and
     * punctuation. A blank term -- or one that normalizes to nothing, such as
     * {@code "!!!"} -- means "no filter" rather than "no results", because an
     * empty search box should show the first page, not an empty one.
     */
    @Transactional(readOnly = true)
    public PageResponse<CompanySummary> search(String searchTerm, Pageable pageable) {
        String normalizedTerm = CompanyNameNormalizer.normalize(searchTerm);
        String term = normalizedTerm.isEmpty() ? null : normalizedTerm;

        Page<Company> page = companyRepository.search(term, pageable);
        return PageResponse.from(page, CompanySummary::from);
    }

    /**
     * Replaces a company's editable fields.
     *
     * <p>Renaming recomputes the normalized name, so an update can collide with
     * a different company and be rejected with 409. The slug is not regenerated:
     * it is a public identifier that already exists in links.
     *
     * @throws CompanyNotFoundException      if no company has this id
     * @throws CompanyAlreadyExistsException if the new name matches another company
     */
    @Transactional
    public CompanyResponse update(UUID id, UpdateCompanyRequest request) {
        Company company = findById(id);

        String normalizedName = CompanyNameNormalizer.normalize(request.name());
        requireMeaningfulName(request.name(), normalizedName);

        // Compared this way round because the path variable is guaranteed
        // non-null while a Company's id is not, so `id.equals(...)` cannot throw.
        Optional<Company> clash = companyRepository.findByNormalizedName(normalizedName);
        if (clash.isPresent() && !id.equals(clash.get().getId())) {
            throw CompanyAlreadyExistsException.forName(request.name(), clash.get().getSlug());
        }

        company.updateDetails(
                request.name().trim(),
                trimToNull(request.description()),
                trimToNull(request.websiteUrl()),
                trimToNull(request.careersUrl()),
                trimToNull(request.logoUrl()),
                trimToNull(request.industry()),
                trimToNull(request.location()));

        Company saved = saveHandlingDuplicates(company, request.name());
        log.info("Updated company {}", saved.getId());
        return CompanyResponse.from(saved);
    }

    private Company findById(UUID id) {
        return companyRepository.findById(id)
                .orElseThrow(() -> CompanyNotFoundException.withId(id));
    }

    /**
     * Translates a unique-constraint violation into the domain exception. Any
     * other integrity failure is a genuine defect and is left to propagate to
     * the global handler as a 500.
     */
    private Company saveHandlingDuplicates(Company company, String requestedName) {
        try {
            return companyRepository.saveAndFlush(company);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Integrity violation saving company '{}'", requestedName, ex);
            throw CompanyAlreadyExistsException.forName(requestedName);
        }
    }

    /**
     * Rejects names that pass {@code @NotBlank} but normalize to nothing, such
     * as {@code "!!!"} or {@code "---"}. Such a name has no normalized form to
     * deduplicate on and no slug to address it by, so it cannot be stored.
     */
    private void requireMeaningfulName(String name, String normalizedName) {
        if (normalizedName.isEmpty()) {
            throw new IllegalCompanyNameException(name);
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
