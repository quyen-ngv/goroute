package com.ds.goroute.config;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.constant.RequestKeyConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityErrorHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authenticationEntryPointReturnsTheApiUnauthorizedContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestKeyConstant.REQUEST_ID, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAuthenticationEntryPoint(objectMapper).commence(
                request,
                response,
                new AuthenticationCredentialsNotFoundException("Missing token"));

        var payload = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(payload.at("/meta/code").asInt()).isEqualTo(ErrorConstant.UNAUTHORIZED);
        assertThat(payload.at("/meta/message").asText()).isEqualTo("Authentication is required");
        assertThat(payload.at("/meta/requestId").asText()).isEqualTo("request-123");
    }

    @Test
    void accessDeniedHandlerReturnsTheApiForbiddenContract() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new ApiAccessDeniedHandler(objectMapper).handle(
                request,
                response,
                new AccessDeniedException("Permission missing"));

        var payload = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(payload.at("/meta/code").asInt()).isEqualTo(ErrorConstant.FORBIDDEN_ERROR);
        assertThat(payload.at("/meta/message").asText()).isEqualTo("Access denied");
    }
}
