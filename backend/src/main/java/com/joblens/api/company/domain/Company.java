package com.joblens.api.company.domain;

import com.joblens.api.common.domain.BaseEntity;
import com.joblens.api.company.CompanyNameNormalizer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A company JobLens knows about, whether it was entered directly or resolved
 * from a scan.
 *
 * <p>This is a persistence type and never leaves the service layer. Controllers
 * return {@code CompanyResponse} / {@code CompanySummary} instead, so the public
 * API is not welded to the schema and a column rename is not a breaking change.
 *
 * <p>There are no public setters. State changes go through named methods that
 * say what they mean and keep derived fields correct -- in particular,
 * {@code normalizedName} is always recomputed from {@code name} and can never be
 * set independently, because the two disagreeing would silently break both
 * duplicate detection and search.
 */
@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 200)
    private String normalizedName;

    @Column(name = "slug", nullable = false, length = 220, updatable = false)
    private String slug;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "careers_url", length = 500)
    private String careersUrl;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "industry", length = 120)
    private String industry;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * For JPA only. Not for application code -- use {@code create(...)}.
     */
    protected Company() {
    }

    private Company(String name, String slug) {
        this.name = name;
        this.slug = slug;
        this.normalizedName = CompanyNameNormalizer.normalize(name);
        this.active = true;
    }

    /**
     * Creates a company. The slug is supplied by the caller because choosing one
     * requires knowing which slugs are already taken, which is a repository
     * concern and not the entity's business.
     */
    public static Company create(String name, String slug, String description, String websiteUrl,
                                 String careersUrl, String logoUrl, String industry,
                                 String location) {
        Company company = new Company(name, slug);
        company.description = description;
        company.websiteUrl = websiteUrl;
        company.careersUrl = careersUrl;
        company.logoUrl = logoUrl;
        company.industry = industry;
        company.location = location;
        return company;
    }

    /**
     * Applies an update from a client.
     *
     * <p>The slug is deliberately not updated. It is a public identifier that
     * already exists in links, bookmarks and anything the frontend has cached;
     * regenerating it on every rename would silently break all of them. The
     * normalized name, by contrast, must follow the name, or the company stops
     * being findable under what it is now called.
     */
    public void updateDetails(String name, String description, String websiteUrl,
                              String careersUrl, String logoUrl, String industry,
                              String location) {
        this.name = name;
        this.normalizedName = CompanyNameNormalizer.normalize(name);
        this.description = description;
        this.websiteUrl = websiteUrl;
        this.careersUrl = careersUrl;
        this.logoUrl = logoUrl;
        this.industry = industry;
        this.location = location;
    }

    /**
     * Hides the company from search without deleting anything. Jobs, tracking
     * rows and scan history that reference it stay intact.
     */
    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public String getCareersUrl() {
        return careersUrl;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public String getIndustry() {
        return industry;
    }

    public String getLocation() {
        return location;
    }

    public boolean isActive() {
        return active;
    }
}
