package com.ds.goroute.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final RequestMatcher API_KEY_ENDPOINTS = new OrRequestMatcher(
            new AntPathRequestMatcher("/v1/api/places/import", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/v1/api/places/import/batch", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/v1/api/places/*", HttpMethod.PUT.name()),
            new AntPathRequestMatcher("/v1/api/admin/places/import", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/v1/api/admin/places/import/batch", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/v1/api/admin/places/*", HttpMethod.PUT.name())
    );

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
                if (!matches(suppliedApiKey, expectedApiKey)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"meta\":{\"code\":4010001,\"message\":\"Invalid X-API-Key\"}}");
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

    private boolean matches(String suppliedApiKey, String configuredApiKey) {
        return MessageDigest.isEqual(
                suppliedApiKey.getBytes(StandardCharsets.UTF_8),
                configuredApiKey.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String configuredApiKey() {
        return gorouteApiKey != null && !gorouteApiKey.isBlank()
                ? gorouteApiKey
                : scrapeServiceApiKey;
    }
}
