package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data @Builder
public class HotelProfileResponse {
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
    private List<String> amenities;
    private Map<String, Object> policies;
    private Map<String, Object> bookingContact;
    private String status;
    private String disabledReason;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
