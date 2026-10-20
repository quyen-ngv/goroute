package com.ds.goroute.service.impl;

import com.ds.goroute.dto.FeedCursor;
import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserReview;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.ContentCommentRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.checkin.CheckinRewardService;
import com.ds.goroute.service.checkin.LocationKeyFactory;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.ContentVisibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The public check-in feed: keyset paging and one query per kind of related row (plan 2.4, 7). */
@ExtendWith(MockitoExtension.class)
class UserCheckinServiceImplFeedTest {

    @Mock private UserCheckinRepository checkins;
    @Mock private com.ds.goroute.mapper.SavedPostMapper savedPosts;
    @Mock private UserReviewRepository reviews;
    @Mock private MediaAssetRepository mediaAssets;
    @Mock private PlaceRepository places;
    @Mock private UserRepository users;
    @Mock private AiTripRepository subscriptions;
    @Mock private ReviewScoringService scoring;
    @Mock private ReviewService reviewService;
    @Mock private ContentModerationService moderation;
    @Mock private ImageStorageCleanupService images;
    @Mock private BusinessConfigService config;
    @Mock private LocationKeyFactory locationKeyFactory;
    @Mock private org.springframework.context.ApplicationEventPublisher events;
    @Mock private SocialNotificationService socialNotifications;
    @Mock private TripAccessGuard tripAccessGuard;
    @Mock private CheckinRewardService rewards;
    @Mock private ActivityRepository activities;
    @Mock private CheckinRepository activityVisits;
    @Mock private NotificationHelper notifications;
    @Mock private ContentCommentRepository contentComments;

    @InjectMocks private UserCheckinServiceImpl service;

    private final UUID viewerId = UUID.randomUUID();

    private UserCheckin post(UUID author, boolean hasPhoto, LocalDateTime createdAt, UUID reviewId) {
        return UserCheckin.builder().id(UUID.randomUUID()).userId(author).reviewId(reviewId)
                .visibility(ContentVisibility.PUBLIC).hasPhoto(hasPhoto).createdAt(createdAt).build();
    }

    @Test
    void withoutACursorTheFeedPagesByNumberAsOlderBuildsExpect() {
        FeedGeoFilter geo = FeedGeoFilter.of(BigDecimal.valueOf(21.03), BigDecimal.valueOf(105.85), BigDecimal.TEN);

        assertThat(service.feed(viewerId, geo, null, 2, 20, null)).isEmpty();

        verify(checkins).findFeed(viewerId, geo, null, null, 20, 40);
    }

    @Test
    void aCursorResumesAfterItsKeyAndIgnoresThePageNumber() {
        FeedCursor sent = new FeedCursor(false, LocalDateTime.of(2026, 10, 1, 8, 0), UUID.randomUUID());

        service.feed(viewerId, null, null, 9, 500, sent.encode());

        verify(checkins).findFeed(viewerId, null, null, sent, 50, 0);
    }

    @Test
    void aForgedCursorIsRejectedBeforeAnyQuery() {
        assertThatThrownBy(() -> service.feed(viewerId, null, null, 0, 20, "not-a-cursor!"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(checkins);
    }

    @Test
    void eachPostCarriesTheCursorOfItsOwnSortKey() {
        UUID author = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 7, 11, 59, 59, 999_999_000);
        UserCheckin withPhoto = post(author, true, createdAt, null);
        UserCheckin withoutPhoto = post(author, false, createdAt.plusHours(1), null);
        when(checkins.findFeed(viewerId, null, null, null, 20, 0)).thenReturn(List.of(withPhoto, withoutPhoto));

        List<UserCheckinResponse> page = service.feed(viewerId, null, null, 0, 20, null);

        assertThat(page).extracting(item -> FeedCursor.decode(item.getFeedCursor())).containsExactly(
                new FeedCursor(true, createdAt, withPhoto.getId()),
                new FeedCursor(false, createdAt.plusHours(1), withoutPhoto.getId()));
    }

    @Test
    void authorsAndLinkedReviewPhotosAreLoadedOncePerPage() {
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        UUID reviewOne = UUID.randomUUID();
        UUID reviewTwo = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        List<UserCheckin> rows = List.of(
                post(authorA, true, now, reviewOne),
                post(authorB, true, now.minusMinutes(1), reviewTwo),
                post(authorA, false, now.minusMinutes(2), null));
        when(checkins.findFeed(viewerId, null, null, null, 20, 0)).thenReturn(rows);
        when(users.findByIds(Set.of(authorA, authorB))).thenReturn(List.of(
                User.builder().id(authorA).fullName("An").build(),
                User.builder().id(authorB).fullName("Binh").build()));
        when(moderation.takenDownIds(any(), any())).thenReturn(Set.of());
        when(reviews.findByIds(List.of(reviewOne, reviewTwo))).thenReturn(List.of(
                UserReview.builder().id(reviewOne).overallRating(5).build(),
                UserReview.builder().id(reviewTwo).overallRating(3).build()));
        when(mediaAssets.findByEntityIds(any(), any())).thenReturn(List.of(
                MediaAsset.builder().id(UUID.randomUUID()).entityType("USER_REVIEW").entityId(reviewTwo)
                        .mediaType("IMAGE").url("https://cdn/two.jpg").build()));

        List<UserCheckinResponse> page = service.feed(viewerId, null, null, 0, 20, null);

        assertThat(page).extracting(UserCheckinResponse::getUserDisplayName).containsExactly("An", "Binh", "An");
        assertThat(page.get(0).getLinkedReview().getPhotos()).isEmpty();
        assertThat(page.get(1).getLinkedReview().getPhotos()).containsExactly("https://cdn/two.jpg");
        assertThat(page.get(2).getLinkedReview()).isNull();
        verify(users, times(1)).findByIds(Set.of(authorA, authorB));
        verify(users, never()).findById(any());
        verify(mediaAssets, times(1)).findByEntityIds(any(), any());
        verify(mediaAssets, never()).findByEntity(anyString(), any());
        verify(checkins, never()).countLikes(any());
        verify(checkins, never()).hasLike(any(), any());
    }

    @Test
    void anEmptyPageQueriesNothingElse() {
        when(checkins.findFeed(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());

        assertThat(service.feed(viewerId, null, null, 0, 20, null)).isEmpty();

        verifyNoInteractions(users, mediaAssets, reviews);
    }
}
