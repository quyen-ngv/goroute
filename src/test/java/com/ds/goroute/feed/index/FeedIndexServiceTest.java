package com.ds.goroute.feed.index;

import com.ds.goroute.feed.FeedTestSupport;
import com.ds.goroute.feed.FeedWeightsProvider;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedKey;
import com.ds.goroute.mapper.FeedItemMapper;
import com.ds.goroute.mapper.FeedViewerMapper;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedIndexServiceTest {

    private final FeedItemMapper items = mock(FeedItemMapper.class);
    private final FeedViewerMapper viewers = mock(FeedViewerMapper.class);
    private FeedIndexService service;

    @BeforeEach
    void setUp() {
        BusinessConfigService config = mock(BusinessConfigService.class);
        when(config.getText(BusinessConfigKey.FEED_RANKING_WEIGHTS)).thenReturn("{}");
        service = new FeedIndexService(items, viewers, new FeedWeightsProvider(config, new ObjectMapper()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theQueueIsIndexedByKindAndThenCleared() {
        UUID checkin = UUID.randomUUID();
        UUID review = UUID.randomUUID();
        UUID trip = UUID.randomUUID();
        List<FeedKey> queued = List.of(
                new FeedKey("CHECKIN", checkin, LocalDateTime.now()),
                new FeedKey("REVIEW", review, LocalDateTime.now()),
                new FeedKey("TRIP", trip, LocalDateTime.now()));
        when(items.selectQueued(anyInt())).thenReturn(queued, List.of());

        int indexed = service.drainQueue();

        assertThat(indexed).isEqualTo(3);
        verify(items).upsertCheckins(List.of(checkin));
        verify(items).upsertReviews(List.of(review));
        verify(items).upsertTrips(List.of(trip));
        verify(items).deleteOrphans("REVIEW", List.of(review));
        var order = inOrder(items);
        order.verify(items).upsertCheckins(any());
        order.verify(items).deleteQueued(queued);
    }

    @Test
    void anEmptyQueueDoesNothing() {
        when(items.selectQueued(anyInt())).thenReturn(List.of());

        assertThat(service.drainQueue()).isZero();
        verify(items, never()).deleteQueued(any());
    }

    @Test
    void aFullBatchIsFollowedByAnother() {
        List<FeedKey> full = new ArrayList<>();
        for (int i = 0; i < FeedIndexService.QUEUE_BATCH; i++) {
            full.add(new FeedKey("CHECKIN", UUID.randomUUID(), LocalDateTime.now()));
        }
        List<FeedKey> rest = List.of(new FeedKey("TRIP", UUID.randomUUID(), LocalDateTime.now()));
        when(items.selectQueued(anyInt())).thenReturn(full, rest);

        assertThat(service.drainQueue()).isEqualTo(FeedIndexService.QUEUE_BATCH + 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dirtyRowsAreRecountedThenScored() {
        FeedItemRow trip = FeedTestSupport.trip(UUID.randomUUID(), LocalDateTime.now());
        trip.setStopCount(1);
        FeedItemRow checkin = FeedTestSupport.checkin(UUID.randomUUID(), LocalDateTime.now());
        checkin.setLikeCount(5);
        checkin.setHasPhoto(true);
        List<FeedKey> dirty = List.of(trip.key(), checkin.key());
        when(items.selectDirty(anyInt())).thenReturn(dirty);
        when(items.selectByKeys(dirty)).thenReturn(List.of(trip, checkin));

        int rescored = service.refreshStats();

        assertThat(rescored).isEqualTo(2);
        var order = inOrder(items);
        order.verify(items).markImpressionsDirty(any());
        order.verify(items).refreshCounts(dirty);
        ArgumentCaptor<Collection<FeedItemRow>> written = ArgumentCaptor.forClass(Collection.class);
        order.verify(items).updateScores(written.capture());
        List<FeedItemRow> rows = new ArrayList<>(written.getValue());
        assertThat(rows.get(0).isRecommendOk()).as("a one-stop trip without photos").isFalse();
        assertThat(rows.get(1).isRecommendOk()).isTrue();
        assertThat(rows.get(1).getEngagementScore()).isGreaterThan(rows.get(0).getEngagementScore());
    }

    @Test
    void theAffinityRebuildReplacesTheTableWithTheConfiguredWeights() {
        when(viewers.insertAffinity(any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyInt(), anyDouble(), anyInt())).thenReturn(42);

        assertThat(service.rebuildAffinity()).isEqualTo(42);

        var order = inOrder(viewers);
        order.verify(viewers).deleteAllAffinity();
        order.verify(viewers).insertAffinity(any(), eq(30.0), eq(1.0), eq(3.0), eq(4.0), eq(0.5), eq(0.3),
                eq(5000), eq(0.5), eq(300));
    }

    @Test
    void reconcileQueuesTheLastSixtyDaysAndStaleRows() {
        service.reconcile();

        ArgumentCaptor<LocalDateTime> recent = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> stale = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(items).enqueueForReconcile(recent.capture(), stale.capture());
        assertThat(recent.getValue()).isBefore(LocalDateTime.now().minusDays(59));
        assertThat(stale.getValue()).isBefore(LocalDateTime.now().minusDays(6));
    }
}
