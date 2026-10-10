package com.ds.goroute.job;

import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.PlaceReviewScoringService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewCleanupJobTest {

    private final PlaceReviewRepository reviewRepository = mock(PlaceReviewRepository.class);
    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceReviewScoringService scoringService = mock(PlaceReviewScoringService.class);
    private final ImageStorageCleanupService imageCleanup = mock(ImageStorageCleanupService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus txStatus = new SimpleTransactionStatus();
    private final ReviewCleanupJob job = new ReviewCleanupJob(
            reviewRepository, placeRepository, scoringService, imageCleanup, transactionManager);

    private final UUID placeId = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void keepsTheTwentyHeaviestReviewsAndDeletesTheRestWithTheirFiles() {
        List<PlaceReview> reviews = reviews(25);
        when(reviewRepository.findByPlaceId(placeId)).thenReturn(reviews);
        // Weight = position in the list, so reviews 5..24 are the twenty kept.
        when(scoringService.calculateReviewWeight(any(), anyInt()))
                .thenAnswer(inv -> (double) reviews.indexOf(inv.getArgument(0)));
        when(transactionManager.getTransaction(any())).thenReturn(txStatus);

        Map<String, Integer> result = job.cleanupPlaceReviews(placeId);

        assertThat(result).containsEntry("kept", 20).containsEntry("deleted", 5);
        ArgumentCaptor<List<UUID>> deleted = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(transactionManager, imageCleanup, reviewRepository);
        order.verify(transactionManager).getTransaction(any());
        order.verify(imageCleanup, times(5)).deleteImagesForEntityRecord(eq("PLACE_REVIEW"), any(UUID.class));
        order.verify(reviewRepository).deleteByIds(deleted.capture());
        order.verify(transactionManager).commit(txStatus);
        assertThat(deleted.getValue()).containsExactlyInAnyOrderElementsOf(
                reviews.subList(0, 5).stream().map(PlaceReview::getId).toList());
    }

    @Test
    void placeWithTwentyOrFewerReviewsIsLeftAlone() {
        when(reviewRepository.findByPlaceId(placeId)).thenReturn(reviews(20));

        Map<String, Integer> result = job.cleanupPlaceReviews(placeId);

        assertThat(result).containsEntry("kept", 20).containsEntry("deleted", 0);
        verify(reviewRepository, never()).deleteByIds(anyList());
        verify(imageCleanup, never()).deleteImagesForEntityRecord(any(), any(UUID.class));
        verify(transactionManager, never()).getTransaction(any());
    }

    @Test
    void aFailedRowDeleteRollsBackSoNoFileIsDeleted() {
        when(reviewRepository.findByPlaceId(placeId)).thenReturn(reviews(21));
        when(transactionManager.getTransaction(any())).thenReturn(txStatus);
        doThrow(new IllegalStateException("db down")).when(reviewRepository).deleteByIds(anyList());

        assertThatThrownBy(() -> job.cleanupPlaceReviews(placeId)).hasMessage("db down");

        // The file deletes were only scheduled for commit; a rollback discards them.
        verify(transactionManager).rollback(txStatus);
        verify(transactionManager, never()).commit(any());
    }

    private List<PlaceReview> reviews(int count) {
        List<PlaceReview> reviews = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> reviews.add(
                PlaceReview.builder().id(UUID.randomUUID()).placeId(placeId).build()));
        return reviews;
    }
}
