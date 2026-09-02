package com.ds.goroute.config.filter;

import com.ds.goroute.config.ApiSecurityErrorResponseWriter;
import com.ds.goroute.constant.ErrorConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final RequestMatcher API_KEY_ENDPOINTS = new OrRequestMatcher(
            new AntPathRequestMatcher("/v1/api/places/detail-refresh-candidates", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/v1/api/places/import", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/v1/api/places/import/batch", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/v1/api/places/*", HttpMethod.PUT.name()),
            new AntPathRequestMatcher("/v1/api/place-reviews/**"),
            new AntPathRequestMatcher("/v1/api/admin/places/import", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/v1/api/admin/places/import/batch", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/v1/api/admin/places/*", HttpMethod.PUT.name())
    );

    private final ObjectMapper objectMapper;

    @Value("${goroute.api-key:}")
    private String gorouteApiKey;

    // Keep compatibility with the shared key already used by the GoRoute <-> scraper callbacks.
    @Value("${scrape.service.api-key:}")
    private String scrapeServiceApiKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String expectedApiKey = configuredApiKey();
        if (API_KEY_ENDPOINTS.matches(request) && expectedApiKey != null && !expectedApiKey.isBlank()) {
            String suppliedApiKey = request.getHeader("X-API-Key");
            if (suppliedApiKey != null && !suppliedApiKey.isBlank()) {
                if (!SecretComparison.matches(suppliedApiKey, expectedApiKey)) {
                    ApiSecurityErrorResponseWriter.write(objectMapper, request, response,
                            HttpServletResponse.SC_UNAUTHORIZED,
                            ErrorConstant.UNAUTHORIZED, "Invalid X-API-Key");
                    return;
                }

                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            "goroute-api-key",
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_API_KEY"))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String configuredApiKey() {
        return gorouteApiKey != null && !gorouteApiKey.isBlank()
                ? gorouteApiKey
                : scrapeServiceApiKey;
    }
}
