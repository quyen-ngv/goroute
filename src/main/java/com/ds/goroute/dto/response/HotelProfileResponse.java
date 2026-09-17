package com.ds.goroute.dto.response;

import com.ds.goroute.dto.HotelNearbyPlace;
import com.ds.goroute.dto.HotelPolicies;
import lombok.Builder;

import java.math.BigDecimal;
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
    /** Curated tourist area, from the auto-map job or an operator override. */
    private UUID locationImageId;
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
    private List<String> languages;
    private Map<String, Object> receptionHours;
    private List<String> houseRules;
    private List<String> accessibilityFeatures;
    private Map<String, Object> parkingDetails;
    private HotelPolicies policies;
    private Map<String, Object> bookingContact;
    private java.math.BigDecimal fromPrice; private String fromPriceCurrency; private Integer roomTypeCount;
    /** Property gallery; falls back to the linked place's photos when the partner uploaded none. */
    private List<String> images;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer openedYear;
    private Integer renovatedYear;
    /** Sum of units across enabled room types. */
    private Integer totalRooms;
    /** Share of every quoted price that is VAT / service charge. Prices already include both. */
    private BigDecimal vatPercent;
    private BigDecimal serviceChargePercent;
    private List<HotelNearbyPlace> nearbyPlaces;
    /** {@link #amenities} grouped for display; {@code popularAmenities} are the codes flagged popular. */
    private List<HotelAmenityGroupResponse> amenityGroups;
    private List<String> popularAmenities;
    private HotelReviewSummaryResponse reviewSummary;
 private String status;
    private String disabledReason;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
