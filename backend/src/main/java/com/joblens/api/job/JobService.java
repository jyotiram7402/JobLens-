package com.joblens.api.job;

import com.joblens.api.common.response.PageResponse;
import com.joblens.api.company.CompanyRepository;
import com.joblens.api.company.domain.Company;
import com.joblens.api.company.exception.CompanyNotFoundException;
import com.joblens.api.job.domain.Job;
import com.joblens.api.job.dto.CreateJobRequest;
import com.joblens.api.job.dto.JobResponse;
import com.joblens.api.job.dto.JobSummary;
import com.joblens.api.job.dto.UpdateJobRequest;
import com.joblens.api.job.exception.JobNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Job business rules: creation against a real company, retrieval, update,
 * closing, and search.
 *
 * <p>Entities never leave this class, and every read maps to a DTO inside the
 * transaction -- the company association is lazy, so mapping outside would fail
 * to initialise it.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;

    /**
     * The company module is reached through its repository here rather than
     * {@code CompanyService} because all this needs is the entity to associate,
     * and {@code CompanyService} only hands out DTOs. It is the one place the
     * "modules talk through services" rule bends, and it bends because a JPA
     * association needs a managed entity, not a projection of one.
     */
    private final CompanyRepository companyRepository;

    public JobService(JobRepository jobRepository, CompanyRepository companyRepository) {
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * @throws CompanyNotFoundException if the company does not exist
     */
    @Transactional
    public JobResponse create(CreateJobRequest request) {
        Company company = companyRepository.findById(request.companyId())
                .orElseThrow(() -> CompanyNotFoundException.withId(request.companyId()));

        Job job = Job.create(
                company,
                request.title().trim(),
                trimToNull(request.description()),
                trimToNull(request.location()),
                request.employmentType(),
                request.workMode(),
                request.experienceMin(),
                request.experienceMax(),
                trimToNull(request.applyUrl()),
                request.postedAt() == null ? Instant.now() : request.postedAt());

        Job saved = jobRepository.saveAndFlush(job);
        log.info("Created job {} for company {}", saved.getId(), company.getId());
        return JobResponse.from(saved);
    }

    /**
     * @throws JobNotFoundException if no job has this id
     */
    @Transactional(readOnly = true)
    public JobResponse getById(UUID id) {
        return JobResponse.from(findWithCompany(id));
    }

    /**
     * Searches jobs.
     *
     * <p>Filters compose with AND; the keyword matches title OR description.
     * The criteria object decides what is filtered on and
     * {@code JobSpecifications} turns that into SQL -- no row is examined in
     * Java, and paging is applied by the database.
     */
    @Transactional(readOnly = true)
    public PageResponse<JobSummary> search(JobSearchCriteria criteria, Pageable pageable) {
        criteria.validate();

        Page<Job> page = jobRepository.findAll(JobSpecifications.from(criteria), pageable);
        return PageResponse.from(page, JobSummary::from);
    }

    /**
     * @throws JobNotFoundException if no job has this id
     */
    @Transactional
    public JobResponse update(UUID id, UpdateJobRequest request) {
        Job job = findWithCompany(id);

        job.updateDetails(
                request.title().trim(),
                trimToNull(request.description()),
                trimToNull(request.location()),
                request.employmentType(),
                request.workMode(),
                request.experienceMin(),
                request.experienceMax(),
                trimToNull(request.applyUrl()),
                request.postedAt() == null ? job.getPostedAt() : request.postedAt());

        log.info("Updated job {}", id);
        return JobResponse.from(job);
    }

    /**
     * Withdraws an opening from search without deleting it.
     *
     * <p>There is no delete endpoint, for the same reason companies have none:
     * tracking rows and, later, scan history will reference jobs, and a filled
     * position is a historical fact rather than a mistake to erase.
     *
     * @throws JobNotFoundException if no job has this id
     */
    @Transactional
    public JobResponse close(UUID id) {
        Job job = findWithCompany(id);
        job.close();
        log.info("Closed job {}", id);
        return JobResponse.from(job);
    }

    private Job findWithCompany(UUID id) {
        return jobRepository.findWithCompanyById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
