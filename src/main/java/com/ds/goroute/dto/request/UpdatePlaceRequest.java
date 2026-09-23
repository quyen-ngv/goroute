package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlaceRequest {

    // Basic Info
    @NotBlank(message = "Title is required")
    private String title;
    private Map<String, PlaceTranslationRequest> translations;

    private String category;
    private String placeGroup;
    private String address;
    private List<String> destinations;

    @NotNull(message = "Latitude is required")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    private BigDecimal longitude;

    /** Null means use CHECKIN.VERIFY_RADIUS_METERS. */
    @Min(value = 20, message = "Verification radius must be at least 20 meters")
    @Max(value = 5000, message = "Verification radius must be at most 5000 meters")
    private Integer verificationRadiusMeters;

    /**
     * Drawn verification area as a GeoJSON Polygon/MultiPolygon, or null to clear it.
     * When present it replaces the radius for verification. Validated server-side by
     * PostGIS (must be valid and at most {@code MAX_VERIFICATION_AREA_KM2}).
     */
    private JsonNode verificationGeometry;

    /** Official ward code; null keeps the resolved value, blank clears it. */
    private String wardCode;

    private String plusCode;
    private String timezone;

    // Contact & Web
    private String phone;
    private String website;
    private String googleMapsLink;

    // Rating & Reviews
    private Integer reviewCount;
    private BigDecimal reviewRating;
    private String reviewsPerRating; // JSON as String

    // Media
    private String thumbnail;
    private String images; // JSON as String

    // Details
    private String descriptions;
    private String aiDescription; // Curated for AI trip planning
    private JsonNode aiReferences; // JSON array of article/review source metadata read by AI
    private String status;
    private String visibilityStatus;
    private String priceRange;

    // Hours & Booking
    private String openHours; // JSON as String
    private String regular; // Regular opening hours JSON as String
    private String popularTimes; // JSON as String
    private String reservations; // JSON as String
    private String orderOnline; // JSON as String
    private String menu; // JSON as String

    // Additional Info
    private String completeAddress; // JSON as String
    private String about; // JSON as String
    private String owner; // JSON as String
    private String emails; // JSON as String

    // Schema v1 flexible attributes, stored as a JSONB object
    private JsonNode attributes;
}
