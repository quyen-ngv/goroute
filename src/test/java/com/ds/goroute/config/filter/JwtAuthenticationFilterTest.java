package com.ds.goroute.config.filter;

import com.ds.goroute.config.cache.AuthSubjectCache;
import com.ds.goroute.entity.User;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void temporaryPartnerAccountCannotUseMarketplaceChatBeforeChangingPassword() throws Exception {
        JwtUtils jwt = mock(JwtUtils.class);
        AdminMapper admin = mock(AdminMapper.class);
        UserRepository users = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(jwt.validateToken("temporary-token")).thenReturn(true);
        when(jwt.getClaimsFromToken("temporary-token")).thenReturn(claims);
        when(claims.get("userId", String.class)).thenReturn(userId.toString());
        when(claims.get("email", String.class)).thenReturn("owner@example.com");
        when(admin.hasAnyRole(userId)).thenReturn(false);
        when(admin.isPartnerUser(userId)).thenReturn(true);
        when(users.findById(userId)).thenReturn(Optional.of(User.builder().id(userId)
                .mustChangePassword(true).build()));

        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/v1/api/marketplace-chat/conversations/" + UUID.randomUUID() + "/messages");
        request.addHeader("Authorization", "Bearer temporary-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new JwtAuthenticationFilter(jwt, admin, users, new com.fasterxml.jackson.databind.ObjectMapper(),
                new AuthSubjectCache()).doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void repeatedRequestsReadTheUserOnceUntilAWriteEvictsIt() throws Exception {
        JwtUtils jwt = mock(JwtUtils.class);
        AdminMapper admin = mock(AdminMapper.class);
        UserRepository users = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        Claims claims = mock(Claims.class);
        when(jwt.validateToken("token")).thenReturn(true);
        when(jwt.getClaimsFromToken("token")).thenReturn(claims);
        when(claims.get("userId", String.class)).thenReturn(userId.toString());
        when(users.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));
        AuthSubjectCache cache = new AuthSubjectCache();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, admin, users,
                new com.fasterxml.jackson.databind.ObjectMapper(), cache);

        authenticatedRequest(filter);
        authenticatedRequest(filter);
        verify(users, times(1)).findById(userId);
        verify(admin, times(1)).hasAnyRole(userId);
        verify(admin, times(1)).isPartnerUser(userId);

        cache.evict(userId);
        authenticatedRequest(filter);
        verify(users, times(2)).findById(userId);
    }

    private static void authenticatedRequest(JwtAuthenticationFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/api/trips");
        request.addHeader("Authorization", "Bearer token");
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));
        SecurityContextHolder.clearContext();
    }
}
