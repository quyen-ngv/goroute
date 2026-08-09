package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.AdminProvisionPartnerRequest;
import com.ds.goroute.dto.request.UpdateHostOrganizationRequest;
import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.UserAccountService;
import com.ds.goroute.type.OrganizationOperationalStatus;
import com.ds.goroute.type.OrganizationType;
import com.ds.goroute.type.OrganizationVerificationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HostOrganizationServiceImplTest {
    @Test
    void partnerUpdateCannotReenableADisabledOrganization() {
        HostOrganizationRepository organizations = mock(HostOrganizationRepository.class);
        PartnerAuthorizationService authorization = mock(PartnerAuthorizationService.class);
        MarketplaceHistoryService history = mock(MarketplaceHistoryService.class);
        HostOrganizationServiceImpl service = service(organizations, authorization, history);
        UUID organizationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        HostOrganization organization = organization(organizationId, ownerId, "DISABLED", 7L);
        when(authorization.requirePermission(organizationId, actorId, "ORGANIZATION_WRITE")).thenReturn(organization);
        when(organizations.update(any())).thenReturn(1);

        UpdateHostOrganizationRequest request = updateRequest();
        request.setOperationalStatus(OrganizationOperationalStatus.ENABLED);
        request.setExpectedVersion(7L);

        assertThrows(BusinessException.class, () -> service.update(actorId, organizationId, request));
        verify(organizations, never()).update(any());
    }

    @Test
    void updateRejectsMissingExpectedVersion() {
        HostOrganizationRepository organizations = mock(HostOrganizationRepository.class);
        PartnerAuthorizationService authorization = mock(PartnerAuthorizationService.class);
        HostOrganizationServiceImpl service = service(organizations, authorization, mock(MarketplaceHistoryService.class));
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(authorization.requirePermission(organizationId, actorId, "ORGANIZATION_WRITE"))
                .thenReturn(organization(organizationId, UUID.randomUUID(), "ENABLED", 2L));

        assertThrows(BusinessException.class, () -> service.update(actorId, organizationId, updateRequest()));
        verify(organizations, never()).update(any());
    }

    @Test
    void adminStatusChangeKeepsTheAuthenticatedActorInAuditHistory() {
        HostOrganizationRepository organizations = mock(HostOrganizationRepository.class);
        MarketplaceHistoryService history = mock(MarketplaceHistoryService.class);
        HostOrganizationServiceImpl service = service(organizations, mock(PartnerAuthorizationService.class), history);
        UUID organizationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(organizations.findById(organizationId)).thenReturn(Optional.of(organization(organizationId, UUID.randomUUID(), "ENABLED", 3L)));
        when(organizations.update(any())).thenReturn(1);

        service.adminUpdateStatus(actorId, organizationId, OrganizationOperationalStatus.SUSPENDED,
                OrganizationVerificationStatus.SUSPENDED, 3L);

        verify(history).record(eq(organizationId), eq("HOST_ORGANIZATION"), eq(organizationId),
                eq("ADMIN_STATUS_CHANGED"), any(), any(), eq(actorId), eq("ADMIN"), eq(null));
    }

    @Test
    void provisioningRejectsAmbiguousOwnerInput() {
        HostOrganizationServiceImpl service = service(mock(HostOrganizationRepository.class),
                mock(PartnerAuthorizationService.class), mock(MarketplaceHistoryService.class));
        AdminProvisionPartnerRequest request = new AdminProvisionPartnerRequest();

        assertThrows(BusinessException.class, () -> service.adminProvision(UUID.randomUUID(), request));
    }

    private HostOrganizationServiceImpl service(HostOrganizationRepository organizations,
            PartnerAuthorizationService authorization, MarketplaceHistoryService history) {
        return new HostOrganizationServiceImpl(organizations, mock(UserRepository.class), authorization, history,
                mock(HotelMarketplaceRepository.class), mock(ActivityCommerceRepository.class), mock(UserAccountService.class));
    }

    private HostOrganization organization(UUID id, UUID ownerId, String status, long version) {
        LocalDateTime now = LocalDateTime.now();
        return HostOrganization.builder().id(id).ownerUserId(ownerId).legalName("Legal name").displayName("Display name")
                .organizationType("BUSINESS").verificationStatus("UNVERIFIED").operationalStatus(status)
                .defaultCurrency("VND").timezone("Asia/Ho_Chi_Minh").settings("{}").dataVersion(version)
                .createdAt(now).updatedAt(now).build();
    }

    private UpdateHostOrganizationRequest updateRequest() {
        UpdateHostOrganizationRequest request = new UpdateHostOrganizationRequest();
        request.setLegalName("Updated legal name");
        request.setDisplayName("Updated display name");
        request.setOrganizationType(OrganizationType.BUSINESS);
        request.setDefaultCurrency("VND");
        request.setTimezone("Asia/Ho_Chi_Minh");
        return request;
    }
}
