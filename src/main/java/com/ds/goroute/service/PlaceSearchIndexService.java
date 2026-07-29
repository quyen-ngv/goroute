package com.ds.goroute.service;

import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.entity.Place;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface PlaceSearchIndexService {

    void indexPlace(Place place);

    void deletePlace(UUID id);

    void triggerReindex();

    /**
     * Searches and paginates places in Lucene. Text relevance, geo radius, distance
     * boost, and structured place filters are evaluated in the same index query.
     */
    List<UUID> searchPlaceIds(PlaceSearchCriteria criteria) throws IOException;
}
