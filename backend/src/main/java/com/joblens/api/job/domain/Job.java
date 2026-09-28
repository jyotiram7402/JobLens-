package com.joblens.api.job.domain;

import com.joblens.api.common.domain.BaseEntity;
import com.joblens.api.common.text.TextNormalizer;
import com.joblens.api.company.domain.Company;
import com.joblens.api.skill.domain.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A public job opening belonging to a company.
 *
 * <p>Like every entity here, it never leaves the service layer: controllers
 * return {@code JobResponse} / {@code JobSummary}.
 *
 * <p>The company association is {@code LAZY}. Making it eager would load a
 * company for every job in every query whether or not anyone asked, and the
 * search path needs a different fix anyway -- see the {@code @EntityGraph} on
 * {@code JobRepository}, which fetches companies in the same query when a page
 * of results is about to be mapped.
 */
@Entity
@Table(name = "jobs")
public class Job extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "normalized_title", nullable = false, length = 200)
    private String normalizedTitle;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "location", length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false, length = 20)
    private WorkMode workMode;

    /** Null means unspecified, not zero. */
    @Column(name = "experience_min")
    private Integer experienceMin;

    /** Null means unspecified, not unbounded-at-zero. */
    @Column(name = "experience_max")
    private Integer experienceMax;

    @Column(name = "apply_url", length = 1000)
    private String applyUrl;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * The skills this opening asks for. All are treated as required in V1;
     * a nice-to-have distinction would need a column on the join table and a
     * scoring rule to go with it, and nothing has asked for one yet.
     *
     * <p>{ LAZY}, because most reads of a job -- search results, the list
     * page -- do not need them. Matching does, and loads them deliberately:
     * one job through an entity graph, many through a single projection query.
     * See { JobRepository}.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "job_skills",
            joinColumns = @JoinColumn(name = "job_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new LinkedHashSet<>();

    /** For JPA only. */
    protected Job() {
    }

    private Job(Company company, String title, Instant postedAt) {
        this.company = company;
        this.title = title;
        this.normalizedTitle = TextNormalizer.normalize(title);
        this.postedAt = postedAt;
        this.active = true;
    }

    /**
     * @param postedAt when the opening was published. Supplied rather than
     *                 defaulted to "now", because a job imported from a careers
     *                 page was posted when the employer posted it, not when we
     *                 noticed. Callers pass {@code Instant.now()} when there is
     *                 no better answer.
     */
    public static Job create(Company company, String title, String description, String location,
                             EmploymentType employmentType, WorkMode workMode,
                             Integer experienceMin, Integer experienceMax,
                             String applyUrl, Instant postedAt) {
        Job job = new Job(company, title, postedAt);
        job.description = description;
        job.location = location;
        job.employmentType = employmentType;
        job.workMode = workMode;
        job.experienceMin = experienceMin;
        job.experienceMax = experienceMax;
        job.applyUrl = applyUrl;
        return job;
    }

    /**
     * Replaces the editable fields.
     *
     * <p>The company is not among them. Moving an opening between employers is
     * not an edit, it is a different job -- and the column is {@code updatable =
     * false} so the mapping agrees.
     */
    public void updateDetails(String title, String description, String location,
                              EmploymentType employmentType, WorkMode workMode,
                              Integer experienceMin, Integer experienceMax,
                              String applyUrl, Instant postedAt) {
        this.title = title;
        this.normalizedTitle = TextNormalizer.normalize(title);
        this.description = description;
        this.location = location;
        this.employmentType = employmentType;
        this.workMode = workMode;
        this.experienceMin = experienceMin;
        this.experienceMax = experienceMax;
        this.applyUrl = applyUrl;
        this.postedAt = postedAt;
    }

    /**
     * Replaces the skills this opening asks for.
     *
     * <p>The collection is mutated rather than reassigned: Hibernate tracks the
     * instance it handed us, and swapping in a fresh Set detaches that tracking
     * and throws on flush.
     */
    public void replaceSkills(Collection<Skill> replacement) {
        this.skills.clear();
        if (replacement != null) {
            this.skills.addAll(replacement);
        }
    }

    /** Hides the opening from search without deleting anything. */
    public void close() {
        this.active = false;
    }

    public void reopen() {
        this.active = true;
    }

    public Company getCompany() {
        return company;
    }

    public String getTitle() {
        return title;
    }

    public String getNormalizedTitle() {
        return normalizedTitle;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public WorkMode getWorkMode() {
        return workMode;
    }

    public Integer getExperienceMin() {
        return experienceMin;
    }

    public Integer getExperienceMax() {
        return experienceMax;
    }

    public String getApplyUrl() {
        return applyUrl;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public boolean isActive() {
        return active;
    }

    /** Unmodifiable: callers change skills through replaceSkills. */
    public Set<Skill> getSkills() {
        return java.util.Collections.unmodifiableSet(skills);
    }
}
