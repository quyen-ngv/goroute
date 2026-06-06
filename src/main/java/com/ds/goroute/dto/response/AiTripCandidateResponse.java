package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTripCandidateResponse {
    private String id;
    private String sourceType;
    private String sourceId;
    private String name;
    private String description;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private BigDecimal rating;
    private Integer reviewCount;
    private String photoUrl;
    private String placeGroup;
    private String category;
    private Integer visitDurationMinutes;
    private String durationText;
    private UUID bookingId;
    private String bookingSource;
    private BigDecimal priceAmount;
    private String priceCurrency;
    private String aiReason;
}
