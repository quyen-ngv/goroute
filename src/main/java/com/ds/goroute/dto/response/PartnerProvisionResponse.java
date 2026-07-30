package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PartnerProvisionResponse {
    private HostOrganizationResponse organization;
    private UUID ownerUserId;
    private String ownerUsername;
    private String temporaryPassword;
    private boolean mustChangePassword;
}
