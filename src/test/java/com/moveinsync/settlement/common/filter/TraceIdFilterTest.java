package com.moveinsync.settlement.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("TraceIdFilter Distributed Tracing Tests")
class TraceIdFilterTest {

    private TraceIdFilter traceIdFilter;

    @BeforeEach
    void setUp() {
        traceIdFilter = new TraceIdFilter();
    }

    @Test
    @DisplayName("Should preserve and propagate existing incoming X-Trace-Id header")
    void shouldPropagateIncomingTraceId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_HEADER, "TRACE-CORRELATION-999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        doAnswer(invocation -> {
            assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isEqualTo("TRACE-CORRELATION-999");
            return null;
        }).when(filterChain).doFilter(request, response);

        traceIdFilter.doFilterInternal(request, response, filterChain);

        assertThat(response.getHeader(TraceIdFilter.TRACE_HEADER)).isEqualTo("TRACE-CORRELATION-999");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull(); // Cleared in finally
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should generate unique 16-character trace ID when header is omitted")
    void shouldGenerateTraceIdWhenHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        doAnswer(invocation -> {
            String mdcTrace = MDC.get(TraceIdFilter.MDC_KEY);
            assertThat(mdcTrace).isNotNull();
            assertThat(mdcTrace).hasSize(16);
            return null;
        }).when(filterChain).doFilter(request, response);

        traceIdFilter.doFilterInternal(request, response, filterChain);

        String responseTrace = response.getHeader(TraceIdFilter.TRACE_HEADER);
        assertThat(responseTrace).isNotNull();
        assertThat(responseTrace).hasSize(16);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }
}
