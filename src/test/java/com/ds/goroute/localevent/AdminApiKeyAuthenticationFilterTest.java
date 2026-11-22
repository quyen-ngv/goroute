package com.ds.goroute.localevent;

import com.ds.goroute.config.filter.AdminApiKeyAuthenticationFilter;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.localevent.service.AdminApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminApiKeyAuthenticationFilterTest {

    private static final String KEY = "grk_" + "a".repeat(40);
    private static final List<String> EVENTS = List.of("events:import");

    private final AdminApiKeyService keys = mock(AdminApiKeyService.class);
    private final AdminApiKeyAuthenticationFilter filter =
            new AdminApiKeyAuthenticationFilter(keys, new ObjectMapper().findAndRegisterModules());
    private final FilterChain chain = mock(FilterChain.class);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aValidKeyAuthenticatesAsItsOwnerAndHidesAnyBearerToken() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        when(keys.authenticate(KEY)).thenReturn(Optional.of(new AdminApiKeyService.AuthenticatedKey(keyId, owner, EVENTS)));
        MockHttpServletRequest request = request("POST", "/v1/api/admin/events/import");
        request.addHeader("Authorization", "Bearer someone-else");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        ArgumentCaptor<ServletRequest> forwarded = ArgumentCaptor.forClass(ServletRequest.class);
        verify(chain).doFilter(forwarded.capture(), any());
        assertThat(((jakarta.servlet.http.HttpServletRequest) forwarded.getValue()).getHeader("Authorization")).isNull();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getName()).isEqualTo(owner.toString());
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(request.getAttribute(RequestKeyConstant.USER_ID)).isEqualTo(owner);
        assertThat(request.getAttribute(AdminApiKeyAuthenticationFilter.API_KEY_ID_ATTRIBUTE)).isEqualTo(keyId);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void lookupAndImportBatchesAreInScope() throws Exception {
        when(keys.authenticate(KEY)).thenReturn(Optional.of(
                new AdminApiKeyService.AuthenticatedKey(UUID.randomUUID(), UUID.randomUUID(), EVENTS)));

        for (String path : new String[]{"/v1/api/admin/events/lookup",
                "/v1/api/admin/events/import-batches", "/v1/api/admin/events/import-batches/" + UUID.randomUUID()}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request("GET", path), response, chain);
            assertThat(response.getStatus()).as(path).isEqualTo(200);
        }
    }

    @Test
    void aRevokedOrExpiredKeyIsRefusedWithoutFallingThrough() throws Exception {
        // The service answers empty for revoked, expired, unknown keys and non-admin owners.
        when(keys.authenticate(anyString())).thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("POST", "/v1/api/admin/events/import"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(String.valueOf(ErrorConstant.ADMIN_API_KEY_INVALID));
        verify(chain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void aKeyOnAnotherPathIsRefusedEvenIfValid() throws Exception {
        for (String[] call : new String[][]{
                {"GET", "/v1/api/admin/events"},
                {"POST", "/v1/api/admin/events"},
                {"GET", "/v1/api/admin/api-keys"},
                {"GET", "/v1/api/admin/events/import"}}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(call[0], call[1]), response, chain);
            assertThat(response.getStatus()).as(call[0] + " " + call[1]).isEqualTo(401);
        }
        verify(keys, never()).authenticate(anyString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void anAgentKeyCanProposeButNotApprove() throws Exception {
        when(keys.authenticate(KEY)).thenReturn(Optional.of(new AdminApiKeyService.AuthenticatedKey(
                UUID.randomUUID(), UUID.randomUUID(), List.of("activity-places:propose"))));

        for (String[] call : new String[][]{
                {"GET", "/v1/api/admin/activities/unlinked"},
                {"POST", "/v1/api/admin/place-import-jobs/activity-proposals"},
                {"GET", "/v1/api/admin/place-import-jobs/" + UUID.randomUUID()}}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(call[0], call[1]), response, chain);
            assertThat(response.getStatus()).as(call[0] + " " + call[1]).isEqualTo(200);
        }
        for (String[] call : new String[][]{
                {"POST", "/v1/api/admin/place-import-mappings/" + UUID.randomUUID() + "/approve"},
                {"POST", "/v1/api/admin/activities/" + UUID.randomUUID() + "/dismiss"},
                {"POST", "/v1/api/admin/events/import"}}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request(call[0], call[1]), response, chain);
            assertThat(response.getStatus()).as(call[0] + " " + call[1]).isEqualTo(401);
        }
    }

    @Test
    void anApproverKeyCanApproveButNotPropose() throws Exception {
        when(keys.authenticate(KEY)).thenReturn(Optional.of(new AdminApiKeyService.AuthenticatedKey(
                UUID.randomUUID(), UUID.randomUUID(), List.of("activity-places:approve"))));

        MockHttpServletResponse approved = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/v1/api/admin/place-import-mappings/" + UUID.randomUUID() + "/approve"),
                approved, chain);
        assertThat(approved.getStatus()).isEqualTo(200);

        MockHttpServletResponse proposed = new MockHttpServletResponse();
        filter.doFilter(request("POST", "/v1/api/admin/place-import-jobs/activity-proposals"), proposed, chain);
        assertThat(proposed.getStatus()).isEqualTo(401);
    }

    @Test
    void withoutTheHeaderTheFilterDoesNothing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/api/admin/events/import");
        request.setRequestURI("/v1/api/admin/events/import");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(keys, never()).authenticate(anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader(AdminApiKeyAuthenticationFilter.HEADER, KEY);
        return request;
    }
}
