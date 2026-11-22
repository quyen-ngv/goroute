package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Body of POST /v1/api/admin/place-import-jobs/activity-proposals: an agent's proposed Google
 * Maps place for each unlinked activity. The places are imported now; the activity is linked
 * only when a human approves the mapping.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateActivityPlaceProposalsRequest {

    /** Same cap as the admin link import: every new link is a full browser scrape. */
    @NotEmpty(message = "At least one proposal is required")
    @Size(max = 50, message = "At most 50 proposals per job")
    private List<@Valid Proposal> proposals;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Proposal {
        @NotNull
        private UUID activityId;

        @NotBlank
        @Pattern(regexp = "https?://.+", message = "googleMapsUrl must be an HTTP(S) URL")
        private String googleMapsUrl;

        /** Visibility of the place if it has to be imported; an existing place keeps its own. */
        @Pattern(regexp = "ACTIVE|INACTIVE", message = "visibilityStatus must be ACTIVE or INACTIVE")
        @Builder.Default
        private String visibilityStatus = "INACTIVE";

        /** Why this place, and how sure: the approver reads it in Telegram. */
        @Size(max = 1000)
        private String note;
    }
}
