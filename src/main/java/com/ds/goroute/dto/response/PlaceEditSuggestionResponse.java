package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A place edit suggestion with its changes (plan §3.11): POST result, GET /me rows, admin detail core. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceEditSuggestionResponse {
    private UUID id;
    private UUID placeId;
    private String placeName;
    private String placeThumbnail;
    private String type;
    private String status;
    private String description;
    private List<String> evidencePhotos;
    private String locale;
    private String rejectReason;
    /** The operator's internal note; only the admin detail fills it, the submitter gets null. */
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private List<PlaceEditChangeResponse> changes;
}
