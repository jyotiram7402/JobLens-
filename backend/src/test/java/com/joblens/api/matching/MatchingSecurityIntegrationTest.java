package com.joblens.api.matching;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access control and end-to-end behaviour of the matching endpoints, through
 * the real filter chain and a real database.
 *
 * <p>The security part cannot be unit tested. {@code GET /api/v1/jobs/**} is
 * public for discovery, and the two matching routes are carved out of it by
 * ordering rules in {@code SecurityConfig}. Whether that ordering is right is a
 * property of the assembled application — get it wrong and one user's match
 * results become an anonymous read.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class MatchingSecurityIntegrationTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Registers a fresh account and returns its token. */
    private String registerAndLogin() throws Exception {
        String email = "match-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","firstName":"Test","lastName":"User"}
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

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private void setProfile(String token, String payload) throws Exception {
        mockMvc.perform(put("/api/v1/users/me/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    /** Creates a company and a job on it, returning the job id. */
    private UUID createJob(String token) throws Exception {
        String companyBody = mockMvc.perform(post("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Matching Test Company %s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String companyId = objectMapper.readTree(companyBody).get("id").asText();

        String jobBody = mockMvc.perform(post("/api/v1/jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":"%s","title":"Java Backend Developer",
                                 "location":"Pune, Maharashtra, India",
                                 "employmentType":"FULL_TIME","workMode":"HYBRID",
                                 "experienceMin":2,"experienceMax":5,
                                 "skills":["Java","Spring Boot","PostgreSQL"]}
                                """.formatted(companyId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(jobBody).get("id").asText());
    }

    // --- access control ---------------------------------------------------

    @Test
    void matchRequiresAuthentication() throws Exception {
        // Public job reads must not have opened this route. If the ordering in
        // SecurityConfig is wrong, this returns 200 instead.
        mockMvc.perform(get("/api/v1/jobs/" + UUID.randomUUID() + "/match"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void recommendationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/recommended"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void publicJobReadsAreStillPublic() throws Exception {
        // The carve-out above must not have closed ordinary discovery.
        mockMvc.perform(get("/api/v1/jobs")).andExpect(status().isOk());
    }

    @Test
    void aMalformedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/recommended")
                        .header(HttpHeaders.AUTHORIZATION, bearer("not.a.token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TOKEN_INVALID"));
    }

    @Test
    void thereIsNoWayToAskForAnotherUsersMatch() throws Exception {
        String token = registerAndLogin();
        setProfile(token, """
                {"skills":["Java"],"yearsOfExperience":3}
                """);
        UUID jobId = createJob(token);

        // A userId parameter is not a field the API has; supplying one changes
        // nothing, because the profile used is always the token's.
        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/match")
                        .param("userId", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId.toString()));
    }

    // --- behaviour --------------------------------------------------------

    @Test
    void returnsAScoreWithABreakdownAndAnExplanation() throws Exception {
        String token = registerAndLogin();
        setProfile(token, """
                {"skills":["Java","Spring Boot","Docker"],
                 "yearsOfExperience":3,
                 "preferredRoles":["Java Backend Developer"],
                 "preferredLocations":["Pune"],
                 "remotePreference":"HYBRID"}
                """);
        UUID jobId = createJob(token);

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/match")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scored").value(true))
                .andExpect(jsonPath("$.score").isNumber())
                .andExpect(jsonPath("$.maxScore").value(100))
                .andExpect(jsonPath("$.breakdown.skills.matchedSkills").isArray())
                .andExpect(jsonPath("$.breakdown.skills.missingSkills[0]").value("PostgreSQL"))
                .andExpect(jsonPath("$.breakdown.experience.applicable").value(true))
                .andExpect(jsonPath("$.breakdown.location.matched").value(true))
                .andExpect(jsonPath("$.breakdown.role.matched").value(true))
                .andExpect(jsonPath("$.breakdown.workMode.matched").value(true))
                .andExpect(jsonPath("$.explanation").isArray());
    }

    @Test
    void skillNamesMatchAcrossCasing() throws Exception {
        // The job asks for "Spring Boot"; the user typed "spring boot". They are
        // one row in the shared vocabulary, so this must count as a match.
        String token = registerAndLogin();
        setProfile(token, """
                {"skills":["java","spring boot"],"yearsOfExperience":3}
                """);
        UUID jobId = createJob(token);

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/match")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown.skills.matchedSkills.length()").value(2));
    }

    @Test
    void anEmptyProfileIsAskedToFillItInRatherThanScoredZero() throws Exception {
        String token = registerAndLogin();
        UUID jobId = createJob(token);

        mockMvc.perform(get("/api/v1/jobs/" + jobId + "/match")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PROFILE_NOT_READY"));
    }

    @Test
    void matchingAnUnknownJobIs404() throws Exception {
        String token = registerAndLogin();
        setProfile(token, """
                {"skills":["Java"],"yearsOfExperience":3}
                """);

        mockMvc.perform(get("/api/v1/jobs/" + UUID.randomUUID() + "/match")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("JOB_NOT_FOUND"));
    }

    @Test
    void recommendationsAreReturnedWithPaginationMetadata() throws Exception {
        String token = registerAndLogin();
        setProfile(token, """
                {"skills":["Java","Spring Boot"],"yearsOfExperience":3,
                 "preferredLocations":["Pune"]}
                """);
        createJob(token);

        mockMvc.perform(get("/api/v1/jobs/recommended")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.hasNext").exists())
                .andExpect(jsonPath("$.content[0].job.title").exists())
                .andExpect(jsonPath("$.content[0].score").isNumber());
    }

    @Test
    void recommendationPageSizeIsCapped() throws Exception {
        String token = registerAndLogin();
        setProfile(token, """
                {"skills":["Java"],"yearsOfExperience":3}
                """);

        mockMvc.perform(get("/api/v1/jobs/recommended")
                        .param("size", "5000")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
