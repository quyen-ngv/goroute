package com.ds.goroute.dto.response;

import com.ds.goroute.dto.GeoCoordinateDto;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ActivityBookingResponse {
    private UUID id;
    private String externalId;
    private String source;
    private String url;
    private String redirectUrl;

    private String title;
    private String description;

    // Location
    private String activityAddress;
    private String departingFrom;
    private List<String> destinations;
    private List<GeoCoordinateDto> destinationCoordinates;
    private List<String> navigationList;
    private List<String> itineraryStops;
    private List<String> pickupAddresses;

    // Pricing
    private BigDecimal priceAmount;
    private String priceCurrency;

    // Stats
    private String duration;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer bookedCount;

    // Media
    private String thumbnail;
    private List<String> images;

    // Details
    private List<String> highlights;
    private List<WhatToExpectResponse> whatToExpect;
    private List<ItineraryResponse> itinerary;

    @Data
    @Builder
    public static class WhatToExpectResponse {
        private String text;
        private String imageUrl;
    }

    @Data
    @Builder
    public static class ItineraryResponse {
        private String title;
        private String content;
        private List<String> images;
    }
}
