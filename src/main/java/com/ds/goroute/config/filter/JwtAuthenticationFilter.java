package com.ds.goroute.config.filter;

import com.ds.goroute.utils.JwtUtils;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.UserRepository;
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
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final AdminMapper adminMapper;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            
            try {
                if (jwtUtils.validateToken(token)) {
                    Claims claims = jwtUtils.getClaimsFromToken(token);
                    String userIdStr = claims.get("userId", String.class);
                    UUID userId = UUID.fromString(userIdStr);
                    String email = claims.get("email", String.class);
                    
                    // Set request attributes for backward compatibility
                    request.setAttribute("userId", userId);
                    request.setAttribute("email", email);
                    
                    // ✅ Set SecurityContext - THIS IS CRITICAL!
                    if (SecurityContextHolder.getContext().getAuthentication() == null) {
                        boolean admin = adminMapper.hasAnyRole(userId);
                        boolean partner = adminMapper.isPartnerUser(userId);
                        boolean mustChangePassword = userRepository.findById(userId)
                                .map(user -> Boolean.TRUE.equals(user.getMustChangePassword())).orElse(false);
                        var authorities = new ArrayList<SimpleGrantedAuthority>();
                        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                        if (admin) authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                        if (partner) authorities.add(new SimpleGrantedAuthority("ROLE_PARTNER"));
                        if (mustChangePassword) authorities.add(new SimpleGrantedAuthority("PASSWORD_CHANGE_REQUIRED"));
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userId.toString(),
                                null,
                                authorities
                        );
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);

                        String path = request.getRequestURI();
                        String contextPath = request.getContextPath();
                        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                            path = path.substring(contextPath.length());
                        }
                        boolean protectedPortalPath = path.startsWith("/v1/api/partner/")
                                || (path.startsWith("/v1/api/admin/") && !path.equals("/v1/api/admin/auth/session"));
                        if (mustChangePassword && protectedPortalPath) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"meta\":{\"code\":4031002,\"message\":\"Password change required\"}}");
                            return;
                        }
                        
                        log.debug("✅ JWT authenticated user: {} ({})", email, userId);
                    }
                }
            } catch (Exception e) {
                log.warn("JWT validation failed: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
