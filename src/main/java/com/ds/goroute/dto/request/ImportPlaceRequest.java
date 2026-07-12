package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ImportPlaceRequest {

    @NotBlank(message = "Place ID is required")
    private String placeId;

    private String cid;
    private String dataId;

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

    private String plusCode;
    private String timezone;
    private String phone;
    private String website;
    private String googleMapsLink;

    private Integer reviewCount;
    private BigDecimal reviewRating;
    private String reviewsPerRating; // JSON as String

    private String thumbnail;
    private String images; // JSON as String

    private String descriptions;
    private String status;
    private String priceRange;

    private String openHours; // JSON as String
    private String popularTimes; // JSON as String
    private String reservations; // JSON as String
    private String orderOnline; // JSON as String
    private String menu; // JSON as String

    private String completeAddress; // JSON as String
    private String about; // JSON as String
    private String owner; // JSON as String
    private String emails; // JSON as String

    // Full raw JSON from Google Maps scraper
    private String rawData; // JSON as String

    // Reviews to import
    private String userReviews; // JSON as String
}
