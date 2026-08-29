package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateActivityRequest {
    private String placeId;
    private UUID customPlaceId;
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String name;
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    /** Destination point for transport activities. */
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String endAddress;
    private BigDecimal endLat;
    private BigDecimal endLng;
    private Integer dayNumber;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer endDayNumber;
    private BigDecimal estimatedCost;
    private String costCurrency;
    private String category;
    private String transportMode;
    private String distanceToNext;
    private String durationToNext;
    private Integer distanceValueToNext;
    private Integer durationValueToNext;
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String notes;
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String description;
    private Boolean isAccommodation;
    private Boolean isStartingPoint;
    private LocalDateTime startingPointDate;

    // Ignore expenses field if sent from frontend
    private List<Object> expenses;
}
