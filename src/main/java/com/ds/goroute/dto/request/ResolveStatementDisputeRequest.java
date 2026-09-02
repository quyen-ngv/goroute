package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Operator decision on a disputed line: accept (commission zeroed) or reject with a note. */
@Data
public class ResolveStatementDisputeRequest {
    @NotNull private Boolean accept;
    @Size(max = 2000) private String note;
}
