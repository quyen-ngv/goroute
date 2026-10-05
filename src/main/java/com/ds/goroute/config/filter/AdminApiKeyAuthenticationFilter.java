package com.ds.goroute.config.filter;

import com.ds.goroute.config.ApiSecurityErrorResponseWriter;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.localevent.service.AdminApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates the events crawler by {@code X-Admin-Api-Key} (epic 17). Runs before the JWT
 * filter and only acts when the header is present:
 *
 * <ul>
 *     <li>the request must be one of the key's scoped paths (import, lookup, import batches),
 *     otherwise 401 — keys are not general admin credentials;</li>
 *     <li>the key must be known, not revoked, not expired, and its owner an active admin,
 *     otherwise 401 at once. It fails closed: a bad key never falls through to another
 *     authentication;</li>
 *     <li>a valid key authenticates as its OWNER (principal = owner UUID, ROLE_ADMIN, request
 *     attributes {@code userId} and {@code ADMIN_API_KEY_ID}), so {@code @adminAuthorization.can}
 *     still checks the owner's {@code events:import} permission.</li>
 * </ul>
 *
 * <p>Any {@code Authorization} header is hidden from the rest of the chain so a bearer token
 * cannot replace the key's identity downstream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Api-Key";
    /** Request attribute holding the UUID of the admin API key that authenticated the request. */
    public static final String API_KEY_ID_ATTRIBUTE = "ADMIN_API_KEY_ID";

    static final RequestMatcher SCOPED_ENDPOINTS = new OrRequestMatcher(
            new AntPathRequestMatcher("/v1/api/admin/events/import", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/v1/api/admin/events/lookup", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/v1/api/admin/events/import-batches/**", HttpMethod.GET.name())
    );

    private final AdminApiKeyService adminApiKeyService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String presentedKey = request.getHeader(HEADER);
        if (presentedKey == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!SCOPED_ENDPOINTS.matches(request)) {
            reject(request, response, "X-Admin-Api-Key is not accepted on this path");
            return;
        }
        Optional<AdminApiKeyService.AuthenticatedKey> key = adminApiKeyService.authenticate(presentedKey.trim());
        if (key.isEmpty()) {
            reject(request, response, "Invalid X-Admin-Api-Key");
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                key.get().ownerUserId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(RequestKeyConstant.USER_ID, key.get().ownerUserId());
        request.setAttribute(API_KEY_ID_ATTRIBUTE, key.get().keyId());
        log.debug("Admin API key {} authenticated as {}", key.get().keyId(), key.get().ownerUserId());
        filterChain.doFilter(new WithoutAuthorizationHeader(request), response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        ApiSecurityErrorResponseWriter.write(objectMapper, request, response,
                HttpServletResponse.SC_UNAUTHORIZED, ErrorConstant.ADMIN_API_KEY_INVALID, message);
    }

    /** Hides the Authorization header so the JWT filter cannot re-identify the caller. */
    static final class WithoutAuthorizationHeader extends HttpServletRequestWrapper {

        WithoutAuthorizationHeader(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return RequestKeyConstant.AUTHORIZATION.equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return RequestKeyConstant.AUTHORIZATION.equalsIgnoreCase(name)
                    ? Collections.emptyEnumeration()
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(RequestKeyConstant.AUTHORIZATION::equalsIgnoreCase);
            return Collections.enumeration(names);
        }
    }
}
