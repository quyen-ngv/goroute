package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class HotelProfile {
    private UUID id;
    private UUID organizationId;
    private UUID placeId;
    private String placeTitle;
    private String placeAddress;
    private String placeThumbnail;
    private String propertyCode;
    private String propertyType;
    private Integer starRating;
    private String description;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
    private String timezone;
    private String amenities;
    private String languages;
    private String receptionHours;
    private String houseRules;
    private String accessibilityFeatures;
    private String parkingDetails;
    private String policies;
    private String bookingContact;
    private java.math.BigDecimal fromPrice; private String fromPriceCurrency; private Integer roomTypeCount;
    /** JSON array of image URLs for the property itself (rooms keep their own). */
    private String images;
    private Integer openedYear;
    private Integer renovatedYear;
    /** Share of every price that is VAT / service charge; prices are stored tax-inclusive. */
    private java.math.BigDecimal vatPercent;
    private java.math.BigDecimal serviceChargePercent;
    /** JSON array of {@link com.ds.goroute.dto.HotelNearbyPlace}. */
    private String nearbyPlaces;
    // Read-only projections from the linked place and room types
    private java.math.BigDecimal placeLatitude;
    private java.math.BigDecimal placeLongitude;
    private String placeImages;
    private java.math.BigDecimal placeReviewRating;
    private Integer placeReviewCount;
    private Integer totalRooms;
    private String status;
    private String disabledReason;
    private Long dataVersion;
    private UUID createdBy;
    private UUID updatedBy;
    /** Curated tourist area (location_images) this row belongs to; null until auto-map resolves one. */
    private UUID locationImageId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
