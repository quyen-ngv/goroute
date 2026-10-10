package com.ds.goroute.localevent.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Persistence facade over {@link LocalEventAttendeeMapper}. */
public interface LocalEventAttendeeRepository {

    /** Whether the person was added; {@code false} when they were already going. */
    boolean add(UUID eventId, UUID userId, LocalDateTime joinedAt);

    /** Whether the person was removed; {@code false} when they were not going. */
    boolean remove(UUID eventId, UUID userId);

    boolean isGoing(UUID eventId, UUID userId);

    long count(UUID eventId);

    List<UUID> findUserIds(UUID eventId, int limit);
}
