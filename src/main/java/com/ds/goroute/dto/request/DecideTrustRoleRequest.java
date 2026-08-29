package com.ds.goroute.dto.request;

import com.ds.goroute.type.TrustRoleStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DecideTrustRoleRequest {

    @NotNull(message = "A decision is required")
    private TrustRoleStatus status;

    @Size(max = 2000)
    private String decisionNote;

    /**
     * Months until the role is looked at again. A badge with no expiry slowly stops
     * describing the person who holds it.
     */
    @Min(1)
    private Integer reviewInMonths;
}
