/* OpsFlow — licensed under the MIT License. See LICENSE in the repository root. */
package com.opsflow.platform.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void placesTheCorrelationIdInTheMdcForTheDurationOfTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "request-scoped-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seenInsideChain = new String[1];
        FilterChain chain = (req, res) -> seenInsideChain[0] = MDC.get(MdcKeys.CORRELATION_ID);

        filter.doFilter(request, response, chain);

        assertThat(seenInsideChain[0]).isEqualTo("request-scoped-id");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("request-scoped-id");
    }

    @Test
    void clearsTheMdcAfterwardsEvenWhenTheRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain exploding = (req, res) -> {
            throw new IllegalStateException("downstream failure");
        };

        try {
            filter.doFilter(request, response, exploding);
        } catch (Exception expected) {
            // The failure itself is not what is under test.
        }

        // Threads are pooled: a leaked entry would attribute this request's id to
        // whichever request reuses the thread next.
        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull();
    }

    @Test
    void generatesAnIdWhenTheClientSuppliesNone() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).matches("[A-Za-z0-9_-]{8,64}");
    }
}
