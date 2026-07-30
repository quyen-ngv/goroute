package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateOrganizationStatusRequest {
    @Pattern(regexp = "ENABLED|DISABLED|SUSPENDED")
    private String operationalStatus;

    @Pattern(regexp = "UNVERIFIED|PENDING|VERIFIED|REJECTED|SUSPENDED")
    private String verificationStatus;
}
