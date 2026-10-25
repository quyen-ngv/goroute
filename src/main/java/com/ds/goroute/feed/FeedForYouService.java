package com.ds.goroute.feed;

import com.ds.goroute.dto.response.FeedForYouPageResponse;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.feed.FeedTypes.FillLevel;
import com.ds.goroute.feed.model.FeedCandidateQuery;
import com.ds.goroute.feed.model.FeedItemRow;
import com.ds.goroute.feed.model.FeedKey;
import com.ds.goroute.feed.rerank.Reranker;
import com.ds.goroute.feed.score.ScoreFactor;
import com.ds.goroute.feed.score.ScoreFactors;
import com.ds.goroute.feed.session.FeedSession;
import com.ds.goroute.feed.session.FeedSessionStore;
import com.ds.goroute.feed.source.CandidateSource;
import com.ds.goroute.mapper.FeedItemMapper;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserCheckinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The ranked "For you" feed (plan §4, design §1): sources -> filter -> score -> re-rank ->
 * session -> page. A ranking is built once per scroll and kept in a {@link FeedSession}; the
 * following pages are slices of it, so nothing on screen moves and nothing is scored twice.
 * When a scroll reaches the end of a full session the next ranking leaves out everything the
 * scroll already served.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedForYouService {

    public static final int MAX_PAGE_SIZE = 50;

    private final FeedRollout rollout;
    private final ViewerContextResolver contexts;
    private final FeedWeightsProvider weightsProvider;
    private final FeedItemMapper itemMapper;
    private final List<CandidateSource> sources;
    private final List<ScoreFactor> factors;
    private final Reranker reranker;
    private final FeedSessionStore sessions;
    private final UserCheckinService checkinService;
    private final ReviewService reviewService;
    private final TripService tripService;

    public FeedForYouPageResponse page(UUID viewerId, FeedFilterSpec spec, String cursor, int size,
                                       Double deviceLat, Double deviceLng, boolean contextOff,
                                       Set<UUID> contextOffTrips, boolean debug) {
        if (!rollout.enabledFor(viewerId)) {
            return FeedForYouPageResponse.builder()
                    .enabled(false)
                    .variant(FeedRollout.LEGACY)
                    .items(List.of())
                    .build();
        }
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        boolean explain = debug && rollout.isBeta(viewerId);
        FeedWeights w = weightsProvider.get();
        int sessionSize = w.i("caps.sessionSize", 500);

        Cursor at = Cursor.decode(cursor);
        FeedSession session = at == null ? null : sessions.get(at.sessionId())
                .filter(found -> java.util.Objects.equals(found.viewerId(), viewerId))
                .filter(found -> found.filterKey().equals(spec.key()))
                .orElse(null);
        boolean restarted = at != null && session == null;
        int offset = session == null ? 0 : Math.max(0, at.offset());

        if (session == null) {
            session = rank(viewerId, spec, w, Set.of(), deviceLat, deviceLng, contextOff, contextOffTrips, explain);
        } else if (offset >= session.ranked().size() && session.full(sessionSize)) {
            // The end of a full ranking: rank again without everything this scroll served.
            Set<String> served = new HashSet<>(session.servedBefore());
            session.ranked().forEach(key -> served.add(key.key()));
            session = rank(viewerId, spec, w, served, deviceLat, deviceLng, contextOff, contextOffTrips, explain);
            offset = 0;
        }

        List<FeedKey> slice = offset >= session.ranked().size() ? List.of()
                : session.ranked().subList(offset, Math.min(offset + pageSize, session.ranked().size()));
        int nextOffset = offset + slice.size();
        boolean hasMore = nextOffset < session.ranked().size() || (session.full(sessionSize) && !slice.isEmpty());

        return FeedForYouPageResponse.builder()
                .enabled(true)
                .variant(FeedRollout.RANKED)
                .sessionId(session.id())
                .nextCursor(hasMore ? new Cursor(session.id(), nextOffset).encode() : null)
                .hasMore(hasMore)
                .fillLevel(session.fillLevel().name())
                .restarted(restarted)
                .context(session.context())
                .items(enrich(viewerId, slice, session))
                .build();
    }

    // ---------------------------------------------------------------------------------------
    // Ranking

    private FeedSession rank(UUID viewerId, FeedFilterSpec spec, FeedWeights w, Set<String> exclude,
                             Double deviceLat, Double deviceLng, boolean contextOff, Set<UUID> contextOffTrips,
                             boolean explain) {
        LocalDateTime now = LocalDateTime.now();
        ViewerContext context = contexts.resolve(viewerId, deviceLat, deviceLng, contextOff, contextOffTrips);
        int minCandidates = w.i("caps.minCandidates", 120);
        int maxCandidates = w.i("caps.maxCandidates", 1400);

        Map<String, FeedCandidate> pool = new LinkedHashMap<>();
        FillLevel level = FillLevel.L1;
        List<FeedCandidate> usable = List.of();
        for (FillLevel attempt : List.of(FillLevel.L1, FillLevel.L2, FillLevel.L3, FillLevel.L4)) {
            level = attempt;
            collect(pool, context, spec, w, attempt, maxCandidates);
            usable = usable(pool, exclude, context, spec, w, attempt, now);
            if (usable.size() >= minCandidates) {
                break;
            }
        }
        if (usable.size() < 20) {
            level = FillLevel.L5;
        }

        ScoreFactor.Request request = new ScoreFactor.Request(context, spec, w, level, now);
        for (FeedCandidate candidate : usable) {
            annotate(candidate, context, w);
            double score = 1.0;
            for (ScoreFactor factor : factors) {
                double value = factor.apply(candidate, request);
                score *= value;
                if (explain) {
                    candidate.explain().put(factor.name(), round(value));
                }
            }
            candidate.score(score);
        }

        List<FeedCandidate> ranked = reranker.rerank(usable, w.i("caps.sessionSize", 500), context, spec, w, now);
        Map<String, FeedForYouPageResponse.Rank> ranks = explain ? new HashMap<>() : Map.of();
        if (explain) {
            for (FeedCandidate candidate : ranked) {
                ranks.put(candidate.keyString(), FeedForYouPageResponse.Rank.builder()
                        .score(round(candidate.score()))
                        .sources(candidate.sources().stream().map(Enum::name).toList())
                        .factors(Map.copyOf(candidate.explain()))
                        .context(candidate.contextReason())
                        .build());
            }
        }

        FeedSession session = new FeedSession(
                UUID.randomUUID().toString(),
                viewerId,
                spec.key(),
                ranked.stream().map(FeedCandidate::key).toList(),
                Set.copyOf(exclude),
                level,
                contextLine(context),
                explain,
                ranks);
        sessions.put(session);
        if (log.isDebugEnabled()) {
            log.debug("Feed ranking for {}: {} candidates, {} ranked, level {}",
                    viewerId, pool.size(), ranked.size(), level);
        }
        return session;
    }

    /** Runs every applicable source at {@code level} and merges into {@code pool}. */
    private void collect(Map<String, FeedCandidate> pool, ViewerContext context, FeedFilterSpec spec,
                         FeedWeights w, FillLevel level, int maxCandidates) {
        FeedCandidateQuery base = baseQuery(context, spec, w, level);
        for (CandidateSource source : sources) {
            if (pool.size() >= maxCandidates) {
                break;
            }
            if (!source.applies(context, spec)) {
                continue;
            }
            FeedCandidateQuery query = source.query(base, context, w, level);
            if (query == null) {
                continue;
            }
            List<FeedItemRow> rows;
            try {
                rows = itemMapper.selectCandidates(query);
            } catch (RuntimeException exception) {
                // One source failing costs its posts, not the feed.
                log.warn("Feed source {} failed: {}", source.kind(), exception.getMessage());
                continue;
            }
            for (FeedItemRow row : rows) {
                pool.computeIfAbsent(row.key().key(), key -> new FeedCandidate(row)).sources().add(source.kind());
            }
        }
    }

    private static FeedCandidateQuery baseQuery(ViewerContext context, FeedFilterSpec spec, FeedWeights w,
                                                FillLevel level) {
        FeedCandidateQuery.FeedCandidateQueryBuilder base = FeedCandidateQuery.builder()
                .viewerId(context.getViewerId())
                .itemTypes(spec.itemTypes().isEmpty() ? null : spec.itemTypeNames())
                .ignoreRecommendGate(spec.tripsChip())
                .relaxedRecommendGate(level.atLeast(FillLevel.L4))
                .limit(100);
        if (spec.kind() != null) {
            base.placeGroups(spec.kind().getPlaceGroups())
                    .subTypes(spec.kind().getSubTypes())
                    .chipTripMinStops(w.i("chipTrips.minStops", 3))
                    .chipTripMinRatio(w.d("chipTrips.minRatio", 0.4));
        }
        if (spec.geo() != null) {
            base.radiusLat(spec.geo().latitude().doubleValue())
                    .radiusLng(spec.geo().longitude().doubleValue())
                    .radiusMeters(spec.geo().radiusKm().doubleValue() * 1000);
        }
        return base.build();
    }

    /**
     * The pool without what this scroll already served and, until L3, without the posts the
     * viewer saw repeatedly lately (D8). Under a chip nothing seen is left out.
     */
    private static List<FeedCandidate> usable(Map<String, FeedCandidate> pool, Set<String> exclude,
                                              ViewerContext context, FeedFilterSpec spec, FeedWeights w,
                                              FillLevel level, LocalDateTime now) {
        ScoreFactor.Request request = new ScoreFactor.Request(context, spec, w, level, now);
        boolean dropRepeated = !spec.narrowed() && !level.atLeast(FillLevel.L3);
        List<FeedCandidate> usable = new ArrayList<>(pool.size());
        for (FeedCandidate candidate : pool.values()) {
            String key = candidate.keyString();
            if (exclude.contains(key)) {
                continue;
            }
            candidate.seen(context.getSeen().get(key));
            if (dropRepeated && candidate.seen() != null
                    && ScoreFactors.Seen.repeatedlySeen(candidate.seen(), request)) {
                continue;
            }
            usable.add(candidate);
        }
        return usable;
    }

    /** Context match (plan §4.9), social proof. The strongest context wins. */
    private static void annotate(FeedCandidate candidate, ViewerContext context, FeedWeights w) {
        FeedItemRow row = candidate.row();
        String province = row.getProvinceCode();
        boolean tripContext = !context.isContextOff();
        if (tripContext && ((row.getPlaceId() != null && context.getItineraryPlaceIds().contains(row.getPlaceId()))
                || (row.isTrip() && candidate.from(FeedTypes.SourceKind.ITINERARY)))) {
            candidate.context(w.d("context.itineraryPlace", 1.2), "itinerary");
        }
        if (tripContext && province != null && context.getTodayProvinces().contains(province)) {
            candidate.context(w.d("context.todayDestination", 0.8), "today");
        }
        if (context.hasDevice() && row.getLatitude() != null && row.getLongitude() != null) {
            double km = distanceKm(context.getDeviceLat(), context.getDeviceLng(), row.getLatitude(), row.getLongitude());
            if (km <= w.d("context.nearDeviceKm", 30)) {
                candidate.nearDevice(true);
                candidate.context(w.d("context.nearDevice", 0.6), "near");
            }
        }
        if (tripContext && province != null && context.getUpcomingProvinces().contains(province)) {
            candidate.context(w.d("context.upcomingTrip", 0.5), "upcoming");
        }
        if (province != null && context.getRelatedProvinces().contains(province)) {
            candidate.context(w.d("context.relatedProvince", 0.3), "related");
        }
        Integer people = context.getSocialProof().get(candidate.keyString());
        candidate.socialPeople(people == null ? 0 : people);
    }

    private FeedForYouPageResponse.Context contextLine(ViewerContext context) {
        if (!context.tripContextActive()) {
            return null;
        }
        String province = context.getTodayProvinces().stream().sorted().findFirst().orElse(null);
        return FeedForYouPageResponse.Context.builder()
                .tripId(context.getOngoingTripId())
                .tripName(context.getOngoingTripName())
                .provinceName(contexts.provinceName(province))
                .build();
    }

    // ---------------------------------------------------------------------------------------
    // Page

    /**
     * The page's posts as the three feeds return them, in ranked order. A post that went
     * private, was deleted or taken down since it was ranked is left out of the page.
     */
    private List<FeedForYouPageResponse.Item> enrich(UUID viewerId, List<FeedKey> slice, FeedSession session) {
        if (slice.isEmpty()) {
            return List.of();
        }
        Map<String, List<UUID>> idsByType = slice.stream().collect(Collectors.groupingBy(
                FeedKey::getItemType, Collectors.mapping(FeedKey::getItemId, Collectors.toList())));
        Map<UUID, UserCheckinResponse> checkins = idsByType.containsKey("CHECKIN")
                ? checkinService.findVisibleByIds(viewerId, idsByType.get("CHECKIN")) : Map.of();
        Map<UUID, UserReviewResponse> reviews = idsByType.containsKey("REVIEW")
                ? reviewService.findVisibleByIds(viewerId, idsByType.get("REVIEW")) : Map.of();
        Map<UUID, PublicTripResponse> trips = idsByType.containsKey("TRIP")
                ? tripService.getPublicTripCards(viewerId, idsByType.get("TRIP")).stream()
                        .collect(Collectors.toMap(PublicTripResponse::getId, Function.identity(), (a, b) -> a))
                : Map.of();

        List<FeedForYouPageResponse.Item> items = new ArrayList<>(slice.size());
        for (FeedKey key : slice) {
            FeedForYouPageResponse.Item.ItemBuilder item = FeedForYouPageResponse.Item.builder().type(key.getItemType());
            switch (key.getItemType()) {
                case "CHECKIN" -> {
                    UserCheckinResponse checkin = checkins.get(key.getItemId());
                    if (checkin == null) {
                        continue;
                    }
                    item.checkin(checkin);
                }
                case "REVIEW" -> {
                    UserReviewResponse review = reviews.get(key.getItemId());
                    if (review == null) {
                        continue;
                    }
                    item.review(review);
                }
                case "TRIP" -> {
                    PublicTripResponse trip = trips.get(key.getItemId());
                    if (trip == null) {
                        continue;
                    }
                    item.trip(trip);
                }
                default -> {
                    continue;
                }
            }
            if (session.debug()) {
                item.rank(session.explain().get(key.key()));
            }
            items.add(item.build());
        }
        return items;
    }

    // ---------------------------------------------------------------------------------------

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** "sessionId:offset", URL-safe Base64. A cursor that does not decode starts a new ranking. */
    record Cursor(String sessionId, int offset) {

        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((sessionId + ':' + offset).getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String cursor) {
            if (cursor == null || cursor.isBlank() || cursor.length() > 200) {
                return null;
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int colon = raw.lastIndexOf(':');
                if (colon <= 0) {
                    return null;
                }
                return new Cursor(raw.substring(0, colon), Integer.parseInt(raw.substring(colon + 1)));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
