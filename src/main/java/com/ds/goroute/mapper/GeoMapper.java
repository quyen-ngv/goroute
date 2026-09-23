package com.ds.goroute.mapper;

import com.ds.goroute.dto.response.GeoDatasetResponse;
import com.ds.goroute.dto.response.GeoProvinceResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Province geometry, dataset bookkeeping and the cross-table backfill that moves the
 * schema from the 63-province model to the 34-province one. Statements that touch a
 * single owned table (wards, places, check-ins) live with that table's mapper; only the
 * remap, which by nature spans tables, lives here.
 */
@Mapper
public interface GeoMapper {

    List<GeoProvinceResponse> findProvinces();

    /** FeatureCollection of every province outline at display resolution, or null before the dataset is loaded. */
    String provincesDisplayGeoJson();

    GeoDatasetResponse findLatestDataset();

    int markBackfilled(@Param("version") String version);

    // --- province remap (63 -> 34) ------------------------------------------------

    /** Provinces that still exist as rows but whose code was retired by the merger. */
    List<Map<String, Object>> findRetiredProvinces();

    int mergeProvinceAliases(@Param("code") String code, @Param("aliases") String aliasesJson);

    int remapPassportDefinitionProvincesInsert();

    int remapPassportDefinitionProvincesDelete();

    int remapPassportProvinceWishesInsert();

    int remapPassportProvinceWishesDelete();

    int remapPassportEventsProvince();

    int remapPlacesProvince();

    int remapCheckinsProvince();

    int remapLocationImagesProvince();

    int deleteRetiredProvinces();

    int updateTotalProvincesConfig(@Param("total") int total);

    // --- ward assignment ---------------------------------------------------------

    int backfillPlacesWard(@Param("force") boolean force);

    int backfillCheckinsWard(@Param("force") boolean force);

    int backfillPassportEventsWardFromCheckins();

    int backfillPassportEventsWardFromPoint();

    int backfillActivityBookingsWard(@Param("force") boolean force);

    int backfillHotelsWardFromPlace(@Param("force") boolean force);

    int backfillTripsWard(@Param("force") boolean force);

    int backfillTripDestinationsWard(@Param("force") boolean force);

    long countPlacesWithoutWard();

    long countCheckinsWithoutWard();
}
