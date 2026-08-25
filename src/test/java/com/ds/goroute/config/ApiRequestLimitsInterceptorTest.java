package com.ds.goroute.config;

import com.ds.goroute.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class ApiRequestLimitsInterceptorTest {

    private ApiRequestLimitsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        ApiRequestLimitsProperties properties = new ApiRequestLimitsProperties();
        properties.setMaxPageIndex(100);
        properties.setMaxPageSize(500);
        properties.setMaxResultLimit(200);
        interceptor = new ApiRequestLimitsInterceptor(properties);
    }

    @Test
    void acceptsRequestsWithinLimits() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("page", "100");
        request.addParameter("size", "500");
        request.addParameter("limit", "200");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void rejectsOversizedAndNonNumericParameters() {
        MockHttpServletRequest oversized = new MockHttpServletRequest();
        oversized.addParameter("size", "501");
        MockHttpServletRequest nonNumeric = new MockHttpServletRequest();
        nonNumeric.addParameter("limit", "all");

        assertThatThrownBy(() -> interceptor.preHandle(
                oversized, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size must be between 1 and 500");
        assertThatThrownBy(() -> interceptor.preHandle(
                nonNumeric, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("limit must be between 1 and 200");
    }
}
