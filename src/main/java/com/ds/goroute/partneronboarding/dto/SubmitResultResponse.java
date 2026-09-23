package com.ds.goroute.partneronboarding.dto;

import com.ds.goroute.partneronboarding.domain.ListingKind;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * What submitting produced.
 *
 * <p>The listing is created as a draft on the marketplace side: going on sale is a separate,
 * deliberate act in the workspace, gated by the same readiness checklist every other listing
 * passes. Saying so here is what lets the wizard end on "created — here is what is left"
 * instead of implying the listing is live.
 */
@Data
@Builder
public class SubmitResultResponse {

    private UUID draftId;
    private ListingKind listingKind;
    private UUID hotelId;
    private UUID activityId;

    /** Where the client should send the partner next to finish and open for sale. */
    public UUID listingId() {
        return hotelId != null ? hotelId : activityId;
    }
}
