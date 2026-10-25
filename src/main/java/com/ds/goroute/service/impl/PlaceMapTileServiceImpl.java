package com.ds.goroute.service.impl;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PlaceImagesDto;
import com.ds.goroute.dto.response.PlaceMapPinResponse;
import com.ds.goroute.dto.response.PlaceMapTileResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.PlaceMapTileService;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.MapTile;
import com.ds.goroute.utils.PlaceDisplayRating;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Serves map tiles from the search index and a tile cache shared by every caller: tiles are
 * the same for everyone at one filter and language, so a city's tiles are read once per
 * cache lifetime however many people look at it.
 */
@Service
@Slf4j
public class PlaceMapTileServiceImpl implements PlaceMapTileService {

    private final PlaceSearchIndexService placeSearchIndexService;
    private final PlaceRepository placeRepository;
    private final PlaceTranslationService placeTranslationService;
    private final Cache tileCache;

    public PlaceMapTileServiceImpl(PlaceSearchIndexService placeSearchIndexService,
                                   PlaceRepository placeRepository,
                                   PlaceTranslationService placeTranslationService,
                                   @Qualifier("searchCacheManager") CacheManager searchCacheManager) {
        this.placeSearchIndexService = placeSearchIndexService;
        this.placeRepository = placeRepository;
        this.placeTranslationService = placeTranslationService;
        this.tileCache = Objects.requireNonNull(searchCacheManager.getCache(CACHE),
                "searchCacheManager has no " + CACHE + " cache");
    }

    /** One tile for one filter and language. */
    record TileKey(MapTile tile, String filter) {
    }

    @Override
    public List<PlaceMapTileResponse> getTiles(List<MapTile> tiles, List<String> placeGroups, List<String> subTypes) {
        List<MapTile> wanted = tiles.stream().distinct().toList();
        List<String> groups = sortedOrEmpty(PlaceGroup.normalizeCodes(placeGroups));
        List<String> kinds = sortedOrEmpty(subTypes);
        String filter = String.join(",", groups) + "|" + String.join(",", kinds)
                + "|" + AcceptLanguageFilter.currentCode();

        Map<MapTile, List<PlaceMapPinResponse>> pinsByTile = new HashMap<>();
        List<MapTile> missing = new ArrayList<>();
        for (MapTile tile : wanted) {
            List<PlaceMapPinResponse> cached = cachedPins(new TileKey(tile, filter));
            if (cached != null) {
                pinsByTile.put(tile, cached);
            } else {
                missing.add(tile);
            }
        }
        if (!missing.isEmpty()) {
            // A rebuild serves from the old index meanwhile, which may rank by an older
            // formula (a schema change is what starts one at boot): not worth keeping.
            boolean cacheable = !placeSearchIndexService.isReindexRunning();
            Map<MapTile, List<UUID>> idsByTile = findIds(missing, groups, kinds);
            Map<UUID, PlaceMapPinResponse> pins = loadPins(idsByTile.values().stream()
                    .flatMap(List::stream)
                    .distinct()
                    .toList());
            for (MapTile tile : missing) {
                List<PlaceMapPinResponse> tilePins = idsByTile.getOrDefault(tile, List.of()).stream()
                        .map(pins::get)
                        .filter(Objects::nonNull)
                        .toList();
                // An empty tile is cached too: open sea is asked for as often as a city.
                if (cacheable) {
                    tileCache.put(new TileKey(tile, filter), tilePins);
                }
                pinsByTile.put(tile, tilePins);
            }
        }
        return wanted.stream()
                .map(tile -> PlaceMapTileResponse.builder()
                        .z(tile.z())
                        .x(tile.x())
                        .y(tile.y())
                        .places(pinsByTile.getOrDefault(tile, List.of()))
                        .build())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<PlaceMapPinResponse> cachedPins(TileKey key) {
        Cache.ValueWrapper hit = tileCache.get(key);
        return hit == null ? null : (List<PlaceMapPinResponse>) hit.get();
    }

    private Map<MapTile, List<UUID>> findIds(List<MapTile> tiles, List<String> groups, List<String> kinds) {
        try {
            return placeSearchIndexService.findMapTilePlaceIds(tiles, groups, kinds);
        } catch (IOException e) {
            log.error("Map tile search failed", e);
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR, "Place search failed");
        }
    }

    private Map<UUID, PlaceMapPinResponse> loadPins(List<UUID> ids) {
        Map<UUID, PlaceMapPinResponse> pins = new HashMap<>();
        if (ids.isEmpty()) {
            return pins;
        }
        List<Place> places = placeRepository.findMapPinsByIds(ids);
        Map<UUID, List<PlaceTranslation>> translations = placeTranslationService.findByPlaceIds(
                places.stream().map(Place::getId).toList());
        String locale = AcceptLanguageFilter.currentCode();
        for (Place place : places) {
            PlaceTranslation translation = PlaceTranslationService.resolveFrom(
                    translations.getOrDefault(place.getId(), List.of()), locale);
            String name = translation != null && translation.getName() != null
                    ? translation.getName()
                    : place.getTitle();
            pins.put(place.getId(), PlaceMapPinResponse.builder()
                    .id(place.getId())
                    .placeId(place.getPlaceId())
                    .name(name)
                    .title(name)
                    .placeGroup(place.getPlaceGroup() != null ? place.getPlaceGroup().name() : null)
                    .subTypes(place.getSubTypes() != null ? List.of(place.getSubTypes()) : List.of())
                    .address(place.getAddress())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .thumbnail(place.getThumbnail())
                    .images(firstImages(place.getImages()))
                    .reviewRating(place.getReviewRating())
                    .adjustedRating(PlaceDisplayRating.of(place))
                    .reviewCount(place.getReviewCount())
                    .tripUseCount(place.getTripUseCount())
                    .checkinCount(place.getCheckinCount())
                    .build());
        }
        return pins;
    }

    /**
     * The first {@link PlaceMapPinResponse#MAX_IMAGES} photos of a place's stored list, each
     * with a title (blank when it has none) so clients that require one can read it.
     */
    static List<PlaceImagesDto> firstImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return List.of();
        }
        List<PlaceImagesDto> images;
        try {
            images = JsonUtils.fromJson(imagesJson, new TypeReference<List<PlaceImagesDto>>() {});
        } catch (RuntimeException malformed) {
            log.warn("Skipping malformed place images: {}", malformed.getMessage());
            return List.of();
        }
        if (images == null) {
            return List.of();
        }
        return images.stream()
                .filter(image -> image != null && image.getImage() != null && !image.getImage().isBlank())
                .limit(PlaceMapPinResponse.MAX_IMAGES)
                .map(image -> new PlaceImagesDto(image.getTitle() == null ? "" : image.getTitle(), image.getImage()))
                .toList();
    }

    private static List<String> sortedOrEmpty(List<String> values) {
        return values == null ? List.of() : values.stream().sorted().toList();
    }
}
