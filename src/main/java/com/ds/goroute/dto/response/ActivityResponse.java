package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class ActivityResponse {
    private UUID id;
    private UUID tripId;
    private Integer dayNumber;
    private Integer sortOrder;
    private String placeId;
    private UUID customPlaceId;
    private UUID placeRefId;
    private ActivityPlaceSummaryResponse place;
    /**
     * The activity's name. When it is still the name of its linked place (it was copied from
     * the place when the activity was added), it is that place's name in the reader's
     * language; a name written by hand comes back as written.
     */
    private String name;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;

    /** Destination point for transport activities (origin = lat/lng). */
    private String endAddress;
    private BigDecimal endLat;
    private BigDecimal endLng;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private Integer endDayNumber;

    private BigDecimal estimatedCost;
    private String costCurrency;
    private String category;
    private String transportMode;
    private BigDecimal rating;
    private String photoUrl;
    private String notes;
    private String description;
    private String status;
    private Boolean checkedIn;
    private Integer checkedInCount;

    // Distance and duration to next activity
    private String distanceToNext;
    private String durationToNext;
    private Integer distanceValueToNext;
    private Integer durationValueToNext;

    // Special flags
    private Boolean isAccommodation;
    private Boolean isStartingPoint;
    private LocalDateTime startingPointDate;

    // Expense tracking
    private BigDecimal actualSpent;
    private Integer expenseCount;
    private List<ExpenseResponse> expenses;

    /** Linked activity booking (Klook, etc.) when added from Explore. */
    private UUID bookingId;
    private String bookingSource;

    /**
     * The local event this stop is (epic 17): its id and its current card (dates, status, cover),
     * so the itinerary shows a cancelled or rescheduled event as such. {@code localEvent} is null
     * when the event was deleted.
     */
    private UUID localEventId;
    private com.ds.goroute.localevent.dto.LocalEventSummaryResponse localEvent;
}
