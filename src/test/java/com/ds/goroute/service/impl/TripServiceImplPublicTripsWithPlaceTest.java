package com.ds.goroute.service.impl;

import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.entity.Trip;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Place Detail's list of public itineraries that include the place. */
class TripServiceImplPublicTripsWithPlaceTest {

    private final TripRepository tripRepository = mock(TripRepository.class);

    private TripServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripServiceImpl(
                mock(StarService.class),
                tripRepository,
                mock(TripAccessGuard.class),
                mock(com.ds.goroute.service.TripChatService.class),
                mock(TripMemberRepository.class),
                mock(UserRepository.class),
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
        UUID placeId = UUID.randomUUID();

        assertThat(service.getPublicTripsWithPlace(placeId, -1, 500, null)).isEmpty();
        service.getPublicTripsWithPlace(placeId, 2, 0, null);
        service.getPublicTripsWithPlace(placeId, 3, 20, null);

        verify(tripRepository).findPublicTripIdsByPlace(placeId, 50, 0);
        verify(tripRepository).findPublicTripIdsByPlace(placeId, 1, 2);
        verify(tripRepository).findPublicTripIdsByPlace(placeId, 20, 60);
    }

    @Test
    void tripsKeepTheQueryOrderAndOneThatWentPrivateIsDropped() {
        UUID placeId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        Trip newer = trip(UUID.randomUUID(), "Đà Nẵng 3 ngày");
        Trip older = trip(viewerId, "Hội An cuối tuần");
        UUID wentPrivate = UUID.randomUUID();
        List<UUID> ids = List.of(newer.getId(), wentPrivate, older.getId());
        when(tripRepository.findPublicTripIdsByPlace(placeId, 20, 0)).thenReturn(ids);
        // The repository hands rows back in no particular order.
        when(tripRepository.findPublicByIds(ids)).thenReturn(List.of(older, newer));

        List<PublicTripResponse> trips = service.getPublicTripsWithPlace(placeId, 0, 20, viewerId);

        assertThat(trips).extracting(PublicTripResponse::getName)
                .containsExactly("Đà Nẵng 3 ngày", "Hội An cuối tuần");
        assertThat(trips).extracting(PublicTripResponse::getIsOwnTrip)
                .containsExactly(false, true);
        assertThat(trips).allSatisfy(trip -> assertThat(trip.getActivities()).isNull());
    }

    @Test
    void noPlaceAsksNothing() {
        assertThat(service.getPublicTripsWithPlace(null, 0, 20, null)).isEmpty();

        verify(tripRepository, never()).findPublicTripIdsByPlace(any(), anyInt(), anyInt());
    }

    private Trip trip(UUID ownerId, String name) {
        return Trip.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .name(name)
                .destination("Đà Nẵng")
                .startDate(LocalDate.of(2026, 10, 10))
                .endDate(LocalDate.of(2026, 10, 12))
                .visibility(TripVisibility.PUBLIC)
                .build();
    }
}
