package com.ds.goroute.partneronboarding.dto;

import com.ds.goroute.partneronboarding.domain.ListingKind;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** A row in "what am I still working on"; the answers themselves are not sent with a list. */
@Data
@Builder
public class DraftSummaryResponse {

    private UUID id;
    private ListingKind listingKind;
    private String status;
    private UUID organizationId;
    private String currentStep;
    private int completedStepCount;
    private LocalDateTime updatedAt;
}
