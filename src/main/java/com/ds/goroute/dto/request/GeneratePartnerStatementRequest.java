package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** Generate (or refresh) and issue one statement period for an organization. */
@Data
public class GeneratePartnerStatementRequest {
    @NotNull private LocalDate periodStart;
    @NotNull private LocalDate periodEnd;
}
