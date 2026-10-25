package com.ds.goroute.feed;

import com.ds.goroute.feed.model.FeedViewerRows;
import com.ds.goroute.mapper.FeedViewerMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds {@link ViewerContext}. The slow-moving part (who they follow, affinity, trips,
 * itinerary, related provinces, what their people engaged with) is cached five minutes per
 * viewer; what they have seen is read on every request, so a post seen a minute ago already
 * counts; the device position comes with the request and is never cached or stored.
 */
@Component
public class ViewerContextResolver {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final FeedViewerMapper viewerMapper;
    private final FeedWeightsProvider weights;
    private final Cache<UUID, ViewerContext> stable = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(20_000)
            .build();

    public ViewerContextResolver(FeedViewerMapper viewerMapper, FeedWeightsProvider weights) {
        this.viewerMapper = viewerMapper;
        this.weights = weights;
    }

    public ViewerContext resolve(UUID viewerId, Double deviceLat, Double deviceLng, boolean contextOff,
                                 Set<UUID> contextOffTrips) {
        FeedWeights w = weights.get();
        ViewerContext base = viewerId == null ? ViewerContext.GUEST
                : stable.get(viewerId, id -> loadStable(id, w));

        String deviceProvince = null;
        boolean hasDevice = deviceLat != null && deviceLng != null
                && Math.abs(deviceLat) <= 90 && Math.abs(deviceLng) <= 180;
        if (hasDevice) {
            deviceProvince = viewerMapper.selectProvinceAt(deviceLat, deviceLng);
        }
        Set<String> related = base.getRelatedProvinces();
        if (deviceProvince != null && !related.contains(deviceProvince)) {
            related = new HashSet<>(related);
            related.add(deviceProvince);
        }

        Map<String, FeedViewerRows.Seen> seen = Map.of();
        if (viewerId != null) {
            List<FeedViewerRows.Seen> rows = viewerMapper.selectSeenSince(viewerId,
                    LocalDateTime.now().minusDays(w.i("seen.windowDays", 14)), w.i("seen.limit", 5000));
            seen = rows.stream().collect(Collectors.toMap(
                    row -> row.getItemType() + ':' + row.getItemId(), row -> row, (first, second) -> first));
        }

        return base.toBuilder()
                .deviceLat(hasDevice ? deviceLat : null)
                .deviceLng(hasDevice ? deviceLng : null)
                .deviceProvince(deviceProvince)
                .relatedProvinces(Set.copyOf(related))
                .contextOff(contextOff || (base.getOngoingTripId() != null
                        && contextOffTrips != null && contextOffTrips.contains(base.getOngoingTripId())))
                .seen(seen)
                .build();
    }

    /** Drops the cached part, e.g. after the viewer follows someone. */
    public void forget(UUID viewerId) {
        if (viewerId != null) {
            stable.invalidate(viewerId);
        }
    }

    private ViewerContext loadStable(UUID viewerId, FeedWeights w) {
        Map<UUID, Boolean> following = new LinkedHashMap<>();
        for (FeedViewerRows.Followed followed : viewerMapper.selectFollowing(viewerId,
                w.i("caps.followingLimit", 500))) {
            following.put(followed.getUserId(), followed.isMutual());
        }
        Map<UUID, Float> affinity = viewerMapper.selectAffinity(viewerId, w.i("affinityJob.perViewer", 300))
                .stream().collect(Collectors.toMap(FeedViewerRows.Affinity::getAuthorId,
                        FeedViewerRows.Affinity::getScore, (first, second) -> first));

        // Trips under way today or starting within the upcoming window.
        LocalDate today = LocalDate.now(ZONE);
        List<FeedViewerRows.ContextTrip> trips = viewerMapper.selectContextTrips(viewerId, today,
                today.plusDays(w.i("context.upcomingDays", 14)));
        FeedViewerRows.ContextTrip ongoing = trips.stream()
                .filter(trip -> !trip.getStartDate().isAfter(today) && !trip.getEndDate().isBefore(today))
                .findFirst().orElse(null);
        Set<String> todayProvinces = new HashSet<>();
        Set<String> upcomingProvinces = new HashSet<>();
        Set<UUID> itinerary = new HashSet<>();
        if (!trips.isEmpty()) {
            List<UUID> tripIds = trips.stream().map(FeedViewerRows.ContextTrip::getTripId).toList();
            List<FeedViewerRows.ContextDestination> destinations = viewerMapper.selectContextDestinations(tripIds);
            for (FeedViewerRows.ContextDestination destination : destinations) {
                if (destination.getProvinceCode() == null) {
                    continue;
                }
                if (ongoing != null && ongoing.getTripId().equals(destination.getTripId())) {
                    if (coversToday(destination, today)) {
                        todayProvinces.add(destination.getProvinceCode());
                    }
                } else {
                    upcomingProvinces.add(destination.getProvinceCode());
                }
            }
            if (ongoing != null && todayProvinces.isEmpty()) {
                // No destination dated today: the trip's primary (or only) destination stands in.
                destinations.stream()
                        .filter(destination -> ongoing.getTripId().equals(destination.getTripId()))
                        .filter(destination -> destination.getProvinceCode() != null)
                        .sorted((a, b) -> Boolean.compare(b.isPrimary(), a.isPrimary()))
                        .findFirst()
                        .ifPresent(destination -> todayProvinces.add(destination.getProvinceCode()));
            }
            itinerary.addAll(viewerMapper.selectItineraryPlaceIds(tripIds, 300));
        }

        Set<String> related = new HashSet<>(viewerMapper.selectRelatedProvinces(viewerId,
                w.i("context.recentCheckins", 10)));

        Map<String, Integer> social = Map.of();
        if (!following.isEmpty()) {
            List<UUID> socialPeople = following.keySet().stream()
                    .limit(w.i("caps.socialFollowingLimit", 200)).toList();
            social = viewerMapper.selectSocialProof(socialPeople,
                            LocalDateTime.now().minusDays(w.i("windowsDays.SOCIAL", 14)),
                            w.i("caps.socialInteractions", 2000))
                    .stream().collect(Collectors.toMap(row -> row.getItemType() + ':' + row.getItemId(),
                            FeedViewerRows.SocialProof::getPeople, (first, second) -> first, LinkedHashMap::new));
        }

        return ViewerContext.builder()
                .viewerId(viewerId)
                .following(following)
                .affinity(affinity)
                .ongoingTripId(ongoing == null ? null : ongoing.getTripId())
                .ongoingTripName(ongoing == null ? null : ongoing.getName())
                .todayProvinces(Set.copyOf(todayProvinces))
                .upcomingProvinces(Set.copyOf(upcomingProvinces))
                .itineraryPlaceIds(Set.copyOf(itinerary))
                .relatedProvinces(Set.copyOf(related))
                .seen(Map.of())
                .socialProof(social)
                .build();
    }

    private static boolean coversToday(FeedViewerRows.ContextDestination destination, LocalDate today) {
        LocalDate start = destination.getStartDate();
        LocalDate end = destination.getEndDate() != null ? destination.getEndDate() : start;
        return start != null && !start.isAfter(today) && end != null && !end.isBefore(today);
    }

    /** The province's display name for the app's context line. */
    public String provinceName(String code) {
        return code == null ? null : viewerMapper.selectProvinceName(code);
    }
}
