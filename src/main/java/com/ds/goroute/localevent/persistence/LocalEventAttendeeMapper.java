package com.ds.goroute.localevent.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Who is going to a local event: one row per person, deleted when they stop going. */
@Mapper
public interface LocalEventAttendeeMapper {

    /** 1 when the person was not going yet, 0 when they already were. */
    int insert(@Param("eventId") UUID eventId, @Param("userId") UUID userId, @Param("joinedAt") LocalDateTime joinedAt);

    int delete(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    boolean exists(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    long countByEvent(@Param("eventId") UUID eventId);

    /** The first people who said they are going, oldest first. */
    List<UUID> findUserIds(@Param("eventId") UUID eventId, @Param("limit") int limit);
}
