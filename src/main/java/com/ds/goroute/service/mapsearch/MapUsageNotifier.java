package com.ds.goroute.service.mapsearch;

import com.ds.goroute.entity.User;
import com.ds.goroute.mapper.MapProviderUsageMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.notification.NotificationMessage;
import com.ds.goroute.service.notification.NotificationTemplateRenderer;
import com.ds.goroute.type.MapBillingApi;
import com.ds.goroute.type.MapSearchOperation;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells people about map limits: the user who ran out, and operators when someone looks like
 * a script or the system budget runs low. Each caller sends at most once per period, and a
 * failure here never fails the request that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MapUsageNotifier {

    /** Reset times are shown on the Vietnam clock, the clock the daily limits run on. */
    static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter RESET_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy '(GMT+7)'");

    private final NotificationService notificationService;
    private final NotificationTemplateRenderer templateRenderer;
    private final UserRepository users;
    private final MapProviderUsageMapper usageMapper;

    /** The user used up today's allowance for one kind of map call. */
    public void userReachedDailyLimit(UUID userId, MapSearchOperation operation, Instant resetAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("operation", operation.name());
        data.put("resetAt", resetAt.toString());
        data.put("resetTime", formatReset(resetAt));
        send(userId, NotificationType.MAP_LIMIT_REACHED, data);
    }

    /** A user keeps calling after being refused: worth an operator's look. */
    public void suspectedSpam(UUID userId, MapSearchOperation operation, long refusedToday) {
        String userName = users.findById(userId).map(MapUsageNotifier::displayName).orElse(userId.toString());
        Map<String, Object> data = new HashMap<>();
        data.put("spamUserId", userId.toString());
        data.put("userName", userName);
        data.put("operation", operation.name());
        data.put("rejectedCount", refusedToday);
        notifyAdmins(NotificationType.MAP_SPAM_ALERT, data);
    }

    /** The month's paid calls for one API crossed the warning share of the cap. */
    public void budgetWarning(MapBillingApi api, long used, long cap) {
        Map<String, Object> data = new HashMap<>();
        data.put("api", api.name());
        data.put("usedPercent", cap <= 0 ? 100 : Math.round(used * 100.0 / cap));
        notifyAdmins(NotificationType.MAP_BUDGET_WARNING, data);
    }

    /** One API hit its cap: the server answers from caches until {@code resumesAt}. */
    public void budgetReached(MapBillingApi api, Instant resumesAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("api", api.name());
        data.put("resetAt", resumesAt.toString());
        data.put("resetTime", formatReset(resumesAt));
        notifyAdmins(NotificationType.MAP_BUDGET_REACHED, data);
    }

    static String formatReset(Instant instant) {
        return RESET_FORMAT.format(instant.atZone(DISPLAY_ZONE));
    }

    private void notifyAdmins(NotificationType type, Map<String, Object> data) {
        List<UUID> admins;
        try {
            admins = usageMapper.findAdminUserIds();
        } catch (RuntimeException exception) {
            log.warn("Could not list operators for {}: {}", type, exception.getMessage());
            return;
        }
        for (UUID adminId : admins) {
            send(adminId, type, new HashMap<>(data));
        }
    }

    private void send(UUID recipientId, NotificationType type, Map<String, Object> data) {
        try {
            String language = users.findById(recipientId).map(User::getLanguage).orElse(null);
            NotificationMessage message = templateRenderer.render(type, data, language);
            notificationService.createNotification(recipientId, null, type, message.title(), message.body(),
                    data, null);
        } catch (RuntimeException exception) {
            log.warn("Could not send {} to {}: {}", type, recipientId, exception.getMessage());
        }
    }

    private static String displayName(User user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return user.getId().toString();
    }
}
