package com.joblens.api.user;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end authentication and profile behaviour through the real filter
 * chain, against a real database.
 *
 * <p>The security configuration is the part of this step that genuinely cannot
 * be unit tested. Whether a route is public, whether a missing token produces a
 * 401 in the right shape, whether one user can reach another's data -- all of
 * that is a property of the assembled application, and a mock of anything would
 * be testing the mock.
 *
 * <p>Each test runs in a transaction that rolls back, so registrations do not
 * leak between them.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class AuthAndProfileIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/users/me";
    private static final String PROFILE = "/api/v1/users/me/profile";

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Registers an account with a unique address and returns its access token. */
    private String registerAndLogin() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","firstName":"John","lastName":"Doe"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post(LOGIN)
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

    // --- registration and login ------------------------------------------

    @Test
    void registrationReturnsTheUserAndNoPasswordOrToken() throws Exception {
        String body = mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"New.User@Example.com","password":"%s",
                                 "firstName":"New","lastName":"User"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.user.id").exists())
                .andReturn().getResponse().getContentAsString();

        // Nothing resembling a credential may appear anywhere in the response.
        assertThat(body).doesNotContain(PASSWORD);
        assertThat(body).doesNotContain("password");
        assertThat(body).doesNotContain("accessToken");
    }

    @Test
    void registrationRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"%s",
                                 "firstName":"New","lastName":"User"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    void registrationRejectsShortPassword() throws Exception {
        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"short@example.com","password":"short",
                                 "firstName":"New","lastName":"User"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void registrationRejectsDuplicateEmailRegardlessOfCasing() throws Exception {
        String payload = """
                {"email":"dupe@example.com","password":"%s",
                 "firstName":"New","lastName":"User"}
                """.formatted(PASSWORD);

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("dupe@example.com", "DUPE@Example.COM")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void loginWithWrongPasswordDoesNotRevealWhetherTheAccountExists() throws Exception {
        mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"known@example.com","password":"%s",
                                 "firstName":"Known","lastName":"User"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated());

        String knownAccountWrongPassword = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"known@example.com","password":"wrong password here"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String unknownAccount = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nobody@example.com","password":"wrong password here"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        // The two responses differ only by the correlation id, so neither can be
        // used to tell a registered address from an unregistered one.
        assertThat(errorCodeOf(knownAccountWrongPassword))
                .isEqualTo(errorCodeOf(unknownAccount));
    }

    private String errorCodeOf(String body) throws Exception {
        JsonNode node = objectMapper.readTree(body);
        return node.get("error").asText() + "|" + node.get("message").asText();
    }

    // --- access control ---------------------------------------------------

    @Test
    void protectedEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void protectedEndpointRejectsAMalformedToken() throws Exception {
        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer("not.a.real.token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("TOKEN_INVALID"));
    }

    @Test
    void publicEndpointsStayReachableWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/v1/meta")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/companies")).andExpect(status().isOk());
    }

    @Test
    void companyWritesRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Should Not Be Created"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanReadTheirOwnAccount() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.profile").exists())
                .andExpect(jsonPath("$.profile.skills").isArray())
                // A password hash must never appear, under any field name.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void eachTokenSeesOnlyItsOwnProfile() throws Exception {
        String firstToken = registerAndLogin();
        String secondToken = registerAndLogin();

        mockMvc.perform(put(PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"headline":"First user","skills":["Java"]}
                                """))
                .andExpect(status().isOk());

        // The second user's profile is untouched. There is no endpoint and no
        // request field through which the first user could have reached it.
        mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, bearer(secondToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").doesNotExist())
                .andExpect(jsonPath("$.skills").isEmpty());
    }

    // --- profile ----------------------------------------------------------

    @Test
    void newAccountStartsWithAnEmptyProfile() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remotePreference").value("ANY"))
                .andExpect(jsonPath("$.skills").isEmpty())
                .andExpect(jsonPath("$.preferredRoles").isEmpty())
                .andExpect(jsonPath("$.preferredLocations").isEmpty());
    }

    @Test
    void updatesProfileAndReadsItBack() throws Exception {
        String token = registerAndLogin();

        String payload = """
                {
                  "headline": "Software Engineer",
                  "summary": "Backend and full-stack engineering.",
                  "yearsOfExperience": 2,
                  "currentRole": "Software Engineer",
                  "preferredRoles": ["Java Backend Developer", "Spring Boot Developer"],
                  "preferredLocations": ["Pune", "Mumbai", "Remote"],
                  "remotePreference": "HYBRID",
                  "skills": ["Java", "Spring Boot", "React", "PostgreSQL"]
                }
                """;

        mockMvc.perform(put(PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline").value("Software Engineer"))
                .andExpect(jsonPath("$.remotePreference").value("HYBRID"))
                .andExpect(jsonPath("$.skills.length()").value(4));

        mockMvc.perform(get(PROFILE).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearsOfExperience").value(2))
                .andExpect(jsonPath("$.preferredLocations.length()").value(3));
    }

    @Test
    void skillsAreDeduplicatedByTheirNormalizedForm() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(put(PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":["Java","java","  JAVA  ","Spring Boot"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(2));
    }

    @Test
    void updateReplacesCollectionsRatherThanMergingThem() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(put(PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":["Java","Docker"]}
                                """))
                .andExpect(status().isOk());

        // Leaving a skill out is how it is removed; there is no delete endpoint.
        mockMvc.perform(put(PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":["Java"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0]").value("Java"));
    }

    @Test
    void rejectsNegativeYearsOfExperience() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(put(PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"yearsOfExperience":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.yearsOfExperience").exists());
    }

    @Test
    void rejectsImplausibleYearsOfExperience() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(put(PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"yearsOfExperience":150}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankSkillNames() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(put(PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skills":["Java","   "]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnExcessiveNumberOfSkills() throws Exception {
        String token = registerAndLogin();

        String tooMany = java.util.stream.IntStream.range(0, 60)
                .mapToObj(i -> "\"skill-" + i + "\"")
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(put(PROFILE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[" + tooMany + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.skills").exists());
    }
}
