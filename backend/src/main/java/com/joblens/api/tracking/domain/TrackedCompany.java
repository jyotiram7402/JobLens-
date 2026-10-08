package com.joblens.api.tracking.domain;

import com.joblens.api.common.domain.BaseEntity;
import com.joblens.api.company.domain.Company;
import com.joblens.api.user.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A user following a company.
 *
 * <pre>
 *   User 1 ---- * TrackedCompany * ---- 1 Company
 * </pre>
 *
 * <p>An entity rather than a {@code @ManyToMany} join table on {@code User},
 * for two reasons. It carries data of its own -- when tracking started -- which
 * a bare join table cannot. And it keeps the relationship owned by this module:
 * a {@code Set<Company>} on {@code User} would make the user module know about
 * tracking, which is backwards.
 *
 * <p>Immutable once created. There is nothing to edit about having tracked a
 * company; untracking deletes the row. Both associations are
 * {@code updatable = false} so the mapping agrees.
 *
 * <p>Both are {@code LAZY}. The listing fetches the company deliberately,
 * through an entity graph; nothing ever needs the user loaded, because the
 * caller's identity is already known from the token.
 */
@Entity
@Table(name = "tracked_companies")
public class TrackedCompany extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    /** For JPA only. */
    protected TrackedCompany() {
    }

    private TrackedCompany(User user, Company company) {
        this.user = user;
        this.company = company;
    }

    public static TrackedCompany of(User user, Company company) {
        return new TrackedCompany(user, company);
    }

    public User getUser() {
        return user;
    }

    public Company getCompany() {
        return company;
    }

    /**
     * When tracking started. The audit timestamp under a name that says what it
     * means here; a separate column would hold the same value and could drift.
     */
    public Instant getTrackedAt() {
        return getCreatedAt();
    }
}
