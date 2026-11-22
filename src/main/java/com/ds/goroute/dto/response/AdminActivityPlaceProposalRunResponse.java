package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** The job that imports the proposed places, and the proposals that were not queued. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminActivityPlaceProposalRunResponse {
    /** Null when every proposal was skipped. */
    private PlaceImportJobResponse job;
    private int queued;
    private List<Skipped> skipped;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Skipped {
        private UUID activityId;
        /** Set instead of activityId for a check-in proposal. */
        private UUID checkinId;
        /**
         * ACTIVITY_NOT_FOUND, CHECKIN_NOT_FOUND, ALREADY_LINKED, DISMISSED, PROPOSAL_PENDING,
         * DUPLICATE or INVALID_URL.
         */
        private String reason;
    }
}
