package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.GroupedPlaceSearchResponse;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.PlaceGroupedSearchService;
import com.ds.goroute.service.PlaceService;
import com.ds.goroute.service.ProvinceNameResolver;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PlaceSearchSort;
import com.ds.goroute.utils.DestinationMatchUtils;
import com.ds.goroute.utils.GeoDistance;
import com.ds.goroute.utils.LuceneKeywordQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lives apart from {@link PlaceServiceImpl} so the area search goes through its proxy and keeps
 * using the {@code placeSearch} cache; the far search runs only when the area falls short.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceGroupedSearchServiceImpl implements PlaceGroupedSearchService {

    /** Far candidates read per place kept, so folding a chain's branches still fills the list. */
    static final int CANDIDATES_PER_KEPT = 3;

    private final PlaceService placeService;
    private final BusinessConfigService businessConfigService;
    private final ProvinceNameResolver provinceNameResolver;

    @Override
    public GroupedPlaceSearchResponse search(String keyword, BigDecimal latitude, BigDecimal longitude,
                                             BigDecimal radius, String category, List<String> placeGroups,
                                             List<String> subTypes, BigDecimal minRating, PlaceSearchSort sort,
                                             String citySlug, List<UUID> foodIds, Boolean excludeLinkedFoodPlaces,
                                             boolean includeInactive, Float minLuceneScore, int page, int size) {
        List<PlaceResponse> nearby = placeService.searchPlaces(keyword, latitude, longitude, radius, category,
                placeGroups, subTypes, minRating, sort, citySlug, foodIds, excludeLinkedFoodPlaces,
                includeInactive, minLuceneScore, page, size);

        List<PlaceResponse> elsewhere = List.of();
        boolean truncated = false;
        boolean plainKeywordSearch = page == 0 && keyword != null && !keyword.isBlank()
                && isBlank(category) && isEmpty(subTypes) && minRating == null && isBlank(citySlug)
                && isEmpty(foodIds) && !Boolean.TRUE.equals(excludeLinkedFoodPlaces) && !includeInactive
                && minLuceneScore == null;
        if (plainKeywordSearch && businessConfigService.getBoolean(BusinessConfigKey.PLACE_SEARCH_ELSEWHERE_ENABLED)) {
            try {
                if (nearby.isEmpty() || placeService.areaKeywordTier(keyword, latitude, longitude, radius,
                        placeGroups) == LuceneKeywordQuery.Match.ANY_WORD) {
                    int max = businessConfigService.getInt(BusinessConfigKey.PLACE_SEARCH_ELSEWHERE_MAX);
                    List<PlaceResponse> distinct = onePerNameAndProvince(placeService.searchPlacesByNameOutside(
                            keyword, latitude, longitude, radius, placeGroups, max * CANDIDATES_PER_KEPT));
                    truncated = distinct.size() > max;
                    elsewhere = truncated ? distinct.subList(0, max) : distinct;
                }
            } catch (RuntimeException e) {
                // The far group is extra: a failure there must not cost the user the area's results.
                log.warn("Place search elsewhere failed for keyword length {}", keyword.length(), e);
                elsewhere = List.of();
                truncated = false;
            }
        }
        return GroupedPlaceSearchResponse.builder()
                .nearby(labelled(nearby, latitude, longitude))
                .elsewhere(labelled(elsewhere, latitude, longitude))
                .elsewhereTruncated(truncated)
                .build();
    }

    /** The first place of each name in each province: a chain's branches in one province fold into one row. */
    static List<PlaceResponse> onePerNameAndProvince(List<PlaceResponse> places) {
        Set<String> seen = new HashSet<>();
        List<PlaceResponse> kept = new ArrayList<>(places.size());
        for (PlaceResponse place : places) {
            String name = place.getTitle() != null ? place.getTitle() : place.getName();
            String key = place.getProvinceCode() == null || name == null
                    ? String.valueOf(place.getId())
                    : DestinationMatchUtils.normalizeKey(name) + "|" + place.getProvinceCode();
            if (seen.add(key)) {
                kept.add(place);
            }
        }
        return kept;
    }

    /** Copies with distance from the point and the province's name; the inputs may be cache entries. */
    private List<PlaceResponse> labelled(List<PlaceResponse> places, BigDecimal latitude, BigDecimal longitude) {
        return places.stream()
                .map(place -> place.toBuilder()
                        .distance(GeoDistance.kilometresOrFarAway(
                                latitude, longitude, place.getLatitude(), place.getLongitude()))
                        .provinceName(provinceNameResolver.nameOf(place.getProvinceCode()))
                        .build())
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }
}
