package com.ds.goroute.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import lombok.Data;

import java.time.LocalDate;

/** Optional body of applying a Pengoo proposal. */
@Data
public class AssistantProposalApplyRequest {
    /** NEW_TRIP: the trip's first day as the applier picked it; the plan's length gives the last. */
    @FutureOrPresent
    private LocalDate startDate;
}
