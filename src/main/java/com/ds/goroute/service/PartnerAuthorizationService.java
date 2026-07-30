package com.ds.goroute.service;

import com.ds.goroute.entity.HostOrganization;

import java.util.UUID;

public interface PartnerAuthorizationService {
    HostOrganization requireOrganization(UUID organizationId, UUID actorUserId);
    HostOrganization requirePermission(UUID organizationId, UUID actorUserId, String permission);
    HostOrganization requireResourcePermission(UUID organizationId, UUID actorUserId, String resourceType,
                                               UUID resourceId, String permission);
    boolean hasResourcePermission(UUID organizationId, UUID actorUserId, String resourceType,
                                  UUID resourceId, String permission);
}
