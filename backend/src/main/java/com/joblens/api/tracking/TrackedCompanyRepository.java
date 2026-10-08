package com.joblens.api.tracking;

import com.joblens.api.tracking.domain.TrackedCompany;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for {@link TrackedCompany}.
 *
 * <p>Every method takes a user id, and every caller passes the one from the
 * verified token. There is no method that returns tracking rows without one,
 * so there is no query that could leak one user's list to another.
 */
public interface TrackedCompanyRepository extends JpaRepository<TrackedCompany, UUID> {

    Optional<TrackedCompany> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    /**
     * A page of the user's tracked companies, with each company fetched in the
     * same query.
     *
     * <p>The {@code @EntityGraph} is the N+1 fix: every row renders its
     * company's name and logo, and without it one query for a page of twelve is
     * followed by twelve more. Safe with pagination because the association is
     * to-one -- a fetch join multiplies no rows, so {@code LIMIT} still applies
     * in SQL.
     */
    @EntityGraph(attributePaths = "company")
    Page<TrackedCompany> findByUserId(UUID userId, Pageable pageable);

    /**
     * Removes one tracking relationship, returning how many rows went.
     *
     * <p>A single {@code DELETE} statement rather than Spring Data's derived
     * {@code deleteBy...}, which loads each matching entity first and then
     * deletes it one by one -- two round trips to remove one row.
     *
     * <p>{@code clearAutomatically} so a stale copy of the deleted row cannot
     * linger in the persistence context for the rest of the transaction.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            DELETE FROM TrackedCompany t
            WHERE t.user.id = :userId AND t.company.id = :companyId
            """)
    int deleteByUserAndCompany(@Param("userId") UUID userId, @Param("companyId") UUID companyId);
}
