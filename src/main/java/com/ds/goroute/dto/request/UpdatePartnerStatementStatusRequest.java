package com.ds.goroute.dto.request;

import com.ds.goroute.type.PartnerStatementStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Operator closing a statement. Only {@code SETTLED} is accepted; the rest are computed states. */
@Data
public class UpdatePartnerStatementStatusRequest {
    @NotNull private PartnerStatementStatus status;
    @Size(max = 2000) private String note;
}
