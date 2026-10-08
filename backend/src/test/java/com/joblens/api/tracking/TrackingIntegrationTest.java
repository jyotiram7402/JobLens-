package com.joblens.api.tracking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblens.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Company tracking through the real filter chain, against a real database.
 *
 * <p>The security half of this cannot be unit tested. {@code GET /companies/**}
 * is public, and tracking status is carved out of that rule by ordering in
 * {@code SecurityConfig}. Whether that ordering is right -- and whether the
 * public company endpoints are still public afterwards -- is a property of the
 * assembled application.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class TrackingIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin() throws Exception {
        String email = "tracker-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","firstName":"Track","lastName":"Er"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String createCompany(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","industry":"Information Technology","location":"Pune"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String trackPath(String companyId) {
        return "/api/v1/companies/" + companyId + "/track";
    }

    // --- security ---------------------------------------------------------

    @Test
    void everyTrackingEndpointRequiresAuthentication() throws Exception {
        String anyCompany = UUID.randomUUID().toString();

        mockMvc.perform(post(trackPath(anyCompany))).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(trackPath(anyCompany))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me/tracked-companies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trackingStatusIsNotSwallowedByThePublicCompaniesRule() throws Exception {
        // GET /companies/** is public. Status is a GET under that prefix, and is
        // carved out ahead of it. If the ordering in SecurityConfig were wrong
        // this would reach the controller anonymously instead of returning 401.
        mockMvc.perform(get(trackPath(UUID.randomUUID().toString())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void publicCompanyEndpointsAreStillPublic() throws Exception {
        // The carve-out must not have closed ordinary discovery.
        mockMvc.perform(get("/api/v1/companies")).andExpect(status().isOk());
    }

    @Test
    void aCompanyCalledTrackKeepsAPublicPage() throws Exception {
        // /companies/by-slug/track has the shape of the tracking route, so the
        // security rule treats it as authenticated. That would lock anonymous
        // visitors out of the page of any company whose slug was "track".
        // The fix is that "track" is a reserved slug: such a company gets
        // track-2, and its page stays public.
        String token = registerAndLogin();

        String body = mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Track"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("track-2"))
                .andReturn().getResponse().getContentAsString();

        String slug = objectMapper.readTree(body).get("slug").asText();

        mockMvc.perform(get("/api/v1/companies/by-slug/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Track"));
    }

    // --- behaviour --------------------------------------------------------

    @Test
    void tracksAndReportsTheStatus() throws Exception {
        String token = registerAndLogin();
        String companyId = createCompany(token, "Tracked Co " + UUID.randomUUID());

        mockMvc.perform(get(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracked").value(false));

        mockMvc.perform(post(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(companyId))
                .andExpect(jsonPath("$.tracked").value(true))
                .andExpect(jsonPath("$.trackedAt").exists());

        mockMvc.perform(get(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracked").value(true));
    }

    @Test
    void trackingTwiceSucceedsBothTimes() throws Exception {
        // Idempotent: a double-click is not an error.
        String token = registerAndLogin();
        String companyId = createCompany(token, "Twice Co " + UUID.randomUUID());

        mockMvc.perform(post(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tracked").value(true));

        // And still exactly one row.
        mockMvc.perform(get("/api/v1/users/me/tracked-companies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void untracksAndUntrackingAgainIsHarmless() throws Exception {
        String token = registerAndLogin();
        String companyId = createCompany(token, "Untrack Co " + UUID.randomUUID());

        mockMvc.perform(post(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.tracked").value(false));

        // Untracking removes the relationship, never the company.
        mockMvc.perform(get("/api/v1/companies/" + companyId)).andExpect(status().isOk());
    }

    @Test
    void anUnknownCompanyIs404ForEveryOperation() throws Exception {
        String token = registerAndLogin();
        String missing = UUID.randomUUID().toString();

        mockMvc.perform(post(trackPath(missing)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("COMPANY_NOT_FOUND"));
        mockMvc.perform(delete(trackPath(missing)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(trackPath(missing)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMalformedCompanyIdIs400() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post(trackPath("not-a-uuid")).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eachUserSeesOnlyTheirOwnList() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        String companyId = createCompany(alice, "Private Co " + UUID.randomUUID());

        mockMvc.perform(post(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/tracked-companies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyId").value(companyId))
                .andExpect(jsonPath("$.content[0].name").exists())
                .andExpect(jsonPath("$.content[0].trackedAt").exists());

        // Bob has tracked nothing, and there is no parameter through which he
        // could ask for Alice's list.
        mockMvc.perform(get("/api/v1/users/me/tracked-companies")
                        .param("userId", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // And Alice's tracking does not appear as Bob's status.
        mockMvc.perform(get(trackPath(companyId)).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(jsonPath("$.tracked").value(false));
    }

    @Test
    void listPageSizeIsCapped() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/users/me/tracked-companies")
                        .param("size", "5000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
