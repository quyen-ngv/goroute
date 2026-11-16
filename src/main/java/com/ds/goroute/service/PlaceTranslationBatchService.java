package com.ds.goroute.service;

import com.ds.goroute.dto.request.PlaceTranslationBatchRequest;
import com.ds.goroute.dto.response.PlaceTranslationBatchResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.type.TranslationLocale;
import com.ds.goroute.type.TranslationSource;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Writes localized place names in bulk ({@code POST /v1/api/admin/places/translations/batch}),
 * keyed by Google place id. A name is written under the rank rule of
 * {@link PlaceTranslationService#syncTranslations}: a MANUAL name is never replaced by a
 * GOOGLE or WIKIDATA one. The changed places are reindexed once each, after the commit.
 */
@Service
@Slf4j
public class PlaceTranslationBatchService {

    static final int MAX_NAME_LENGTH = 500;
    private static final int LOOKUP_BATCH_SIZE = 500;
    private static final Set<TranslationSource> ACCEPTED_SOURCES = Set.of(
            TranslationSource.GOOGLE, TranslationSource.WIKIDATA, TranslationSource.MANUAL);

    private final PlaceRepository placeRepository;
    private final PlaceTranslationService placeTranslationService;
    private final PlaceSearchIndexService placeSearchIndexService;
    private final PlaceService placeService;
    private final TransactionTemplate transaction;

    public PlaceTranslationBatchService(PlaceRepository placeRepository,
                                        PlaceTranslationService placeTranslationService,
                                        PlaceSearchIndexService placeSearchIndexService,
                                        PlaceService placeService,
                                        PlatformTransactionManager transactionManager) {
        this.placeRepository = placeRepository;
        this.placeTranslationService = placeTranslationService;
        this.placeSearchIndexService = placeSearchIndexService;
        this.placeService = placeService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public PlaceTranslationBatchResponse writeNames(List<PlaceTranslationBatchRequest.Item> items) {
        List<Row> rows = new ArrayList<>(items.size());
        int skippedInvalid = 0;
        for (PlaceTranslationBatchRequest.Item item : items) {
            Optional<Row> row = validate(item);
            if (row.isPresent()) {
                rows.add(row.get());
            } else {
                skippedInvalid++;
            }
        }

        Map<String, UUID> placeIds = resolvePlaces(rows.stream().map(Row::googlePlaceId).distinct().toList());
        Set<String> notFound = new LinkedHashSet<>();
        List<Row> found = new ArrayList<>(rows.size());
        for (Row row : rows) {
            if (placeIds.containsKey(row.googlePlaceId())) {
                found.add(row);
            } else {
                notFound.add(row.googlePlaceId());
            }
        }

        Counts counts = found.isEmpty() ? new Counts() : transaction.execute(status -> write(found, placeIds));
        for (UUID placeId : counts.changedPlaces) {
            placeRepository.findSearchIndexRow(placeId).ifPresent(placeSearchIndexService::indexPlace);
        }
        if (!counts.changedPlaces.isEmpty()) {
            placeService.evictSearchCache();
        }
        log.info("Place name batch: {} rows, {} inserted, {} updated, {} kept MANUAL, {} invalid, {} places not found",
                items.size(), counts.inserted, counts.updated, counts.skippedManual, skippedInvalid, notFound.size());
        return PlaceTranslationBatchResponse.builder()
                .inserted(counts.inserted)
                .updated(counts.updated)
                .skippedManual(counts.skippedManual)
                .skippedInvalid(skippedInvalid)
                .notFound(new ArrayList<>(notFound))
                .build();
    }

    private Counts write(List<Row> rows, Map<String, UUID> placeIds) {
        Set<String> existing = new HashSet<>();
        placeTranslationService.findByPlaceIds(placeIds.values()).forEach((placeId, translations) -> {
            for (PlaceTranslation translation : translations) {
                if (translation.getLocale() != null) {
                    existing.add(key(placeId, translation.getLocale()));
                }
            }
        });
        Counts counts = new Counts();
        for (Row row : rows) {
            UUID placeId = placeIds.get(row.googlePlaceId());
            boolean written = placeTranslationService.writeName(placeId, row.locale(), row.name(), row.source());
            if (!written) {
                counts.skippedManual++;
                continue;
            }
            if (existing.add(key(placeId, row.locale()))) {
                counts.inserted++;
            } else {
                counts.updated++;
            }
            counts.changedPlaces.add(placeId);
        }
        return counts;
    }

    /** Google place id → catalogue id: by {@code place_id} first, then by {@code cid}. */
    private Map<String, UUID> resolvePlaces(List<String> googlePlaceIds) {
        Map<String, UUID> result = new HashMap<>();
        for (List<String> batch : Lists.partition(googlePlaceIds, LOOKUP_BATCH_SIZE)) {
            for (Place place : placeRepository.findReferencesByPlaceIds(batch)) {
                result.putIfAbsent(place.getPlaceId(), place.getId());
            }
        }
        List<String> missing = googlePlaceIds.stream().filter(id -> !result.containsKey(id)).toList();
        for (List<String> batch : Lists.partition(missing, LOOKUP_BATCH_SIZE)) {
            for (Place place : placeRepository.findReferencesByCids(batch)) {
                result.putIfAbsent(place.getCid(), place.getId());
            }
        }
        return result;
    }

    private static Optional<Row> validate(PlaceTranslationBatchRequest.Item item) {
        if (item == null || item.getGooglePlaceId() == null || item.getGooglePlaceId().isBlank()
                || item.getName() == null || item.getSource() == null) {
            return Optional.empty();
        }
        String name = item.getName().trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return Optional.empty();
        }
        Optional<TranslationLocale> locale = TranslationLocale.fromCode(item.getLocale());
        Optional<TranslationSource> source = parseSource(item.getSource());
        if (locale.isEmpty() || source.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Row(item.getGooglePlaceId().trim(), locale.get(), name, source.get()));
    }

    private static Optional<TranslationSource> parseSource(String value) {
        try {
            TranslationSource source = TranslationSource.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return ACCEPTED_SOURCES.contains(source) ? Optional.of(source) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String key(UUID placeId, TranslationLocale locale) {
        return placeId + "|" + locale.code();
    }

    private record Row(String googlePlaceId, TranslationLocale locale, String name, TranslationSource source) {
    }

    private static final class Counts {
        private int inserted;
        private int updated;
        private int skippedManual;
        private final Set<UUID> changedPlaces = new LinkedHashSet<>();
    }
}
