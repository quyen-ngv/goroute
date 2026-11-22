package com.ds.goroute.localevent.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persistence facade over {@link LocalEventSaveMapper}: saved events and the reminders sent for them. */
@Repository
@RequiredArgsConstructor
public class LocalEventSaveRepository {

    private final LocalEventSaveMapper mapper;

    /** Whether the event was newly saved; {@code false} when it already was. */
    public boolean add(UUID eventId, UUID userId, LocalDateTime savedAt) {
        return mapper.insert(eventId, userId, savedAt) == 1;
    }

    public boolean remove(UUID eventId, UUID userId) {
        return mapper.delete(eventId, userId) == 1;
    }

    public boolean isSaved(UUID eventId, UUID userId) {
        return eventId != null && userId != null && mapper.exists(eventId, userId);
    }

    /** Of {@code eventIds}, the ones this person saved. */
    public Set<UUID> findSavedEventIds(UUID userId, Collection<UUID> eventIds) {
        if (userId == null || eventIds == null || eventIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(mapper.findSavedEventIds(userId, eventIds));
    }

    public List<UUID> findSaversWithoutReminder(UUID eventId, String kind, boolean skipCheckedIn, int limit) {
        return mapper.findSaversWithoutReminder(eventId, kind, skipCheckedIn, limit);
    }

    /** Whether the reminder is now claimed for this person; {@code false} when it was sent before. */
    public boolean claimReminder(UUID eventId, UUID userId, String kind, LocalDateTime sentAt) {
        return mapper.insertReminder(eventId, userId, kind, sentAt) == 1;
    }
}
