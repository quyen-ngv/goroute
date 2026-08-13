package com.ds.goroute.dto.request;

import com.ds.goroute.type.HotelPropertyType;
import com.ds.goroute.type.MarketplacePublicationStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class UpsertHotelRequest {
    @NotNull private UUID organizationId;
    @NotNull private UUID placeId;
    @Size(max = 100) private String propertyCode;
    private HotelPropertyType propertyType = HotelPropertyType.HOTEL;
    @Min(1) @Max(5) private Integer starRating;
    private String description;
    private LocalTime checkInTime;
    private LocalTime checkOutTime;
    @Size(max = 100) private String timezone;
    private List<String> amenities;
    private List<String> languages;
    private Map<String, Object> receptionHours;
    private List<String> houseRules;
    private List<String> accessibilityFeatures;
    private Map<String, Object> parkingDetails;
    private Map<String, Object> policies;
    private Map<String, Object> bookingContact;
    private MarketplacePublicationStatus status;
    private String disabledReason;
    private Long expectedVersion;
}
