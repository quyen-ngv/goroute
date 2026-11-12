package com.ds.goroute.service.post;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserCheckinPhoto;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.PostMediaSource;
import com.ds.goroute.type.PostMediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The author's own photos and videos of a trip, each matched to a stop: what the trip-story draft
 * is written from and what the editor's "from the trip" tray offers.
 *
 * <p>Only the author's material: their check-ins (recorded on the trip, or made during its dates
 * without picking the trip) and the trip memories they uploaded. A trip mate's photo is theirs to
 * publish. Every visibility comes along -- the author decides what goes into the story. A photo
 * that named no stop is matched to the nearest stop within the check-in radius; a photo that
 * matches nothing is still offered, unplaced.
 */
@Component
@RequiredArgsConstructor
public class TripStoryMediaCollector {

    /** The same ceiling the shared-trip page puts on a trip's check-ins. */
    static final int MAX_TRIP_CHECKINS = 200;

    /** Check-ins made this long before the first day or after the last still count as the trip's. */
    private static final int DATE_WINDOW_PAD_DAYS = 1;

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private final UserCheckinRepository checkinRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final BusinessConfigService config;

    /** One photo or video the author may put in the story. */
    public record Item(PostMediaSource sourceType, UUID sourceId, UUID checkinId, PostMediaType mediaType,
                       String url, String thumbnailUrl, Integer durationSeconds, String caption,
                       LocalDateTime takenAt, ContentVisibility visibility, UUID activityId, boolean suggested) {
    }

    /** Everything of the author's on {@code trip}, oldest first; {@code activities} are the trip's live stops. */
    public List<Item> collect(Trip trip, UUID authorId, List<Activity> activities) {
        Set<UUID> activityIds = activities.stream().map(Activity::getId).collect(Collectors.toSet());
        int radiusMeters = config.getInt(BusinessConfigKey.CHECKIN_VERIFY_RADIUS_METERS);
        List<Item> items = new ArrayList<>();

        LocalDate start = trip.getStartDate();
        LocalDate end = trip.getEndDate() == null ? start : trip.getEndDate();
        LocalDateTime from = start == null ? null : start.minusDays(DATE_WINDOW_PAD_DAYS).atStartOfDay();
        LocalDateTime to = start == null ? null : end.plusDays(DATE_WINDOW_PAD_DAYS + 1L).atStartOfDay();
        List<UserCheckin> checkins = checkinRepository.findMineForTrip(authorId, trip.getId(), from, to, MAX_TRIP_CHECKINS);
        List<UUID> checkinIds = checkins.stream().map(UserCheckin::getId).toList();
        Map<UUID, List<UserCheckinPhoto>> photosByCheckin = checkinIds.isEmpty() ? Map.of()
                : checkinRepository.findPhotosForCheckins(checkinIds).stream()
                        .sorted(Comparator.comparing(UserCheckinPhoto::getPosition,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .collect(Collectors.groupingBy(UserCheckinPhoto::getCheckinId));
        for (UserCheckin checkin : checkins) {
            UUID recorded = checkin.getActivityId() != null && activityIds.contains(checkin.getActivityId())
                    ? checkin.getActivityId() : null;
            UUID activityId = recorded != null ? recorded
                    : nearest(activities, checkin.getLatitude(), checkin.getLongitude(), radiusMeters);
            for (UserCheckinPhoto photo : photosByCheckin.getOrDefault(checkin.getId(), List.of())) {
                // A check-in video without its still frame cannot be shown as a post video.
                if (photo.isVideo() && (photo.getThumbnailUrl() == null || photo.getThumbnailUrl().isBlank())) {
                    continue;
                }
                if (photo.getUrl() == null || photo.getUrl().isBlank()) {
                    continue;
                }
                items.add(new Item(PostMediaSource.CHECKIN_PHOTO, photo.getId(), checkin.getId(),
                        photo.isVideo() ? PostMediaType.VIDEO : PostMediaType.IMAGE,
                        photo.getUrl(), photo.isVideo() ? photo.getThumbnailUrl() : null,
                        photo.isVideo() ? photo.getDurationSeconds() : null,
                        blankToNull(checkin.getCaption()),
                        photo.getCapturedAt() != null ? photo.getCapturedAt()
                                : checkin.getVisitedAt() != null ? checkin.getVisitedAt() : checkin.getCreatedAt(),
                        checkin.getVisibility() == null ? ContentVisibility.PUBLIC : checkin.getVisibility(),
                        activityId, recorded == null && activityId != null));
            }
        }

        // A memory video has no poster frame to show, so only memory photos come along.
        for (MediaAsset memory : mediaAssetRepository.findTripMemoriesByTripId(trip.getId())) {
            if (!authorId.equals(memory.getUploadedBy()) || memory.getUrl() == null || memory.getUrl().isBlank()) {
                continue;
            }
            if (memory.getMediaType() != null && !"IMAGE".equalsIgnoreCase(memory.getMediaType())) {
                continue;
            }
            UUID recorded = memory.getActivityId() != null && activityIds.contains(memory.getActivityId())
                    ? memory.getActivityId() : null;
            UUID activityId = recorded != null ? recorded
                    : nearest(activities, memory.getLatitude(), memory.getLongitude(), radiusMeters);
            items.add(new Item(PostMediaSource.TRIP_MEMORY, memory.getId(), null, PostMediaType.IMAGE,
                    memory.getUrl(), null, null, blankToNull(memory.getCaption()),
                    memory.getTakenAt() != null ? memory.getTakenAt() : memory.getCreatedAt(),
                    ContentVisibility.PUBLIC, activityId, recorded == null && activityId != null));
        }

        items.sort(Comparator.comparing(Item::takenAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    /** The closest stop within {@code radiusMeters} of a point, or null. */
    static UUID nearest(List<Activity> activities, BigDecimal lat, BigDecimal lng, int radiusMeters) {
        if (lat == null || lng == null) {
            return null;
        }
        Activity best = null;
        double bestDistance = radiusMeters;
        for (Activity activity : activities) {
            if (activity.getLat() == null || activity.getLng() == null) {
                continue;
            }
            double distance = distanceMeters(lat.doubleValue(), lng.doubleValue(),
                    activity.getLat().doubleValue(), activity.getLng().doubleValue());
            if (distance <= bestDistance) {
                best = activity;
                bestDistance = distance;
            }
        }
        return best == null ? null : best.getId();
    }

    private static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /** Items grouped by the stop they belong to; the unplaced ones under the null key. */
    public static Map<UUID, List<Item>> byActivity(List<Item> items) {
        return items.stream().collect(Collectors.groupingBy(
                item -> item.activityId() == null ? NO_ACTIVITY : item.activityId(),
                java.util.LinkedHashMap::new, Collectors.toList()));
    }

    /** The key of the unplaced group in {@link #byActivity}. */
    public static final UUID NO_ACTIVITY = new UUID(0L, 0L);
}
