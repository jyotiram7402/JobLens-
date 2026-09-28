package com.joblens.api.job;

import com.joblens.api.company.CompanyRepository;
import com.joblens.api.company.domain.Company;
import com.joblens.api.company.exception.CompanyNotFoundException;
import com.joblens.api.common.exception.InvalidRequestException;
import com.joblens.api.job.domain.EmploymentType;
import com.joblens.api.job.domain.Job;
import com.joblens.api.job.domain.WorkMode;
import com.joblens.api.job.dto.CreateJobRequest;
import com.joblens.api.job.dto.JobResponse;
import com.joblens.api.job.dto.UpdateJobRequest;
import com.joblens.api.job.exception.JobNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private com.joblens.api.skill.SkillService skillService;

    @InjectMocks
    private JobService jobService;

    private static Company company() {
        return Company.create("Alpha Systems", "alpha-systems", null, null, null, null,
                null, null);
    }

    private static CreateJobRequest createRequest(UUID companyId, Instant postedAt) {
        return new CreateJobRequest(companyId, "  Java Backend Developer  ", "  ",
                " Pune ", EmploymentType.FULL_TIME, WorkMode.HYBRID, 2, 5, null,
                java.util.List.of("Java"), postedAt);
    }

    @Test
    void createsJobAgainstAnExistingCompany() {
        UUID companyId = UUID.randomUUID();
        Instant postedAt = Instant.parse("2026-09-20T10:00:00Z");
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company()));
        when(jobRepository.saveAndFlush(any(Job.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobResponse response = jobService.create(createRequest(companyId, postedAt));

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).saveAndFlush(saved.capture());

        assertThat(saved.getValue().getTitle()).isEqualTo("Java Backend Developer");
        assertThat(saved.getValue().getNormalizedTitle()).isEqualTo("java backend developer");
        assertThat(saved.getValue().getLocation()).isEqualTo("Pune");
        // Blank optional fields become null rather than empty strings.
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(response.postedAt()).isEqualTo(postedAt);
    }

    @Test
    void defaultsPostedAtToNowWhenNotSupplied() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company()));
        when(jobRepository.saveAndFlush(any(Job.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now().minus(1, ChronoUnit.MINUTES);
        jobService.create(createRequest(companyId, null));

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPostedAt()).isAfter(before);
    }

    @Test
    void rejectsJobForUnknownCompany() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.create(createRequest(companyId, null)))
                .isInstanceOf(CompanyNotFoundException.class);

        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void throwsNotFoundForUnknownJob() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findWithCompanyAndSkillsById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getById(id))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void updateRecomputesNormalizedTitle() {
        UUID id = UUID.randomUUID();
        Job job = Job.create(company(), "Old Title", null, null, EmploymentType.FULL_TIME,
                WorkMode.ONSITE, null, null, null, Instant.parse("2026-09-01T00:00:00Z"));
        when(jobRepository.findWithCompanyAndSkillsById(id)).thenReturn(Optional.of(job));

        jobService.update(id, new UpdateJobRequest("Senior Java Engineer", null, null,
                EmploymentType.CONTRACT, WorkMode.REMOTE, 5, 8, null, null, null));

        assertThat(job.getTitle()).isEqualTo("Senior Java Engineer");
        assertThat(job.getNormalizedTitle()).isEqualTo("senior java engineer");
        assertThat(job.getEmploymentType()).isEqualTo(EmploymentType.CONTRACT);
    }

    @Test
    void updateKeepsTheOriginalPostedAtWhenNotSupplied() {
        UUID id = UUID.randomUUID();
        Instant originallyPosted = Instant.parse("2026-09-01T00:00:00Z");
        Job job = Job.create(company(), "Old Title", null, null, EmploymentType.FULL_TIME,
                WorkMode.ONSITE, null, null, null, originallyPosted);
        when(jobRepository.findWithCompanyAndSkillsById(id)).thenReturn(Optional.of(job));

        jobService.update(id, new UpdateJobRequest("New Title", null, null,
                EmploymentType.FULL_TIME, WorkMode.ONSITE, null, null, null, null, null));

        assertThat(job.getPostedAt()).isEqualTo(originallyPosted);
    }

    @Test
    void closeHidesTheJobWithoutDeletingIt() {
        UUID id = UUID.randomUUID();
        Job job = Job.create(company(), "A Job", null, null, EmploymentType.FULL_TIME,
                WorkMode.ONSITE, null, null, null, Instant.now());
        when(jobRepository.findWithCompanyAndSkillsById(id)).thenReturn(Optional.of(job));

        JobResponse response = jobService.close(id);

        assertThat(response.active()).isFalse();
        verify(jobRepository, never()).delete(any());
    }

    @Test
    void searchValidatesCriteriaBeforeQuerying() {
        JobSearchCriteria inverted = new JobSearchCriteria(null, null, null, null, null,
                5, 2, true, null, null);

        assertThatThrownBy(() -> jobService.search(inverted, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidRequestException.class);

        verify(jobRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(PageRequest.class));
    }
}
