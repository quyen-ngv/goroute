package com.ds.goroute.service.trip;

import com.ds.goroute.dto.PublicTripSearchCriteria;
import com.ds.goroute.entity.TripRankRow;
import com.ds.goroute.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicTripRankingCacheTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final AtomicLong nanos = new AtomicLong();
    private PublicTripRankingCache cache;

    private final PublicTripSearchCriteria everywhere =
            PublicTripSearchCriteria.of(null, null, null, null, null, true, null, true, true);

    @BeforeEach
    void setUp() {
        cache = new PublicTripRankingCache(tripRepository, nanos::get);
        when(tripRepository.searchPublicTripRanking(any(), isNull(), eq(PublicTripRankingCache.WINDOW), eq(0)))
                .thenReturn(List.of(new TripRankRow(UUID.randomUUID(), UUID.randomUUID())));
    }

    @Test
    void oneQueryServesEveryReaderUntilTheEntryExpires() {
        cache.window(everywhere);
        cache.window(everywhere);
        verify(tripRepository, times(1)).searchPublicTripRanking(everywhere, null, PublicTripRankingCache.WINDOW, 0);

        nanos.addAndGet(PublicTripRankingCache.TTL.plus(Duration.ofSeconds(1)).toNanos());
        cache.window(everywhere);

        verify(tripRepository, times(2)).searchPublicTripRanking(everywhere, null, PublicTripRankingCache.WINDOW, 0);
    }

    @Test
    void theRankingIsAskedForWithoutAnyViewer() {
        assertThat(cache.window(everywhere)).hasSize(1);

        verify(tripRepository).searchPublicTripRanking(everywhere, null, PublicTripRankingCache.WINDOW, 0);
    }

    @Test
    void evictAndInvalidateForceARecount() {
        cache.window(everywhere);
        cache.evict(everywhere);
        cache.window(everywhere);
        // No transaction here, so invalidation is immediate.
        cache.invalidateAllAfterCommit();
        cache.window(everywhere);

        verify(tripRepository, times(3)).searchPublicTripRanking(everywhere, null, PublicTripRankingCache.WINDOW, 0);
    }
}
