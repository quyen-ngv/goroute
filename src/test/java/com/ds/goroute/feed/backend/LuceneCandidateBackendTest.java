package com.ds.goroute.feed.backend;

import com.ds.goroute.feed.FeedTestSupport;
import com.ds.goroute.feed.index.FeedLuceneIndex;
import com.ds.goroute.feed.model.FeedCandidateQuery;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedKey;
import com.ds.goroute.mapper.FeedItemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The Lucene backend on a real index in a temporary directory; Postgres is a mock that keeps Lucene's keys. */
class LuceneCandidateBackendTest {

    @TempDir
    Path dir;

    private final FeedItemMapper mapper = mock(FeedItemMapper.class);
    private final LocalDateTime now = LocalDateTime.now();
    private final UUID viewer = UUID.randomUUID();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final Map<String, FeedItemRow> rows = new HashMap<>();
    private FeedLuceneIndex index;
    private LuceneCandidateBackend backend;

    private FeedItemRow add(FeedItemRow row) {
        rows.put(row.key().key(), row);
        return row;
    }

    private FeedItemRow checkin(UUID author, int hoursAgo) {
        return add(FeedTestSupport.checkin(author, now.minusHours(hoursAgo)));
    }

    @BeforeEach
    void setUp() throws Exception {
        when(mapper.selectPage(any(), any(), anyInt())).thenAnswer(invocation -> {
            List<FeedItemRow> page = new ArrayList<>(rows.values());
            return invocation.<String>getArgument(0).isEmpty() ? page : List.of();
        });
        when(mapper.selectByKeys(any())).thenAnswer(invocation -> {
            List<FeedItemRow> found = new ArrayList<>();
            for (FeedKey key : invocation.<Collection<FeedKey>>getArgument(0)) {
                if (rows.containsKey(key.key())) {
                    found.add(rows.get(key.key()));
                }
            }
            return found;
        });
        // Postgres keeps whatever Lucene found, in Lucene's order, up to the limit.
        when(mapper.selectCandidates(any())).thenAnswer(invocation -> {
            FeedCandidateQuery q = invocation.getArgument(0);
            return q.getKeys().stream().map(key -> rows.get(key.key())).limit(q.getLimit()).toList();
        });
        index = new FeedLuceneIndex(mapper, dir.toString());
        backend = new LuceneCandidateBackend(index, mapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        index.close();
    }

    private void build() throws Exception {
        index.init();
        for (int i = 0; i < 100 && !index.ready(); i++) {
            Thread.sleep(50);
        }
        assertThat(index.ready()).isTrue();
    }

    private FeedCandidateQuery.FeedCandidateQueryBuilder plan() {
        return FeedCandidateQuery.builder().viewerId(viewer).limit(10);
    }

    private static List<UUID> ids(List<FeedItemRow> rows) {
        return rows.stream().map(FeedItemRow::getItemId).toList();
    }

    @Test
    void authorsNewestFirstWithoutTheViewerOrIneligiblePosts() throws Exception {
        FeedItemRow old = checkin(alice, 30);
        FeedItemRow fresh = checkin(alice, 1);
        FeedItemRow mid = checkin(bob, 5);
        checkin(UUID.randomUUID(), 2);
        checkin(viewer, 0);
        FeedItemRow hidden = checkin(alice, 3);
        hidden.setEligible(false);
        build();

        List<FeedItemRow> found = backend.execute(plan().authorIds(List.of(alice, bob, viewer)).build());

        assertThat(ids(found)).containsExactly(fresh.getItemId(), mid.getItemId(), old.getItemId());
    }

    @Test
    void theRecommendationGateAndItsTwoEscapes() throws Exception {
        FeedItemRow ok = checkin(alice, 1);
        FeedItemRow weakTrip = add(FeedTestSupport.trip(alice, now.minusHours(2)));
        weakTrip.setRecommendOk(false);
        FeedItemRow weakCheckin = checkin(alice, 3);
        weakCheckin.setRecommendOk(false);
        build();

        assertThat(ids(backend.execute(plan().authorIds(List.of(alice)).build())))
                .containsExactly(ok.getItemId());
        // L4: a not-recommended check-in passes, a photoless one-stop trip still does not.
        assertThat(ids(backend.execute(plan().authorIds(List.of(alice)).relaxedRecommendGate(true).build())))
                .containsExactly(ok.getItemId(), weakCheckin.getItemId());
        // The Trips chip: everything eligible.
        assertThat(ids(backend.execute(plan().authorIds(List.of(alice)).ignoreRecommendGate(true).build())))
                .containsExactly(ok.getItemId(), weakTrip.getItemId(), weakCheckin.getItemId());
    }

    @Test
    void provincesTypesWindowAndImpressionCeiling() throws Exception {
        FeedItemRow hanoiNew = checkin(alice, 1);
        hanoiNew.setProvinceCode("01");
        FeedItemRow hanoiOld = checkin(alice, 50);
        hanoiOld.setProvinceCode("01");
        FeedItemRow hanoiSeen = checkin(bob, 2);
        hanoiSeen.setProvinceCode("01");
        hanoiSeen.setImpressionCount(40);
        FeedItemRow hanoiTrip = add(FeedTestSupport.trip(bob, now.minusHours(3)));
        hanoiTrip.setProvinceCode("01");
        checkin(bob, 1).setProvinceCode("79");
        build();

        assertThat(ids(backend.execute(plan().provinceCodes(List.of("01")).since(now.minusHours(24)).build())))
                .containsExactly(hanoiNew.getItemId(), hanoiSeen.getItemId(), hanoiTrip.getItemId());
        assertThat(ids(backend.execute(plan().provinceCodes(List.of("01")).maxImpressions(30)
                .itemTypes(List.of("CHECKIN")).build())))
                .containsExactly(hanoiNew.getItemId(), hanoiOld.getItemId());
    }

    @Test
    void nearAPointByDistance() throws Exception {
        FeedItemRow near = checkin(alice, 5);
        near.setLatitude(21.03);
        near.setLongitude(105.85);
        FeedItemRow far = checkin(alice, 1);
        far.setLatitude(10.77);
        far.setLongitude(106.70);
        checkin(alice, 2);
        build();

        assertThat(ids(backend.execute(plan().nearLat(21.0285).nearLng(105.8542).nearMeters(30_000d).build())))
                .containsExactly(near.getItemId());
    }

    @Test
    void trendingOrdersByStoredEngagement() throws Exception {
        FeedItemRow low = checkin(alice, 1);
        low.setEngagementScore(1.5f);
        FeedItemRow high = checkin(bob, 20);
        high.setEngagementScore(4f);
        FeedItemRow mid = checkin(bob, 10);
        mid.setEngagementScore(2f);
        build();

        assertThat(ids(backend.execute(plan().orderByEngagement(true).since(now.minusDays(7)).build())))
                .containsExactly(high.getItemId(), mid.getItemId(), low.getItemId());
    }

    @Test
    void postgresGetsTheKeysInsteadOfTheSelectorAndAMargin() throws Exception {
        for (int i = 0; i < 60; i++) {
            checkin(alice, i);
        }
        build();

        backend.execute(plan().authorIds(List.of(alice)).limit(30).build());

        ArgumentCaptor<FeedCandidateQuery> recheck = ArgumentCaptor.forClass(FeedCandidateQuery.class);
        verify(mapper).selectCandidates(recheck.capture());
        assertThat(recheck.getValue().getAuthorIds()).isNull();
        assertThat(recheck.getValue().getViewerId()).isEqualTo(viewer);
        assertThat(recheck.getValue().getLimit()).isEqualTo(30);
        assertThat(recheck.getValue().getKeys()).hasSize(30 + LuceneCandidateBackend.MIN_MARGIN);
    }

    @Test
    void anEmptySelectorReadsNothing() throws Exception {
        checkin(alice, 1);
        build();

        assertThat(backend.execute(plan().authorIds(List.of()).build())).isEmpty();
        assertThat(backend.execute(plan().keys(List.of()).build())).isEmpty();
        verify(mapper, never()).selectCandidates(any());
    }

    @Test
    void itineraryPlacesChipsAndAnUnbuiltIndexStayOnSql() throws Exception {
        assertThat(backend.supports(plan().build())).isFalse();
        build();

        assertThat(backend.supports(plan().build())).isTrue();
        assertThat(backend.supports(plan().placeIds(List.of(UUID.randomUUID())).build())).isFalse();
        assertThat(backend.supports(plan().placeGroups(List.of("FOOD_AND_DRINK")).build())).isFalse();
        assertThat(backend.supports(plan().subTypes(List.of("CAFE")).build())).isFalse();
    }

    @Test
    void refreshFollowsTheRowsAndDropsTheMissingOnes() throws Exception {
        FeedItemRow first = checkin(alice, 1);
        FeedItemRow second = checkin(alice, 2);
        build();

        second.setEligible(false);
        rows.remove(first.key().key());
        index.refresh(List.of(first.key(), second.key()));
        FeedItemRow third = checkin(alice, 3);
        index.refresh(List.of(third.key()));

        assertThat(ids(backend.execute(plan().authorIds(List.of(alice)).build()))).containsExactly(third.getItemId());
        verify(mapper, atLeastOnce()).selectByKeys(eq(List.of(third.key())));
    }
}
