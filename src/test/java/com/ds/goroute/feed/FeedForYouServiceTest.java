package com.ds.goroute.feed;

import com.ds.goroute.dto.response.FeedForYouPageResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.feed.model.FeedCandidateQuery;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedViewerRows;
import com.ds.goroute.feed.rerank.Reranker;
import com.ds.goroute.feed.rerank.RerankRules;
import com.ds.goroute.feed.score.ScoreFactor;
import com.ds.goroute.feed.score.ScoreFactors;
import com.ds.goroute.feed.session.CaffeineFeedSessionStore;
import com.ds.goroute.feed.source.CandidateSource;
import com.ds.goroute.feed.source.FeedSources;
import com.ds.goroute.mapper.FeedItemMapper;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedForYouServiceTest {

    private final UUID viewer = UUID.randomUUID();
    private final LocalDateTime now = LocalDateTime.now();
    private final FeedFilterSpec everything = FeedFilterSpec.of(List.of(), null, null);

    private final FeedRollout rollout = mock(FeedRollout.class);
    private final ViewerContextResolver contexts = mock(ViewerContextResolver.class);
    private final FeedItemMapper itemMapper = mock(FeedItemMapper.class);
    private final UserCheckinService checkinService = mock(UserCheckinService.class);
    private final ReviewService reviewService = mock(ReviewService.class);
    private final TripService tripService = mock(TripService.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);

    /** Every source query answers with this pool, as if the whole app matched. */
    private List<FeedItemRow> pool = new ArrayList<>();
    private Set<UUID> invisible = Set.of();
    private ViewerContext context;
    private FeedForYouService service;

    @BeforeEach
    void setUp() {
        when(rollout.enabledFor(any())).thenReturn(true);
        context = ViewerContext.GUEST.toBuilder().viewerId(viewer).build();
        when(contexts.resolve(any(), any(), any(), anyBoolean(), any())).thenAnswer(invocation -> context);
        when(itemMapper.selectCandidates(any())).thenAnswer(invocation -> {
            FeedCandidateQuery query = invocation.getArgument(0);
            return pool.stream().limit(query.getLimit()).toList();
        });
        when(checkinService.findVisibleByIds(any(), any())).thenAnswer(invocation -> {
            Collection<UUID> ids = invocation.getArgument(1);
            Map<UUID, UserCheckinResponse> visible = new HashMap<>();
            for (UUID id : ids) {
                if (!invisible.contains(id)) {
                    visible.put(id, UserCheckinResponse.builder().id(id).build());
                }
            }
            return visible;
        });
        weights("{}");
    }

    private void weights(String override) {
        when(config.getText(BusinessConfigKey.FEED_RANKING_WEIGHTS)).thenReturn(override);
        FeedWeightsProvider provider = new FeedWeightsProvider(config, new ObjectMapper());
        List<CandidateSource> sources = List.of(
                new FeedSources.InNetwork(), new FeedSources.Social(), new FeedSources.Geo(),
                new FeedSources.Itinerary(), new FeedSources.NearMe(), new FeedSources.Trending(),
                new FeedSources.Fresh());
        List<ScoreFactor> factors = List.of(
                new ScoreFactors.Stored(), new ScoreFactors.Freshness(), new ScoreFactors.Affinity(),
                new ScoreFactors.Context(), new ScoreFactors.Social(), new ScoreFactors.Seen());
        Reranker reranker = new Reranker(List.of(new RerankRules.Author(), new RerankRules.TypeRun(),
                new RerankRules.PhotolessTrip()));
        service = new FeedForYouService(rollout, contexts, provider, itemMapper, sources, factors, reranker,
                new CaffeineFeedSessionStore(), checkinService, reviewService, tripService);
    }

    /** {@code count} check-ins by distinct authors, newest first, scores falling. */
    private List<FeedItemRow> checkins(int count) {
        List<FeedItemRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FeedItemRow row = FeedTestSupport.checkin(UUID.randomUUID(), now.minusDays(10).minusMinutes(i));
            row.setEngagementScore(100 - i * 0.01f);
            row.setImpressionCount(500);
            rows.add(row);
        }
        return rows;
    }

    private FeedForYouPageResponse page(String cursor, int size) {
        return service.page(viewer, everything, cursor, size, null, null, false, Set.of(), false);
    }

    private static List<UUID> ids(FeedForYouPageResponse page) {
        return page.getItems().stream().map(item -> item.getCheckin().getId()).toList();
    }

    @Test
    void aViewerOffTheRankedFeedIsToldSoWithoutAnyRanking() {
        when(rollout.enabledFor(viewer)).thenReturn(false);

        FeedForYouPageResponse page = page(null, 20);

        assertThat(page.isEnabled()).isFalse();
        assertThat(page.getVariant()).isEqualTo(FeedRollout.LEGACY);
        assertThat(page.getItems()).isEmpty();
        verify(itemMapper, never()).selectCandidates(any());
    }

    @Test
    void thePagesOfAScrollAreSlicesOfOneRanking() {
        pool = checkins(150);

        FeedForYouPageResponse first = page(null, 20);
        FeedForYouPageResponse second = page(first.getNextCursor(), 20);

        assertThat(first.isEnabled()).isTrue();
        assertThat(first.getVariant()).isEqualTo(FeedRollout.RANKED);
        assertThat(first.getItems()).hasSize(20);
        assertThat(second.getItems()).hasSize(20);
        assertThat(second.getSessionId()).isEqualTo(first.getSessionId());
        assertThat(ids(second)).doesNotContainAnyElementsOf(ids(first));
        assertThat(ids(first).get(0)).as("highest score first").isEqualTo(pool.get(0).getItemId());
        // Ranked once: the second page reads the session, it does not query again.
        int queriesForFirstPage = org.mockito.Mockito.mockingDetails(itemMapper).getInvocations().size();
        page(second.getNextCursor(), 20);
        assertThat(org.mockito.Mockito.mockingDetails(itemMapper).getInvocations()).hasSize(queriesForFirstPage);
    }

    @Test
    void theLastPageHasNoCursor() {
        pool = checkins(150);
        String cursor = null;
        List<UUID> all = new ArrayList<>();
        FeedForYouPageResponse page;
        do {
            page = page(cursor, 50);
            all.addAll(ids(page));
            cursor = page.getNextCursor();
        } while (cursor != null);

        assertThat(page.isHasMore()).isFalse();
        assertThat(all).hasSize(150).doesNotHaveDuplicates();
    }

    @Test
    void postsSeenRepeatedlyLatelyAreLeftOutWhenThereIsEnoughElse() {
        pool = checkins(150);
        FeedItemRow seenTwice = pool.get(0);
        context = context.toBuilder().seen(Map.of(seenTwice.key().key(),
                seen(seenTwice, 2, false, now.minusHours(3)))).build();

        List<UUID> everythingRanked = new ArrayList<>();
        String cursor = null;
        do {
            FeedForYouPageResponse page = page(cursor, 50);
            everythingRanked.addAll(ids(page));
            cursor = page.getNextCursor();
        } while (cursor != null);

        assertThat(everythingRanked).doesNotContain(seenTwice.getItemId()).hasSize(149);
    }

    @Test
    void aPostSeenOnceGoesDownTheList() {
        pool = checkins(150);
        FeedItemRow seenOnce = pool.get(0);
        context = context.toBuilder().seen(Map.of(seenOnce.key().key(),
                seen(seenOnce, 1, false, now.minusHours(3)))).build();

        List<UUID> firstPage = ids(page(null, 20));

        assertThat(firstPage).doesNotContain(seenOnce.getItemId());
    }

    @Test
    void withFewPostsTheLadderWidensAndSeenPostsComeBack() {
        pool = checkins(30);
        FeedItemRow seenTwice = pool.get(0);
        context = context.toBuilder().seen(Map.of(seenTwice.key().key(),
                seen(seenTwice, 2, false, now.minusHours(3)))).build();

        FeedForYouPageResponse page = page(null, 50);

        assertThat(page.getFillLevel()).isEqualTo("L4");
        assertThat(ids(page)).hasSize(30).contains(seenTwice.getItemId());
        assertThat(ids(page).get(29)).as("far down").isEqualTo(seenTwice.getItemId());
    }

    @Test
    void almostNothingToShowIsL5() {
        pool = checkins(5);

        assertThat(page(null, 20).getFillLevel()).isEqualTo("L5");
    }

    @Test
    void aPostGonePrivateSinceItWasRankedIsLeftOutOfThePage() {
        pool = checkins(150);
        invisible = Set.of(pool.get(1).getItemId());

        FeedForYouPageResponse page = page(null, 20);

        assertThat(ids(page)).hasSize(19).doesNotContain(pool.get(1).getItemId());
        assertThat(page.isHasMore()).isTrue();
    }

    @Test
    void anExpiredCursorStartsANewRankingAndSaysSo() {
        pool = checkins(150);
        String unknown = new FeedForYouService.Cursor(UUID.randomUUID().toString(), 40).encode();

        FeedForYouPageResponse page = page(unknown, 20);

        assertThat(page.isRestarted()).isTrue();
        assertThat(page.getItems()).hasSize(20);
        assertThat(ids(page).get(0)).isEqualTo(pool.get(0).getItemId());
    }

    @Test
    void aCursorFromAnotherChipStartsOver() {
        pool = checkins(150);
        String cursor = page(null, 20).getNextCursor();
        FeedFilterSpec trips = FeedFilterSpec.of(List.of(com.ds.goroute.type.FeedItemType.TRIP), null, null);

        FeedForYouPageResponse page = service.page(viewer, trips, cursor, 20, null, null, false, Set.of(), false);

        assertThat(page.isRestarted()).isTrue();
    }

    @Test
    void theEndOfAFullSessionRanksAgainWithoutWhatWasServed() {
        weights("{\"caps\":{\"sessionSize\":40}}");
        pool = checkins(100);

        FeedForYouPageResponse first = page(null, 40);
        FeedForYouPageResponse next = page(first.getNextCursor(), 40);

        assertThat(first.isHasMore()).isTrue();
        assertThat(next.getSessionId()).isNotEqualTo(first.getSessionId());
        assertThat(ids(next)).hasSize(40).doesNotContainAnyElementsOf(ids(first));
    }

    @Test
    void theScoreBreakdownIsOnlyForBetaUsersAskingForIt() {
        pool = checkins(150);
        when(rollout.isBeta(viewer)).thenReturn(true);

        FeedForYouPageResponse withDebug = service.page(viewer, everything, null, 5, null, null, false, Set.of(), true);
        FeedForYouPageResponse without = page(null, 5);

        assertThat(withDebug.getItems().get(0).getRank()).isNotNull();
        assertThat(withDebug.getItems().get(0).getRank().getFactors())
                .containsKeys("EQN", "F", "A", "G", "S", "seen");
        assertThat(without.getItems().get(0).getRank()).isNull();
    }

    @Test
    void debugIsIgnoredForEveryoneElse() {
        pool = checkins(150);
        when(rollout.isBeta(viewer)).thenReturn(false);

        FeedForYouPageResponse page = service.page(viewer, everything, null, 5, null, null, false, Set.of(), true);

        assertThat(page.getItems().get(0).getRank()).isNull();
    }

    @Test
    void theOngoingTripNamesTheContextLine() {
        pool = checkins(150);
        UUID tripId = UUID.randomUUID();
        context = context.toBuilder().ongoingTripId(tripId).ongoingTripName("Đà Lạt").todayProvinces(Set.of("68")).build();
        when(contexts.provinceName("68")).thenReturn("Lâm Đồng");

        FeedForYouPageResponse page = page(null, 5);

        assertThat(page.getContext()).isNotNull();
        assertThat(page.getContext().getTripId()).isEqualTo(tripId);
        assertThat(page.getContext().getProvinceName()).isEqualTo("Lâm Đồng");
    }

    @Test
    void noContextLineOnceTheViewerTurnedItOff() {
        pool = checkins(150);
        context = context.toBuilder().ongoingTripId(UUID.randomUUID()).todayProvinces(Set.of("68"))
                .contextOff(true).build();

        assertThat(page(null, 5).getContext()).isNull();
    }

    @Test
    void contextPostsRankAboveOthersOfTheSameStandingButNotAllOfThem() {
        List<FeedItemRow> rows = checkins(150);
        for (int i = 100; i < 110; i++) {
            rows.get(i).setProvinceCode("68");
        }
        pool = rows;
        context = context.toBuilder().ongoingTripId(UUID.randomUUID()).todayProvinces(Set.of("68")).build();

        List<FeedItemRow> firstPage = page(null, 20).getItems().stream()
                .map(item -> rows.stream().filter(r -> r.getItemId().equals(item.getCheckin().getId())).findFirst().orElseThrow())
                .toList();

        assertThat(firstPage.stream().filter(r -> "68".equals(r.getProvinceCode())).count()).isPositive();
    }

    @Test
    void everySourceThatAppliesIsAsked() {
        pool = checkins(150);
        context = context.toBuilder()
                .following(Map.of(UUID.randomUUID(), false))
                .relatedProvinces(Set.of("01"))
                .deviceLat(21.0).deviceLng(105.8)
                .build();

        page(null, 20);

        verify(itemMapper, atLeastOnce()).selectCandidates(org.mockito.ArgumentMatchers.argThat(
                query -> query != null && query.getAuthorIds() != null));
        verify(itemMapper, atLeastOnce()).selectCandidates(org.mockito.ArgumentMatchers.argThat(
                query -> query != null && query.getProvinceCodes() != null));
        verify(itemMapper, atLeastOnce()).selectCandidates(org.mockito.ArgumentMatchers.argThat(
                query -> query != null && query.getNearLat() != null));
        verify(itemMapper, atLeastOnce()).selectCandidates(org.mockito.ArgumentMatchers.argThat(
                query -> query != null && query.isOrderByEngagement()));
    }

    @Test
    void chipsReachEveryQuery() {
        pool = checkins(150);
        FeedFilterSpec cafesNearby = FeedFilterSpec.of(List.of(),
                com.ds.goroute.dto.FeedPlaceKind.of(null, List.of("CAFE")),
                com.ds.goroute.dto.FeedGeoFilter.of(java.math.BigDecimal.valueOf(21), java.math.BigDecimal.valueOf(105.8),
                        java.math.BigDecimal.valueOf(50)));

        service.page(viewer, cafesNearby, null, 20, null, null, false, Set.of(), false);

        verify(itemMapper, never()).selectCandidates(org.mockito.ArgumentMatchers.argThat(
                query -> query != null && (query.getSubTypes() == null || query.getRadiusMeters() == null)));
        verify(itemMapper, atLeastOnce()).selectCandidates(org.mockito.ArgumentMatchers.argThat(
                query -> query != null && query.getRadiusMeters() == 50_000.0 && query.getChipTripMinStops() == 3));
    }

    @Test
    void cursorsRoundTripAndRejectGarbage() {
        FeedForYouService.Cursor cursor = new FeedForYouService.Cursor("abc-123", 40);

        assertThat(FeedForYouService.Cursor.decode(cursor.encode())).isEqualTo(cursor);
        assertThat(FeedForYouService.Cursor.decode("%%%")).isNull();
        assertThat(FeedForYouService.Cursor.decode(null)).isNull();
        assertThat(FeedForYouService.Cursor.decode("x".repeat(300))).isNull();
    }

    @Test
    void theContextResolverGetsTheTripsTurnedOff() {
        pool = checkins(150);
        UUID tripId = UUID.randomUUID();

        service.page(viewer, everything, null, 20, 21.0, 105.8, false, Set.of(tripId), false);

        verify(contexts, times(1)).resolve(eq(viewer), eq(21.0), eq(105.8), eq(false), eq(Set.of(tripId)));
    }

    private static FeedViewerRows.Seen seen(FeedItemRow row, int count, boolean opened, LocalDateTime at) {
        FeedViewerRows.Seen seen = new FeedViewerRows.Seen();
        seen.setItemType(row.getItemType());
        seen.setItemId(row.getItemId());
        seen.setSeenCount(count);
        seen.setOpened(opened);
        seen.setLastSeenAt(at);
        return seen;
    }

}
