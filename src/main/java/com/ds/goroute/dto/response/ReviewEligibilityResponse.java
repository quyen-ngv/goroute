package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Whether the caller may write a review for a given hotel booking / activity order. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewEligibilityResponse {

    public enum Reason { OK, NOT_OWNER, NOT_COMPLETED, ALREADY_REVIEWED, NOT_FOUND }

    private boolean eligible;
    private Reason reason;
    /** Review that already covers this booking or its place, when {@code reason == ALREADY_REVIEWED}. */
    private UUID existingReviewId;
    /** Review target derived from the booking: the hotel's place. */
    private UUID placeId;
    /** Review target derived from the order: the activity product. */
    private UUID activityBookingId;
}
