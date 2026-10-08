package com.joblens.api.tracking;

import com.joblens.api.company.CompanyRepository;
import com.joblens.api.company.domain.Company;
import com.joblens.api.support.RepositoryTest;
import com.joblens.api.tracking.domain.TrackedCompany;
import com.joblens.api.user.UserRepository;
import com.joblens.api.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tracking table's rules, against real PostgreSQL.
 *
 * <p>The unique constraint is the whole data-integrity story of this feature,
 * and a constraint can only be tested by a database that enforces it.
 */
@RepositoryTest
class TrackedCompanyRepositoryTest {

    @Autowired
    private TrackedCompanyRepository trackedCompanyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    private User alice;
    private User bob;
    private Company alpha;
    private Company beta;

    @BeforeEach
    void seed() {
        alice = userRepository.saveAndFlush(
                User.register("alice@example.com", "{bcrypt}hash", "Alice", "Example"));
        bob = userRepository.saveAndFlush(
                User.register("bob@example.com", "{bcrypt}hash", "Bob", "Example"));
        alpha = companyRepository.saveAndFlush(
                Company.create("Alpha Systems", "alpha-systems", null, null, null, null, null, null));
        beta = companyRepository.saveAndFlush(
                Company.create("Beta Industries", "beta-industries", null, null, null, null, null, null));
    }

    @Test
    void recordsWhenTrackingStarted() {
        TrackedCompany saved = trackedCompanyRepository.saveAndFlush(TrackedCompany.of(alice, alpha));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTrackedAt()).isNotNull();
    }

    @Test
    void theDatabaseRejectsTrackingTheSameCompanyTwice() {
        // The rule the feature rests on. If this passes only because the
        // service checks first, two simultaneous requests would still create a
        // duplicate -- so it is tested here, without the service.
        trackedCompanyRepository.saveAndFlush(TrackedCompany.of(alice, alpha));

        assertThatThrownBy(() ->
                trackedCompanyRepository.saveAndFlush(TrackedCompany.of(alice, alpha)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentUsersMayTrackTheSameCompany() {
        trackedCompanyRepository.saveAndFlush(TrackedCompany.of(alice, alpha));

        // The uniqueness is per user, not per company.
        TrackedCompany bobs = trackedCompanyRepository.saveAndFlush(TrackedCompany.of(bob, alpha));

        assertThat(bobs.getId()).isNotNull();
    }

    @Test
    void findsOneRelationshipByUserAndCompany() {
        trackedCompanyRepository.saveAndFlush(TrackedCompany.of(alice, alpha));

        assertThat(trackedCompanyRepository.findByUserIdAndCompanyId(alice.getId(), alpha.getId()))
                .isPresent();
        assertThat(trackedCompanyRepository.findByUserIdAndCompanyId(bob.getId(), alpha.getId()))
                .isEmpty();
    }

    @Test
    void listsOnlyTheGivenUsersCompanies() {
        trackedCompanyRepository.saveAndFlush(TrackedCompany.of(alice, alpha));
        trackedCompanyRepository.saveAndFlush(TrackedCompany.of(alice, beta));
        trackedCompanyRepository.saveAndFlush(TrackedCompany.of(bob, beta));

        Page<TrackedCompany> alicesList = trackedCompanyRepository.findByUserId(alice.getId(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"))));

        assertThat(alicesList.getTotalElements()).isEqualTo(2);
        assertThat(alicesList.getContent())
                .extracting(tracking -> tracking.getCompany().getName())
                .containsExactlyInAnyOrder("Alpha Systems", "Beta Industries");
    }

    @Test
    void deletesOnlyTheNamedRelationship() {
        trackedCompanyRepository.saveAndFlush(TrackedCompany.of(alice, alpha));
        trackedCompanyRepository.saveAndFlush(TrackedCompany.of(alice, beta));
        trackedCompanyRepository.saveAndFlush(TrackedCompany.of(bob, alpha));

        int removed = trackedCompanyRepository.deleteByUserAndCompany(alice.getId(), alpha.getId());

        assertThat(removed).isEqualTo(1);
        // Alice's other company and Bob's identical relationship are untouched.
        assertThat(trackedCompanyRepository.findByUserIdAndCompanyId(alice.getId(), beta.getId()))
                .isPresent();
        assertThat(trackedCompanyRepository.findByUserIdAndCompanyId(bob.getId(), alpha.getId()))
                .isPresent();
        // And the company itself still exists.
        assertThat(companyRepository.existsById(alpha.getId())).isTrue();
    }

    @Test
    void deletingSomethingNotTrackedRemovesNothing() {
        assertThat(trackedCompanyRepository.deleteByUserAndCompany(alice.getId(), alpha.getId()))
                .isZero();
    }
}
