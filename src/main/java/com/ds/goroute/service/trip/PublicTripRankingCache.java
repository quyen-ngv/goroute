package com.ds.goroute.service.trip;

import com.ds.goroute.dto.PublicTripSearchCriteria;
import com.ds.goroute.entity.TripRankRow;
import com.ds.goroute.repository.TripRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

/**
 * The top of each public trip search ranking, shared by every viewer.
 *
 * <p>The ranking itself never depends on who asks: per-viewer parts (dropping the viewer's
 * own trips, their vote, "is own trip") are applied by the caller on top of the cached ids.
 * So the Feed's "everywhere" tab is one entry for the whole app, and a radius search is one
 * entry per ~1 km cell (see {@link PublicTripSearchCriteria}).
 *
 * <p>Entries live {@link #TTL}: the recommended score moves with the clock, and a newly
 * public trip appears within that time without any eviction. Trip edits and deletions evict
 * everything after their transaction commits, and readers re-check visibility when they load
 * the trips, so a stale entry can only reorder results, never leak one. Local to the single
 * backend instance, like every Caffeine cache here.
 */
@Component
public class PublicTripRankingCache {

    /** Ranked positions held per search: ten pages of 20. Deeper pages go to the database. */
    public static final int WINDOW = 200;

    static final Duration TTL = Duration.ofMinutes(2);
    private static final long MAX_ENTRIES = 1_000;

    private final TripRepository tripRepository;
    private final Cache<PublicTripSearchCriteria, List<TripRankRow>> cache;

    @Autowired
    public PublicTripRankingCache(TripRepository tripRepository) {
        this(tripRepository, Ticker.systemTicker());
    }

    PublicTripRankingCache(TripRepository tripRepository, Ticker ticker) {
        this.tripRepository = tripRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_ENTRIES)
                .ticker(ticker)
                .build();
    }

    /**
     * The first {@link #WINDOW} positions of the ranking for {@code criteria}, with no viewer
     * excluded. Fewer than {@link #WINDOW} means this is the whole ranking.
     */
    public List<TripRankRow> window(PublicTripSearchCriteria criteria) {
        return cache.get(criteria,
                key -> List.copyOf(tripRepository.searchPublicTripRanking(key, null, WINDOW, 0)));
    }

    /** Drops one ranking, so the next read recomputes it. */
    public void evict(PublicTripSearchCriteria criteria) {
        cache.invalidate(criteria);
    }

    /**
     * Drops every ranking once the current transaction commits (at once when there is none),
     * so a reader cannot re-cache the state from before the change.
     */
    public void invalidateAllAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.invalidateAll();
                }
            });
        } else {
            cache.invalidateAll();
        }
    }
}
