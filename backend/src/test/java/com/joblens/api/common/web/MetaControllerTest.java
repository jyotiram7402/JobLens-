package com.joblens.api.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.joblens.api.security.JwtService;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-slice test: the web layer only, with no database and no service
 * beans. This is the pattern every future controller test follows, which is
 * why it exists now against the one trivial endpoint we have.
 *
 * <p>Security auto-configuration is excluded: the default Spring Security chain
 * would answer every request with a 401 before the controller was reached.
 * Access control is covered end to end by AuthAndProfileIntegrationTest.
 */
@WebMvcTest(value = MetaController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@ActiveProfiles("test")
class MetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * JwtAuthenticationFilter is a @Component Filter, so @WebMvcTest includes it
     * in the slice and it needs this collaborator to be constructible. With no
     * Authorization header present it does nothing.
     */
    @MockBean
    private JwtService jwtService;

    @Test
    void returnsApplicationMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("joblens-api"))
                .andExpect(jsonPath("$.environment").value("test"));
    }

    @Test
    void attachesCorrelationIdToEveryResponse() throws Exception {
        mockMvc.perform(get("/api/v1/meta"))
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }
}
