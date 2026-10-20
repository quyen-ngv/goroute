package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.FollowingMomentRefRequest;
import com.ds.goroute.dto.response.FollowingMomentResponse;
import com.ds.goroute.dto.response.FollowingMomentsResponse;
import com.ds.goroute.dto.response.PublicTripResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.Notification;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.FollowingMomentsService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.notification.NotificationChangePublisher;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.FollowingPostType;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowingMomentsServiceImpl implements FollowingMomentsService {

    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;
    private final NotificationRepository notificationRepository;
    private final NotificationChangePublisher changePublisher;
    private final UserBlockService blockService;
    private final UserCheckinService checkinService;
    private final TripService tripService;
    private final Gson gson;

    @Override
    @Transactional
    public FollowingMomentsResponse unread(UUID viewerId) {
        if (!isEnabledFor(viewerId)) {
            return FollowingMomentsResponse.builder().enabled(false).items(List.of()).build();
        }
        int windowHours = config.getInt(BusinessConfigKey.SOCIAL_MOMENTS_WINDOW_HOURS);
        List<Moment> moments = notificationRepository.findUnreadFollowingPosts(viewerId, windowHours, MAX_ITEMS)
                .stream()
                .map(this::toMoment)
                .filter(moment -> moment != null)
                .toList();
        if (moments.isEmpty()) {
            return FollowingMomentsResponse.builder().enabled(true).items(List.of()).build();
        }

        Map<UUID, UserCheckinResponse> checkinCards =
                checkinService.findVisibleByIds(viewerId, idsOf(moments, FollowingPostType.CHECKIN));
        List<PublicTripResponse> tripCards = tripService.getPublicTripCards(viewerId, idsOf(moments, FollowingPostType.TRIP));
        Set<UUID> authors = new LinkedHashSet<>();
        checkinCards.values().forEach(checkin -> authors.add(checkin.getUserId()));
        tripCards.forEach(trip -> authors.add(trip.getOwnerId()));
        authors.remove(null);
        Set<UUID> blockedAuthors = authors.isEmpty() ? Set.of() : blockService.blockedAmong(viewerId, authors);
        Map<UUID, UserCheckinResponse> checkins = checkinCards.values().stream()
                .filter(checkin -> !blockedAuthors.contains(checkin.getUserId()))
                .collect(Collectors.toMap(UserCheckinResponse::getId, Function.identity(), (first, second) -> first));
        Map<UUID, PublicTripResponse> trips = tripCards.stream()
                .filter(trip -> !blockedAuthors.contains(trip.getOwnerId()))
                .collect(Collectors.toMap(PublicTripResponse::getId, Function.identity(), (first, second) -> first));

        List<FollowingMomentResponse> items = new ArrayList<>();
        Set<String> gone = new LinkedHashSet<>();
        Set<String> listed = new LinkedHashSet<>();
        for (Moment moment : moments) {
            String key = moment.postType().dedupeKey(moment.postId());
            UserCheckinResponse checkin = moment.postType() == FollowingPostType.CHECKIN ? checkins.get(moment.postId()) : null;
            PublicTripResponse trip = moment.postType() == FollowingPostType.TRIP ? trips.get(moment.postId()) : null;
            if (checkin == null && trip == null) {
                gone.add(key);
                continue;
            }
            if (!listed.add(key)) {
                continue;
            }
            items.add(FollowingMomentResponse.builder()
                    .notificationId(moment.notificationId())
                    .postType(moment.postType())
                    .postId(moment.postId())
                    .createdAt(moment.createdAt())
                    .checkin(checkin)
                    .trip(trip)
                    .build());
        }
        // A post that can no longer be shown is as good as seen: leaving it unread would keep it
        // in the badge and bring it back here on every launch.
        if (!gone.isEmpty() && notificationRepository.markFollowingPostsRead(viewerId, gone) > 0) {
            changePublisher.listChanged(viewerId);
        }
        return FollowingMomentsResponse.builder().enabled(true).items(items).build();
    }

    @Override
    @Transactional
    public int markRead(UUID viewerId, List<FollowingMomentRefRequest> posts) {
        if (posts == null || posts.isEmpty()) {
            return 0;
        }
        Set<String> keys = posts.stream()
                .map(post -> post.getPostType().dedupeKey(post.getPostId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int changed = notificationRepository.markFollowingPostsRead(viewerId, keys);
        if (changed > 0) {
            changePublisher.listChanged(viewerId);
        }
        return changed;
    }

    private boolean isEnabledFor(UUID viewerId) {
        return config.getBoolean(BusinessConfigKey.SOCIAL_FOLLOWER_FANOUT_ENABLED) || betaAccess.isBetaUser(viewerId);
    }

    private static List<UUID> idsOf(List<Moment> moments, FollowingPostType postType) {
        return moments.stream()
                .filter(moment -> moment.postType() == postType)
                .map(Moment::postId)
                .distinct()
                .toList();
    }

    /** The post a row announces, read from its data; null for a row this code did not write. */
    private Moment toMoment(Notification row) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = row.getData() == null ? null : gson.fromJson(row.getData(), Map.class);
            if (data == null || data.get("postType") == null || data.get("postId") == null) {
                return null;
            }
            return new Moment(row.getId(), FollowingPostType.valueOf(String.valueOf(data.get("postType"))),
                    UUID.fromString(String.valueOf(data.get("postId"))), row.getCreatedAt());
        } catch (RuntimeException malformed) {
            log.warn("Skipping follower notification {} with unreadable data: {}", row.getId(), malformed.getMessage());
            return null;
        }
    }

    private record Moment(UUID notificationId, FollowingPostType postType, UUID postId,
                          java.time.LocalDateTime createdAt) {
    }
}
