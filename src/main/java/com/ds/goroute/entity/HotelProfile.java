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
    private String policies;
    private String bookingContact;
    private String status;
    private String disabledReason;
    private Long dataVersion;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
