package com.ds.goroute.service.marketplace;

import com.ds.goroute.dto.HotelNearbyPlace;
import com.ds.goroute.dto.HotelPolicies;
import com.ds.goroute.dto.response.HotelAmenityGroupResponse;
import com.ds.goroute.dto.response.HotelProfileResponse;
import com.ds.goroute.dto.response.HotelReviewSummaryResponse;
import com.ds.goroute.entity.HotelProfile;
import com.ds.goroute.entity.PlaceScore;
import com.ds.goroute.entity.RatePlan;
import com.ds.goroute.entity.RoomType;
import com.ds.goroute.dto.response.RatePlanResponse;
import com.ds.goroute.dto.response.RoomTypeResponse;
import com.ds.goroute.repository.PlaceScoreRepository;
import com.ds.goroute.type.HotelAmenity;
import com.ds.goroute.type.HotelAmenityGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** Turns stored hotels, room types and rate plans into the shapes partners, operators and guests read. */
@Component
@RequiredArgsConstructor
public class HotelCatalogAssembler {
    static final String REVIEW_SOURCE_GOROUTE = "GOROUTE";
    static final String REVIEW_SOURCE_EXTERNAL = "EXTERNAL";

    private final MarketplaceJson json;
    private final PlaceScoreRepository placeScoreRepository;
    private final MarketplaceDisplayPrice displayPrice;

    public HotelProfileResponse toResponse(HotelProfile h) {
        List<String> amenities = json.readList(h.getAmenities(), String.class);
        return HotelProfileResponse.builder()
                .id(h.getId()).organizationId(h.getOrganizationId()).placeId(h.getPlaceId())
                .placeTitle(h.getPlaceTitle()).placeAddress(h.getPlaceAddress()).placeThumbnail(h.getPlaceThumbnail())
                .locationImageId(h.getLocationImageId())
                .propertyCode(h.getPropertyCode()).propertyType(h.getPropertyType()).starRating(h.getStarRating())
                .description(h.getDescription()).checkInTime(h.getCheckInTime()).checkOutTime(h.getCheckOutTime())
                .timezone(h.getTimezone()).amenities(amenities)
                .amenityGroups(groupAmenities(amenities)).popularAmenities(popularAmenities(amenities))
                .languages(json.readList(h.getLanguages(), String.class))
                .receptionHours(json.readMap(h.getReceptionHours()))
                .houseRules(json.readList(h.getHouseRules(), String.class))
                .accessibilityFeatures(json.readList(h.getAccessibilityFeatures(), String.class))
                .parkingDetails(json.readMap(h.getParkingDetails()))
                .policies(json.read(h.getPolicies(), HotelPolicies.class, new HotelPolicies()))
                .bookingContact(json.readMap(h.getBookingContact()))
                .images(json.readList(h.getImages(), String.class))
                .latitude(h.getPlaceLatitude()).longitude(h.getPlaceLongitude())
                .openedYear(h.getOpenedYear()).renovatedYear(h.getRenovatedYear()).totalRooms(h.getTotalRooms())
                .vatPercent(h.getVatPercent()).serviceChargePercent(h.getServiceChargePercent())
                .nearbyPlaces(json.readList(h.getNearbyPlaces(), HotelNearbyPlace.class))
                .status(h.getStatus()).disabledReason(h.getDisabledReason())
                .fromPrice(h.getFromPrice()).fromPriceCurrency(h.getFromPriceCurrency()).roomTypeCount(h.getRoomTypeCount())
                .dataVersion(h.getDataVersion()).createdAt(h.getCreatedAt()).updatedAt(h.getUpdatedAt())
                .build();
    }

    /**
     * Guest-facing variant: the gallery falls back to the place's photos, and the review summary is
     * attached. Kept separate because the summary costs a query that lists and partner screens skip.
     */
    public HotelProfileResponse toPublicDetail(HotelProfile h) {
        HotelProfileResponse response = toResponse(h);
        response.setImages(gallery(response.getImages(), h.getPlaceThumbnail(), json.readList(h.getPlaceImages(), String.class)));
        response.setReviewSummary(reviewSummary(h));
        return response;
    }

    public RoomTypeResponse toRoomResponse(RoomType r) {
        return RoomTypeResponse.builder().id(r.getId()).hotelId(r.getHotelId()).code(r.getCode()).name(r.getName())
                .description(r.getDescription()).maxAdults(r.getMaxAdults()).standardAdults(r.getStandardAdults())
                .maxChildren(r.getMaxChildren()).maxInfants(r.getMaxInfants()).maxOccupancy(r.getMaxOccupancy())
                .bedroomCount(r.getBedroomCount()).bathroomCount(r.getBathroomCount()).viewType(r.getViewType())
                .bathroomType(r.getBathroomType()).smokingAllowed(r.getSmokingAllowed())
                .bedConfig(json.readMaps(r.getBedConfig()))
                .amenities(json.readList(r.getAmenities(), String.class))
                .accessibilityFeatures(json.readList(r.getAccessibilityFeatures(), String.class))
                .images(json.readList(r.getImages(), String.class)).roomSizeSqm(r.getRoomSizeSqm()).totalUnits(r.getTotalUnits())
                .status(r.getStatus()).disabledReason(r.getDisabledReason()).dataVersion(r.getDataVersion())
                .createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).build();
    }

    public RatePlanResponse toRateResponse(RatePlan r) {
        return RatePlanResponse.builder().id(r.getId()).roomTypeId(r.getRoomTypeId()).code(r.getCode()).name(r.getName())
                .description(r.getDescription()).currency(r.getCurrency()).basePrice(r.getBasePrice())
                .pricingModel(r.getPricingModel()).baseOccupancy(r.getBaseOccupancy())
                .extraAdultFee(r.getExtraAdultFee()).extraChildFee(r.getExtraChildFee()).mealPlan(r.getMealPlan())
                .includedBenefits(json.readList(r.getIncludedBenefits(), String.class))
                .cancellationPolicy(json.readMap(r.getCancellationPolicy()))
                .prepaymentPolicy(json.readMap(r.getPrepaymentPolicy())).noShowPolicy(json.readMap(r.getNoShowPolicy()))
                .occupancyPricing(json.readMap(r.getOccupancyPricing())).minStay(r.getMinStay()).maxStay(r.getMaxStay())
                .minAdvanceDays(r.getMinAdvanceDays()).maxAdvanceDays(r.getMaxAdvanceDays()).refundable(r.getRefundable())
                .status(r.getStatus()).dataVersion(r.getDataVersion()).createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).build();
    }

    /**
     * Public catalogue prices are shown in the guest's currency. Only the money fields move: policy
     * JSON stays as the partner authored it, and partner or admin endpoints keep the stored price.
     */
    public RatePlanResponse displayed(RatePlanResponse r) {
        String stored = r.getCurrency();
        r.setBasePrice(displayPrice.convert(r.getBasePrice(), stored));
        r.setExtraAdultFee(displayPrice.convert(r.getExtraAdultFee(), stored));
        r.setExtraChildFee(displayPrice.convert(r.getExtraChildFee(), stored));
        r.setCurrency(displayPrice.currencyOf(stored));
        return r;
    }

    static List<String> gallery(List<String> hotelImages, String placeThumbnail, List<String> placeImages) {
        Stream<String> source = hotelImages.isEmpty()
                ? Stream.concat(Stream.ofNullable(placeThumbnail), placeImages.stream())
                : hotelImages.stream();
        return source.filter(Objects::nonNull).map(String::trim).filter(url -> !url.isEmpty())
                .collect(LinkedHashSet<String>::new, LinkedHashSet::add, Collection::addAll)
                .stream().toList();
    }

    static List<HotelAmenityGroupResponse> groupAmenities(List<String> amenities) {
        Map<HotelAmenityGroup, List<String>> grouped = new EnumMap<>(HotelAmenityGroup.class);
        for (String value : amenities) {
            if (value == null || value.isBlank()) continue;
            Optional<HotelAmenity> known = HotelAmenity.fromValue(value);
            HotelAmenityGroup group = known.map(HotelAmenity::group).orElse(HotelAmenityGroup.OTHER);
            String entry = known.map(Enum::name).orElse(value.trim());
            List<String> bucket = grouped.computeIfAbsent(group, ignored -> new ArrayList<>());
            if (!bucket.contains(entry)) bucket.add(entry);
        }
        return grouped.entrySet().stream()
                .map(e -> new HotelAmenityGroupResponse(e.getKey().name(), List.copyOf(e.getValue())))
                .toList();
    }

    static List<String> popularAmenities(List<String> amenities) {
        return amenities.stream().map(HotelAmenity::fromValue).flatMap(Optional::stream)
                .filter(HotelAmenity::popular).map(Enum::name).distinct().toList();
    }

    /** In-app reviews when there are any; otherwise the imported place rating, without aspects. */
    private HotelReviewSummaryResponse reviewSummary(HotelProfile h) {
        Optional<PlaceScore> score = h.getPlaceId() == null ? Optional.empty() : placeScoreRepository.findByPlaceId(h.getPlaceId());
        return score.filter(s -> s.getTripmindScore() != null && s.getReviewCount() != null && s.getReviewCount() > 0)
                .map(s -> HotelReviewSummaryResponse.builder()
                        .score(s.getTripmindScore()).reviewCount(s.getReviewCount()).source(REVIEW_SOURCE_GOROUTE)
                        .locationScore(s.getLocationScore()).cleanlinessScore(s.getCleanlinessScore())
                        .serviceScore(s.getServiceScore()).facilitiesScore(s.getFacilitiesScore()).build())
                .orElseGet(() -> h.getPlaceReviewRating() == null ? null : HotelReviewSummaryResponse.builder()
                        .score(h.getPlaceReviewRating()).reviewCount(h.getPlaceReviewCount()).source(REVIEW_SOURCE_EXTERNAL).build());
    }
}
