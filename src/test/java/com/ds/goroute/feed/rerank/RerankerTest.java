package com.ds.goroute.feed.rerank;

import com.ds.goroute.dto.FeedPlaceKind;
import com.ds.goroute.feed.FeedCandidate;
import com.ds.goroute.feed.FeedFilterSpec;
import com.ds.goroute.feed.FeedTestSupport;
import com.ds.goroute.feed.FeedTypes;
import com.ds.goroute.feed.FeedWeights;
import com.ds.goroute.feed.ViewerContext;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.type.FeedItemType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RerankerTest {

    private final LocalDateTime now = LocalDateTime.now();
    private final FeedWeights w = FeedTestSupport.defaults();
    private final FeedFilterSpec everything = FeedFilterSpec.of(List.of(), null, null);
    private final Reranker reranker = new Reranker(List.of(
            new RerankRules.Author(),
            new RerankRules.TypeRun(),
            new RerankRules.Province(),
            new RerankRules.PhotolessTrip(),
            new RerankRules.ContextCap(),
            new RerankRules.PlaceChip(),
            new RerankRules.InNetworkShare()));

    private FeedCandidate candidate(FeedItemRow row, double score) {
        // Old and widely seen, so it never takes an exploration slot unless a test wants it.
        row.setPublishedAt(row.getPublishedAt() == null ? now.minusDays(10) : row.getPublishedAt());
        row.setImpressionCount(row.getImpressionCount() == 0 ? 500 : row.getImpressionCount());
        FeedCandidate candidate = new FeedCandidate(row);
        candidate.score(score);
        return candidate;
    }

    private FeedCandidate checkin(UUID author, double score) {
        return candidate(FeedTestSupport.checkin(author, null), score);
    }

    private List<FeedCandidate> rerank(List<FeedCandidate> candidates, ViewerContext context, FeedFilterSpec spec) {
        return reranker.rerank(candidates, 500, context, spec, w, now);
    }

    @Test
    void anAuthorNeverHasMoreThanTwoPostsInTen() {
        UUID prolific = UUID.randomUUID();
        List<FeedCandidate> candidates = new ArrayList<>();
        IntStream.range(0, 10).forEach(i -> candidates.add(checkin(prolific, 100 - i)));
        IntStream.range(0, 10).forEach(i -> candidates.add(checkin(UUID.randomUUID(), 10 - i * 0.1)));

        List<FeedCandidate> ranked = rerank(candidates, ViewerContext.GUEST, everything);

        List<FeedCandidate> firstTen = ranked.subList(0, 10);
        assertThat(firstTen.stream().filter(c -> c.row().getAuthorId().equals(prolific)).count()).isEqualTo(2);
    }

    @Test
    void theRulesShapeTheListButNeverEmptyIt() {
        UUID onlyAuthor = UUID.randomUUID();
        List<FeedCandidate> candidates = new ArrayList<>();
        IntStream.range(0, 7).forEach(i -> candidates.add(checkin(onlyAuthor, 10 - i)));

        List<FeedCandidate> ranked = rerank(candidates, ViewerContext.GUEST, everything);

        assertThat(ranked).hasSize(7).containsExactlyInAnyOrderElementsOf(candidates);
    }

    @Test
    void notThreePostsOfOneKindInARow() {
        List<FeedCandidate> candidates = new ArrayList<>();
        IntStream.range(0, 5).forEach(i -> candidates.add(checkin(UUID.randomUUID(), 10 - i * 0.01)));
        FeedItemRow review = FeedTestSupport.review(UUID.randomUUID(), null);
        candidates.add(candidate(review, 9));

        List<FeedCandidate> ranked = rerank(candidates, ViewerContext.GUEST, everything);

        assertThat(ranked.get(2).row().type()).isEqualTo(FeedItemType.REVIEW);
    }

    @Test
    void aPhotolessTripStaysOutOfTheTopThreeAndAppearsOncePerTen() {
        List<FeedCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            candidates.add(candidate(FeedTestSupport.trip(UUID.randomUUID(), null), 100 - i));
        }
        IntStream.range(0, 12).forEach(i -> candidates.add(checkin(UUID.randomUUID(), 10 - i * 0.1)));

        List<FeedCandidate> ranked = rerank(candidates, ViewerContext.GUEST, everything);

        assertThat(ranked.subList(0, 3)).noneMatch(FeedCandidate::photolessTrip);
        assertThat(ranked.subList(0, 10).stream().filter(FeedCandidate::photolessTrip).count()).isEqualTo(1);
    }

    @Test
    void aPhotolessTripOnTheViewersRouteMayLead() {
        FeedCandidate onRoute = candidate(FeedTestSupport.trip(UUID.randomUUID(), null), 100);
        onRoute.context(1.2, "itinerary");
        List<FeedCandidate> candidates = new ArrayList<>(List.of(onRoute));
        IntStream.range(0, 5).forEach(i -> candidates.add(checkin(UUID.randomUUID(), 10)));

        List<FeedCandidate> ranked = rerank(candidates, ViewerContext.GUEST, everything);

        assertThat(ranked.get(0)).isSameAs(onRoute);
    }

    @Test
    void aTripWithPhotosIsNotHeldBack() {
        FeedItemRow withPhotos = FeedTestSupport.trip(UUID.randomUUID(), null);
        withPhotos.setPhotoCount(4);
        FeedCandidate trip = candidate(withPhotos, 100);
        List<FeedCandidate> candidates = new ArrayList<>(List.of(trip));
        IntStream.range(0, 5).forEach(i -> candidates.add(checkin(UUID.randomUUID(), 10)));

        assertThat(rerank(candidates, ViewerContext.GUEST, everything).get(0)).isSameAs(trip);
    }

    @Test
    void atMostFourContextPostsInTenWhenTheViewerHasAContext() {
        ViewerContext withDevice = ViewerContext.GUEST.toBuilder().deviceLat(21.0).deviceLng(105.8).build();
        List<FeedCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            FeedCandidate near = checkin(UUID.randomUUID(), 100 - i);
            near.context(0.6, "near");
            candidates.add(near);
        }
        IntStream.range(0, 10).forEach(i -> candidates.add(checkin(UUID.randomUUID(), 10 - i * 0.1)));

        List<FeedCandidate> ranked = rerank(candidates, withDevice, everything);

        assertThat(ranked.subList(0, 10).stream().filter(c -> c.contextBoost() >= 0.6).count()).isEqualTo(4);
    }

    @Test
    void underAPlaceChipOnePlaceGetsTwoPostsInTen() {
        UUID famousCafe = UUID.randomUUID();
        FeedFilterSpec cafes = FeedFilterSpec.of(List.of(), FeedPlaceKind.of(null, List.of("CAFE")), null);
        List<FeedCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            FeedItemRow row = FeedTestSupport.checkin(UUID.randomUUID(), null);
            row.setPlaceId(famousCafe);
            candidates.add(candidate(row, 100 - i));
        }
        IntStream.range(0, 8).forEach(i -> candidates.add(checkin(UUID.randomUUID(), 10 - i * 0.1)));

        List<FeedCandidate> ranked = rerank(candidates, ViewerContext.GUEST, cafes);

        assertThat(ranked.subList(0, 10).stream()
                .filter(c -> famousCafe.equals(c.row().getPlaceId())).count()).isEqualTo(2);
    }

    @Test
    void theFifthSlotOfEachTenGoesToANewLittleSeenPost() {
        List<FeedCandidate> candidates = new ArrayList<>();
        IntStream.range(0, 12).forEach(i -> candidates.add(checkin(UUID.randomUUID(), 100 - i)));
        FeedItemRow brandNew = FeedTestSupport.checkin(UUID.randomUUID(), now.minusHours(2));
        brandNew.setImpressionCount(3);
        FeedCandidate fresh = new FeedCandidate(brandNew);
        fresh.score(0.01);
        candidates.add(fresh);

        List<FeedCandidate> ranked = rerank(candidates, ViewerContext.GUEST, everything);

        assertThat(ranked.get(4)).isSameAs(fresh);
    }

    @Test
    void followedAuthorsAreAtMostHalfOfTenForSomeoneWhoFollowsSeveral() {
        Map<UUID, Boolean> following = new HashMap<>();
        List<FeedCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            UUID friend = UUID.randomUUID();
            following.put(friend, false);
            FeedCandidate post = checkin(friend, 100 - i);
            post.sources().add(FeedTypes.SourceKind.IN_NETWORK);
            candidates.add(post);
        }
        IntStream.range(0, 10).forEach(i -> candidates.add(checkin(UUID.randomUUID(), 40 - i * 0.1)));
        ViewerContext viewer = ViewerContext.GUEST.toBuilder().viewerId(UUID.randomUUID()).following(following).build();

        List<FeedCandidate> ranked = rerank(candidates, viewer, everything);

        assertThat(ranked.subList(0, 10).stream()
                .filter(c -> c.from(FeedTypes.SourceKind.IN_NETWORK)).count()).isLessThanOrEqualTo(5);
    }

    @Test
    void stopsAtTheRequestedSize() {
        List<FeedCandidate> candidates = new ArrayList<>();
        IntStream.range(0, 30).forEach(i -> candidates.add(checkin(UUID.randomUUID(), 30 - i)));

        assertThat(reranker.rerank(candidates, 12, ViewerContext.GUEST, everything, w, now)).hasSize(12);
    }
}
