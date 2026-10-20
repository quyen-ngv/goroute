package com.ds.goroute.service.notification;

import com.ds.goroute.dto.response.CheckinPhotoResponse;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.FollowerFanoutLog;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.FollowerFanoutLogRepository;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.UserRealtimePublisher;
import com.ds.goroute.service.external.FirebaseService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.FollowerFanoutStatus;
import com.ds.goroute.type.FollowingPostType;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.UserRealtimeEventType;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tells an author's followers that they checked in or shared a trip (epic 12).
 *
 * <p>Runs on its own thread after the post committed (FollowerFanoutListener) and walks the
 * followers a chunk at a time, in follower-id order. Per chunk: one INSERT for every row (the
 * unique (user_id, dedupe_key) of V242 makes a replay write nothing), one query for the devices,
 * and one FCM multicast per language and 500 tokens. Ten thousand followers are 20 chunks.
 *
 * <p>Not realtime on purpose: a short pause between chunks keeps a big fan-out from crowding the
 * database. Progress is written to {@code follower_fanout_logs} as it goes; that table is a trace
 * and nothing polls it.
 *
 * <p>Pushes are throttled twice. Per author: within the cooldown only the in-app row is written,
 * and the next push after it counts the posts that were held back. Per recipient: past the daily
 * maximum of follower posts, rows are still written but not pushed. The moments popup reads the
 * rows, so a follower who opens the app during a cooldown still sees every post.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowerFanoutService {

    /** The daily cap is a rolling day: the database counts rows of the last 24 hours. */
    private static final int DAILY_CAP_WINDOW_HOURS = 24;

    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;
    private final UserRepository userRepository;
    private final UserCheckinRepository checkinRepository;
    private final UserCheckinService checkinService;
    private final TripRepository tripRepository;
    private final TripService tripService;
    private final ContentModerationService contentModerationService;
    private final NotificationRepository notificationRepository;
    private final FollowerFanoutLogRepository logRepository;
    private final FirebaseService firebaseService;
    private final UserRealtimePublisher realtimePublisher;
    private final Gson gson;

    public void fanOutCheckin(UUID checkinId, UUID authorId) {
        fanOut(FollowingPostType.CHECKIN, checkinId, authorId);
    }

    public void fanOutTrip(UUID tripId, UUID ownerId) {
        fanOut(FollowingPostType.TRIP, tripId, ownerId);
    }

    private void fanOut(FollowingPostType postType, UUID postId, UUID authorId) {
        if (postId == null || authorId == null) {
            return;
        }
        // Off: only followers on the beta list are told. Nobody on it means nothing to do, and no
        // trace row for every post of a feature nobody can see yet.
        Set<String> betaUsernames = config.getBoolean(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_ENABLED)
                ? null
                : betaAccess.betaUsernames();
        if (betaUsernames != null && betaUsernames.isEmpty()) {
            return;
        }
        Optional<Map<String, Object>> content = describe(postType, postId, authorId);
        if (content.isEmpty()) {
            return;
        }
        if (postType == FollowingPostType.TRIP && !tripRepository.claimFollowersNotification(postId)) {
            // Announced before, or private again by now.
            return;
        }

        int cooldownMinutes = config.getInt(BusinessConfigKey.SOCIAL_FOLLOWER_PUSH_COOLDOWN_MINUTES);
        boolean pushEnabled = !logRepository.isPushCoolingDown(authorId, cooldownMinutes);
        int postCount = pushEnabled ? logRepository.countHeldBackSinceLastPush(authorId) + 1 : 1;

        FollowerFanoutLog trace = FollowerFanoutLog.builder()
                .id(UUID.randomUUID())
                .postType(postType)
                .postId(postId)
                .authorId(authorId)
                .status(FollowerFanoutStatus.RUNNING)
                .pushEnabled(pushEnabled)
                .postCount(postCount)
                .build();
        logRepository.insert(trace);

        try {
            FollowerFanoutStatus outcome = walkFollowers(trace, content.get(), betaUsernames);
            logRepository.finish(trace.getId(), outcome, null);
            log.info("FOLLOWER_FANOUT_{} {} {} by {}: followers={} notified={} pushed={}", outcome,
                    postType, postId, authorId, trace.getFollowerTotal(), trace.getNotifiedCount(),
                    trace.getPushedCount());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            logRepository.finish(trace.getId(), FollowerFanoutStatus.FAILED, "interrupted");
        } catch (RuntimeException failure) {
            log.error("FOLLOWER_FANOUT_FAILED {} {} by {} after {} followers: {}", postType, postId,
                    authorId, trace.getFollowerTotal(), failure.getMessage(), failure);
            logRepository.finish(trace.getId(), FollowerFanoutStatus.FAILED, failure.getMessage());
        }
    }

    private FollowerFanoutStatus walkFollowers(FollowerFanoutLog trace, Map<String, Object> rowData,
                                               Set<String> betaUsernames) throws InterruptedException {
        FollowingPostType postType = trace.getPostType();
        NotificationType type = postType.notificationType();
        String dedupeKey = postType.dedupeKey(trace.getPostId());
        String rowJson = gson.toJson(rowData);
        Map<String, Object> pushData = new LinkedHashMap<>(rowData);
        pushData.put("postCount", trace.getPostCount());

        int batchSize = config.getInt(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_BATCH_SIZE);
        int pauseMs = config.getInt(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_PAUSE_MS);
        int dailyMax = config.getInt(BusinessConfigKey.SOCIAL_FOLLOWER_PUSH_DAILY_MAX);
        Map<String, Object> realtimePayload = Map.of("type", type.name());

        UUID after = null;
        boolean isFirstChunk = true;
        while (true) {
            // A post deleted, made private or taken down mid-walk stops the rest of its followers
            // being told. The first chunk was checked by the caller a moment ago.
            if (!isFirstChunk && !isStillVisible(postType, trace.getPostId())) {
                return FollowerFanoutStatus.CANCELLED;
            }
            List<UUID> chunk = userRepository.findNotifiableFollowerIds(
                    trace.getAuthorId(), after, betaUsernames, batchSize);
            if (chunk.isEmpty()) {
                return FollowerFanoutStatus.DONE;
            }
            List<UUID> notified = notificationRepository.insertFollowerNotifications(
                    chunk, type, rowJson, trace.getAuthorId(), dedupeKey);

            int pushed = 0;
            if (trace.isPushEnabled() && !notified.isEmpty()) {
                Set<UUID> pushable = new HashSet<>(notified);
                pushable.removeAll(notificationRepository.findOverFollowingDailyCap(
                        notified, DAILY_CAP_WINDOW_HOURS, dailyMax));
                pushed = firebaseService.sendPushToUsers(pushable, type, pushData);
            }
            // Badge and list refresh for whoever has the app open; no per-person unread count,
            // which would be a count query per follower.
            notified.forEach(recipientId -> realtimePublisher.send(
                    UserRealtimeEventType.NOTIFICATION_CHANGED, recipientId, realtimePayload));

            after = chunk.get(chunk.size() - 1);
            trace.setLastFollowerId(after);
            trace.setFollowerTotal(trace.getFollowerTotal() + chunk.size());
            trace.setNotifiedCount(trace.getNotifiedCount() + notified.size());
            trace.setPushedCount(trace.getPushedCount() + pushed);
            logRepository.updateProgress(trace);

            if (chunk.size() < batchSize) {
                return FollowerFanoutStatus.DONE;
            }
            isFirstChunk = false;
            if (pauseMs > 0) {
                Thread.sleep(pauseMs);
            }
        }
    }

    /**
     * What every row and push of this post says, or empty when the post is not public any more.
     * Built once for the whole fan-out: the wording does not depend on the reader.
     */
    private Optional<Map<String, Object>> describe(FollowingPostType postType, UUID postId, UUID authorId) {
        Optional<User> author = userRepository.findById(authorId);
        if (author.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", postType.notificationType().name());
        data.put("postType", postType.name());
        data.put("postId", postId.toString());
        data.put("targetType", postType.name());
        data.put("targetId", postId.toString());
        data.put("authorId", authorId.toString());
        putIfPresent(data, "actorName", displayName(author.get()));
        putIfPresent(data, "actorAvatarUrl", author.get().getAvatarUrl());

        if (postType == FollowingPostType.CHECKIN) {
            UserCheckinResponse checkin = checkinService.findVisibleByIds(authorId, List.of(postId)).get(postId);
            if (checkin == null || !authorId.equals(checkin.getUserId())) {
                return Optional.empty();
            }
            putIfPresent(data, "placeName", firstNonBlank(checkin.getCustomName(), checkin.getPlaceName(),
                    checkin.getLocationName()));
            putIfPresent(data, "imageUrl", firstPhoto(checkin));
            data.put("deepLink", "/checkins/" + postId);
        } else {
            List<PublicTripResponse> cards = tripService.getPublicTripCards(authorId, List.of(postId));
            if (cards.isEmpty() || !authorId.equals(cards.get(0).getOwnerId())) {
                return Optional.empty();
            }
            PublicTripResponse trip = cards.get(0);
            putIfPresent(data, "tripTitle", trip.getName());
            putIfPresent(data, "imageUrl", firstNonBlank(trip.getCoverImageUrl(),
                    trip.getMemoryImageUrls() == null || trip.getMemoryImageUrls().isEmpty()
                            ? null : trip.getMemoryImageUrls().get(0)));
            data.put("deepLink", "/tour/trip/" + postId);
        }
        return Optional.of(data);
    }

    private boolean isStillVisible(FollowingPostType postType, UUID postId) {
        if (postType == FollowingPostType.TRIP) {
            return !tripRepository.findPublicByIds(List.of(postId)).isEmpty();
        }
        return checkinRepository.findById(postId)
                .filter(checkin -> checkin.getVisibility() == ContentVisibility.PUBLIC)
                .filter(checkin -> !Boolean.TRUE.equals(checkin.getIsRemoved()))
                .filter(checkin -> contentModerationService
                        .takenDownIds(ModeratedContentType.CHECKIN, List.of(postId)).isEmpty())
                .isPresent();
    }

    private static String firstPhoto(UserCheckinResponse checkin) {
        if (checkin.getPhotos() == null) {
            return null;
        }
        return checkin.getPhotos().stream()
                .map(CheckinPhotoResponse::getUrl)
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String displayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().trim();
        }
        return user.getUsername() == null || user.getUsername().isBlank() ? null : user.getUsername().trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }
}
