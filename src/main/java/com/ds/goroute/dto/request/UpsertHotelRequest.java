package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.dto.HotelNearbyPlace;
import com.ds.goroute.dto.HotelPolicies;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

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
    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
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
    @Valid private HotelPolicies policies;
    private Map<String, Object> bookingContact;
    @Size(max = 50) private List<@Size(max = 2000) String> images;
    @Min(1800) @Max(2100) private Integer openedYear;
    @Min(1800) @Max(2100) private Integer renovatedYear;
    @DecimalMin("0") @DecimalMax("100") private BigDecimal vatPercent;
    @DecimalMin("0") @DecimalMax("100") private BigDecimal serviceChargePercent;
    @Valid @Size(max = 30) private List<HotelNearbyPlace> nearbyPlaces;
    private MarketplacePublicationStatus status;
    private String disabledReason;
    private Long expectedVersion;
}
