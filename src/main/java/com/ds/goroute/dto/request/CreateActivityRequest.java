package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateActivityRequest {
    private String placeId;
    private UUID customPlaceId;

    @NotBlank(message = "Activity name is required")
    @Size(max = 1000, message = "Activity name must be at most 1000 characters")
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String name;

    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;

    /** Destination address for transport activities (origin = address). */
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String endAddress;
    private BigDecimal endLat;
    private BigDecimal endLng;

    @NotNull(message = "Day number is required")
    private Integer dayNumber;

    private LocalTime startTime;
    private LocalTime endTime;
    private Integer endDayNumber;
    private BigDecimal estimatedCost;
    private String costCurrency;
    private String category;
    private String transportMode;
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String notes;
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String description;

    // Special flags
    private Boolean isAccommodation;
    private Boolean isStartingPoint;
    private LocalDateTime startingPointDate;

    // Booking reference (optional â€” when adding from activity catalog)
    private UUID bookingId;
    private String bookingSource; // KLOOK, VIATOR

    /**
     * Set when the stop is a published local event (epic 17). The name, coordinates and times
     * still come from this request; the server links the event, its catalogue place when it has
     * one, and its cover photo.
     */
    private UUID localEventId;
}
