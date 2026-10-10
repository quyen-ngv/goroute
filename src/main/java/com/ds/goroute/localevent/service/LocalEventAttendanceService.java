package com.ds.goroute.localevent.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.dto.LocalEventAttendanceResponse;
import com.ds.goroute.localevent.persistence.LocalEventAttendeeRepository;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.LocalEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Going to a local event (epic 17): a signed-in person marks a published event as "going" and
 * is in its group chat until they stop.
 *
 * <p>The event data stays public; joining is user activity and sits behind the same switch as
 * the Home tab ({@code EVENT/ENABLED}, beta users always pass), checked here on the server.
 */
@Service
@RequiredArgsConstructor
public class LocalEventAttendanceService {

    /** Faces shown to people going. */
    static final int PREVIEW_LIMIT = 8;

    private final LocalEventRepository events;
    private final LocalEventAttendeeRepository attendees;
    private final LocalEventChatService chat;
    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;
    private final UserRepository users;

    /** Replaced in tests. */
    Clock clock = Clock.systemUTC();

    public boolean isEnabled(UUID userId) {
        return config.getBoolean(BusinessConfigKey.EVENT_ENABLED) || betaAccess.isBetaUser(userId);
    }

    /** Never refused for the switch: a client asks this to learn whether to offer "Join" at all. */
    @Transactional(readOnly = true)
    public LocalEventAttendanceResponse attendance(UUID userId, UUID eventId) {
        LocalEvent event = requirePublished(eventId);
        return view(event, userId, isEnabled(userId));
    }

    @Transactional
    public LocalEventAttendanceResponse join(UUID userId, UUID eventId) {
        requireEnabled(userId);
        LocalEvent event = requirePublished(eventId);
        if (!isJoinable(event)) {
            throw new BusinessException(ErrorConstant.LOCAL_EVENT_NOT_JOINABLE, "This event is cancelled or over");
        }
        if (attendees.add(eventId, userId, now())) {
            chat.onJoined(eventId, userId);
        }
        return view(event, userId, true);
    }

    /** Idempotent; works whatever happened to the event since, so nobody is stuck in its chat. */
    @Transactional
    public LocalEventAttendanceResponse leave(UUID userId, UUID eventId) {
        requireEnabled(userId);
        LocalEvent event = requireExisting(eventId);
        if (attendees.remove(eventId, userId)) {
            chat.onLeft(eventId, userId);
        }
        return view(event, userId, true);
    }

    /** The event's group chat, created on first use; only people going may open it. */
    @Transactional
    public MarketplaceConversationResponse conversation(UUID userId, UUID eventId) {
        requireEnabled(userId);
        requireExisting(eventId);
        if (!attendees.isGoing(eventId, userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You are not going to this event");
        }
        return chat.open(userId, eventId);
    }

    private LocalEventAttendanceResponse view(LocalEvent event, UUID userId, boolean enabled) {
        boolean going = attendees.isGoing(event.getId(), userId);
        return LocalEventAttendanceResponse.builder()
                .enabled(enabled)
                .going(going)
                .goingCount(attendees.count(event.getId()))
                .joinable(isJoinable(event))
                .attendeesPreview(going ? preview(event.getId()) : List.of())
                .build();
    }

    private List<LocalEventAttendanceResponse.Attendee> preview(UUID eventId) {
        List<UUID> ids = attendees.findUserIds(eventId, PREVIEW_LIMIT);
        if (ids.isEmpty()) return List.of();
        Map<UUID, User> byId = users.findByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        return ids.stream()
                .filter(byId::containsKey)
                .map(id -> {
                    User user = byId.get(id);
                    String name = user.getFullName() == null || user.getFullName().isBlank()
                            ? user.getUsername() : user.getFullName();
                    return LocalEventAttendanceResponse.Attendee.builder()
                            .id(id)
                            .name(name)
                            .avatarUrl(user.getAvatarUrl())
                            .build();
                })
                .toList();
    }

    /** Not cancelled and not over yet (end times are stored in UTC). */
    private boolean isJoinable(LocalEvent event) {
        boolean cancelled = LocalEventStatus.CANCELLED.name().equals(event.getEventStatus());
        return !cancelled && event.getEndAt() != null && event.getEndAt().isAfter(now());
    }

    private void requireEnabled(UUID userId) {
        if (!isEnabled(userId)) {
            throw new BusinessException(ErrorConstant.LOCAL_EVENT_JOIN_DISABLED, "Joining events is not available yet");
        }
    }

    private LocalEvent requirePublished(UUID eventId) {
        return events.findPublishedById(eventId).orElseThrow(LocalEventAttendanceService::notFound);
    }

    /** Any event that was not deleted, published or not: leaving and the chat outlive publication. */
    private LocalEvent requireExisting(UUID eventId) {
        return events.findById(eventId)
                .filter(event -> !Boolean.TRUE.equals(event.getIsDeleted()))
                .orElseThrow(LocalEventAttendanceService::notFound);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorConstant.LOCAL_EVENT_NOT_FOUND, "Event not found");
    }
}
