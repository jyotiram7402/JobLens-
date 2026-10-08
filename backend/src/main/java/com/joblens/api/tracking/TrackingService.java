package com.joblens.api.tracking;

import com.joblens.api.common.response.PageResponse;
import com.joblens.api.company.CompanyRepository;
import com.joblens.api.company.domain.Company;
import com.joblens.api.company.exception.CompanyNotFoundException;
import com.joblens.api.tracking.domain.TrackedCompany;
import com.joblens.api.tracking.dto.TrackedCompanyResponse;
import com.joblens.api.tracking.dto.TrackingStatusResponse;
import com.joblens.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Following and unfollowing companies.
 *
 * <p>Every method takes the user id as an argument, and every caller gets it
 * from the security context. No code path accepts a user id from a client, so
 * there is no request that can read or change another user's tracking.
 *
 * <h2>Idempotent by design</h2>
 *
 * <p>Tracking a company you already track succeeds and returns the existing
 * relationship. Untracking a company you do not track succeeds and does
 * nothing. Neither is an error, because neither is a mistake: the user asked
 * for a state, and that state now holds. A 409 for a double-click would be an
 * error message describing a success.
 *
 * <p>The one case that is an error is a company that does not exist, which is
 * a 404 for all three operations -- the same {@code COMPANY_NOT_FOUND} the
 * company endpoints already return, because a bad id is a bad id regardless of
 * which endpoint it was sent to.
 *
 * <h2>Module boundaries</h2>
 *
 * <p>This reads {@code CompanyRepository} and {@code UserRepository} directly
 * rather than going through their services, the same exception
 * {@code JobService} makes: a JPA association needs a managed entity, and those
 * services only hand out DTOs. It reads users and companies; it never changes
 * them.
 */
@Service
public class TrackingService {

    private static final Logger log = LoggerFactory.getLogger(TrackingService.class);

    private final TrackedCompanyRepository trackedCompanyRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    /**
     * Used instead of {@code @Transactional} for {@link #track}, because that
     * method needs to run a <em>second</em> transaction after the first one
     * fails. See {@link #track} for why.
     */
    private final TransactionTemplate transactionTemplate;

    public TrackingService(TrackedCompanyRepository trackedCompanyRepository,
                           CompanyRepository companyRepository,
                           UserRepository userRepository,
                           TransactionTemplate transactionTemplate) {
        this.trackedCompanyRepository = trackedCompanyRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Starts tracking a company. Idempotent.
     *
     * <h3>Concurrent duplicates</h3>
     *
     * <p>The check for an existing row and the insert are not atomic. Two
     * requests arriving together -- a double-click, a retry after a slow
     * response -- can both see "not tracked" and both insert. The unique
     * constraint on {@code (user_id, company_id)} guarantees only one insert
     * wins; the loser gets a constraint violation.
     *
     * <p>That violation must not become a 500, because from the user's point of
     * view nothing went wrong: they asked to track the company and it is
     * tracked. So the loser reads the winner's row and returns it, exactly as if
     * it had found it on the first check.
     *
     * <p>The re-read has to happen in a <em>new</em> transaction. Once a
     * statement fails inside a transaction, that transaction is marked
     * rollback-only -- PostgreSQL refuses further statements in it, and Spring
     * would throw on commit. Catching the exception inside an
     * {@code @Transactional} method and carrying on is the classic mistake here:
     * it looks right and fails at commit. {@link TransactionTemplate} makes the
     * two transactions explicit, which is why it is used for this one method.
     *
     * @throws CompanyNotFoundException if the company does not exist
     */
    public TrackingStatusResponse track(UUID userId, UUID companyId) {
        try {
            return transactionTemplate.execute(status -> trackOrReturnExisting(userId, companyId));
        } catch (DataIntegrityViolationException raceLost) {
            log.info("Concurrent track request for company {} by user {}; returning existing row",
                    companyId, userId);
            return transactionTemplate.execute(status ->
                    trackedCompanyRepository.findByUserIdAndCompanyId(userId, companyId)
                            .map(TrackingStatusResponse::trackedSince)
                            // No row after all: the violation was something else
                            // -- most plausibly a token for a user who no longer
                            // exists. Not a race, so not swallowed.
                            .orElseThrow(() -> raceLost));
        }
    }

    private TrackingStatusResponse trackOrReturnExisting(UUID userId, UUID companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> CompanyNotFoundException.withId(companyId));

        Optional<TrackedCompany> existing =
                trackedCompanyRepository.findByUserIdAndCompanyId(userId, companyId);
        if (existing.isPresent()) {
            return TrackingStatusResponse.trackedSince(existing.get());
        }

        // getReferenceById returns a proxy without querying: the user's
        // existence is already vouched for by the token, and the foreign key
        // catches the rare case where it is not.
        TrackedCompany saved = trackedCompanyRepository.saveAndFlush(
                TrackedCompany.of(userRepository.getReferenceById(userId), company));

        log.info("User {} started tracking company {}", userId, companyId);
        return TrackingStatusResponse.trackedSince(saved);
    }

    /**
     * Stops tracking a company. Idempotent: untracking something not tracked
     * succeeds and changes nothing.
     *
     * <p>Only the relationship row is removed. The company itself is untouched.
     *
     * @throws CompanyNotFoundException if the company does not exist
     */
    @Transactional
    public void untrack(UUID userId, UUID companyId) {
        requireCompanyExists(companyId);

        int removed = trackedCompanyRepository.deleteByUserAndCompany(userId, companyId);
        if (removed > 0) {
            log.info("User {} stopped tracking company {}", userId, companyId);
        }
    }

    /**
     * Whether the user tracks a company.
     *
     * @throws CompanyNotFoundException if the company does not exist
     */
    @Transactional(readOnly = true)
    public TrackingStatusResponse status(UUID userId, UUID companyId) {
        requireCompanyExists(companyId);

        return trackedCompanyRepository.findByUserIdAndCompanyId(userId, companyId)
                .map(TrackingStatusResponse::trackedSince)
                .orElseGet(() -> TrackingStatusResponse.notTracked(companyId));
    }

    /**
     * A page of the user's tracked companies. One query for the page with
     * companies fetched alongside, plus one count -- see the repository.
     */
    @Transactional(readOnly = true)
    public PageResponse<TrackedCompanyResponse> list(UUID userId, Pageable pageable) {
        return PageResponse.from(trackedCompanyRepository.findByUserId(userId, pageable),
                TrackedCompanyResponse::from);
    }

    /**
     * An existence check rather than a load: nothing here needs the company's
     * fields, only the guarantee that the id refers to something.
     */
    private void requireCompanyExists(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw CompanyNotFoundException.withId(companyId);
        }
    }
}
