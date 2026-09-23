package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.GeoBackfillResponse;
import com.ds.goroute.dto.response.GeoDatasetResponse;
import com.ds.goroute.dto.response.GeoProvinceResponse;
import com.ds.goroute.dto.response.GeoWardResponse;
import com.ds.goroute.entity.Ward;
import com.ds.goroute.mapper.GeoMapper;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.WardRepository;
import com.ds.goroute.service.GeoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeoServiceImpl implements GeoService {

    private static final int CURRENT_PROVINCE_COUNT = 34;

    private final WardRepository wardRepository;
    private final PlaceRepository placeRepository;
    private final GeoMapper geoMapper;
    private final GeoJsonStore geoJsonStore;

    @Override
    @Transactional(readOnly = true)
    public Optional<Ward> resolveWard(BigDecimal latitude, BigDecimal longitude) {
        return wardRepository.findCovering(latitude, longitude);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ward> findWard(String code) {
        return code == null || code.isBlank() ? Optional.empty() : wardRepository.findByCode(code.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ward> findWards(Collection<String> codes) {
        return wardRepository.findByCodes(codes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GeoProvinceResponse> provinces() {
        return geoMapper.findProvinces();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GeoWardResponse> wardsOf(String provinceCode) {
        return wardRepository.findByProvince(provinceCode).stream().map(GeoWardResponse::from).toList();
    }

    @Override
    public Optional<String> wardsGeoJson(String provinceCode) {
        return geoJsonStore.wardsGeoJson(provinceCode);
    }

    @Override
    public Optional<String> provincesGeoJson() {
        return geoJsonStore.provincesGeoJson();
    }

    @Override
    @Transactional(readOnly = true)
    public GeoDatasetResponse dataset() {
        GeoDatasetResponse dataset = geoMapper.findLatestDataset();
        if (dataset == null) {
            dataset = GeoDatasetResponse.builder().build();
        }
        dataset.setProvincesActive(geoMapper.findProvinces().stream().count());
        dataset.setWardsActive(wardRepository.countActive());
        dataset.setPlacesWithoutWard(geoMapper.countPlacesWithoutWard());
        dataset.setCheckinsWithoutWard(geoMapper.countCheckinsWithoutWard());
        return dataset;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> datasetVersion() {
        return Optional.ofNullable(geoMapper.findLatestDataset()).map(GeoDatasetResponse::getVersion);
    }

    @Override
    @Transactional
    public void assignPlaceWard(UUID placeId) {
        placeRepository.assignWard(placeId);
    }

    /**
     * Order matters. Aliases are merged before the retired rows go, FK tables are
     * re-pointed before the rows they point at are deleted, and wards are assigned last
     * so the province a place ends up with comes from its boundary, not from the remap.
     */
    @Override
    @Transactional
    public GeoBackfillResponse backfill(boolean reassignExisting) {
        long started = System.currentTimeMillis();
        GeoDatasetResponse dataset = geoMapper.findLatestDataset();
        if (dataset == null || wardRepository.countActive() == 0) {
            throw new IllegalStateException(
                    "No boundary dataset loaded: run scripts/geo/build_geo_sql.mjs and psql first");
        }
        Map<String, Long> steps = new LinkedHashMap<>();

        List<Map<String, Object>> retired = geoMapper.findRetiredProvinces();
        long merged = 0;
        for (Map<String, Object> row : retired) {
            String aliases = row.get("aliases") == null ? "[]" : row.get("aliases").toString();
            merged += geoMapper.mergeProvinceAliases(row.get("new_code").toString(), aliases);
        }
        steps.put("provinceAliasesMerged", merged);
        step(steps, "passportDefinitionProvincesRemapped", geoMapper::remapPassportDefinitionProvincesInsert);
        step(steps, "passportDefinitionProvincesRetiredRemoved", geoMapper::remapPassportDefinitionProvincesDelete);
        step(steps, "passportWishesRemapped", geoMapper::remapPassportProvinceWishesInsert);
        step(steps, "passportWishesRetiredRemoved", geoMapper::remapPassportProvinceWishesDelete);
        step(steps, "passportEventsProvinceRemapped", geoMapper::remapPassportEventsProvince);
        step(steps, "placesProvinceRemapped", geoMapper::remapPlacesProvince);
        step(steps, "checkinsProvinceRemapped", geoMapper::remapCheckinsProvince);
        step(steps, "locationImagesProvinceRemapped", geoMapper::remapLocationImagesProvince);
        step(steps, "provincesRetired", geoMapper::deleteRetiredProvinces);
        step(steps, "totalProvincesConfigUpdated", () -> geoMapper.updateTotalProvincesConfig(CURRENT_PROVINCE_COUNT));

        assignWards(steps, reassignExisting);

        geoMapper.markBackfilled(dataset.getVersion());
        geoJsonStore.evictAll();

        GeoBackfillResponse response = GeoBackfillResponse.builder()
                .datasetVersion(dataset.getVersion())
                .durationMs(System.currentTimeMillis() - started)
                .steps(steps)
                .placesWithoutWard(geoMapper.countPlacesWithoutWard())
                .checkinsWithoutWard(geoMapper.countCheckinsWithoutWard())
                .build();
        log.info("Geo backfill for {} finished in {} ms: {}", dataset.getVersion(), response.getDurationMs(), steps);
        return response;
    }

    /**
     * Fills the ward on every table that derives one. Order matters once: hotels inherit
     * from their Place, so places go first.
     */
    private void assignWards(Map<String, Long> steps, boolean force) {
        step(steps, "placesWardAssigned", () -> geoMapper.backfillPlacesWard(force));
        step(steps, "checkinsWardAssigned", () -> geoMapper.backfillCheckinsWard(force));
        step(steps, "passportEventsWardFromCheckin", geoMapper::backfillPassportEventsWardFromCheckins);
        step(steps, "passportEventsWardFromPoint", geoMapper::backfillPassportEventsWardFromPoint);
        step(steps, "activityBookingsWardAssigned", () -> geoMapper.backfillActivityBookingsWard(force));
        step(steps, "hotelsWardFromPlace", () -> geoMapper.backfillHotelsWardFromPlace(force));
        step(steps, "tripsWardAssigned", () -> geoMapper.backfillTripsWard(force));
        step(steps, "tripDestinationsWardAssigned", () -> geoMapper.backfillTripDestinationsWard(force));
    }

    /**
     * Just the ward assignment, for rows created since the last pass. Runs on the hourly
     * job beside the tourist-area backfill: places arrive from the scraper and partners
     * add products continuously, so without it "which ward is this in" would silently be
     * null for everything recent. Never touches a row that already has a ward, so an
     * operator's correction survives.
     */
    @Override
    @Transactional
    public Map<String, Long> assignPendingWards() {
        if (wardRepository.countActive() == 0) {
            return Map.of();
        }
        Map<String, Long> steps = new LinkedHashMap<>();
        assignWards(steps, false);
        return steps;
    }

    private static void step(Map<String, Long> steps, String name, IntSupplier statement) {
        steps.put(name, (long) statement.getAsInt());
    }
}
