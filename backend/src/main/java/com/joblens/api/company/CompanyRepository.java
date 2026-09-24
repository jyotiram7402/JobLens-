package com.joblens.api.company;

import com.joblens.api.company.domain.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link Company}.
 *
 * <p>Every query here is parameterised -- derived method names and JPQL with
 * named parameters -- so user input is never concatenated into a statement and
 * SQL injection has no surface to attack.
 */
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    /**
     * Looks up a company by its public URL identifier.
     */
    Optional<Company> findBySlug(String slug);

    /**
     * The duplicate check. Callers pass an already-normalized value.
     */
    Optional<Company> findByNormalizedName(String normalizedName);

    /**
     * Used while generating a unique slug. {@code exists} rather than
     * {@code find} because the candidate loop only needs a yes or no, and
     * loading whole rows to discard them would be wasteful.
     */
    boolean existsBySlug(String slug);

    /**
     * Search by name, restricted to active companies.
     *
     * <p>Matching is on {@code normalizedName}, so a search for
     * {@code "TATA  consultancy"} finds {@code "Tata Consultancy Services"}:
     * the caller normalizes the term with the same rules the stored value was
     * built with, which is what makes case, spacing, accents and punctuation
     * stop mattering. That also means the term cannot contain {@code LIKE}
     * wildcards -- normalization strips them.
     *
     * <p>A {@code null} term returns every active company, which is what an
     * unfiltered first page should do.
     *
     * <p>{@code LIKE '%term%'} cannot use a B-tree index, so this is a
     * sequential scan. It is the right trade-off at V1 volumes and the wrong one
     * later; step 6 replaces it with a trigram index. Paging is applied in the
     * database, so no more than one page of rows is ever loaded regardless.
     */
    @Query("""
            SELECT c FROM Company c
            WHERE c.active = true
              AND (:term IS NULL OR c.normalizedName LIKE CONCAT('%', :term, '%'))
            """)
    Page<Company> search(@Param("term") String term, Pageable pageable);
}
