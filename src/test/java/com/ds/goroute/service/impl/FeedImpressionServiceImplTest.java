package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.FeedImpressionRequest;
import com.ds.goroute.mapper.FeedImpressionMapper;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.FeedItemType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedImpressionServiceImplTest {

    private final FeedImpressionMapper mapper = mock(FeedImpressionMapper.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final FeedImpressionServiceImpl service = new FeedImpressionServiceImpl(mapper, config);
    private final UUID viewer = UUID.randomUUID();

    private static FeedImpressionRequest.Item item(FeedItemType type, UUID id, int dwell, boolean opened) {
        return FeedImpressionRequest.Item.builder().type(type).id(id).dwellMs(dwell).opened(opened).build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSamePostTwiceInABatchIsOneRowWithTheLongerDwellAndOpened() {
        UUID post = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        service.record(viewer, List.of(
                item(FeedItemType.CHECKIN, post, 1200, false),
                item(FeedItemType.TRIP, other, 3000, false),
                item(FeedItemType.CHECKIN, post, 4000, true)));

        ArgumentCaptor<Collection<FeedImpressionRequest.Item>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(mapper).upsertBatch(eq(viewer), captor.capture());
        List<FeedImpressionRequest.Item> sent = new ArrayList<>(captor.getValue());
        assertThat(sent).hasSize(2);
        FeedImpressionRequest.Item merged = sent.get(0);
        assertThat(merged.getId()).isEqualTo(post);
        assertThat(merged.getDwellMs()).isEqualTo(4000);
        assertThat(merged.isOpened()).isTrue();
    }

    @Test
    void theSameIdUnderTwoKindsIsTwoPosts() {
        UUID id = UUID.randomUUID();

        service.record(viewer, List.of(item(FeedItemType.CHECKIN, id, 1000, false),
                item(FeedItemType.REVIEW, id, 1000, false)));

        verify(mapper).upsertBatch(eq(viewer), org.mockito.ArgumentMatchers.argThat(items -> items.size() == 2));
    }

    @Test
    void aGuestOrAnEmptyBatchWritesNothing() {
        service.record(null, List.of(item(FeedItemType.CHECKIN, UUID.randomUUID(), 1000, false)));
        service.record(viewer, List.of());
        service.record(viewer, null);

        verifyNoInteractions(mapper);
    }

    @Test
    void seenIdsAsksOnceForThePage() {
        UUID seen = UUID.randomUUID();
        UUID unseen = UUID.randomUUID();
        when(mapper.findSeenIds(eq(viewer), eq(FeedItemType.CHECKIN), any())).thenReturn(List.of(seen));

        assertThat(service.seenIds(viewer, FeedItemType.CHECKIN, List.of(seen, unseen))).containsExactly(seen);
        verify(mapper, times(1)).findSeenIds(eq(viewer), eq(FeedItemType.CHECKIN), any());
    }

    @Test
    void seenIdsForAGuestOrNoPostsIsEmptyWithoutAQuery() {
        assertThat(service.seenIds(null, FeedItemType.TRIP, List.of(UUID.randomUUID()))).isEmpty();
        assertThat(service.seenIds(viewer, FeedItemType.TRIP, Set.of())).isEmpty();

        verify(mapper, never()).findSeenIds(any(), any(), any());
    }

    @Test
    void purgeDeletesInSlicesUntilASliceComesBackShort() {
        when(config.getInt(BusinessConfigKey.FEED_IMPRESSION_RETENTION_DAYS)).thenReturn(90);
        when(mapper.deleteSeenBefore(any(), anyInt()))
                .thenReturn(FeedImpressionServiceImpl.PURGE_SLICE, FeedImpressionServiceImpl.PURGE_SLICE, 12);

        int deleted = service.purgeExpired();

        assertThat(deleted).isEqualTo(2 * FeedImpressionServiceImpl.PURGE_SLICE + 12);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper, times(3)).deleteSeenBefore(cutoff.capture(), eq(FeedImpressionServiceImpl.PURGE_SLICE));
        assertThat(java.time.Duration.between(cutoff.getValue(), LocalDateTime.now()).toDays())
                .isCloseTo(90L, within(1L));
    }
}
