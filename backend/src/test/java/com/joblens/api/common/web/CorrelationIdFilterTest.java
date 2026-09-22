package com.joblens.api.common.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test -- no Spring context, because the filter needs none. This is
 * the cheapest tier of the test strategy and the default for logic that does
 * not touch the framework.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesCorrelationIdWhenRequestHasNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/meta"), response,
                new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
    }

    @Test
    void reusesInboundCorrelationIdSoATraceSpansServices() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/meta");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("abc123");
    }

    @Test
    void truncatesOverlongInboundValue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/meta");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "x".repeat(500));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).hasSize(64);
    }

    @Test
    void clearsMdcAfterwardsSoPooledThreadsDoNotLeakIt() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/meta"),
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
