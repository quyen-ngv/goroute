package com.ds.goroute.feed.score;

import com.ds.goroute.dto.FeedPlaceKind;
import com.ds.goroute.feed.FeedCandidate;
import com.ds.goroute.feed.FeedFilterSpec;
import com.ds.goroute.feed.FeedTestSupport;
import com.ds.goroute.feed.FeedTypes;
import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.ViewerContext;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedViewerRows;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScoreFactorsTest {

    private final LocalDateTime now = LocalDateTime.now();
    private final FeedWeights w = FeedTestSupport.defaults();
    private final FeedFilterSpec everything = FeedFilterSpec.of(List.of(), null, null);

    private ScoreFactor.Request request(ViewerContext context, FeedFilterSpec spec, FeedTypes.FillLevel level) {
        return new ScoreFactor.Request(context, spec, w, level, now);
    }

    private ScoreFactor.Request request() {
        return request(ViewerContext.GUEST, everything, FeedTypes.FillLevel.L1);
    }

    @Test
    void freshnessHalvesEveryHalfLifeDownToTheFloor() {
        ScoreFactors.Freshness f = new ScoreFactors.Freshness();
        FeedCandidate justNow = new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now));
        FeedCandidate twoDays = new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now.minusHours(48)));
        FeedCandidate aMonth = new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now.minusDays(30)));

        assertThat(f.apply(justNow, request())).isCloseTo(1.0, within(1e-6));
        assertThat(f.apply(twoDays, request())).isCloseTo(0.5, within(1e-3));
        assertThat(f.apply(aMonth, request())).isEqualTo(0.15);
    }

    @Test
    void aTripAgesFourTimesSlowerThanACheckin() {
        ScoreFactors.Freshness f = new ScoreFactors.Freshness();
        FeedCandidate trip = new FeedCandidate(FeedTestSupport.trip(UUID.randomUUID(), now.minusDays(7)));

        assertThat(f.apply(trip, request())).isCloseTo(0.5, within(1e-3));
    }

    @Test
    void aStrongContextKeepsOldPostsAboveAHigherFloor() {
        ScoreFactors.Freshness f = new ScoreFactors.Freshness();
        FeedCandidate oldNearby = new FeedCandidate(FeedTestSupport.review(UUID.randomUUID(), now.minusDays(120)));
        oldNearby.context(0.6, "near");

        assertThat(f.apply(oldNearby, request())).isEqualTo(0.5);
    }

    @Test
    void affinityAddsForFollowingMutualAndInteractions() {
        UUID friend = UUID.randomUUID();
        UUID mutualFriend = UUID.randomUUID();
        ViewerContext viewer = ViewerContext.GUEST.toBuilder()
                .viewerId(UUID.randomUUID())
                .following(Map.of(friend, false, mutualFriend, true))
                .affinity(Map.of(mutualFriend, (float) (Math.E - 1)))
                .build();
        ScoreFactors.Affinity a = new ScoreFactors.Affinity();

        assertThat(a.apply(new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now)),
                request(viewer, everything, FeedTypes.FillLevel.L1))).isEqualTo(1.0);
        assertThat(a.apply(new FeedCandidate(FeedTestSupport.checkin(friend, now)),
                request(viewer, everything, FeedTypes.FillLevel.L1))).isEqualTo(2.0);
        // 1 + follow 1.0 + mutual 0.3 + 0.5 x ln(1 + (e - 1))
        assertThat(a.apply(new FeedCandidate(FeedTestSupport.checkin(mutualFriend, now)),
                request(viewer, everything, FeedTypes.FillLevel.L1))).isCloseTo(2.8, within(1e-6));
    }

    @Test
    void contextIsOnePlusTheStrongestMatchAndAReviewNearbyGetsMore() {
        ScoreFactors.Context g = new ScoreFactors.Context();
        FeedCandidate review = new FeedCandidate(FeedTestSupport.review(UUID.randomUUID(), now));
        review.context(0.3, "related");
        review.context(0.6, "near");
        review.nearDevice(true);
        FeedCandidate checkin = new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now));
        checkin.context(0.6, "near");
        checkin.nearDevice(true);

        assertThat(review.contextReason()).isEqualTo("near");
        assertThat(g.apply(review, request())).isCloseTo(1.6 * 1.2, within(1e-9));
        assertThat(g.apply(checkin, request())).isCloseTo(1.6, within(1e-9));
    }

    @Test
    void socialProofCountsAtMostThreePeople() {
        ScoreFactors.Social s = new ScoreFactors.Social();
        FeedCandidate popular = new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now));
        popular.socialPeople(9);

        assertThat(s.apply(popular, request())).isCloseTo(1.6, within(1e-9));
    }

    @Test
    void aSeenPostGoesDownAndAnOpenedOneFurther() {
        ScoreFactors.Seen seen = new ScoreFactors.Seen();
        FeedCandidate once = new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now));
        once.seen(seen(1, false, now.minusHours(1)));
        FeedCandidate opened = new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now));
        opened.seen(seen(1, true, now.minusHours(1)));
        FeedCandidate unseen = new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now));

        assertThat(seen.apply(unseen, request())).isEqualTo(1.0);
        assertThat(seen.apply(once, request())).isEqualTo(0.25);
        assertThat(seen.apply(opened, request())).isEqualTo(0.15);
    }

    @Test
    void underAChipSeenPostsOnlyGoDownALittle() {
        ScoreFactors.Seen seen = new ScoreFactors.Seen();
        FeedCandidate opened = new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now));
        opened.seen(seen(5, true, now.minusHours(1)));
        FeedFilterSpec cafes = FeedFilterSpec.of(List.of(), FeedPlaceKind.of(null, List.of("CAFE")), null);

        assertThat(seen.apply(opened, request(ViewerContext.GUEST, cafes, FeedTypes.FillLevel.L1))).isEqualTo(0.7);
    }

    @Test
    void fromL3APostSeenRepeatedlyComesBackFarDown() {
        ScoreFactors.Seen seen = new ScoreFactors.Seen();
        FeedCandidate repeated = new FeedCandidate(FeedTestSupport.checkin(UUID.randomUUID(), now));
        repeated.seen(seen(3, false, now.minusHours(5)));

        assertThat(seen.apply(repeated, request(ViewerContext.GUEST, everything, FeedTypes.FillLevel.L3)))
                .isCloseTo(0.25 * 0.3, within(1e-9));
    }

    @Test
    void repeatedlySeenNeedsTwoSightingsWithinThreeDays() {
        ScoreFactor.Request request = request();

        assertThat(ScoreFactors.Seen.repeatedlySeen(seen(2, false, now.minusDays(1)), request)).isTrue();
        assertThat(ScoreFactors.Seen.repeatedlySeen(seen(1, false, now.minusDays(1)), request)).isFalse();
        assertThat(ScoreFactors.Seen.repeatedlySeen(seen(4, false, now.minusDays(4)), request)).isFalse();
    }

    @Test
    void theStoredScoreStandsInForEngagementQualityAndNegative() {
        FeedItemRow row = FeedTestSupport.checkin(UUID.randomUUID(), now);
        row.setEngagementScore(3.5f);
        FeedItemRow unscored = FeedTestSupport.checkin(UUID.randomUUID(), now);
        unscored.setEngagementScore(0);

        assertThat(new ScoreFactors.Stored().apply(new FeedCandidate(row), request())).isEqualTo(3.5);
        assertThat(new ScoreFactors.Stored().apply(new FeedCandidate(unscored), request())).isEqualTo(1.0);
    }

    private static FeedViewerRows.Seen seen(int count, boolean opened, LocalDateTime lastSeenAt) {
        FeedViewerRows.Seen seen = new FeedViewerRows.Seen();
        seen.setItemType("CHECKIN");
        seen.setItemId(UUID.randomUUID());
        seen.setSeenCount(count);
        seen.setOpened(opened);
        seen.setLastSeenAt(lastSeenAt);
        return seen;
    }
}
