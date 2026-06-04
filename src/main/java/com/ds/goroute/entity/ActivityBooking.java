package com.ds.goroute.entity;

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
public class ActivityBooking {
    private UUID id;
    private String externalId;
    private String source; // KLOOK, VIATOR
    private String url;

    // Basic Info
    private String title;
    private String description;

    // Redirect URL for affiliate
    private String redirectUrl; // Default = url, cÃ³ thá»ƒ edit Ä‘á»ƒ thÃªm affiliate link

    // Location
    private String activityAddress;
    private String departingFrom;
    private String navigationList; // JSON array
    private String itineraryStops; // JSON array
    private String pickupAddresses; // JSON array
    private String destinations; // JSON array
    /** JSON array of {lat, lng} for geo search */
    private String destinationCoordinates;
    /** First coordinate denormalized for indexed geo search */
    private Double searchLat;
    private Double searchLng;
    /** Pipe-separated normalized destination keys for fuzzy SQL search */
    private String destinationsNorm;

    // Pricing
    private BigDecimal priceAmount;
    private String priceCurrency;

    // Stats
    private String durationRaw;
    private BigDecimal durationHours;
    private Integer visitDurationMinutes;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer bookedCount;

    // Media & Content (JSON)
    private String thumbnail;
    private String images; // JSON array
    private String highlights; // JSON array
    private String whatToExpect; // JSON array of {text, image}
    private String itinerary; // JSON array of {title, content, images[]}

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
