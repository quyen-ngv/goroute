package com.ds.goroute.service.impl;

import com.ds.goroute.dto.FeedCursor;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.entity.ActivityBooking;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.ReviewHelpfulVote;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserReview;
import com.ds.goroute.entity.UserReviewProfile;
import com.ds.goroute.repository.ActivityBookingRepository;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.ContentCommentRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceScoreRepository;
import com.ds.goroute.repository.ReviewHelpfulVoteRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewProfileRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.ReviewFraudDetectionService;
import com.ds.goroute.service.ReviewScoringService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.UserTier;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Review lists cost a fixed number of queries per page, never one per review (plan 2.2). */
class ReviewServiceImplListTest {

    private final UserReviewRepository reviews = mock(UserReviewRepository.class);
    private final UserReviewProfileRepository profiles = mock(UserReviewProfileRepository.class);
    private final ReviewHelpfulVoteRepository votes = mock(ReviewHelpfulVoteRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final ActivityBookingRepository bookings = mock(ActivityBookingRepository.class);
    private final MediaAssetRepository mediaAssets = mock(MediaAssetRepository.class);
    private final ContentCommentRepository comments = mock(ContentCommentRepository.class);

    private final ReviewServiceImpl service = new ReviewServiceImpl(
            mock(StarService.class), reviews, profiles, mock(PlaceScoreRepository.class),
            votes, users, places,
            mock(UserCheckinRepository.class), bookings, mock(HotelMarketplaceRepository.class),
            mock(ActivityCommerceRepository.class), mediaAssets, mock(ReviewScoringService.class),
            mock(ReviewFraudDetectionService.class), mock(ImageStorageCleanupService.class),
            mock(SocialNotificationService.class), mock(ContentModerationService.class), comments, mock(com.ds.goroute.service.checkin.CheckinVerifier.class),
            mock(com.ds.goroute.mapper.SavedPostMapper.class));

    private final UUID viewerId = UUID.randomUUID();
    private final UUID placeId = UUID.randomUUID();
    private final UUID authorA = UUID.randomUUID();
    private final UUID authorB = UUID.randomUUID();

    private UserReview review(UUID author, UUID place, UUID booking, boolean hasPhoto, LocalDateTime createdAt) {
        return UserReview.builder().id(UUID.randomUUID()).userId(author).placeId(place)
                .activityBookingId(booking).overallRating(4).helpfulVotes(0).hasPhoto(hasPhoto)
                .createdAt(createdAt).build();
    }

    @Test
    void aPlacesReviewListLoadsEveryRelatedRowOncePerPage() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 7, 12, 0);
        UserReview first = review(authorA, placeId, null, true, now);
        UserReview second = review(authorB, placeId, null, false, now.minusHours(1));
        UserReview third = review(authorA, placeId, null, false, now.minusHours(2));
        when(reviews.findByPlaceId(placeId, 20, 0)).thenReturn(List.of(first, second, third));
        when(users.findByIds(any())).thenReturn(List.of(
                User.builder().id(authorA).fullName("An").build(),
                User.builder().id(authorB).fullName("Binh").build()));
        when(profiles.findByUserIds(any())).thenReturn(List.of(
                UserReviewProfile.builder().userId(authorA).tier(UserTier.EXPERT).build()));
        when(places.findByIds(List.of(placeId))).thenReturn(List.of(
                Place.builder().id(placeId).title("Pho 10").build()));
        when(votes.findByReviewIdsAndUserId(any(), eq(viewerId))).thenReturn(List.of(
                ReviewHelpfulVote.builder().reviewId(second.getId()).userId(viewerId).isHelpful(false).build()));

        List<UserReviewResponse> page = service.getPlaceReviews(placeId, viewerId, 0, 20);

        assertThat(page).extracting(UserReviewResponse::getUserName).containsExactly("An", "Binh", "An");
        assertThat(page).extracting(UserReviewResponse::getUserTier)
                .containsExactly(UserTier.EXPERT, UserTier.NEWCOMER, UserTier.EXPERT);
        assertThat(page).extracting(UserReviewResponse::getPlaceName).containsOnly("Pho 10");
        assertThat(page).extracting(UserReviewResponse::getHasVotedHelpful).containsExactly(null, false, null);
        assertThat(page).extracting(UserReviewResponse::getFeedCursor).containsOnlyNulls();

        verify(users, times(1)).findByIds(Set.of(authorA, authorB));
        verify(profiles, times(1)).findByUserIds(Set.of(authorA, authorB));
        verify(places, times(1)).findByIds(List.of(placeId));
        verify(votes, times(1)).findByReviewIdsAndUserId(
                List.of(first.getId(), second.getId(), third.getId()), viewerId);
        verify(mediaAssets, times(1)).findByEntityIds(eq("USER_REVIEW"), any());
        verify(bookings, never()).findByIds(any());
        verify(users, never()).findById(any());
        verify(profiles, never()).findByUserId(any());
        verify(places, never()).findById(any());
        verify(votes, never()).findByReviewIdAndUserId(any(), any());
        verify(mediaAssets, never()).findByEntity(anyString(), any());
    }

    @Test
    void activityReviewsLoadTheirProductsInOneQuery() {
        UUID bookingId = UUID.randomUUID();
        UserReview first = review(authorA, null, bookingId, false, LocalDateTime.now());
        UserReview second = review(authorB, null, bookingId, false, LocalDateTime.now());
        when(bookings.findById(bookingId)).thenReturn(Optional.of(ActivityBooking.builder().id(bookingId).build()));
        when(reviews.findByActivityBookingId(bookingId, 20, 0)).thenReturn(List.of(first, second));
        when(bookings.findByIds(List.of(bookingId))).thenReturn(List.of(
                ActivityBooking.builder().id(bookingId).title("Kayak").build()));

        List<UserReviewResponse> page = service.getActivityBookingReviews(bookingId, null, 0, 20);

        assertThat(page).extracting(UserReviewResponse::getActivityBookingTitle).containsOnly("Kayak");
        verify(bookings, times(1)).findByIds(List.of(bookingId));
        verify(places, never()).findByIds(any());
        verify(votes, never()).findByReviewIdsAndUserId(any(), any());
    }

    @Test
    void theFeedResumesAfterTheCursorAndHandsOutTheNextOne() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 7, 9, 30, 1, 5_000);
        UserReview last = review(authorA, placeId, null, true, createdAt);
        FeedCursor sent = new FeedCursor(true, createdAt.plusHours(1), UUID.randomUUID());
        when(reviews.findFeedReviews(eq(viewerId), isNull(), isNull(), eq(sent), eq(20), eq(0), isNull()))
                .thenReturn(List.of(last));

        List<UserReviewResponse> page = service.getFeedReviews(viewerId, null, null, 7, 20, null, sent.encode());

        assertThat(page).singleElement().satisfies(item -> assertThat(FeedCursor.decode(item.getFeedCursor()))
                .isEqualTo(new FeedCursor(true, createdAt, last.getId())));
        verify(reviews).findFeedReviews(viewerId, null, null, sent, 20, 0, null);
    }

    @Test
    void withoutACursorTheFeedStillPagesByNumber() {
        when(reviews.findFeedReviews(any(), any(), any(), any(), anyInt(), anyInt(), any())).thenReturn(List.of());

        service.getFeedReviews(viewerId, null, null, 3, 20, null);

        verify(reviews).findFeedReviews(viewerId, null, null, null, 20, 60, null);
    }

    @Test
    void theShuffledFeedIgnoresCursorsAndHandsNoneOut() {
        UserReview row = review(authorA, placeId, null, true, LocalDateTime.now());
        when(reviews.findFeedReviews(any(), any(), isNull(), any(), anyInt(), anyInt(), eq("seed"))).thenReturn(List.of(row));
        String cursor = new FeedCursor(true, LocalDateTime.now(), UUID.randomUUID()).encode();

        List<UserReviewResponse> page = service.getFeedReviews(viewerId, null, null, 2, 20, "seed", cursor);

        assertThat(page).extracting(UserReviewResponse::getFeedCursor).containsOnlyNulls();
        verify(reviews).findFeedReviews(viewerId, null, null, null, 20, 40, "seed");
    }

    @Test
    void everyListIsBoundedEvenWhenTheCallerDoesNotValidate() {
        UUID target = UUID.randomUUID();
        when(users.findById(target)).thenReturn(Optional.of(User.builder().id(target).build()));

        service.getUserReviewsForProfile(target, null, -2, 10_000);

        verify(reviews).findByUserId(target, ReviewServiceImpl.MAX_PAGE_SIZE, 0);
    }
}
