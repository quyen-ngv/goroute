package com.ds.goroute.service;

import com.ds.goroute.dto.PlaceSearchCriteria;
import com.ds.goroute.entity.Place;
import com.ds.goroute.type.PlaceSubType;
import com.ds.goroute.utils.MapTile;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
     * How many places of the area {@code criteria} describes (its sub-type filter ignored) carry
     * each of {@code kinds}, in that order, then {@link PlaceSubType#UNTYPED_FILTER}: those that
     * carry none. A place with two kinds counts under both.
     */
    Map<String, Integer> countBySubType(PlaceSearchCriteria criteria, List<PlaceSubType> kinds)
            throws IOException;

    /**
     * The places a map draws on each of {@code tiles} (one zoom for all): in every tile, the
     * most prominent ACTIVE place of each of its cells {@code MAP_TILE_CELL_DEPTH} zooms
     * closer, most prominent first. Ranked by {@link com.ds.goroute.utils.PlaceProminence}, so
     * a place kept on a tile is kept on the closer tile it falls in. Every tile is in the
     * result, a tile with no place as an empty list.
     *
     * @param placeGroups any of these groups; null or empty for every group
     * @param subTypes    any of these sub types, as in {@link #searchPlaceIds}; null or empty for all
     */
    Map<MapTile, List<UUID>> findMapTilePlaceIds(Collection<MapTile> tiles, List<String> placeGroups,
                                                List<String> subTypes) throws IOException;

    /**
     * Name search without a location, best match first: the type-ahead behind GET /places.
     * Accent-folded and over every translated name. Text with no searchable character
     * returns nothing.
     *
     * @param includeInactive false keeps only ACTIVE places
     */
    List<UUID> searchPlaceIdsByName(String keyword, List<String> placeGroups, boolean includeInactive,
                                    int page, int size) throws IOException;

    /** How many ACTIVE places {@link #searchPlaceIdsByName} finds for {@code keyword} in these groups. */
    int countPlacesByName(String keyword, List<String> placeGroups) throws IOException;
}
