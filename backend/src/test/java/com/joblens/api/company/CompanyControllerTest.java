package com.joblens.api.company;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblens.api.common.response.PageResponse;
import com.joblens.api.company.dto.CompanyResponse;
import com.joblens.api.company.dto.CompanySummary;
import com.joblens.api.company.dto.CreateCompanyRequest;
import com.joblens.api.company.exception.CompanyAlreadyExistsException;
import com.joblens.api.company.exception.CompanyNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.joblens.api.security.JwtService;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer only: routing, validation, status codes and the error body. The
 * service is mocked, so these tests say nothing about business rules -- that is
 * {@code CompanyServiceTest}'s job -- and everything about the HTTP contract.
 *
 * <p>Security auto-configuration is excluded: the default Spring Security chain
 * would answer every request with a 401 before the controller was reached.
 * Access control is covered end to end by AuthAndProfileIntegrationTest, which
 * runs the real filter chain.
 */
@WebMvcTest(value = CompanyController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@ActiveProfiles("test")
class CompanyControllerTest {

    private static final String BASE = "/api/v1/companies";

    @Autowired
    private MockMvc mockMvc;

    /**
     * JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest includes it
     * in the slice and it needs this collaborator to be constructible. With no
     * Authorization header present it does nothing.
     */
    @MockBean
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CompanyService companyService;

    private static CompanyResponse response(UUID id) {
        return new CompanyResponse(id, "acme-corporation", "Acme Corporation",
                "A description", "https://example.com", "https://example.com/careers",
                null, "Information Technology", "Pune, India", true,
                Instant.parse("2026-09-24T10:00:00Z"), Instant.parse("2026-09-24T10:00:00Z"));
    }

    private static CreateCompanyRequest validRequest() {
        return new CreateCompanyRequest("Acme Corporation", "A description",
                "https://example.com", "https://example.com/careers", null,
                "Information Technology", "Pune, India");
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        UUID id = UUID.randomUUID();
        when(companyService.create(any())).thenReturn(response(id));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/" + id))
                .andExpect(jsonPath("$.slug").value("acme-corporation"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        CreateCompanyRequest invalid = new CreateCompanyRequest("  ", null, null, null,
                null, null, null);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void createRejectsNonHttpUrl() throws Exception {
        CreateCompanyRequest invalid = new CreateCompanyRequest("Acme Corporation", null,
                "javascript:alert(1)", null, null, null, null);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.websiteUrl").exists());
    }

    @Test
    void createRejectsOversizedName() throws Exception {
        CreateCompanyRequest invalid = new CreateCompanyRequest("a".repeat(201), null, null,
                null, null, null, null);

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void createReturns409ForDuplicate() throws Exception {
        when(companyService.create(any()))
                .thenThrow(CompanyAlreadyExistsException.forName("Acme Corporation", "acme-corporation"));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("COMPANY_ALREADY_EXISTS"));
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void getByIdReturnsCompany() throws Exception {
        UUID id = UUID.randomUUID();
        when(companyService.getById(id)).thenReturn(response(id));

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Acme Corporation"))
                // Internal fields must never appear in a response.
                .andExpect(jsonPath("$.normalizedName").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(companyService.getById(id)).thenThrow(CompanyNotFoundException.withId(id));

        mockMvc.perform(get(BASE + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("COMPANY_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value(BASE + "/" + id));
    }

    @Test
    void getByIdRejectsMalformedUuid() throws Exception {
        mockMvc.perform(get(BASE + "/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchReturnsPageEnvelope() throws Exception {
        CompanySummary summary = new CompanySummary(UUID.randomUUID(), "acme-corporation",
                "Acme Corporation", "Information Technology", "Pune, India", null);
        when(companyService.search(eq("acme"), any()))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 20, 1, 1, true, true));

        mockMvc.perform(get(BASE).param("search", "acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("acme-corporation"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true));
    }

    @Test
    void searchRejectsPageSizeAboveTheLimit() throws Exception {
        mockMvc.perform(get(BASE).param("size", "5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void searchRejectsNegativePage() throws Exception {
        mockMvc.perform(get(BASE).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }
}
