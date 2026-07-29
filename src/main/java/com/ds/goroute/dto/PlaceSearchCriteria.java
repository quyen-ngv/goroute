package com.ds.goroute.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Internal criteria for the unified Lucene place search.
 */
public record PlaceSearchCriteria(
        String keyword,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal radiusKm,
        String category,
        List<String> placeGroups,
        BigDecimal minRating,
        String citySlug,
        List<UUID> foodIds,
        Boolean excludeLinkedFoodPlaces,
        boolean includeInactive,
        Float minLuceneScore,
        int page,
        int size) {

    public static final int MAX_PAGE_SIZE = 100;
}
