package com.ds.goroute.config.filter;

import com.ds.goroute.config.InternalApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InternalApiAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsCallbacksWhenServerTokenIsNotConfigured() throws Exception {
        InternalApiAuthenticationFilter filter = filter("", "");
        MockHttpServletRequest request = request("/v1/api/internal/place-detail-refresh/jobs/123/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void rejectsInvalidCallbackToken() throws Exception {
        InternalApiAuthenticationFilter filter = filter("ai-secret", "scrape-secret");
        MockHttpServletRequest request = request("/v1/api/internal/social-location-jobs/123/events");
        request.addHeader("X-Internal-Token", "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void authenticatesAiWorkerWithDedicatedToken() throws Exception {
        InternalApiAuthenticationFilter filter = filter("ai-secret", "scrape-secret");
        MockHttpServletRequest request = request("/v1/api/internal/ai-trip-generations/123/events");
        request.addHeader("X-Internal-Token", "ai-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("ai-trip-worker");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_INTERNAL");
        verify(chain).doFilter(request, response);
    }

    @Test
    void assistantCallbacksUseTheAiWorkerTokenNotTheScrapeToken() throws Exception {
        InternalApiAuthenticationFilter filter = filter("ai-secret", "scrape-secret");
        MockHttpServletRequest withScrapeToken = request("/v1/api/internal/assistant/turns/123/reply");
        withScrapeToken.addHeader("X-Internal-Token", "scrape-secret");
        MockHttpServletResponse refused = new MockHttpServletResponse();
        filter.doFilter(withScrapeToken, refused, mock(FilterChain.class));
        assertThat(refused.getStatus()).isEqualTo(401);

        MockHttpServletRequest withAiToken = request("/v1/api/internal/assistant/turns/123/reply");
        withAiToken.addHeader("X-Internal-Token", "ai-secret");
        MockHttpServletResponse accepted = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(withAiToken, accepted, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("ai-trip-worker");
        verify(chain).doFilter(withAiToken, accepted);
    }

    private InternalApiAuthenticationFilter filter(String aiToken, String scrapeToken) {
        return new InternalApiAuthenticationFilter(
                new InternalApiProperties(aiToken, scrapeToken),
                new ObjectMapper().findAndRegisterModules());
    }

    private MockHttpServletRequest request(String path) {
        return new MockHttpServletRequest("POST", path);
    }
}
