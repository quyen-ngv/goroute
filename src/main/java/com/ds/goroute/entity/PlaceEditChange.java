package com.ds.goroute.entity;

import com.ds.goroute.type.PlaceEditAuthor;
import com.ds.goroute.type.PlaceEditChangeStatus;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceEditRejectReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of {@code place_edit_changes} (V286). Values are JSON text in the §3.2 shapes. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceEditChange {
    private UUID id;
    private UUID suggestionId;
    private UUID placeId;
    private UUID userId;
    private PlaceEditField field;
    private String oldValue;
    private String newValue;
    private String appliedValue;
    private PlaceEditAuthor author;
    private PlaceEditChangeStatus status;
    private PlaceEditRejectReason rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
