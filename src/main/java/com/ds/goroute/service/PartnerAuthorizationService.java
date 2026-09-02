package com.ds.goroute.service;

import com.ds.goroute.entity.HostOrganization;

import java.util.List;
import java.util.UUID;

public interface PartnerAuthorizationService {
    HostOrganization requireOrganization(UUID organizationId, UUID actorUserId);
    HostOrganization requirePermission(UUID organizationId, UUID actorUserId, String permission);
    HostOrganization requireResourcePermission(UUID organizationId, UUID actorUserId, String resourceType,
                                               UUID resourceId, String permission);
    boolean hasPermission(UUID organizationId, UUID actorUserId, String permission);
    boolean hasResourcePermission(UUID organizationId, UUID actorUserId, String resourceType,
                                  UUID resourceId, String permission);

    /**
     * Users who should be told about an event on a resource: the organization owner plus every
     * active member holding {@code permission} on that resource (scopes respected). The owner is
     * never an {@code organization_members} row, so callers must not iterate members themselves.
     */
    /**
     * Resource ids (hotels or products of the organization) the actor may act on with {@code permission}.
     * Returns {@code null} when the actor is unrestricted (owner, admin, or a member without scopes but with the
     * org-level permission) so SQL can skip the filter; an empty list means "nothing".
     */
    List<UUID> accessibleResourceIds(UUID organizationId, UUID actorUserId, String resourceType,
                                     List<UUID> candidateIds, String permission);

    List<UUID> notificationRecipients(UUID organizationId, String resourceType, UUID resourceId,
                                      String permission, UUID excludeUserId);
}
