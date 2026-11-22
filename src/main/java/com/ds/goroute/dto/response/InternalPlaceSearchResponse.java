package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Same field names as the scrape worker's old Google Maps search result, so the worker's
 * matching code and every backend reader of {@code mapSearch.candidates[]} keep working.
 * {@code provider} is {@code CATALOG} or {@code MAPBOX}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalPlaceSearchResponse {
    private boolean success;
    private String query;
    private String provider;
    private int count;
    private List<Candidate> candidates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Candidate {
        private int rank;
        private String title;
        private String googleMapsLink;
        private String resolvedUrl;
        private String placeId;
        private String cid;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String address;
        private String category;
        /** Our {@code places.id} when the hit is a catalogue place. */
        private String catalogPlaceId;
        /** Mapbox's {@code mapbox_id} when the hit came from Mapbox. */
        private String mapboxId;
    }
}
