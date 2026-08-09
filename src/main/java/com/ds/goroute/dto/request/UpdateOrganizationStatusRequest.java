package com.ds.goroute.dto.request;

import com.ds.goroute.type.OrganizationOperationalStatus;
import com.ds.goroute.type.OrganizationVerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class UpdateOrganizationStatusRequest {
    private OrganizationOperationalStatus operationalStatus;

    private OrganizationVerificationStatus verificationStatus;

    @NotNull
    @Positive
    private Long expectedVersion;
}
