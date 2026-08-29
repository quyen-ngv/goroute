package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddBookingToTripRequest {
    @NotNull
    private UUID tripId;

    @NotNull
    private Integer dayNumber;

    /** ISO 4217 target currency. When provided, estimatedCost is converted. */
    private String targetCurrency;

    /** HH:mm start time in the itinerary. */
    private String startTime;

    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String notes;

    /** Short user-written itinerary description for this tour. */
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String description;
}
