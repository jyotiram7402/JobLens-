package com.joblens.api.job;

import com.joblens.api.job.domain.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link Job}.
 *
 * <p>{@link JpaSpecificationExecutor} is what lets search compose filters
 * dynamically -- see {@code JobSpecifications}. Everything it produces becomes
 * SQL, so filtering, sorting and paging all happen in PostgreSQL.
 */
public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    /**
     * Search, with the company fetched in the same query.
     *
     * <p>This override exists purely to add the {@code @EntityGraph}, and it is
     * the N+1 fix. Every result row renders its company's name and slug, and
     * {@code Job.company} is {@code LAZY} -- so without this, one query for a
     * page of 20 jobs is followed by up to 20 more, one per distinct company.
     * That is invisible on seed data and ruinous on a real list.
     *
     * <p>A fetch join is safe here specifically because the association is
     * to-<em>one</em>. Fetch joining a collection alongside pagination forces
     * Hibernate to load every row and paginate in memory (the
     * {@code HHH000104} warning); a to-one join multiplies no rows, so
     * {@code LIMIT}/{@code OFFSET} still work in SQL.
     *
     * <p>The alternatives were making the association {@code EAGER} -- which
     * would drag a company into every query that touches a job, whether or not
     * anyone wanted it -- or a DTO projection, which is faster still but means
     * a second mapping to keep in step with the entity. This is the simplest
     * correct option.
     */
    @Override
    @EntityGraph(attributePaths = "company")
    Page<Job> findAll(Specification<Job> specification, Pageable pageable);

    /**
     * A single job with its company, for the detail endpoint.
     */
    @EntityGraph(attributePaths = "company")
    Optional<Job> findWithCompanyById(UUID id);

    /**
     * Whether a company has any openings at all. Cheaper than counting when the
     * answer only needs to be yes or no.
     */
    boolean existsByCompanyId(UUID companyId);
}
