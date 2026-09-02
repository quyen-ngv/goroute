package com.ds.goroute.service;

import com.ds.goroute.dto.request.RefreshPlaceReviewsRequest;
import com.ds.goroute.dto.request.ReviewInput;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.type.PlaceVisibilityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceReviewRefreshServiceTest {
    private PlaceReviewRepository reviewRepository;
    private PlaceRepository placeRepository;
    private ImageMigrationService imageMigrationService;
    private StorageService storageService;
    private PlaceReviewService service;
    private Place place;

    @BeforeEach
    void setUp() {
        reviewRepository = mock(PlaceReviewRepository.class);
        placeRepository = mock(PlaceRepository.class);
        imageMigrationService = mock(ImageMigrationService.class);
        storageService = mock(StorageService.class);
        service = new PlaceReviewService(
                reviewRepository,
                placeRepository,
                imageMigrationService,
                storageService,
                mock(PlaceReviewScoringService.class),
                new PlaceReviewScoreCalculator(),
                mock(BusinessConfigService.class));

        place = Place.builder()
                .id(UUID.randomUUID())
                .placeId("google-place-1")
                .visibilityStatus(PlaceVisibilityStatus.ACTIVE)
                .reviewRating(BigDecimal.valueOf(4.6))
                .reviewCount(1200)
                .build();
        when(placeRepository.findById(place.getId())).thenReturn(Optional.of(place));
    }

    @Test
    void prepareOnlyValidatesAndPreservesOldReviewsAndImages() {
        PlaceReview old = PlaceReview.builder()
                .profilePicture("https://onestudy.id.vn/resource/goroute/reviews/profile.webp")
                .images("[\"https://onestudy.id.vn/resource/goroute/reviews/photo.webp\"]")
                .build();
        when(reviewRepository.findByPlaceId(place.getId())).thenReturn(List.of(old));
        when(storageService.extractObjectKey(anyString())).thenReturn("reviews/object.webp");

        service.prepareRefresh(place.getId());

        verify(reviewRepository, never()).deleteByPlaceId(place.getId());
        verify(storageService, never()).deleteFiles(anyList());
        verify(placeRepository, never()).updateReviewRefreshMetadata(place);
    }

    @Test
    void scoresTwoHundredImageReviewsAndStoresOnlyThirtyCompressedReviews() {
        PlaceReview old = PlaceReview.builder()
                .profilePicture("https://onestudy.id.vn/resource/goroute/reviews/old-profile.webp")
                .images("[\"https://onestudy.id.vn/resource/goroute/reviews/old-photo.webp\"]")
                .build();
        when(reviewRepository.findByPlaceId(place.getId())).thenReturn(List.of(old));
        List<ReviewInput> reviews = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            reviews.add(review(index));
        }
        when(imageMigrationService.migrateCompressedImages(anyList(), anyString()))
                .thenAnswer(invocation -> {
                    List<String> urls = invocation.getArgument(0);
                    return Map.of(urls.getFirst(),
                            "https://onestudy.id.vn/resource/goroute/reviews/" + urls.getFirst().hashCode() + ".webp");
                });
        when(storageService.extractObjectKey(anyString())).thenReturn("reviews/compressed.webp");

        Map<String, Object> result = service.completeRefresh(RefreshPlaceReviewsRequest.builder()
                .placeId(place.getId())
                .googlePlaceId(place.getPlaceId())
                .scrapedAt(OffsetDateTime.now())
                .reviews(reviews)
                .build());

        ArgumentCaptor<List<PlaceReview>> inserted = ArgumentCaptor.forClass(List.class);
        verify(reviewRepository).insertBatch(inserted.capture());
        assertThat(inserted.getValue())
                .hasSize(30)
                .allMatch(review -> review.getImages().contains("onestudy.id.vn"));
        assertThat(result.get("sampled")).isEqualTo(200);
        assertThat(result.get("inserted")).isEqualTo(30);
        assertThat(place.getScoreSampleCount()).isEqualTo(200);
        assertThat(place.getScoreSource()).isEqualTo("SCRAPED_REVIEWS");
        assertThat(place.getLastScrapedAt()).isNotNull();
        verify(placeRepository).updateReviewRefreshMetadata(place);
        InOrder replacementOrder = inOrder(reviewRepository, placeRepository, storageService);
        replacementOrder.verify(reviewRepository).deleteByPlaceId(place.getId());
        replacementOrder.verify(reviewRepository).insertBatch(anyList());
        replacementOrder.verify(placeRepository).updateReviewRefreshMetadata(place);
        replacementOrder.verify(storageService).deleteFiles(anyList());
    }

    @Test
    void emptyScrapePreservesExistingReviews() {
        when(reviewRepository.findByPlaceId(place.getId())).thenReturn(List.of(PlaceReview.builder().build()));

        assertThatThrownBy(() -> service.completeRefresh(RefreshPlaceReviewsRequest.builder()
                .placeId(place.getId())
                .googlePlaceId(place.getPlaceId())
                .scrapedAt(OffsetDateTime.now())
                .reviews(List.of())
                .build()))
                .isInstanceOf(com.ds.goroute.exception.BusinessException.class);

        verify(reviewRepository, never()).deleteByPlaceId(place.getId());
        verify(storageService, never()).deleteFiles(anyList());
    }

    @Test
    void legacyBatchMigratesExternalImagesBeforeInsert() {
        ReviewInput input = review(1);
        input.setProfilePicture("https://googleusercontent.example/profile.jpg");
        when(placeRepository.findByPlaceId(place.getPlaceId())).thenReturn(place);
        when(reviewRepository.findByReviewId(input.getReviewId())).thenReturn(Optional.empty());
        when(imageMigrationService.migrateCompressedImage(anyString(), anyString()))
                .thenReturn("https://onestudy.id.vn/resource/goroute/reviews/profile.webp");
        when(imageMigrationService.migrateCompressedImages(anyList(), anyString()))
                .thenReturn(Map.of(input.getUserImages().getFirst(),
                        "https://onestudy.id.vn/resource/goroute/reviews/photo.webp"));
        when(storageService.extractObjectKey(any())).thenAnswer(invocation -> {
            Object value = invocation.getArgument(0);
            return value != null && value.toString().contains("onestudy.id.vn")
                    ? "reviews/managed.webp" : null;
        });

        service.batchInsertReviews(List.of(input));

        ArgumentCaptor<List<PlaceReview>> inserted = ArgumentCaptor.forClass(List.class);
        verify(reviewRepository).insertBatch(inserted.capture());
        assertThat(inserted.getValue()).singleElement().satisfies(review -> {
            assertThat(review.getProfilePicture()).contains("onestudy.id.vn");
            assertThat(review.getImages()).contains("onestudy.id.vn");
            assertThat(review.getImages()).doesNotContain("googleusercontent");
        });
    }

    @Test
    void legacyBatchDiscardsExternalUrlsWhenMigrationFails() {
        ReviewInput input = review(2);
        input.setProfilePicture("https://googleusercontent.example/profile.jpg");
        when(placeRepository.findByPlaceId(place.getPlaceId())).thenReturn(place);
        when(reviewRepository.findByReviewId(input.getReviewId())).thenReturn(Optional.empty());
        when(imageMigrationService.migrateCompressedImage(anyString(), anyString()))
                .thenReturn(input.getProfilePicture());
        when(imageMigrationService.migrateCompressedImages(anyList(), anyString()))
                .thenReturn(Map.of(input.getUserImages().getFirst(), input.getUserImages().getFirst()));
        when(storageService.extractObjectKey(any())).thenReturn(null);

        service.batchInsertReviews(List.of(input));

        ArgumentCaptor<List<PlaceReview>> inserted = ArgumentCaptor.forClass(List.class);
        verify(reviewRepository).insertBatch(inserted.capture());
        assertThat(inserted.getValue()).singleElement().satisfies(review -> {
            assertThat(review.getProfilePicture()).isNull();
            assertThat(review.getImages()).isEqualTo("[]");
        });
    }

    private ReviewInput review(int index) {
        String id = "review-" + index;
        return ReviewInput.builder()
                .reviewId(id)
                .googlePlaceId(place.getPlaceId())
                .authorName("Reviewer " + index)
                .isLocalGuide(index % 2 == 0)
                .totalReviews(index)
                .totalPhotos(index * 2)
                .rating(index % 5 + 1)
                .reviewText(Map.of("en", "x".repeat(index + 1)))
                .reviewDate("2026-08-10T00:00:00Z")
                .userImages(List.of("https://googleusercontent.example/" + id + ".jpg"))
                .likes(index)
                .contentHash("hash-" + id)
                .isDeleted(false)
                .build();
    }
}
