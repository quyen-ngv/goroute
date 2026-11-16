package com.ds.goroute.localevent.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

/**
 * POST /v1/api/events/{eventId}/trips/{tripId}/activities: where in the trip the event goes.
 * Everything else about the stop (name, venue, coordinates, cover) is taken from the event.
 */
@Data
public class LocalEventAddToTripRequest {

    @NotNull(message = "Day number is required")
    @Min(1)
    private Integer dayNumber;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    /** The trip's own activity category (attraction, entertainment…); the app's default when absent. */
    @Size(max = 50)
    private String category;
}
