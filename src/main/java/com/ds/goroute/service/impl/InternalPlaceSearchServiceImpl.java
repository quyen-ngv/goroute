package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.InternalPlaceSearchRequest;
import com.ds.goroute.dto.response.InternalPlaceSearchResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.InternalPlaceSearchService;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.mapsearch.MapText;
import com.ds.goroute.service.placelookup.PlaceHit;
import com.ds.goroute.service.placelookup.PlaceLookupException;
import com.ds.goroute.service.placelookup.PlaceLookupProvider;
import com.ds.goroute.service.placelookup.PlaceLookupProviderType;
import com.ds.goroute.service.placelookup.PlaceLookupProviders;
import com.ds.goroute.service.placelookup.PlaceLookupQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Social-video place lookup: our own catalogue first, Mapbox only when the catalogue has no
 * place of that name. Nothing is scraped and nothing is written to {@code places}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalPlaceSearchServiceImpl implements InternalPlaceSearchService {

    /** How many name hits the catalogue is asked for before the name check below. */
    private static final int CATALOGUE_SEARCH_SIZE = 5;

    private final PlaceSearchIndexService placeSearchIndexService;
    private final PlaceRepository placeRepository;
    private final PlaceLookupProviders placeLookupProviders;

    @Override
    public InternalPlaceSearchResponse search(InternalPlaceSearchRequest request) {
        String query = request.getQuery().trim();
        String name = request.getName() == null || request.getName().isBlank() ? query : request.getName().trim();
        List<InternalPlaceSearchResponse.Candidate> catalogue = catalogue(name, request.getLimit());
        if (!catalogue.isEmpty()) {
            return response(query, "CATALOG", catalogue);
        }
        return response(query, PlaceLookupProviderType.MAPBOX.name(), mapbox(query, request.getLimit()));
    }

    /** ACTIVE catalogue places whose name is the asked name, or contains it, accents ignored. */
    private List<InternalPlaceSearchResponse.Candidate> catalogue(String name, int limit) {
        List<UUID> ids;
        try {
            ids = placeSearchIndexService.searchPlaceIdsByName(name, null, false, 0, CATALOGUE_SEARCH_SIZE);
        } catch (IOException e) {
            log.warn("Catalogue search failed for the social worker, trying Mapbox: {}", e.getMessage());
            return List.of();
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Place> byId = placeRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity(), (left, right) -> left));
        List<InternalPlaceSearchResponse.Candidate> candidates = new ArrayList<>();
        for (UUID id : ids) {
            Place place = byId.get(id);
            if (place == null || place.getLatitude() == null || place.getLongitude() == null
                    || !sameName(name, place.getTitle())) {
                continue;
            }
            candidates.add(InternalPlaceSearchResponse.Candidate.builder()
                    .rank(candidates.size() + 1)
                    .title(place.getTitle())
                    .catalogPlaceId(place.getId().toString())
                    .placeId(place.getPlaceId())
                    .cid(place.getCid())
                    .googleMapsLink(place.getGoogleMapsLink())
                    .latitude(place.getLatitude())
                    .longitude(place.getLongitude())
                    .address(place.getAddress())
                    .category(place.getCategory())
                    .build());
            if (candidates.size() >= limit) {
                break;
            }
        }
        return candidates;
    }

    private List<InternalPlaceSearchResponse.Candidate> mapbox(String query, int limit) {
        PlaceLookupProvider mapbox = placeLookupProviders.get(PlaceLookupProviderType.MAPBOX);
        if (!mapbox.isAvailable()) {
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR,
                    "Mapbox place search is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        List<PlaceHit> hits;
        try {
            hits = mapbox.searchText(PlaceLookupQuery.text(query, limit));
        } catch (PlaceLookupException e) {
            log.warn("Mapbox place search failed for the social worker: {}", e.getMessage());
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR,
                    "Mapbox place search failed", HttpStatus.BAD_GATEWAY);
        }
        List<InternalPlaceSearchResponse.Candidate> candidates = new ArrayList<>();
        for (PlaceHit hit : hits) {
            if (candidates.size() >= limit) {
                break;
            }
            candidates.add(InternalPlaceSearchResponse.Candidate.builder()
                    .rank(candidates.size() + 1)
                    .title(hit.name())
                    .mapboxId(hit.providerPlaceId())
                    .latitude(hit.latitude())
                    .longitude(hit.longitude())
                    .address(hit.address())
                    .category(hit.primaryType())
                    .build());
        }
        return candidates;
    }

    /**
     * Every word of the shorter name appears in the longer one: "Hồ Gươm" is "Hồ Gươm (Hồ Hoàn
     * Kiếm)". Lucene alone also returns places that share only one word.
     */
    private static boolean sameName(String asked, String title) {
        Set<String> askedWords = words(asked);
        Set<String> titleWords = words(title);
        if (askedWords.isEmpty() || titleWords.isEmpty()) {
            return false;
        }
        return askedWords.size() <= titleWords.size()
                ? titleWords.containsAll(askedWords)
                : askedWords.containsAll(titleWords);
    }

    private static Set<String> words(String text) {
        String normalized = MapText.normalize(text);
        return normalized.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(normalized.split(" ")));
    }

    private static InternalPlaceSearchResponse response(String query, String provider,
                                                        List<InternalPlaceSearchResponse.Candidate> candidates) {
        return InternalPlaceSearchResponse.builder()
                .success(true)
                .query(query)
                .provider(provider)
                .count(candidates.size())
                .candidates(candidates)
                .build();
    }
}
