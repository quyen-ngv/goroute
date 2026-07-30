package com.ds.goroute.service;

import com.ds.goroute.dto.request.CreateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpdateHostOrganizationRequest;
import com.ds.goroute.dto.request.UpsertOrganizationMemberRequest;
import com.ds.goroute.dto.response.HostOrganizationResponse;
import com.ds.goroute.dto.response.OrganizationMemberResponse;
import com.ds.goroute.dto.request.UpsertOrganizationMemberScopeRequest;
import com.ds.goroute.dto.request.AdminProvisionPartnerRequest;
import com.ds.goroute.dto.request.ProvisionPartnerMemberRequest;
import com.ds.goroute.dto.response.OrganizationMemberScopeResponse;
import com.ds.goroute.dto.response.PartnerProvisionResponse;
import com.ds.goroute.dto.response.PartnerMemberProvisionResponse;

import java.util.List;
import java.util.UUID;

public interface HostOrganizationService {
    HostOrganizationResponse create(UUID actorUserId, CreateHostOrganizationRequest request);
    List<HostOrganizationResponse> listMine(UUID actorUserId);
    HostOrganizationResponse getMine(UUID actorUserId, UUID organizationId);
    HostOrganizationResponse update(UUID actorUserId, UUID organizationId, UpdateHostOrganizationRequest request);
    List<OrganizationMemberResponse> listMembers(UUID actorUserId, UUID organizationId);
    OrganizationMemberResponse upsertMember(UUID actorUserId, UUID organizationId, UpsertOrganizationMemberRequest request);
    void updateMemberStatus(UUID actorUserId, UUID organizationId, UUID memberUserId, String status);
    List<OrganizationMemberScopeResponse> listMemberScopes(UUID actorUserId,UUID organizationId,UUID memberUserId);
    OrganizationMemberScopeResponse upsertMemberScope(UUID actorUserId,UUID organizationId,UUID memberUserId,
                                                      UUID scopeId,UpsertOrganizationMemberScopeRequest request);
    void deleteMemberScope(UUID actorUserId,UUID organizationId,UUID memberUserId,UUID scopeId);
    List<HostOrganizationResponse> adminList(String query, String status, int page, int size);
    HostOrganizationResponse adminGet(UUID organizationId);
    List<OrganizationMemberResponse> adminListMembers(UUID organizationId);
    List<OrganizationMemberScopeResponse> adminListMemberScopes(UUID organizationId,UUID memberUserId);
    HostOrganizationResponse adminUpdateStatus(UUID organizationId, String operationalStatus, String verificationStatus);
    PartnerProvisionResponse adminProvision(UUID actorUserId, AdminProvisionPartnerRequest request);
    HostOrganizationResponse adminUpdate(UUID actorUserId, UUID organizationId, UpdateHostOrganizationRequest request);
    HostOrganizationResponse adminDisable(UUID actorUserId, UUID organizationId, String reason);
    OrganizationMemberResponse adminUpsertMember(UUID actorUserId, UUID organizationId, UpsertOrganizationMemberRequest request);
    PartnerMemberProvisionResponse provisionMember(UUID actorUserId, UUID organizationId, ProvisionPartnerMemberRequest request);
    PartnerMemberProvisionResponse adminProvisionMember(UUID actorUserId, UUID organizationId, ProvisionPartnerMemberRequest request);
    void adminUpdateMemberStatus(UUID actorUserId, UUID organizationId, UUID memberUserId, String status);
    OrganizationMemberScopeResponse adminUpsertMemberScope(UUID actorUserId, UUID organizationId, UUID memberUserId,
                                                            UUID scopeId, UpsertOrganizationMemberScopeRequest request);
    void adminDeleteMemberScope(UUID actorUserId, UUID organizationId, UUID memberUserId, UUID scopeId);
}
