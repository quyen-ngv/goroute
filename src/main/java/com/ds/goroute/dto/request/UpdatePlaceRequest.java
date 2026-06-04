package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlaceRequest {

    // Basic Info
    @NotBlank(message = "Title is required")
    private String title;

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
    private String status;
    private String priceRange;

    // Hours & Booking
    private String openHours; // JSON as String
    private String popularTimes; // JSON as String
    private String reservations; // JSON as String
    private String orderOnline; // JSON as String
    private String menu; // JSON as String

    // Additional Info
    private String completeAddress; // JSON as String
    private String about; // JSON as String
    private String owner; // JSON as String
    private String emails; // JSON as String
}
