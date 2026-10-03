package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.InternalPlaceSearchRequest;
import com.ds.goroute.dto.response.InternalPlaceSearchResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.InternalPlaceSearchService;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InternalPlaceSearchServiceImpl implements InternalPlaceSearchService {

    private final PlaceLookupProviders placeLookupProviders;

    @Override
    public boolean isAvailable() {
        return google().isAvailable();
    }

    @Override
    public InternalPlaceSearchResponse search(InternalPlaceSearchRequest request) {
        PlaceLookupProvider google = google();
        if (!google.isAvailable()) {
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR,
                    "Google place search is not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String query = request.getQuery().trim();
        List<PlaceHit> hits;
        try {
            hits = google.searchText(PlaceLookupQuery.text(query, request.getLimit()));
        } catch (PlaceLookupException e) {
            log.warn("Google place search failed for the scrape worker: {}", e.getMessage());
            throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR,
                    "Google place search failed", HttpStatus.BAD_GATEWAY);
        }
        List<InternalPlaceSearchResponse.Candidate> candidates = new ArrayList<>();
        for (PlaceHit hit : hits) {
            if (candidates.size() >= request.getLimit()) {
                break;
            }
            candidates.add(InternalPlaceSearchResponse.Candidate.builder()
                    .rank(candidates.size() + 1)
                    .title(hit.name())
                    .googleMapsLink(hit.googleMapsUri())
                    .resolvedUrl(hit.googleMapsUri())
                    .placeId(hit.providerPlaceId())
                    .cid(cid(hit.googleMapsUri()))
                    .latitude(hit.latitude())
                    .longitude(hit.longitude())
                    .address(hit.address())
                    .category(hit.primaryType())
                    .build());
        }
        return InternalPlaceSearchResponse.builder()
                .success(true)
                .query(query)
                .provider(PlaceLookupProviderType.GOOGLE.name())
                .count(candidates.size())
                .candidates(candidates)
                .build();
    }

    private PlaceLookupProvider google() {
        return placeLookupProviders.get(PlaceLookupProviderType.GOOGLE);
    }

    /** Google's Maps URI for a place has the form {@code https://maps.google.com/?cid=123...}. */
    private String cid(String googleMapsUri) {
        if (googleMapsUri == null || googleMapsUri.isBlank()) {
            return null;
        }
        try {
            String cid = UriComponentsBuilder.fromUriString(googleMapsUri).build().getQueryParams().getFirst("cid");
            return cid == null || cid.isBlank() ? null : cid;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
