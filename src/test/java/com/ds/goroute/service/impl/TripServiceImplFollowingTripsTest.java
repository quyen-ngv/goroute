package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripRankRow;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.PlaceScoreRepository;
import com.ds.goroute.repository.ReviewHelpfulVoteRepository;
import com.ds.goroute.repository.TripDestinationRepository;
import com.ds.goroute.repository.TripHelpfulVoteRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripNoteRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewProfileRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.ExchangeRateService;
import com.ds.goroute.service.ExpenseService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.LocationImageService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.TripRealtimePublisher;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.TripVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Home "Friends" tab's trip list, and the search it shares a card mapping with.
 *
 * <p>The mapping was lifted out of public trip search so both lists render the same card;
 * the search case below is there so the Friends-only date fallback cannot leak into it.
 */
class TripServiceImplFollowingTripsTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private TripServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripServiceImpl(
                mock(StarService.class),
                tripRepository,
                mock(TripAccessGuard.class),
                mock(com.ds.goroute.service.TripChatService.class),
                mock(TripMemberRepository.class),
                userRepository,
                mock(ActivityRepository.class),
                mock(ExpenseRepository.class),
                mock(ExpenseSplitRepository.class),
                mock(CheckinRepository.class),
                mock(TripNoteRepository.class),
                mock(NotificationHelper.class),
                mock(LocationImageService.class),
                mock(ExpenseService.class),
                mock(ExchangeRateService.class),
                mock(UserReviewRepository.class),
                mock(UserReviewProfileRepository.class),
                mock(ReviewHelpfulVoteRepository.class),
                mock(TripHelpfulVoteRepository.class),
                mock(PlaceScoreRepository.class),
                mock(MediaAssetRepository.class),
                mock(ImageStorageCleanupService.class),
                mock(TripRealtimePublisher.class),
                mock(com.ds.goroute.service.TrackService.class),
                mock(TripDestinationRepository.class),
                mock(SocialNotificationService.class),
                mock(UserCheckinService.class),
                Runnable::run,
                mock(com.ds.goroute.repository.ContentCommentRepository.class),
                mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @Test
    void pageAndSizeAreBoundedBeforeTheyReachTheQuery() {
        UUID viewerId = UUID.randomUUID();

        assertThat(service.getFollowingTrips(viewerId, null, null, null, -1, 500)).isEmpty();
        service.getFollowingTrips(viewerId, null, null, null, 2, 0);
        service.getFollowingTrips(viewerId, null, null, null, 3, 20);

        verify(tripRepository).findFollowingTrips(viewerId, null, null, null, 50, 0);
        verify(tripRepository).findFollowingTrips(viewerId, null, null, null, 1, 2);
        verify(tripRepository).findFollowingTrips(viewerId, null, null, null, 20, 60);
    }

    @Test
    void aTripWithNoShareDateFallsBackToWhenItWasCreated() {
        UUID viewerId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 1, 8, 0);
        Trip trip = trip(UUID.randomUUID(), null, createdAt);
        when(tripRepository.findFollowingTrips(viewerId, null, null, null, 20, 0)).thenReturn(List.of(trip));

        List<PublicTripResponse> trips = service.getFollowingTrips(viewerId, null, null, null, 0, 20);

        assertThat(trips).singleElement()
                .extracting(PublicTripResponse::getPublicSharedAt)
                .isEqualTo(createdAt);
    }

    @Test
    void aRecordedShareDateIsKept() {
        UUID viewerId = UUID.randomUUID();
        LocalDateTime sharedAt = LocalDateTime.of(2026, 9, 20, 21, 15);
        Trip trip = trip(UUID.randomUUID(), sharedAt, LocalDateTime.of(2026, 9, 1, 8, 0));
        when(tripRepository.findFollowingTrips(viewerId, null, null, null, 20, 0)).thenReturn(List.of(trip));

        assertThat(service.getFollowingTrips(viewerId, null, null, null, 0, 20)).singleElement()
                .extracting(PublicTripResponse::getPublicSharedAt)
                .isEqualTo(sharedAt);
    }

    @Test
    void aFollowedOwnersTripIsNeverTheViewersOwnAndCarriesTheOwner() {
        UUID viewerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Trip trip = trip(ownerId, null, LocalDateTime.of(2026, 9, 1, 8, 0));
        when(tripRepository.findFollowingTrips(viewerId, null, null, null, 20, 0)).thenReturn(List.of(trip));
        when(userRepository.findByIds(Set.of(ownerId))).thenReturn(List.of(
                User.builder().id(ownerId).fullName("Linh").avatarUrl("https://cdn/linh.jpg").build()));

        PublicTripResponse summary = service.getFollowingTrips(viewerId, null, null, null, 0, 20).get(0);

        assertThat(summary.getIsOwnTrip()).isFalse();
        assertThat(summary.getOwnerId()).isEqualTo(ownerId);
        assertThat(summary.getOwnerName()).isEqualTo("Linh");
        assertThat(summary.getOwnerAvatarUrl()).isEqualTo("https://cdn/linh.jpg");
        assertThat(summary.getActivities()).isNull();
        assertThat(summary.getDestinations()).singleElement()
                .satisfies(destination -> assertThat(destination.getName()).isEqualTo("Đà Nẵng"));
    }

    @Test
    void searchStillReportsAMissingShareDateAndTheViewersOwnTrip() {
        UUID viewerId = UUID.randomUUID();
        Trip ownTrip = trip(viewerId, null, LocalDateTime.of(2026, 9, 1, 8, 0));
        when(tripRepository.searchPublicTripRanking(any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(new TripRankRow(ownTrip.getId(), viewerId)));
        when(tripRepository.findPublicByIds(List.of(ownTrip.getId()))).thenReturn(List.of(ownTrip));

        PublicTripResponse summary = service.searchPublicTrips(null, null, null, null, null, true, null,
                false, false, null, 0, 20, viewerId, null).get(0);

        assertThat(summary.getPublicSharedAt()).isNull();
        assertThat(summary.getIsOwnTrip()).isTrue();
    }

    private Trip trip(UUID ownerId, LocalDateTime publicSharedAt, LocalDateTime createdAt) {
        return Trip.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .name("Đà Nẵng 3 ngày")
                .destination("Đà Nẵng")
                .startDate(LocalDate.of(2026, 10, 10))
                .endDate(LocalDate.of(2026, 10, 12))
                .visibility(TripVisibility.PUBLIC)
                .publicSharedAt(publicSharedAt)
                .createdAt(createdAt)
                .build();
    }
}
