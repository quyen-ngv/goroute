package com.ds.goroute.service.impl;

import com.ds.goroute.entity.HostOrganization;
import com.ds.goroute.entity.OrganizationMember;
import com.ds.goroute.entity.OrganizationMemberScope;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.HostOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnerAuthorizationServiceImplTest {
    private HostOrganizationRepository repository;
    private AdminMapper adminMapper;
    private PartnerAuthorizationServiceImpl service;
    private UUID organizationId;
    private UUID ownerId;
    private UUID employeeId;
    private HostOrganization organization;

    @BeforeEach
    void setUp() {
        repository=mock(HostOrganizationRepository.class);
        adminMapper=mock(AdminMapper.class);
        service=new PartnerAuthorizationServiceImpl(repository,adminMapper);
        organizationId=UUID.randomUUID(); ownerId=UUID.randomUUID(); employeeId=UUID.randomUUID();
        organization=HostOrganization.builder().id(organizationId).ownerUserId(ownerId).build();
        when(repository.findById(organizationId)).thenReturn(Optional.of(organization));
    }

    @Test
    void ownerAlwaysHasResourcePermission() {
        assertSame(organization,service.requireResourcePermission(
                organizationId,ownerId,"HOTEL",UUID.randomUUID(),"INVENTORY_WRITE"));
        verify(repository,never()).findMember(any(),any());
    }

    @Test
    void suspendedEmployeeIsDenied() {
        when(repository.findMember(organizationId,employeeId)).thenReturn(Optional.of(
                OrganizationMember.builder().id(UUID.randomUUID()).organizationId(organizationId)
                        .userId(employeeId).roleCode("PROPERTY_MANAGER").memberStatus("SUSPENDED").build()));
        assertThrows(BusinessException.class,()->service.requirePermission(organizationId,employeeId,"HOTEL_READ"));
    }

    @Test
    void resourceScopeAllowsOnlyMatchingHotel() {
        UUID membershipId=UUID.randomUUID(); UUID allowedHotel=UUID.randomUUID(); UUID otherHotel=UUID.randomUUID();
        OrganizationMember member=OrganizationMember.builder().id(membershipId).organizationId(organizationId)
                .userId(employeeId).roleCode("PROPERTY_MANAGER").memberStatus("ACTIVE")
                .validFrom(LocalDateTime.now().minusDays(1)).validUntil(LocalDateTime.now().plusDays(1)).build();
        when(repository.findMember(organizationId,employeeId)).thenReturn(Optional.of(member));
        when(repository.findMemberScopes(membershipId,"HOTEL")).thenReturn(List.of(
                OrganizationMemberScope.builder().id(UUID.randomUUID()).membershipId(membershipId)
                        .resourceType("HOTEL").resourceId(allowedHotel).permissions("[]").build()));

        assertDoesNotThrow(()->service.requireResourcePermission(
                organizationId,employeeId,"HOTEL",allowedHotel,"HOTEL_WRITE"));
        assertThrows(BusinessException.class,()->service.requireResourcePermission(
                organizationId,employeeId,"HOTEL",otherHotel,"HOTEL_WRITE"));
    }

    @Test
    void scopedRoleCanGrantMoreThanOrganizationRole() {
        UUID membershipId=UUID.randomUUID(); UUID hotelId=UUID.randomUUID();
        OrganizationMember member=OrganizationMember.builder().id(membershipId).organizationId(organizationId)
                .userId(employeeId).roleCode("VIEWER").memberStatus("ACTIVE").permissions("[]").build();
        when(repository.findMember(organizationId,employeeId)).thenReturn(Optional.of(member));
        when(repository.findMemberScopes(membershipId,"HOTEL")).thenReturn(List.of(
                OrganizationMemberScope.builder().id(UUID.randomUUID()).membershipId(membershipId)
                        .resourceType("HOTEL").resourceId(hotelId).roleCode("PROPERTY_MANAGER")
                        .accessEffect("ALLOW").permissions("[]").build()));

        assertDoesNotThrow(()->service.requireResourcePermission(
                organizationId,employeeId,"HOTEL",hotelId,"HOTEL_WRITE"));
    }

    @Test
    void matchingDenyScopeAlwaysWins() {
        UUID membershipId=UUID.randomUUID(); UUID hotelId=UUID.randomUUID();
        OrganizationMember member=OrganizationMember.builder().id(membershipId).organizationId(organizationId)
                .userId(employeeId).roleCode("PARTNER_ADMIN").memberStatus("ACTIVE").permissions("[]").build();
        when(repository.findMember(organizationId,employeeId)).thenReturn(Optional.of(member));
        when(repository.findMemberScopes(membershipId,"HOTEL")).thenReturn(List.of(
                OrganizationMemberScope.builder().id(UUID.randomUUID()).membershipId(membershipId)
                        .resourceType("HOTEL").resourceId(hotelId).accessEffect("ALLOW").permissions("[]").build(),
                OrganizationMemberScope.builder().id(UUID.randomUUID()).membershipId(membershipId)
                        .resourceType("HOTEL").resourceId(hotelId).accessEffect("DENY")
                        .permissions("[\"INVENTORY_WRITE\"]").build()));

        assertThrows(BusinessException.class,()->service.requireResourcePermission(
                organizationId,employeeId,"HOTEL",hotelId,"INVENTORY_WRITE"));
    }

    @Test
    void suspendedOrganizationAllowsReadButBlocksMutationEvenForOwner() {
        organization.setOperationalStatus("SUSPENDED");
        assertSame(organization,service.requirePermission(organizationId,ownerId,"HOTEL_READ"));
        assertThrows(BusinessException.class,()->service.requirePermission(
                organizationId,ownerId,"HOTEL_WRITE"));
    }
}
