package com.ds.goroute.service.impl;

import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.PlaceSearchIndexService;
import com.ds.goroute.service.PlaceUsageStatsService;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recounts place usage after the fact. Marks are kept in memory: the backend runs as one
 * instance by design, and a mark lost to a restart is repaired by the nightly
 * {@link #recountAll()}.
 */
@Service
@Slf4j
public class PlaceUsageStatsServiceImpl implements PlaceUsageStatsService {

    /** Places recounted per statement, so an IN list stays small. */
    static final int RECOUNT_BATCH_SIZE = 500;

    private final PlaceRepository placeRepository;
    private final PlaceSearchIndexService placeSearchIndexService;
    private final TransactionTemplate transaction;
    private final Set<String> marked = ConcurrentHashMap.newKeySet();

    public PlaceUsageStatsServiceImpl(PlaceRepository placeRepository,
                                      PlaceSearchIndexService placeSearchIndexService,
                                      PlatformTransactionManager transactionManager) {
        this.placeRepository = placeRepository;
        this.placeSearchIndexService = placeSearchIndexService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void markChanged(Collection<String> placeReferences) {
        if (placeReferences == null) {
            return;
        }
        for (String reference : placeReferences) {
            if (reference != null && !reference.isBlank()) {
                marked.add(reference.trim());
            }
        }
    }

    @Override
    public int recountMarked() {
        if (marked.isEmpty()) {
            return 0;
        }
        List<String> references = new ArrayList<>(marked);
        marked.removeAll(references);
        try {
            List<UUID> placeIds = resolve(references);
            int changed = 0;
            for (List<UUID> batch : Lists.partition(placeIds, RECOUNT_BATCH_SIZE)) {
                changed += recount(batch);
            }
            return changed;
        } catch (RuntimeException failure) {
            // Tried again on the next run rather than lost.
            marked.addAll(references);
            throw failure;
        }
    }

    @Override
    public int recountAll() {
        return recount(null);
    }

    /** Recounts {@code placeIds} (every place when null) and reindexes the ones that changed. */
    private int recount(List<UUID> placeIds) {
        List<UUID> changed = transaction.execute(status -> placeRepository.recomputeUsageCounts(placeIds));
        if (changed == null || changed.isEmpty()) {
            return 0;
        }
        // The index ranks map tiles and popular searches by usage; it is rewritten from the row.
        for (UUID placeId : changed) {
            placeRepository.findSearchIndexRow(placeId).ifPresent(placeSearchIndexService::indexPlace);
        }
        return changed.size();
    }

    /** Catalogue ids of the places the references name; unknown references drop out. */
    private List<UUID> resolve(List<String> references) {
        // A UUID-shaped reference is looked up as a Google id as well: harmless if it is not one.
        Set<UUID> found = new LinkedHashSet<>();
        for (List<String> batch : Lists.partition(references, RECOUNT_BATCH_SIZE)) {
            List<UUID> ids = batch.stream().map(PlaceUsageStatsServiceImpl::parseUuid)
                    .filter(Objects::nonNull).toList();
            found.addAll(placeRepository.findIdsByReferences(ids, batch));
        }
        return List.copyOf(found);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
