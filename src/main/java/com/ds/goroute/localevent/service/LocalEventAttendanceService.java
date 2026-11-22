package com.ds.goroute.localevent.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.dto.LocalEventAttendanceResponse;
import com.ds.goroute.localevent.dto.MyLocalEventResponse;
import com.ds.goroute.localevent.persistence.LocalEventAttendeeRepository;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.localevent.persistence.LocalEventSaveRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.LocalEventStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Going to a local event (epic 17): a signed-in person marks a published event as "going" and
 * is in its group chat until they stop.
 *
 * <p>The event data stays public; joining is user activity and sits behind the same switch as
 * the Home tab ({@code EVENT/ENABLED}, beta users always pass), checked here on the server.
 *
 * <p>Saving an event (V285) is the private half of the same thing: a bookmark that puts the
 * event on the saver's Feed and sends them its reminders, with no chat and nothing shown to
 * anyone else. Going to an event saves it too.
 */
@Service
@RequiredArgsConstructor
public class LocalEventAttendanceService {

    /** Faces shown to people going. */
    static final int PREVIEW_LIMIT = 8;

    /** The most events GET /events/mine returns. */
    static final int MINE_LIMIT = 100;

    private final LocalEventRepository events;
    private final LocalEventAttendeeRepository attendees;
    private final LocalEventChatService chat;
    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;
    private final UserRepository users;
    private final LocalEventSaveRepository saves;
    private final UserCheckinRepository checkins;
    private final LocalEventResponses responses;

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
        // Going implies wanting the reminders and the Feed strip.
        saves.add(eventId, userId, now());
        return view(event, userId, true);
    }

    /** Bookmarks a published event. Idempotent; an event already over can still be kept. */
    @Transactional
    public LocalEventAttendanceResponse save(UUID userId, UUID eventId) {
        requireEnabled(userId);
        LocalEvent event = requirePublished(eventId);
        saves.add(eventId, userId, now());
        return view(event, userId, true);
    }

    /** Removes the bookmark. Idempotent; it does not stop the person going. */
    @Transactional
    public LocalEventAttendanceResponse unsave(UUID userId, UUID eventId) {
        requireEnabled(userId);
        LocalEvent event = requireExisting(eventId);
        saves.remove(eventId, userId);
        return view(event, userId, true);
    }

    /**
     * The caller's events: published ones they saved or are going to that end after
     * {@code from} (default now), soonest first, at most {@link #MINE_LIMIT}. Empty while the
     * switch is off for the caller.
     */
    @Transactional(readOnly = true)
    public List<MyLocalEventResponse> mine(UUID userId, Instant from) {
        if (!isEnabled(userId)) {
            return List.of();
        }
        LocalDateTime fromUtc = from == null ? now() : LocalEventTimes.toUtc(from);
        List<LocalEvent> events = this.events.findSavedOrGoing(userId, fromUtc, MINE_LIMIT);
        if (events.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = events.stream().map(LocalEvent::getId).toList();
        Set<UUID> saved = saves.findSavedEventIds(userId, ids);
        Set<UUID> going = attendees.findGoingEventIds(userId, ids);
        Set<UUID> checkedIn = checkins.findCheckedInLocalEventIds(userId, ids);
        return events.stream()
                .map(event -> {
                    MyLocalEventResponse response = new MyLocalEventResponse();
                    responses.fillSummary(response, event);
                    response.setSaved(saved.contains(event.getId()));
                    response.setGoing(going.contains(event.getId()));
                    response.setCheckedIn(checkedIn.contains(event.getId()));
                    return response;
                })
                .toList();
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
                .saved(saves.isSaved(event.getId(), userId))
                .checkedIn(!checkins.findCheckedInLocalEventIds(userId, List.of(event.getId())).isEmpty())
                .checkinCount(checkins.countByLocalEvent(event.getId()))
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
