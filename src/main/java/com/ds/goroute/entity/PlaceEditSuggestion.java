package com.ds.goroute.entity;

import com.ds.goroute.type.PlaceEditRejectReason;
import com.ds.goroute.type.PlaceEditReviewChannel;
import com.ds.goroute.type.PlaceEditStatus;
import com.ds.goroute.type.PlaceEditType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of {@code place_edit_suggestions} (V286). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceEditSuggestion {
    private UUID id;
    private UUID placeId;
    private UUID userId;
    private PlaceEditType type;
    private PlaceEditStatus status;
    private String description;
    /** JSON array of storage URLs. */
    private String evidencePhotos;
    private String locale;
    private PlaceEditRejectReason rejectReason;
    private String reviewNote;
    private PlaceEditReviewChannel reviewChannel;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
