package com.ds.goroute.dto.request;

import com.ds.goroute.dto.GeoCoordinateDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ImportActivityBookingRequest {
    @NotBlank
    private String url;

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private LocationInfo location;
    private List<String> destinations;
    private List<GeoCoordinateDto> destinationCoordinates;

    @NotNull
    private PriceInfo price;

    private String duration;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer bookedCount;

    private List<String> images;
    private List<String> highlights;
    private List<WhatToExpectItem> whatToExpect;
    private List<ItineraryItem> itinerary;

    @Data
    public static class LocationInfo {
        private String activityAddress;
        private List<GeoCoordinateDto> destinationCoordinates;
        private List<String> navigationList;
        private List<String> itineraryStops;
        private List<String> pickupAddresses;
    }

    @Data
    public static class PriceInfo {
        private String raw;
        private BigDecimal amount;
    }

    @Data
    public static class WhatToExpectItem {
        private String text;
        private String image;
    }

    @Data
    public static class ItineraryItem {
        private String title;
        private String content;
        private List<String> images;
    }
}
