package com.ds.goroute.config.filter;

import com.ds.goroute.config.InternalApiProperties;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.exception.BusinessError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalApiAuthenticationFilter extends OncePerRequestFilter {

    static final String INTERNAL_API_PREFIX = "/v1/api/internal/";
    static final String AI_TRIP_PREFIX = "/v1/api/internal/ai-trip-generations/";
    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final InternalApiProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean missingConfigurationLogged = new AtomicBoolean();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !pathWithinApplication(request).startsWith(INTERNAL_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestPath = pathWithinApplication(request);
        String expectedToken = requestPath.startsWith(AI_TRIP_PREFIX)
                ? properties.aiTripToken()
                : properties.scrapeCallbackToken();

        if (expectedToken == null || expectedToken.isBlank()) {
            if (missingConfigurationLogged.compareAndSet(false, true)) {
                log.error("Internal API authentication is not configured; internal callbacks are disabled");
            }
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    ErrorConstant.INTERNAL_SERVER_ERROR, "Internal callback authentication is unavailable");
            return;
        }

        String suppliedToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!SecretComparison.matches(suppliedToken, expectedToken)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorConstant.UNAUTHORIZED, "Invalid internal credentials");
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String principal = requestPath.startsWith(AI_TRIP_PREFIX) ? "ai-trip-worker" : "scrape-worker";
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
        }
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), BaseResponse.ofFailed(
                new BusinessError(code, message), message));
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }
}
