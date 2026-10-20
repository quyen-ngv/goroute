package com.ds.goroute.service.impl;

import com.ds.goroute.dto.PublicTripSearchCriteria;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripHelpfulVote;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.TripRankRow;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.CheckinRepository;
import com.ds.goroute.repository.ContentCommentRepository;
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
import com.ds.goroute.service.trip.PublicTripRankingCache;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.TripVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Public trip search and the Friends tab: a page of cards costs a fixed number of queries,
 * and the shared ranking cache only ever changes cost, never the result.
 */
class TripServiceImplPublicTripCardsTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TripMemberRepository tripMemberRepository = mock(TripMemberRepository.class);
    private final ActivityRepository activityRepository = mock(ActivityRepository.class);
    private final LocationImageService locationImageService = mock(LocationImageService.class);
    private final TripHelpfulVoteRepository tripHelpfulVoteRepository = mock(TripHelpfulVoteRepository.class);
    private final MediaAssetRepository mediaAssetRepository = mock(MediaAssetRepository.class);
    private final TripDestinationRepository tripDestinationRepository = mock(TripDestinationRepository.class);
    private final ContentCommentRepository contentCommentRepository = mock(ContentCommentRepository.class);

    private final UUID viewerId = UUID.randomUUID();
    private TripServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripServiceImpl(
                mock(StarService.class),
                tripRepository,
                mock(TripAccessGuard.class),
                mock(com.ds.goroute.service.TripChatService.class),
                tripMemberRepository,
                userRepository,
                activityRepository,
                mock(ExpenseRepository.class),
                mock(ExpenseSplitRepository.class),
                mock(CheckinRepository.class),
                mock(TripNoteRepository.class),
                mock(NotificationHelper.class),
                locationImageService,
                mock(ExpenseService.class),
                mock(ExchangeRateService.class),
                mock(UserReviewRepository.class),
                mock(UserReviewProfileRepository.class),
                mock(ReviewHelpfulVoteRepository.class),
                tripHelpfulVoteRepository,
                mock(PlaceScoreRepository.class),
                mediaAssetRepository,
                mock(ImageStorageCleanupService.class),
                mock(TripRealtimePublisher.class),
                mock(com.ds.goroute.service.TrackService.class),
                tripDestinationRepository,
                mock(SocialNotificationService.class),
                mock(UserCheckinService.class),
                Runnable::run,
                contentCommentRepository,
                mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    @Test
    void aPageOfCardsIsBuiltFromOneQueryPerKindOfData() {
        UUID ownerId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        Trip first = trip(ownerId, "Đà Lạt", null);
        Trip second = trip(ownerId, "Đà Lạt", null);
        Trip third = trip(viewerId, "Huế", "https://cdn/cover.jpg");
        List<Trip> trips = List.of(first, second, third);
        when(tripRepository.findFollowingTrips(viewerId, null, null, null, 20, 0)).thenReturn(trips);
        when(userRepository.findByIds(any())).thenReturn(List.of(
                User.builder().id(ownerId).fullName("Linh").build()));
        when(tripHelpfulVoteRepository.findByTripIdsAndUserId(anyCollection(), eq(viewerId))).thenReturn(Map.of(
                first.getId(), TripHelpfulVote.builder().tripId(first.getId()).userId(viewerId).isHelpful(true).build()));
        when(tripMemberRepository.countAcceptedByTripIds(anyCollection())).thenReturn(Map.of(first.getId(), 3));
        when(activityRepository.countByTripIds(anyCollection())).thenReturn(Map.of(first.getId(), 7));
        when(mediaAssetRepository.findTripMemoryPreviewsByTripIds(anyCollection(), eq(6))).thenReturn(Map.of(
                first.getId(), List.of(memory(first.getId(), activityId, ownerId))));
        when(mediaAssetRepository.countTripMemoriesByTripIds(anyCollection())).thenReturn(Map.of(first.getId(), 9));
        when(activityRepository.findNamesByIds(anyCollection())).thenReturn(Map.of(activityId, "Hồ Xuân Hương"));
        when(locationImageService.getImageForDestination("Đà Lạt")).thenReturn("https://cdn/dalat.jpg");

        List<PublicTripResponse> cards = service.getFollowingTrips(viewerId, null, null, null, 0, 20);

        assertThat(cards).extracting(PublicTripResponse::getId)
                .containsExactly(first.getId(), second.getId(), third.getId());
        PublicTripResponse card = cards.get(0);
        assertThat(card.getOwnerName()).isEqualTo("Linh");
        assertThat(card.getHasVotedHelpful()).isTrue();
        assertThat(card.getTotalMembers()).isEqualTo(3);
        assertThat(card.getTotalActivities()).isEqualTo(7);
        assertThat(card.getTotalMemoryCount()).isEqualTo(9);
        assertThat(card.getMemoryImageUrls()).containsExactly("https://cdn/memory.jpg");
        assertThat(card.getMemoryImageUrlsV2()).singleElement()
                .satisfies(image -> {
                    assertThat(image.getActivityName()).isEqualTo("Hồ Xuân Hương");
                    assertThat(image.getUploaderName()).isEqualTo("Linh");
                });
        assertThat(card.getCoverImageUrl()).isEqualTo("https://cdn/dalat.jpg");
        assertThat(cards.get(1).getHasVotedHelpful()).isNull();
        assertThat(cards.get(1).getTotalMemoryCount()).isZero();
        assertThat(cards.get(1).getTotalActivities()).isZero();
        assertThat(cards.get(2).getIsOwnTrip()).isTrue();
        assertThat(cards.get(2).getCoverImageUrl()).isEqualTo("https://cdn/cover.jpg");

        // One batch call per kind of data, and the per-trip lookups never run.
        verify(userRepository, times(2)).findByIds(any()); // owners, then photo uploaders
        verify(userRepository, never()).findById(any());
        verify(tripDestinationRepository, times(1)).findByTripIds(anyCollection());
        verify(tripDestinationRepository, never()).findByTripId(any());
        verify(tripHelpfulVoteRepository, times(1)).findByTripIdsAndUserId(anyCollection(), eq(viewerId));
        verify(tripHelpfulVoteRepository, never()).findByTripIdAndUserId(any(), any());
        verify(tripMemberRepository, times(1)).countAcceptedByTripIds(anyCollection());
        verify(tripMemberRepository, never()).findByTripId(any());
        verify(activityRepository, times(1)).countByTripIds(anyCollection());
        verify(activityRepository, never()).countByTripId(any());
        verify(activityRepository, never()).findByTripId(any());
        verify(mediaAssetRepository, times(1)).findTripMemoryPreviewsByTripIds(anyCollection(), eq(6));
        verify(mediaAssetRepository, never()).findTripMemoriesByTripId(any());
        // Two trips to Đà Lạt without a cover: one fallback lookup.
        verify(locationImageService, times(1)).getImageForDestination("Đà Lạt");
        verify(locationImageService, never()).getImageForDestination("Huế");
    }

    @Test
    void anAnonymousViewerCostsNoVoteQuery() {
        Trip trip = trip(UUID.randomUUID(), "Huế", null);
        when(tripRepository.searchPublicTripRanking(any(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(new TripRankRow(trip.getId(), trip.getOwnerId())));
        when(tripRepository.findPublicByIds(List.of(trip.getId()))).thenReturn(List.of(trip));

        PublicTripResponse card = service.searchPublicTrips(null, null, null, null, null, true, null,
                false, true, null, 0, 20, null, null).get(0);

        assertThat(card.getHasVotedHelpful()).isNull();
        assertThat(card.getIsOwnTrip()).isFalse();
        verify(tripHelpfulVoteRepository, never()).findByTripIdsAndUserId(any(), any());
    }

    @Test
    void withoutTheCacheTheDatabaseRanksExcludesAndBoundsThePage() {
        service.searchPublicTrips(BigDecimal.valueOf(21), BigDecimal.valueOf(105), BigDecimal.valueOf(1000),
                null, null, false, null, false, false, null, 2, 500, viewerId, viewerId);

        verify(tripRepository).searchPublicTripRanking(any(PublicTripSearchCriteria.class), eq(viewerId), eq(50), eq(100));
    }

    @Test
    void pageOrderFollowsTheRankingNotTheLoadOrder() {
        Trip first = trip(UUID.randomUUID(), "A", "x");
        Trip second = trip(UUID.randomUUID(), "B", "y");
        when(tripRepository.searchPublicTripRanking(any(), any(), anyInt(), anyInt())).thenReturn(List.of(
                new TripRankRow(first.getId(), first.getOwnerId()), new TripRankRow(second.getId(), second.getOwnerId())));
        when(tripRepository.findPublicByIds(anyList())).thenReturn(List.of(second, first));

        assertThat(service.searchPublicTrips(null, null, null, null, null, true, null, false, false, null,
                0, 20, null, null))
                .extracting(PublicTripResponse::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Nested
    class WithTheSharedRanking {

        private PublicTripRankingCache cache;
        private final List<Trip> ranked = new ArrayList<>();

        @BeforeEach
        void attachCache() {
            cache = new PublicTripRankingCache(tripRepository);
            ReflectionTestUtils.setField(service, "publicTripRankingCache", cache);
            IntStream.range(0, 30).forEach(index -> ranked.add(trip(UUID.randomUUID(), "City " + index, "c")));
            when(tripRepository.searchPublicTripRanking(any(), isNull(), eq(PublicTripRankingCache.WINDOW), eq(0)))
                    .thenAnswer(invocation -> ranked.stream()
                            .map(trip -> new TripRankRow(trip.getId(), trip.getOwnerId()))
                            .toList());
            when(tripRepository.findPublicByIds(anyList())).thenAnswer(invocation -> {
                List<UUID> ids = invocation.getArgument(0);
                return ranked.stream().filter(trip -> ids.contains(trip.getId())).toList();
            });
        }

        @Test
        void differentViewersShareOneRankingQuery() {
            search(UUID.randomUUID(), 0);
            search(UUID.randomUUID(), 1);
            search(null, 0);

            verify(tripRepository, times(1)).searchPublicTripRanking(any(), any(), anyInt(), anyInt());
        }

        @Test
        void theViewersOwnAndJoinedTripsAreDroppedBeforePaging() {
            Trip own = trip(viewerId, "Mine", "c");
            ranked.add(1, own);
            Trip joined = ranked.get(3);
            when(tripMemberRepository.findByTripIdsAndUserId(anyList(), eq(viewerId))).thenReturn(List.of(
                    TripMember.builder().tripId(joined.getId()).userId(viewerId).status(MemberStatus.ACCEPTED).build()));

            List<UUID> page = search(viewerId, 0).stream().map(PublicTripResponse::getId).toList();

            List<UUID> expected = ranked.stream()
                    .filter(trip -> trip != own && trip != joined)
                    .limit(20)
                    .map(Trip::getId)
                    .toList();
            assertThat(page).containsExactlyElementsOf(expected);
        }

        @Test
        void aPageBeyondTheWindowGoesToTheDatabase() {
            service.searchPublicTrips(null, null, null, null, null, true, null, true, true, null,
                    PublicTripRankingCache.WINDOW / 20, 20, viewerId, viewerId);

            verify(tripRepository).searchPublicTripRanking(any(), eq(viewerId), eq(20), eq(PublicTripRankingCache.WINDOW));
        }

        @Test
        void aTripThatWentPrivateSinceRankingIsReplacedByRankingAgain() {
            Trip gone = ranked.get(0);
            search(null, 0);
            // Made private after it was ranked: no longer loadable, still in the cached ranking.
            ranked.remove(gone);

            List<PublicTripResponse> page = search(null, 0);

            assertThat(page).hasSize(20).extracting(PublicTripResponse::getId).doesNotContain(gone.getId());
            verify(tripRepository, times(2)).searchPublicTripRanking(any(), any(), anyInt(), anyInt());
        }

        private List<PublicTripResponse> search(UUID viewer, int page) {
            return service.searchPublicTrips(null, null, null, null, null, true, null, true, true, null,
                    page, 20, viewer, viewer);
        }
    }

    private Trip trip(UUID ownerId, String destination, String cover) {
        return Trip.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .name(destination + " 3 ngày")
                .destination(destination)
                .coverImageUrl(cover)
                .startDate(LocalDate.of(2026, 10, 10))
                .endDate(LocalDate.of(2026, 10, 12))
                .visibility(TripVisibility.PUBLIC)
                .createdAt(LocalDateTime.of(2026, 9, 1, 8, 0))
                .build();
    }

    private MediaAsset memory(UUID tripId, UUID activityId, UUID uploaderId) {
        return MediaAsset.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .activityId(activityId)
                .entityType("TRIP_ACTIVITY_MEMORY")
                .entityId(activityId)
                .mediaType("IMAGE")
                .assetRole("MEMORY")
                .url("https://cdn/memory.jpg")
                .uploadedBy(uploaderId)
                .build();
    }
}
