package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String name;

    private String address;
    private BigDecimal lat;
    private BigDecimal lng;

    /** Destination address for transport activities (origin = address). */
    private String endAddress;
    private BigDecimal endLat;
    private BigDecimal endLng;

    @NotNull(message = "Day number is required")
    private Integer dayNumber;

    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal estimatedCost;
    private String costCurrency;
    private String category;
    private String transportMode;
    private String notes;
    private String description;

    // Special flags
    private Boolean isAccommodation;
    private Boolean isStartingPoint;
    private LocalDateTime startingPointDate;

    // Booking reference (optional â€” when adding from activity catalog)
    private UUID bookingId;
    private String bookingSource; // KLOOK, VIATOR
}
