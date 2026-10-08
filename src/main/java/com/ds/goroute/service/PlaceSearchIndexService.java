package com.ds.goroute.service;

import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.entity.Place;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface PlaceSearchIndexService {

    void indexPlace(Place place);

    void deletePlace(UUID id);

    /**
     * Rebuilds the whole index from the database in the background, into a fresh directory
     * that replaces the live one only once complete. Searches keep using the old index
     * meanwhile. Returns at once.
     *
     * @return false when a rebuild is already running (no second one is started)
     */
    boolean triggerReindex();

    /** Whether a background rebuild is running. */
    boolean isReindexRunning();

    /**
     * Searches and paginates places in Lucene. Text relevance, geo radius, distance
     * boost, and structured place filters are evaluated in the same index query.
     */
    List<UUID> searchPlaceIds(PlaceSearchCriteria criteria) throws IOException;

    /**
     * Name search without a location, best match first: the type-ahead behind GET /places.
     * Accent-folded and over every translated name. Text with no searchable character
     * returns nothing.
     *
     * @param includeInactive false keeps only ACTIVE places
     */
    List<UUID> searchPlaceIdsByName(String keyword, List<String> placeGroups, boolean includeInactive,
                                    int page, int size) throws IOException;
}
