package com.ds.goroute.service;

import com.ds.goroute.dto.response.PlaceMapTileResponse;
import com.ds.goroute.utils.MapTile;

import java.util.List;

/**
 * The places a map draws, by slippy-map tile: the way Google Maps keeps a zoomed-out view
 * readable. Each tile holds the most prominent place of each of its cells, so a country view
 * shows the best-known places of every region and a closer view adds the next ones.
 */
public interface PlaceMapTileService {

    /** Cache of one tile's pins for one filter and language, evicted with {@code placeSearch}. */
    String CACHE = "placeMapTile";

    /** Closest and furthest tile zooms served: below 3 a tile spans a continent. */
    int MIN_ZOOM = 3;
    int MAX_ZOOM = 18;

    /** Most tiles one request asks for: a phone screen and the ring around it. */
    int MAX_TILES = 32;

    /**
     * The pins of every tile in {@code tiles} (one zoom for all), in the order asked, each
     * tile's places most prominent first.
     *
     * @param placeGroups any of these groups; null or empty for every group
     * @param subTypes    validated {@code PlaceSubType} filter codes; null or empty for all
     */
    List<PlaceMapTileResponse> getTiles(List<MapTile> tiles, List<String> placeGroups, List<String> subTypes);
}
