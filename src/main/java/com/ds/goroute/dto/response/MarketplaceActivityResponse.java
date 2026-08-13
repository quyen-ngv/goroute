package com.ds.goroute.dto.response;

import com.ds.goroute.dto.ActivityItineraryItem;
import com.ds.goroute.dto.ActivityWhatToExpectItem;
import com.ds.goroute.dto.GeoCoordinateDto;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
public class MarketplaceActivityResponse {
    private UUID id; private UUID organizationId; private UUID placeId; private String placeTitle; private String externalId; private String title; private String activityType;
    private String source; private String url; private String redirectUrl; private String description; private String activityAddress;
    private String departingFrom; private List<String> destinations; private List<GeoCoordinateDto> destinationCoordinates;
    private List<String> navigationList; private List<String> itineraryStops; private List<String> pickupAddresses;
    private List<String> languages; private String meetingPoint; private List<String> includedItems; private List<String> excludedItems;
    private java.util.Map<String,Object> eligibility; private List<String> accessibilityFeatures; private String confirmationType; private String voucherType;
    private String redemptionInstructions; private java.util.Map<String,Object> cancellationPolicy; private List<String> requiredInformation;
    private BigDecimal priceAmount; private String priceCurrency;
    private String durationRaw; private BigDecimal durationHours; private Integer visitDurationMinutes; private BigDecimal rating; private Integer reviewCount; private Integer bookedCount; private String thumbnail;
    private List<String> images; private List<String> highlights; private List<ActivityWhatToExpectItem> whatToExpect;
    private List<ActivityItineraryItem> itinerary; private String productStatus; private String inventoryMode;
    private Long dataVersion; private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
