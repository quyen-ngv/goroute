package com.ds.goroute.service;

import com.ds.goroute.dto.response.GeoBackfillResponse;
import com.ds.goroute.dto.response.GeoDatasetResponse;
import com.ds.goroute.dto.response.GeoProvinceResponse;
import com.ds.goroute.dto.response.GeoWardResponse;
import com.ds.goroute.entity.Ward;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Administrative boundaries: the 34 provinces and their wards, and the one question
 * everything else asks of them -- which ward is this point in.
 */
public interface GeoService {

    /** The ward covering the point; empty outside every boundary or before the dataset is loaded. */
    Optional<Ward> resolveWard(BigDecimal latitude, BigDecimal longitude);

    Optional<Ward> findWard(String code);

    List<Ward> findWards(Collection<String> codes);

    List<GeoProvinceResponse> provinces();

    List<GeoWardResponse> wardsOf(String provinceCode);

    /** Simplified ward polygons of one province as a GeoJSON FeatureCollection; empty for an unknown province. */
    Optional<String> wardsGeoJson(String provinceCode);

    /** Simplified outlines of every province; empty before the dataset is loaded. */
    Optional<String> provincesGeoJson();

    GeoDatasetResponse dataset();

    /** Just the loaded release's version -- the ETag of every GeoJSON layer -- without the live counts. */
    Optional<String> datasetVersion();

    /** Assigns the ward (and province) a freshly written place sits in. */
    void assignPlaceWard(UUID placeId);

    /**
     * Moves the schema onto the loaded dataset: retires merged province codes everywhere
     * they are referenced, then assigns a ward to every place, check-in and passport event.
     * Idempotent; safe to run again after a new dataset release.
     */
    GeoBackfillResponse backfill(boolean reassignExisting);

    /**
     * Assigns the ward on rows that do not have one yet, across every table that derives
     * one. Empty when no boundary dataset is loaded. Cheap and idempotent: meant for the
     * hourly job, not for an operator.
     */
    java.util.Map<String, Long> assignPendingWards();
}
