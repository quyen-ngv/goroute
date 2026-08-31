package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripMemoryResponse {
    private UUID id;
    private UUID tripId;
    private UUID activityId;
    private String mediaType;
    private String url;
    private String caption;
    private String description;
    private LocalDateTime takenAt;
    private String dateSource;
    private String captureSource;
    private BigDecimal latitude;
    private BigDecimal longitude;

    /** The catalogued place, when the author picked one. Null is normal. */
    private UUID placeId;

    /** What to show as the location; set even without a {@link #placeId}. */
    private String locationName;

    /** Where the location came from; same vocabulary as a check-in target. */
    private String locationSource;

    private UUID uploadedBy;
    private String uploaderName;
    private String uploaderAvatarUrl;
    private LocalDateTime createdAt;
}
