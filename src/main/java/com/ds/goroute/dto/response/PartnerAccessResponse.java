package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

/** Effective organization-level permissions for the signed-in partner user.
 * Resource scopes are still evaluated by every protected endpoint. */
@Value
@Builder
public class PartnerAccessResponse {
    UUID organizationId;
    boolean organizationOwner;
    List<String> permissions;
}
