package com.ds.goroute.config.filter;

import com.ds.goroute.config.ApiSecurityErrorResponseWriter;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.utils.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Portals a user holding a temporary password may not reach until they have changed
     * it. The session endpoint is exempt so the portal can discover why it is being
     * refused and send the user to the password form.
     */
    private static final List<String> PASSWORD_CHANGE_PROTECTED_PREFIXES = List.of(
            "/v1/api/partner/",
            "/v1/api/marketplace-chat/",
            "/v1/api/admin/");
    private static final String ADMIN_SESSION_PATH = "/v1/api/admin/auth/session";

    private final JwtUtils jwtUtils;
    private final AdminMapper adminMapper;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = bearerToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtils.validateToken(token)) {
                Claims claims = jwtUtils.getClaimsFromToken(token);
                UUID userId = UUID.fromString(claims.get("userId", String.class));
                String email = claims.get("email", String.class);

                request.setAttribute(RequestKeyConstant.USER_ID, userId);
                request.setAttribute(RequestKeyConstant.EMAIL, email);

                // An earlier filter (API key, internal token) may already have decided
                // who the caller is; that decision wins.
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    boolean mustChangePassword = authenticate(request, userId);
                    if (mustChangePassword && isPasswordChangeProtected(pathWithinApplication(request))) {
                        ApiSecurityErrorResponseWriter.write(objectMapper, request, response,
                                HttpServletResponse.SC_FORBIDDEN,
                                ErrorConstant.PASSWORD_CHANGE_REQUIRED,
                                "Password change required");
                        return;
                    }
                    log.debug("JWT authenticated user: {} ({})", email, userId);
                }
            }
        } catch (Exception exception) {
            // An unreadable or expired token leaves the request anonymous; the
            // authorization rules decide whether that is good enough for this path.
            log.warn("JWT validation failed: {}", exception.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * @return whether the user still holds a temporary password
     */
    private boolean authenticate(HttpServletRequest request, UUID userId) {
        boolean admin = adminMapper.hasAnyRole(userId);
        boolean partner = adminMapper.isPartnerUser(userId);
        boolean mustChangePassword = userRepository.findById(userId)
                .map(user -> Boolean.TRUE.equals(user.getMustChangePassword()))
                .orElse(false);

        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (admin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        if (partner) {
            authorities.add(new SimpleGrantedAuthority("ROLE_PARTNER"));
        }
        if (mustChangePassword) {
            authorities.add(new SimpleGrantedAuthority("PASSWORD_CHANGE_REQUIRED"));
        }

        var authToken = new UsernamePasswordAuthenticationToken(userId.toString(), null, authorities);
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
        return mustChangePassword;
    }

    private boolean isPasswordChangeProtected(String path) {
        if (ADMIN_SESSION_PATH.equals(path)) {
            return false;
        }
        return PASSWORD_CHANGE_PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String bearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader(RequestKeyConstant.AUTHORIZATION);
        return authHeader != null && authHeader.startsWith(BEARER_PREFIX)
                ? authHeader.substring(BEARER_PREFIX.length())
                : null;
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
