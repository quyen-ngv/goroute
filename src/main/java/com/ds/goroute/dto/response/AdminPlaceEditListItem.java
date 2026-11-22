package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** One row of GET /v1/api/admin/place-edit-suggestions (plan §3.11). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPlaceEditListItem {
    private UUID id;
    private UUID placeId;
    private String placeName;
    private String placeAddress;
    private String placeThumbnail;
    private UUID userId;
    private String userName;
    private String userAvatar;
    private String type;
    private String status;
    private List<String> fields;
    /** At most 140 characters of the submitter's description. */
    private String descriptionPreview;
    private int evidencePhotoCount;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
