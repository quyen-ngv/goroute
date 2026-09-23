package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.LocationAreaCandidateRow;
import com.ds.goroute.dto.response.LocationAreaAutoMapResponse;
import com.ds.goroute.dto.response.LocationAreaCoverageResponse;
import com.ds.goroute.entity.LocationImage;
import com.ds.goroute.enums.LocationAreaTarget;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.LocationAreaMapper;
import com.ds.goroute.repository.LocationImageRepository;
import com.ds.goroute.service.LocationAreaService;
import com.ds.goroute.utils.LocationAreaMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationAreaServiceImpl implements LocationAreaService {

    /**
     * Ceiling on rows the name fallback loads per target per run. The coordinate pass
     * runs entirely in the database; only its leftovers come into memory, and a bound
     * keeps one run from loading an unbounded catalogue after a bad import.
     */
    private static final int NAME_FALLBACK_LIMIT = 20_000;

    /** Coordinates are only meaningful for these; the rest derive their area otherwise. */
    private static final List<LocationAreaTarget> COORDINATE_TARGETS = List.of(
            LocationAreaTarget.PLACE,
            LocationAreaTarget.TRIP,
            LocationAreaTarget.TRIP_DESTINATION,
            LocationAreaTarget.ACTIVITY);

    private final LocationAreaMapper locationAreaMapper;
    private final LocationImageRepository locationImageRepository;

    @Override
    @Transactional(readOnly = true)
    public LocationAreaCoverageResponse coverage() {
        List<LocationAreaCoverageResponse.TargetCoverage> targets = new ArrayList<>();
        for (LocationAreaTarget target : LocationAreaTarget.values()) {
            long total = locationAreaMapper.countTotal(target);
            long mapped = locationAreaMapper.countMapped(target);
            targets.add(LocationAreaCoverageResponse.TargetCoverage.builder()
                    .target(target.name())
                    .label(target.getLabel())
                    .total(total)
                    .mapped(mapped)
                    .unmapped(total - mapped)
                    .build());
        }
        return LocationAreaCoverageResponse.builder().targets(targets).build();
    }

    @Override
    @Transactional
    public LocationAreaAutoMapResponse autoMap(boolean dryRun) {
        List<LocationImage> areas = locationImageRepository.findAll();
        Map<LocationAreaTarget, int[]> counters = new EnumMap<>(LocationAreaTarget.class);
        for (LocationAreaTarget target : LocationAreaTarget.values()) {
            counters.put(target, new int[]{0, 0});
        }

        // 1. Coordinates. One statement per target, resolved entirely in the database.
        for (LocationAreaTarget target : COORDINATE_TARGETS) {
            counters.get(target)[0] = locationAreaMapper.assignByCoordinates(target);
        }

        // 2. Food rows have no coordinates; they key off the shared city slug vocabulary.
        counters.get(LocationAreaTarget.FOOD_CITY_SCORE)[0] =
                locationAreaMapper.assignFoodScoresByCitySlug();

        // 3. Name fallback for whatever the first two passes left behind.
        for (LocationAreaTarget target : LocationAreaTarget.values()) {
            if (!target.supportsNameFallback()) {
                continue;
            }
            counters.get(target)[1] = assignByName(target, areas);
        }

        // 4. Hotels last: they copy the area of their Place, which is only final once the
        //    place passes above have run.
        counters.get(LocationAreaTarget.HOTEL)[0] = locationAreaMapper.assignHotelsFromPlace();

        locationAreaMapper.syncActivityAreaLinks();

        List<LocationAreaAutoMapResponse.TargetResult> results = new ArrayList<>();
        for (LocationAreaTarget target : LocationAreaTarget.values()) {
            int[] counts = counters.get(target);
            results.add(LocationAreaAutoMapResponse.TargetResult.builder()
                    .target(target.name())
                    .label(target.getLabel())
                    .byCoordinates(counts[0])
                    .byName(counts[1])
                    .stillUnmapped(locationAreaMapper.countTotal(target) - locationAreaMapper.countMapped(target))
                    .build());
        }

        if (dryRun) {
            // Counted against the real statements, then discarded. An estimate built from
            // a second set of queries could disagree with what a real run would do.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.info("Location area auto-map dry run completed and rolled back");
        } else {
            log.info("Location area auto-map applied: {}", results);
        }

        return LocationAreaAutoMapResponse.builder().dryRun(dryRun).targets(results).build();
    }

    /**
     * Resolves the area from the row's own address text. Runs row by row rather than in
     * SQL because the accent, spacing and token rules live in
     * {@link LocationAreaMatcher} and must stay identical to what the rest of the
     * application matches with.
     */
    private int assignByName(LocationAreaTarget target, List<LocationImage> areas) {
        List<LocationAreaCandidateRow> candidates =
                locationAreaMapper.findUnmapped(target, NAME_FALLBACK_LIMIT);
        int assigned = 0;
        for (LocationAreaCandidateRow candidate : candidates) {
            Optional<LocationImage> area =
                    LocationAreaMatcher.matchByText(areas, List.of(safe(candidate.getMatchText())));
            if (area.isEmpty()) {
                continue;
            }
            assigned += locationAreaMapper.assignArea(target, candidate.getId(), area.get().getId());
        }
        return assigned;
    }

    @Override
    @Transactional
    public void assignNewPlace(UUID placeId) {
        if (locationAreaMapper.assignPlaceByCoordinates(placeId) > 0) {
            return;
        }
        LocationAreaCandidateRow candidate =
                locationAreaMapper.findUnmappedById(LocationAreaTarget.PLACE, placeId);
        if (candidate == null) {
            return;
        }
        LocationAreaMatcher.matchByText(locationImageRepository.findAll(), List.of(safe(candidate.getMatchText())))
                .ifPresent(area -> locationAreaMapper.assignArea(LocationAreaTarget.PLACE, placeId, area.getId()));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    @Transactional
    public void assign(LocationAreaTarget target, UUID id, UUID locationImageId) {
        if (locationImageId != null) {
            locationImageRepository.findById(locationImageId)
                    .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Location not found"));
        }
        if (locationAreaMapper.setArea(target, id, locationImageId) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, target.getLabel() + " not found");
        }
    }
}
