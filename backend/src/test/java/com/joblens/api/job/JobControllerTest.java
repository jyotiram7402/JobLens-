package com.joblens.api.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblens.api.common.response.PageResponse;
import com.joblens.api.company.exception.CompanyNotFoundException;
import com.joblens.api.job.domain.EmploymentType;
import com.joblens.api.job.domain.WorkMode;
import com.joblens.api.job.dto.CreateJobRequest;
import com.joblens.api.job.dto.JobCompanyRef;
import com.joblens.api.job.dto.JobSummary;
import com.joblens.api.job.exception.JobNotFoundException;
import com.joblens.api.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract of job search: which parameters bind, which are rejected,
 * and what reaches the service.
 *
 * <p>The service is mocked, so nothing here says anything about whether the
 * filters work -- that is {@code JobSearchRepositoryTest}, against a real
 * database. What this proves is that a request turns into the criteria the
 * caller asked for, and that a bad request never gets that far.
 *
 * <p>Security auto-configuration is excluded: the default chain would answer
 * every request with a 401 before the controller was reached.
 */
@WebMvcTest(value = JobController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@ActiveProfiles("test")
class JobControllerTest {

    private static final String BASE = "/api/v1/jobs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    /** Needed because JwtAuthenticationFilter is a @Component Filter. */
    @MockBean
    private JwtService jwtService;

    private static JobSummary summary() {
        return new JobSummary(UUID.randomUUID(), "Java Backend Developer",
                new JobCompanyRef(UUID.randomUUID(), "Example Company", "example-company"),
                "Pune, Maharashtra, India", EmploymentType.FULL_TIME, WorkMode.HYBRID,
                2, 5, Instant.parse("2026-09-20T10:00:00Z"), true);
    }

    private static PageResponse<JobSummary> onePage() {
        return new PageResponse<>(List.of(summary()), 0, 20, 1, 1, true, true, false, false);
    }

    private static PageResponse<JobSummary> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0, true, true, false, false);
    }

    private JobSearchCriteria capturedCriteria() {
        ArgumentCaptor<JobSearchCriteria> captor = ArgumentCaptor.forClass(JobSearchCriteria.class);
        verify(jobService).search(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    // --- happy paths ------------------------------------------------------

    @Test
    void returnsPaginationMetadata() throws Exception {
        when(jobService.search(any(), any())).thenReturn(onePage());

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Java Backend Developer"))
                .andExpect(jsonPath("$.content[0].company.slug").value("example-company"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));
    }

    @Test
    void searchDefaultsToActiveJobsOnly() throws Exception {
        when(jobService.search(any(), any())).thenReturn(onePage());

        mockMvc.perform(get(BASE)).andExpect(status().isOk());

        assertThat(capturedCriteria().active()).isTrue();
    }

    @Test
    void bindsEveryFilterParameter() throws Exception {
        when(jobService.search(any(), any())).thenReturn(onePage());
        UUID companyId = UUID.randomUUID();

        mockMvc.perform(get(BASE)
                        .param("search", "java")
                        .param("companyId", companyId.toString())
                        .param("location", "Pune")
                        .param("employmentType", "FULL_TIME")
                        .param("workMode", "HYBRID")
                        .param("experienceMin", "2")
                        .param("experienceMax", "5")
                        .param("postedAfter", "2026-09-01T00:00:00Z")
                        .param("postedBefore", "2026-09-30T00:00:00Z"))
                .andExpect(status().isOk());

        JobSearchCriteria criteria = capturedCriteria();
        assertThat(criteria.search()).isEqualTo("java");
        assertThat(criteria.companyId()).isEqualTo(companyId);
        assertThat(criteria.location()).isEqualTo("Pune");
        assertThat(criteria.employmentType()).isEqualTo(EmploymentType.FULL_TIME);
        assertThat(criteria.workMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(criteria.experienceMin()).isEqualTo(2);
        assertThat(criteria.experienceMax()).isEqualTo(5);
        assertThat(criteria.postedAfter()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(criteria.postedBefore()).isEqualTo(Instant.parse("2026-09-30T00:00:00Z"));
    }

    @Test
    void emptyResultIsASuccessNotA404() throws Exception {
        when(jobService.search(any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get(BASE).param("search", "cobol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- rejected requests ------------------------------------------------

    @Test
    void rejectsUnknownEmploymentType() throws Exception {
        // Silently ignoring an unrecognised filter would return results the
        // caller did not ask for and looks like the filter is broken.
        mockMvc.perform(get(BASE).param("employmentType", "PERMANENT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownWorkMode() throws Exception {
        mockMvc.perform(get(BASE).param("workMode", "ANYWHERE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedCompanyId() throws Exception {
        mockMvc.perform(get(BASE).param("companyId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMalformedDate() throws Exception {
        mockMvc.perform(get(BASE).param("postedAfter", "last tuesday"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeExperience() throws Exception {
        mockMvc.perform(get(BASE).param("experienceMin", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsPageSizeAboveTheLimit() throws Exception {
        mockMvc.perform(get(BASE).param("size", "1000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get(BASE).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSortFieldOutsideTheAllowlist() throws Exception {
        mockMvc.perform(get(BASE).param("sort", "salary,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsAnonymousRequestForInactiveJobs() throws Exception {
        // Withdrawn positions are an administrative view. Without this, anyone
        // could enumerate jobs an employer deliberately took down.
        mockMvc.perform(get(BASE).param("active", "false"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    // --- detail and write endpoints ---------------------------------------

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.getById(id)).thenThrow(new JobNotFoundException(id));

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("JOB_NOT_FOUND"));
    }

    @Test
    void getByIdRejectsMalformedUuid() throws Exception {
        mockMvc.perform(get(BASE + "/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsMissingRequiredFields() throws Exception {
        CreateJobRequest invalid = new CreateJobRequest(null, "  ", null, null, null, null,
                null, null, null, null);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.companyId").exists())
                .andExpect(jsonPath("$.details.title").exists())
                .andExpect(jsonPath("$.details.employmentType").exists())
                .andExpect(jsonPath("$.details.workMode").exists());
    }

    @Test
    void createReturns404ForUnknownCompany() throws Exception {
        UUID companyId = UUID.randomUUID();
        when(jobService.create(any())).thenThrow(CompanyNotFoundException.withId(companyId));

        CreateJobRequest request = new CreateJobRequest(companyId, "Java Backend Developer",
                null, "Pune", EmploymentType.FULL_TIME, WorkMode.HYBRID, 2, 5, null, null);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("COMPANY_NOT_FOUND"));
    }

    @Test
    void createRejectsNonHttpApplyUrl() throws Exception {
        CreateJobRequest request = new CreateJobRequest(UUID.randomUUID(), "Developer",
                null, null, EmploymentType.FULL_TIME, WorkMode.REMOTE, null, null,
                "javascript:alert(1)", null);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.applyUrl").exists());
    }
}
