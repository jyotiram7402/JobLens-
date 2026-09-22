package com.joblens.api.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Fields every persistent entity in JobLens shares.
 *
 * <p>This is a mapped superclass, not a table and not a business entity: it
 * exists so that companies, jobs, users and everything after them get an
 * identifier, audit timestamps and optimistic locking without each module
 * re-deciding how to do it.
 *
 * <ul>
 *   <li><b>Identity</b> comes from a database sequence/identity column. Ids are
 *       assigned by PostgreSQL, never by the application.</li>
 *   <li><b>Timestamps</b> are maintained by Spring Data auditing (enabled in
 *       {@code JpaConfig}) and stored as UTC instants. They are not updatable
 *       by callers.</li>
 *   <li><b>Version</b> gives optimistic locking. Two concurrent updates to the
 *       same row fail loudly instead of silently overwriting each other, which
 *       matters once tracking and matching write on a user's behalf.</li>
 * </ul>
 *
 * <p>Columns are defined here rather than in a migration; Flyway still owns the
 * actual schema, and {@code ddl-auto: validate} fails startup if the two ever
 * disagree.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Long getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    /**
     * Entities are equal only when both have been persisted and share an id.
     * An unsaved entity is equal to nothing but itself, which is the behaviour
     * that keeps collections correct across a {@code persist()} call.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that) || !getClass().isInstance(other)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    /**
     * Deliberately constant per type. An id-based hash code would change when
     * the entity is persisted, losing it inside any hash-based collection it had
     * already been added to.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "%s{id=%d}".formatted(getClass().getSimpleName(), id);
    }
}
