package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** A partner contesting one statement line. The reason is what the operator resolves against. */
@Data
public class OpenStatementDisputeRequest {
    @NotBlank @Size(max = 2000) private String reason;
}
