package com.ds.goroute.dto.request;

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

    private String notes;
}
