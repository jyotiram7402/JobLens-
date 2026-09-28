package com.joblens.api.job;

import com.joblens.api.job.domain.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.List;
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
     * A single job with both its company and its skills, for the match endpoint.
     *
     * <p>Two collections would be a cartesian product, but company is to-one,
     * so this is one to-one join plus one collection -- which Hibernate handles
     * in a single query without multiplying rows unmanageably. For one job that
     * is the right trade; for a page of them, see {@link #findSkillsForJobs}.
     */
    @EntityGraph(attributePaths = {"company", "skills"})
    Optional<Job> findWithCompanyAndSkillsById(UUID id);

    /**
     * The most recent active openings, with their companies fetched.
     *
     * <p>For the matching module, which needs a bounded candidate set without
     * reaching into this package's Specification internals. A derived query is
     * enough here -- there is exactly one filter -- and it keeps the module
     * boundary intact: matching depends on this interface, not on how search
     * happens to be built.
     */
    @EntityGraph(attributePaths = "company")
    Page<Job> findByActiveTrue(Pageable pageable);

    /**
     * The skills of many jobs, in one query.
     *
     * <p>This is the N+1 fix for recommendations. Scoring 200 candidate jobs
     * needs every job's skills; letting the lazy collection load per job would
     * be 200 extra queries, and fetch-joining the collection across a paged
     * query would multiply rows and force Hibernate to paginate in memory.
     *
     * <p>A flat projection sidesteps both: one query returns every (job, skill)
     * pair for the candidate set, and the service groups them by job id.
     */
    @Query("""
            SELECT j.id AS jobId, s.normalizedName AS normalizedName, s.name AS name
            FROM Job j JOIN j.skills s
            WHERE j.id IN :jobIds
            """)
    List<JobSkillRow> findSkillsForJobs(@Param("jobIds") Collection<UUID> jobIds);

    /**
     * Whether a company has any openings at all. Cheaper than counting when the
     * answer only needs to be yes or no.
     */
    boolean existsByCompanyId(UUID companyId);

    /** Projection for {@link #findSkillsForJobs}. */
    interface JobSkillRow {
        UUID getJobId();

        String getNormalizedName();

        String getName();
    }
}
