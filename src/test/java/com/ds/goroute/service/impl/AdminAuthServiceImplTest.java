package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.CreateHostOrganizationRequest;
import com.ds.goroute.dto.request.PartnerRegisterRequest;
import com.ds.goroute.dto.response.AuthResponse;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.HostOrganizationService;
import com.ds.goroute.type.OrganizationType;
import com.ds.goroute.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthServiceImplTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AdminMapper adminMapper = mock(AdminMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtUtils jwt = mock(JwtUtils.class);
    private final HostOrganizationService organizations = mock(HostOrganizationService.class);
    private final AdminAuthServiceImpl service = new AdminAuthServiceImpl(users, adminMapper, encoder, jwt, organizations);

    @Test
    void registrationRejectsDuplicateUsernameBeforeCreatingAnything() {
        when(users.findByUsername("owner")).thenReturn(Optional.of(User.builder().id(UUID.randomUUID()).build()));

        assertThrows(BusinessException.class, () -> service.partnerRegister(request()));

        verify(users, never()).insert(any());
        verify(organizations, never()).create(any(), any());
    }

    @Test
    void registrationRejectsDuplicateEmailEvenWhenTheOldAccountWasDeleted() {
        when(users.findByUsername("owner")).thenReturn(Optional.empty());
        when(users.findByEmailIncludingDeleted("owner@example.com"))
                .thenReturn(Optional.of(User.builder().id(UUID.randomUUID()).build()));

        assertThrows(BusinessException.class, () -> service.partnerRegister(request()));

        verify(users, never()).insert(any());
        verify(organizations, never()).create(any(), any());
    }

    @Test
    void registrationCreatesAnActiveOwnerAndTheirOrganizationThenSignsThemIn() {
        when(users.findByUsername("owner")).thenReturn(Optional.empty());
        when(users.findByEmailIncludingDeleted("owner@example.com")).thenReturn(Optional.empty());
        when(encoder.encode("correct-horse-battery")).thenReturn("hash");
        when(jwt.generateToken(anyMap(), anyString())).thenReturn("token");
        when(organizations.create(any(), any())).thenReturn(HostOrganizationResponse.builder().id(UUID.randomUUID()).build());

        AuthResponse response = service.partnerRegister(request());

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(users).insert(user.capture());
        assertEquals("owner", user.getValue().getUsername());
        assertEquals("owner@example.com", user.getValue().getEmail());
        assertEquals("ACTIVE", user.getValue().getAccountStatus());
        assertFalse(user.getValue().getMustChangePassword());
        assertEquals("hash", user.getValue().getPasswordHash());

        ArgumentCaptor<CreateHostOrganizationRequest> organization = ArgumentCaptor.forClass(CreateHostOrganizationRequest.class);
        verify(organizations).create(eq(user.getValue().getId()), organization.capture());
        assertEquals("Legal Co", organization.getValue().getLegalName());
        assertEquals("Display", organization.getValue().getDisplayName());
        assertEquals("owner@example.com", organization.getValue().getContactEmail());
        assertEquals("0901234567", organization.getValue().getContactPhone());

        assertEquals("token", response.getAccessToken());
        assertEquals(user.getValue().getId(), response.getUser().getId());
    }

    private PartnerRegisterRequest request() {
        PartnerRegisterRequest request = new PartnerRegisterRequest();
        request.setUsername(" owner ");
        request.setEmail("Owner@Example.com");
        request.setPassword("correct-horse-battery");
        request.setFullName("Owner Name");
        request.setLegalName("Legal Co");
        request.setDisplayName("Display");
        request.setOrganizationType(OrganizationType.BUSINESS);
        request.setTimezone("Asia/Ho_Chi_Minh");
        request.setContactPhone("0901234567");
        return request;
    }
}
