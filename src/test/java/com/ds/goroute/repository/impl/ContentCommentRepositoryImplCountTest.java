package com.ds.goroute.repository.impl;

import com.ds.goroute.entity.ContentCommentCount;
import com.ds.goroute.mapper.ContentCommentMapper;
import com.ds.goroute.type.ModeratedContentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContentCommentRepositoryImplCountTest {

    private final ContentCommentMapper mapper = mock(ContentCommentMapper.class);
    private final ContentCommentRepositoryImpl repository = new ContentCommentRepositoryImpl(mapper);

    private static ContentCommentCount row(UUID id, int count) {
        ContentCommentCount row = new ContentCommentCount();
        row.setContentId(id);
        row.setCommentCount(count);
        return row;
    }

    @Test
    void everyRequestedTargetGetsACountAndTargetsWithoutCommentsAreZero() {
        UUID commented = UUID.randomUUID();
        UUID silent = UUID.randomUUID();
        when(mapper.countActiveByTargets(eq("CHECKIN"), anyList(), eq(true)))
                .thenReturn(List.of(row(commented, 3)));

        Map<UUID, Integer> counts = repository.countActiveByTargets(
                ModeratedContentType.CHECKIN, List.of(commented, silent, commented));

        assertThat(counts).containsOnly(Map.entry(commented, 3), Map.entry(silent, 0));
        verify(mapper).countActiveByTargets("CHECKIN", List.of(commented, silent), true);
    }

    @Test
    void reviewsKeepTheirTakenDownThreadLikeTheSingleCount() {
        UUID review = UUID.randomUUID();

        repository.countActiveByTargets(ModeratedContentType.REVIEW, List.of(review));

        verify(mapper).countActiveByTargets("REVIEW", List.of(review), false);
    }

    @Test
    void anEmptyPageCostsNoQuery() {
        assertThat(repository.countActiveByTargets(ModeratedContentType.TRIP, List.of())).isEmpty();
        verifyNoInteractions(mapper);
    }

    @Test
    void longListsAreSplitIntoBatches() {
        List<UUID> ids = new ArrayList<>();
        IntStream.range(0, 1200).forEach(i -> ids.add(UUID.randomUUID()));

        Map<UUID, Integer> counts = repository.countActiveByTargets(ModeratedContentType.TRIP, ids);

        assertThat(counts).hasSize(1200);
        verify(mapper, times(3)).countActiveByTargets(eq("TRIP"), anyList(), eq(true));
    }
}
