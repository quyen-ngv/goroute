package com.ds.goroute.dto.request;

import com.ds.goroute.dto.ActivityItineraryItem;
import com.ds.goroute.dto.ActivityWhatToExpectItem;
import com.ds.goroute.dto.GeoCoordinateDto;
import com.ds.goroute.type.MarketplacePublicationStatus;
import com.ds.goroute.type.ActivityProductType;
import com.ds.goroute.type.MarketplaceConfirmationType;
import com.ds.goroute.type.MarketplaceVoucherType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class UpsertMarketplaceActivityRequest {
    @NotNull private UUID organizationId;
    private UUID placeId;
    private ActivityProductType activityType = ActivityProductType.TOUR;
    @NotBlank @Size(max=500) private String title;
    @Size(max=10000) private String description;
    @Size(max=500) private String activityAddress;
    @Size(max=2000) private String url;
    @Size(max=2000) private String redirectUrl;
    @Size(max=200) private String departingFrom;
    @Size(max=100) private List<@Size(max=200) String> destinations;
    @Valid @Size(max=100) private List<GeoCoordinateDto> destinationCoordinates;
    @Size(max=100) private List<@Size(max=500) String> navigationList;
    @Size(max=100) private List<@Size(max=1000) String> itineraryStops;
    @Size(max=100) private List<@Size(max=1000) String> pickupAddresses;
    @Size(max=20) private List<@Size(max=100) String> languages;
    @Size(max=2000) private String meetingPoint;
    @Size(max=100) private List<@Size(max=1000) String> includedItems;
    @Size(max=100) private List<@Size(max=1000) String> excludedItems;
    private java.util.Map<String, Object> eligibility;
    @Size(max=100) private List<@Size(max=500) String> accessibilityFeatures;
    private MarketplaceConfirmationType confirmationType = MarketplaceConfirmationType.INSTANT;
    private MarketplaceVoucherType voucherType = MarketplaceVoucherType.QR_CODE;
    @Size(max=10000) private String redemptionInstructions;
    private java.util.Map<String, Object> cancellationPolicy;
    @Size(max=100) private List<@Size(max=500) String> requiredInformation;
    @DecimalMin("0") private BigDecimal priceAmount;
    @Pattern(regexp="[A-Z]{3}") private String priceCurrency="VND";
    @Size(max=100) private String durationRaw;
    @DecimalMin("0") private BigDecimal durationHours;
    private Integer visitDurationMinutes;
    @Size(max=2000) private String thumbnail;
    @Size(max=30) private List<@Size(max=2000) String> images;
    @Size(max=50) private List<@Size(max=1000) String> highlights;
    @Valid @Size(max=50) private List<ActivityWhatToExpectItem> whatToExpect;
    @Valid @Size(max=100) private List<ActivityItineraryItem> itinerary;
    private MarketplacePublicationStatus productStatus=MarketplacePublicationStatus.DRAFT;
    private Long expectedVersion;
}
