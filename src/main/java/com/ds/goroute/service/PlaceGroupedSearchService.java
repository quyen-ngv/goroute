package com.ds.goroute.service;

import com.ds.goroute.dto.response.GroupedPlaceSearchResponse;
import com.ds.goroute.type.PlaceSearchSort;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** GET /places/search/grouped: the area search, plus far places by name when the area finds too little. */
public interface PlaceGroupedSearchService {

    /**
     * {@code nearby} is {@link PlaceService#searchPlaces} for exactly these parameters (same
     * cache, same order). {@code elsewhere} is filled on the first page of a keyword search with
     * no filter besides the place groups, when the area finds nothing or only names sharing one
     * word with the keyword. Both carry {@code distance} from the point and {@code provinceName}.
     */
    GroupedPlaceSearchResponse search(String keyword, BigDecimal latitude, BigDecimal longitude,
                                      BigDecimal radius, String category, List<String> placeGroups,
                                      List<String> subTypes, BigDecimal minRating, PlaceSearchSort sort,
                                      String citySlug, List<UUID> foodIds, Boolean excludeLinkedFoodPlaces,
                                      boolean includeInactive, Float minLuceneScore, int page, int size);
}
