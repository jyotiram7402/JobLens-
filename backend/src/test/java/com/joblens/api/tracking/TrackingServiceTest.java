package com.joblens.api.tracking;

import com.joblens.api.common.response.PageResponse;
import com.joblens.api.company.CompanyRepository;
import com.joblens.api.company.domain.Company;
import com.joblens.api.company.exception.CompanyNotFoundException;
import com.joblens.api.tracking.domain.TrackedCompany;
import com.joblens.api.tracking.dto.TrackedCompanyResponse;
import com.joblens.api.tracking.dto.TrackingStatusResponse;
import com.joblens.api.user.UserRepository;
import com.joblens.api.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tracking rules, with the repositories mocked.
 *
 * <p>The {@link TransactionTemplate} is real, built on a mocked transaction
 * manager. That is what lets the concurrent-duplicate path be tested here at
 * all: the template genuinely runs the callback, propagates the exception and
 * runs the second callback, without a database underneath it.
 */
@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID COMPANY_ID = UUID.randomUUID();

    @Mock
    private TrackedCompanyRepository trackedCompanyRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private UserRepository userRepository;

    private TrackingService trackingService;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(mock(PlatformTransactionManager.class));
        trackingService = new TrackingService(trackedCompanyRepository, companyRepository,
                userRepository, transactionTemplate);
    }

    private static Company company() {
        return Company.create("Example Company", "example-company", null, null, null, null,
                "Information Technology", "Pune, India");
    }

    private static User user() {
        return User.register("user@example.com", "{bcrypt}hash", "Test", "User");
    }

    // --- track ------------------------------------------------------------

    @Test
    void tracksACompanyItWasNotAlreadyTracking() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(trackedCompanyRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user());
        when(trackedCompanyRepository.saveAndFlush(any(TrackedCompany.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TrackingStatusResponse response = trackingService.track(USER_ID, COMPANY_ID);

        assertThat(response.tracked()).isTrue();
        verify(trackedCompanyRepository).saveAndFlush(any(TrackedCompany.class));
    }

    @Test
    void trackingTwiceIsIdempotentAndInsertsNothing() {
        // Already tracked: the request succeeds with the existing relationship
        // rather than failing with a conflict that describes a success.
        TrackedCompany existing = TrackedCompany.of(user(), company());
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(trackedCompanyRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(existing));

        TrackingStatusResponse response = trackingService.track(USER_ID, COMPANY_ID);

        assertThat(response.tracked()).isTrue();
        verify(trackedCompanyRepository, never()).saveAndFlush(any());
    }

    @Test
    void aConcurrentDuplicateReturnsTheWinnersRowInsteadOfFailing() {
        // Both requests passed the existence check; this one lost the insert.
        // The unique constraint settles it, and the loser must still succeed.
        TrackedCompany winner = TrackedCompany.of(user(), company());
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(trackedCompanyRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.empty())       // first check, inside the failed transaction
                .thenReturn(Optional.of(winner));   // re-read, in a fresh one
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user());
        when(trackedCompanyRepository.saveAndFlush(any(TrackedCompany.class)))
                .thenThrow(new DataIntegrityViolationException("uq_tracked_companies_user_company"));

        TrackingStatusResponse response = trackingService.track(USER_ID, COMPANY_ID);

        assertThat(response.tracked()).isTrue();
    }

    @Test
    void anIntegrityViolationThatIsNotARaceIsNotSwallowed() {
        // Violation, but no row afterwards: something else went wrong -- most
        // plausibly a token for a user that no longer exists. Hiding it would
        // report success for a request that changed nothing.
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company()));
        when(trackedCompanyRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user());
        when(trackedCompanyRepository.saveAndFlush(any(TrackedCompany.class)))
                .thenThrow(new DataIntegrityViolationException("fk_user"));

        assertThatThrownBy(() -> trackingService.track(USER_ID, COMPANY_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void trackingAnUnknownCompanyIs404() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trackingService.track(USER_ID, COMPANY_ID))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(trackedCompanyRepository, never()).saveAndFlush(any());
    }

    // --- untrack ----------------------------------------------------------

    @Test
    void untracksACompany() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(true);
        when(trackedCompanyRepository.deleteByUserAndCompany(USER_ID, COMPANY_ID)).thenReturn(1);

        trackingService.untrack(USER_ID, COMPANY_ID);

        verify(trackedCompanyRepository).deleteByUserAndCompany(USER_ID, COMPANY_ID);
    }

    @Test
    void untrackingSomethingNotTrackedSucceedsQuietly() {
        // Idempotent: the requested state, not tracked, already holds.
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(true);
        when(trackedCompanyRepository.deleteByUserAndCompany(USER_ID, COMPANY_ID)).thenReturn(0);

        trackingService.untrack(USER_ID, COMPANY_ID);

        verify(trackedCompanyRepository).deleteByUserAndCompany(USER_ID, COMPANY_ID);
    }

    @Test
    void untrackingAnUnknownCompanyIs404() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> trackingService.untrack(USER_ID, COMPANY_ID))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(trackedCompanyRepository, never()).deleteByUserAndCompany(any(), any());
    }

    @Test
    void untrackingNeverTouchesTheCompanyItself() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(true);

        trackingService.untrack(USER_ID, COMPANY_ID);

        verify(companyRepository, never()).delete(any());
        verify(companyRepository, never()).deleteById(any());
    }

    // --- status -----------------------------------------------------------

    @Test
    void reportsTrackedWithTheTrackedTime() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(true);
        when(trackedCompanyRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(TrackedCompany.of(user(), company())));

        assertThat(trackingService.status(USER_ID, COMPANY_ID).tracked()).isTrue();
    }

    @Test
    void reportsNotTrackedWithNoTime() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(true);
        when(trackedCompanyRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        TrackingStatusResponse status = trackingService.status(USER_ID, COMPANY_ID);

        assertThat(status.tracked()).isFalse();
        assertThat(status.trackedAt()).isNull();
        assertThat(status.companyId()).isEqualTo(COMPANY_ID);
    }

    @Test
    void statusOfAnUnknownCompanyIs404() {
        when(companyRepository.existsById(COMPANY_ID)).thenReturn(false);

        assertThatThrownBy(() -> trackingService.status(USER_ID, COMPANY_ID))
                .isInstanceOf(CompanyNotFoundException.class);
    }

    // --- list -------------------------------------------------------------

    @Test
    void listsOnlyTheCallersTrackedCompanies() {
        // The repository is only ever asked for this user's rows. There is no
        // method that returns tracking rows without a user id.
        Pageable pageable = PageRequest.of(0, 12);
        when(trackedCompanyRepository.findByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(TrackedCompany.of(user(), company())),
                        pageable, 1));

        PageResponse<TrackedCompanyResponse> page = trackingService.list(USER_ID, pageable);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).name()).isEqualTo("Example Company");
        verify(trackedCompanyRepository).findByUserId(eq(USER_ID), any(Pageable.class));
    }
}
