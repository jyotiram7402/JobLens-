package com.joblens.api.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
 */
@WebMvcTest(MetaController.class)
@ActiveProfiles("test")
class MetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
