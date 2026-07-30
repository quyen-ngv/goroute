package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PartnerMemberProvisionResponse {
    private OrganizationMemberResponse member;
    private String username;
    private String temporaryPassword;
    private boolean mustChangePassword;
}
