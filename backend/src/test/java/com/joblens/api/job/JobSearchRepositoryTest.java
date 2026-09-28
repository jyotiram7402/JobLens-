package com.joblens.api.job;

import com.joblens.api.company.CompanyRepository;
import com.joblens.api.company.domain.Company;
import com.joblens.api.job.domain.EmploymentType;
import com.joblens.api.job.domain.Job;
import com.joblens.api.job.domain.WorkMode;
import com.joblens.api.support.RepositoryTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The search, against real PostgreSQL.
 *
 * <p>This is where the filter logic is actually verified. A mock would only
 * prove that a specification object was built; whether it becomes SQL that
 * returns the right rows -- with the right null handling, the right case
 * folding, the right boolean structure -- can only be answered by a database.
 *
 * <p>Fixtures are deliberately awkward: a keyword in a description but not a
 * title, several spellings of one city, jobs with no experience stated, and a
 * closed job. Searching over tidy data proves very little.
 */
@RepositoryTest
class JobSearchRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private Company alpha;
    private Company beta;

    @BeforeEach
    void seed() {
        alpha = companyRepository.saveAndFlush(company("Alpha Systems", "alpha-systems"));
        beta = companyRepository.saveAndFlush(company("Beta Industries", "beta-industries"));

        // Keyword in the title.
        save(alpha, "Java Backend Developer", "Build REST services.",
                "Pune, Maharashtra, India", EmploymentType.FULL_TIME, WorkMode.HYBRID,
                2, 5, NOW.minus(2, ChronoUnit.DAYS), true);

        // Keyword only in the description.
        save(alpha, "Platform Engineer", "Our platform runs on Java and Spring Boot.",
                "Hybrid - Pune", EmploymentType.FULL_TIME, WorkMode.HYBRID,
                3, null, NOW.minus(5, ChronoUnit.DAYS), true);

        // Upper-case keyword in the title.
        save(beta, "Senior SPRING Engineer", "Lead a team building microservices.",
                "Bengaluru, India", EmploymentType.FULL_TIME, WorkMode.ONSITE,
                6, 10, NOW.minus(1, ChronoUnit.DAYS), true);

        // No experience stated at all.
        save(beta, "Frontend Developer", "React and TypeScript.",
                "Remote", EmploymentType.FULL_TIME, WorkMode.REMOTE,
                null, null, NOW.minus(10, ChronoUnit.DAYS), true);

        save(beta, "Software Engineering Intern", "Six-month internship with Java services.",
                "Pune / Remote", EmploymentType.INTERNSHIP, WorkMode.REMOTE,
                0, 1, NOW.minus(20, ChronoUnit.DAYS), true);

        // Closed.
        save(alpha, "Filled Java Role", "Already filled.",
                "Pune, Maharashtra, India", EmploymentType.FULL_TIME, WorkMode.ONSITE,
                2, 5, NOW.minus(45, ChronoUnit.DAYS), false);
    }

    private static Company company(String name, String slug) {
        return Company.create(name, slug, null, null, null, null, null, null);
    }

    private void save(Company company, String title, String description, String location,
                      EmploymentType employmentType, WorkMode workMode,
                      Integer experienceMin, Integer experienceMax, Instant postedAt,
                      boolean active) {
        Job job = Job.create(company, title, description, location, employmentType, workMode,
                experienceMin, experienceMax, null, postedAt);
        if (!active) {
            job.close();
        }
        jobRepository.saveAndFlush(job);
    }

    private Page<Job> search(JobSearchCriteria criteria) {
        return search(criteria, PageRequest.of(0, 20, JobSortParser.DEFAULT_SORT));
    }

    private Page<Job> search(JobSearchCriteria criteria, Pageable pageable) {
        return jobRepository.findAll(JobSpecifications.from(criteria), pageable);
    }

    private static JobSearchCriteria criteria() {
        return new JobSearchCriteria(null, null, null, null, null, null, null, true, null, null);
    }

    private static JobSearchCriteria withSearch(String search) {
        return new JobSearchCriteria(search, null, null, null, null, null, null, true, null, null);
    }

    // --- keyword ----------------------------------------------------------

    @Test
    void matchesKeywordInTitle() {
        assertThat(search(withSearch("backend")).getContent())
                .extracting(Job::getTitle)
                .containsExactly("Java Backend Developer");
    }

    @Test
    void matchesKeywordInDescriptionAsWellAsTitle() {
        // "java" is in two titles and two descriptions; one job has it only in
        // the description, which is the case this exists to prove.
        assertThat(search(withSearch("java")).getContent())
                .extracting(Job::getTitle)
                .containsExactlyInAnyOrder(
                        "Java Backend Developer",      // title
                        "Platform Engineer",           // description only
                        "Software Engineering Intern");// description only
    }

    @Test
    void keywordSearchIsCaseInsensitive() {
        var lower = search(withSearch("spring")).getContent();
        var upper = search(withSearch("SPRING")).getContent();
        var mixed = search(withSearch("sPrInG")).getContent();

        assertThat(lower).extracting(Job::getTitle).contains("Senior SPRING Engineer");
        assertThat(upper).hasSameSizeAs(lower);
        assertThat(mixed).hasSameSizeAs(lower);
    }

    @Test
    void keywordMatchesPartialWords() {
        assertThat(search(withSearch("engineer")).getContent())
                .extracting(Job::getTitle)
                .contains("Platform Engineer", "Senior SPRING Engineer",
                        "Software Engineering Intern");
    }

    @Test
    void keywordWildcardsAreEscapedRatherThanInterpreted() {
        // Without escaping, "%" is a LIKE wildcard and this would return
        // everything. No job contains a literal percent sign.
        assertThat(search(withSearch("%")).getContent()).isEmpty();
        assertThat(search(withSearch("_")).getContent()).isEmpty();
    }

    @Test
    void unmatchedKeywordReturnsAnEmptyPageNotAnError() {
        Page<Job> page = search(withSearch("cobol"));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
    }

    // --- individual filters -----------------------------------------------

    @Test
    void filtersByCompany() {
        JobSearchCriteria byCompany = new JobSearchCriteria(null, beta.getId(), null, null,
                null, null, null, true, null, null);

        assertThat(search(byCompany).getContent())
                .extracting(job -> job.getCompany().getId())
                .containsOnly(beta.getId());
    }

    @Test
    void filtersByLocationSubstringCaseInsensitively() {
        JobSearchCriteria byLocation = new JobSearchCriteria(null, null, "pune", null,
                null, null, null, true, null, null);

        // Three different spellings, one filter.
        assertThat(search(byLocation).getContent())
                .extracting(Job::getTitle)
                .containsExactlyInAnyOrder("Java Backend Developer", "Platform Engineer",
                        "Software Engineering Intern");
    }

    @Test
    void filtersByEmploymentType() {
        JobSearchCriteria internships = new JobSearchCriteria(null, null, null,
                EmploymentType.INTERNSHIP, null, null, null, true, null, null);

        assertThat(search(internships).getContent())
                .extracting(Job::getTitle)
                .containsExactly("Software Engineering Intern");
    }

    @Test
    void filtersByWorkMode() {
        JobSearchCriteria remote = new JobSearchCriteria(null, null, null, null,
                WorkMode.REMOTE, null, null, true, null, null);

        assertThat(search(remote).getContent())
                .extracting(Job::getWorkMode)
                .containsOnly(WorkMode.REMOTE);
    }

    @Test
    void excludesClosedJobsByDefault() {
        assertThat(search(criteria()).getContent())
                .extracting(Job::getTitle)
                .doesNotContain("Filled Java Role");
    }

    @Test
    void canAskForClosedJobsExplicitly() {
        JobSearchCriteria closed = new JobSearchCriteria(null, null, null, null, null,
                null, null, false, null, null);

        assertThat(search(closed).getContent())
                .extracting(Job::getTitle)
                .containsExactly("Filled Java Role");
    }

    // --- experience -------------------------------------------------------

    @Test
    void experienceMinFindsJobsSomeoneWithThatMuchCouldTake() {
        // Someone with 2 years. Jobs wanting 0-1 are excluded; jobs wanting 6+
        // are not, because the searcher may have more than the minimum they
        // stated -- the ranges still overlap.
        JobSearchCriteria twoYears = new JobSearchCriteria(null, null, null, null, null,
                2, null, true, null, null);

        assertThat(search(twoYears).getContent())
                .extracting(Job::getTitle)
                .contains("Java Backend Developer", "Platform Engineer")
                .doesNotContain("Software Engineering Intern");
    }

    @Test
    void experienceMaxFindsJobsReachableWithAtMostThatMuch() {
        JobSearchCriteria atMostFour = new JobSearchCriteria(null, null, null, null, null,
                null, 4, true, null, null);

        assertThat(search(atMostFour).getContent())
                .extracting(Job::getTitle)
                .contains("Java Backend Developer", "Software Engineering Intern")
                .doesNotContain("Senior SPRING Engineer");
    }

    @Test
    void experienceRangeMatchesOverlappingJobs() {
        JobSearchCriteria threeToFour = new JobSearchCriteria(null, null, null, null, null,
                3, 4, true, null, null);

        assertThat(search(threeToFour).getContent())
                .extracting(Job::getTitle)
                .contains("Java Backend Developer", "Platform Engineer")
                .doesNotContain("Senior SPRING Engineer", "Software Engineering Intern");
    }

    @Test
    void jobsWithUnspecifiedExperienceAreNeverFilteredOut() {
        // The single most important assertion in this class. An employer who
        // stated no range has not said "zero"; treating null as 0 would quietly
        // drop these jobs out of every experience-filtered search.
        JobSearchCriteria tenYears = new JobSearchCriteria(null, null, null, null, null,
                10, null, true, null, null);

        assertThat(search(tenYears).getContent())
                .extracting(Job::getTitle)
                .contains("Frontend Developer");
    }

    @Test
    void openEndedJobRangeMatchesAboveItsMinimum() {
        // "Platform Engineer" wants 3+, with no upper bound.
        JobSearchCriteria twentyYears = new JobSearchCriteria(null, null, null, null, null,
                20, null, true, null, null);

        assertThat(search(twentyYears).getContent())
                .extracting(Job::getTitle)
                .contains("Platform Engineer");
    }

    // --- dates ------------------------------------------------------------

    @Test
    void filtersByPostedAfter() {
        JobSearchCriteria recent = new JobSearchCriteria(null, null, null, null, null,
                null, null, true, NOW.minus(3, ChronoUnit.DAYS), null);

        assertThat(search(recent).getContent())
                .extracting(Job::getTitle)
                .containsExactlyInAnyOrder("Java Backend Developer", "Senior SPRING Engineer");
    }

    @Test
    void filtersByPostedBefore() {
        JobSearchCriteria old = new JobSearchCriteria(null, null, null, null, null,
                null, null, true, null, NOW.minus(9, ChronoUnit.DAYS));

        assertThat(search(old).getContent())
                .extracting(Job::getTitle)
                .containsExactlyInAnyOrder("Frontend Developer", "Software Engineering Intern");
    }

    @Test
    void filtersByDateWindow() {
        JobSearchCriteria window = new JobSearchCriteria(null, null, null, null, null,
                null, null, true,
                NOW.minus(11, ChronoUnit.DAYS), NOW.minus(4, ChronoUnit.DAYS));

        assertThat(search(window).getContent())
                .extracting(Job::getTitle)
                .containsExactlyInAnyOrder("Platform Engineer", "Frontend Developer");
    }

    // --- combinations -----------------------------------------------------

    @Test
    void combinesEveryFilterWithAnd() {
        JobSearchCriteria combined = new JobSearchCriteria(
                "java", alpha.getId(), "pune", EmploymentType.FULL_TIME, WorkMode.HYBRID,
                2, 5, true, NOW.minus(3, ChronoUnit.DAYS), NOW);

        assertThat(search(combined).getContent())
                .extracting(Job::getTitle)
                .containsExactly("Java Backend Developer");
    }

    @Test
    void contradictoryFiltersReturnNothingRatherThanFailing() {
        JobSearchCriteria impossible = new JobSearchCriteria(
                "java", beta.getId(), "munich", EmploymentType.CONTRACT, WorkMode.ONSITE,
                null, null, true, null, null);

        assertThat(search(impossible).getContent()).isEmpty();
    }

    // --- paging and sorting -----------------------------------------------

    @Test
    void pagesInTheDatabase() {
        Page<Job> first = search(criteria(), PageRequest.of(0, 2, JobSortParser.DEFAULT_SORT));

        assertThat(first.getContent()).hasSize(2);
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.hasPrevious()).isFalse();
    }

    @Test
    void laterPagesDoNotRepeatEarlierRows() {
        Page<Job> first = search(criteria(), PageRequest.of(0, 2, JobSortParser.DEFAULT_SORT));
        Page<Job> second = search(criteria(), PageRequest.of(1, 2, JobSortParser.DEFAULT_SORT));

        assertThat(first.getContent()).doesNotContainAnyElementsOf(second.getContent());
        assertThat(second.hasPrevious()).isTrue();
    }

    @Test
    void defaultSortIsNewestFirst() {
        assertThat(search(criteria()).getContent())
                .extracting(Job::getPostedAt)
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void sortsByTitleAscending() {
        Pageable byTitle = PageRequest.of(0, 20, JobSortParser.parse("title,asc"));

        assertThat(search(criteria(), byTitle).getContent())
                .extracting(Job::getTitle)
                .isSorted();
    }

    @Test
    void sortsByPostedAtAscending() {
        Pageable oldestFirst = PageRequest.of(0, 20, JobSortParser.parse("postedAt,asc"));

        assertThat(search(criteria(), oldestFirst).getContent())
                .extracting(Job::getPostedAt)
                .isSorted();
    }

    // --- lookups ----------------------------------------------------------

    @Test
    void findsOneJobWithItsCompany() {
        UUID id = search(withSearch("backend")).getContent().get(0).getId();

        assertThat(jobRepository.findWithCompanyById(id))
                .get()
                .extracting(job -> job.getCompany().getName())
                .isEqualTo("Alpha Systems");
    }

    @Test
    void reportsWhetherACompanyHasOpenings() {
        assertThat(jobRepository.existsByCompanyId(alpha.getId())).isTrue();
        assertThat(jobRepository.existsByCompanyId(UUID.randomUUID())).isFalse();
    }
}
