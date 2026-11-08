package com.ds.goroute.config;

import com.ds.goroute.constant.ErrorConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException {
        // A rejected token logs its reason just before this line (same requestId); "missing"
        // means the caller sent no Authorization header at all (guest, or signed out).
        log.warn("401 {} {} (Authorization header {})", request.getMethod(), request.getRequestURI(),
                request.getHeader(HttpHeaders.AUTHORIZATION) == null ? "missing" : "sent");
        ApiSecurityErrorResponseWriter.write(
                objectMapper,
                request,
                response,
                HttpServletResponse.SC_UNAUTHORIZED,
                ErrorConstant.UNAUTHORIZED,
                "Authentication is required");
    }
}
