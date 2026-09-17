package com.ds.goroute.service.notification;

import com.ds.goroute.entity.Notification;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.ContentCommentRepository;
import com.ds.goroute.repository.NotificationRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.NotificationType;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delivers interaction notifications to the author of social content.
 *
 * <p>Rather than sending a push for every reaction, interactions are coalesced for an
 * unread target notification for 30 minutes. The notification centre stays current
 * (latest actor and actor count) while only the first interaction produces a push.
 */
@Service
@RequiredArgsConstructor
public class SocialNotificationService {

    private static final int ACTOR_PREVIEW_LIMIT = 3;
    private static final int ACTOR_DEDUPLICATION_LIMIT = 50;

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ContentCommentRepository contentCommentRepository;
    private final Gson gson;

    public void notifyLike(UUID recipientId, UUID actorId, String targetType, UUID targetId) {
        notify(recipientId, actorId, NotificationType.SOCIAL_LIKE, targetType, targetId);
    }

    public void notifyComment(UUID recipientId, UUID actorId, String targetType, UUID targetId) {
        notify(recipientId, actorId, NotificationType.SOCIAL_COMMENT, targetType, targetId);
    }

    private void notify(UUID recipientId, UUID actorId, NotificationType type,
                        String targetType, UUID targetId) {
        if (recipientId == null || actorId == null || targetId == null || recipientId.equals(actorId)) {
            return;
        }

        String actorName = userRepository.findById(actorId)
                .map(this::displayName)
                .orElse("Someone");
        Notification existing = notificationRepository
                .findRecentUnreadSocialNotification(recipientId, type, targetType, targetId)
                .orElse(null);
        if (existing == null) {
            Map<String, Object> data = new HashMap<>();
            data.put("socialAction", type == NotificationType.SOCIAL_LIKE ? "like" : "comment");
            data.put("targetType", targetType);
            data.put("targetId", targetId.toString());
            data.put("actorName", actorName);
            data.put("actorNames", List.of(actorName));
            data.put("actorIds", List.of(actorId.toString()));
            data.put("actorCount", 1);
            data.put("deepLink", deepLinkFor(targetType, targetId));
            notificationService.createNotification(
                    recipientId, null, type, null, null, data, actorId);
            return;
        }

        Map<String, Object> data = readData(existing.getData());
        List<String> actorIds = stringList(data.get("actorIds"));
        List<String> actorNames = stringList(data.get("actorNames"));
        boolean isNewActor = !actorIds.contains(actorId.toString());
        int actorCount = actorCount(data.get("actorCount"), actorIds.size());
        if (isNewActor) {
            actorCount++;
            if (actorIds.size() < ACTOR_DEDUPLICATION_LIMIT) {
                actorIds.add(actorId.toString());
            }
            actorNames.add(actorName);
        }
        List<String> previewNames = actorNames.stream()
                .skip(Math.max(0, actorNames.size() - ACTOR_PREVIEW_LIMIT))
                .toList();
        data.put("actorName", actorName);
        data.put("actorIds", actorIds);
        data.put("actorNames", previewNames);
        data.put("actorCount", actorCount);
        data.putIfAbsent("socialAction", type == NotificationType.SOCIAL_LIKE ? "like" : "comment");
        data.putIfAbsent("targetType", targetType);
        data.putIfAbsent("targetId", targetId.toString());
        data.putIfAbsent("deepLink", deepLinkFor(targetType, targetId));
        existing.setActorId(actorId);
        existing.setData(gson.toJson(data));
        existing.setBody(null);
        notificationRepository.updateSocialNotification(existing);
    }

    private Map<String, Object> readData(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return new HashMap<>();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = gson.fromJson(rawData, Map.class);
            return parsed == null ? new HashMap<>() : new HashMap<>(parsed);
        } catch (RuntimeException ignored) {
            return new HashMap<>();
        }
    }

    private List<String> stringList(Object value) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    values.add(item.toString());
                }
            }
        }
        return new ArrayList<>(values);
    }

    private int actorCount(Object value, int fallback) {
        if (value instanceof Number number) {
            return Math.max(number.intValue(), fallback);
        }
        try {
            return Math.max(Integer.parseInt(String.valueOf(value)), fallback);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String displayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getUsername() == null || user.getUsername().isBlank()
                ? "Someone"
                : user.getUsername();
    }

    /**
     * Where a tap on the notification lands.
     *
     * <p>Every non-trip target used to fall through to {@code /trips}, so being liked on a
     * check-in or a review opened the reader's own trip list instead of the post that was
     * liked. A comment target is the comment itself, which has no screen of its own: it
     * resolves to the post the thread hangs under.
     */
    private String deepLinkFor(String targetType, UUID targetId) {
        if (ModeratedContentType.CONTENT_COMMENT.name().equals(targetType)) {
            return contentCommentRepository.findById(targetId)
                    .map(comment -> deepLinkFor(comment.getContentType().name(), comment.getContentId()))
                    .orElse("/trips");
        }
        if (ModeratedContentType.CHECKIN.name().equals(targetType)) {
            return "/checkins/" + targetId;
        }
        if (ModeratedContentType.REVIEW.name().equals(targetType)) {
            return "/reviews/" + targetId;
        }
        if (ModeratedContentType.PLACE_COLLECTION.name().equals(targetType)) {
            return "/collections/" + targetId;
        }
        return "TRIP".equals(targetType) ? "/trip/" + targetId : "/trips";
    }
}
