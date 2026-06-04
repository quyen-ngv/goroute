package com.ds.goroute.dto.request;

import com.ds.goroute.dto.GeoCoordinateDto;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateActivityBookingRequest {
    private String url;
    private String redirectUrl;
    private String title;
    private String description;
    private String activityAddress;
    private String departingFrom;
    private List<String> destinations;
    private List<GeoCoordinateDto> destinationCoordinates;
    private List<String> navigationList;
    private List<String> itineraryStops;
    private List<String> pickupAddresses;
    private BigDecimal priceAmount;
    private String priceCurrency;
    private String duration;
    private BigDecimal rating;
    private Integer reviewCount;
    private Integer bookedCount;
    private String thumbnail;
    private List<String> images;
    private List<String> highlights;
    private List<WhatToExpectItem> whatToExpect;
    private List<ItineraryItem> itinerary;

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
