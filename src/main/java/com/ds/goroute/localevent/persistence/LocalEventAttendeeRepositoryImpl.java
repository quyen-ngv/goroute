package com.ds.goroute.localevent.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LocalEventAttendeeRepositoryImpl implements LocalEventAttendeeRepository {

    private final LocalEventAttendeeMapper mapper;

    @Override
    public boolean add(UUID eventId, UUID userId, LocalDateTime joinedAt) {
        return mapper.insert(eventId, userId, joinedAt) == 1;
    }

    @Override
    public boolean remove(UUID eventId, UUID userId) {
        return mapper.delete(eventId, userId) == 1;
    }

    @Override
    public boolean isGoing(UUID eventId, UUID userId) {
        return eventId != null && userId != null && mapper.exists(eventId, userId);
    }

    @Override
    public long count(UUID eventId) {
        return mapper.countByEvent(eventId);
    }

    @Override
    public List<UUID> findUserIds(UUID eventId, int limit) {
        return mapper.findUserIds(eventId, limit);
    }
}
