package com.ds.goroute.localevent.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Who saved a local event ({@code local_event_saves}) and which reminders each of them was
 * already sent ({@code local_event_reminders}); V285.
 */
@Mapper
public interface LocalEventSaveMapper {

    /** 1 when the event was not saved yet, 0 when it already was. */
    int insert(@Param("eventId") UUID eventId, @Param("userId") UUID userId, @Param("savedAt") LocalDateTime savedAt);

    int delete(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    boolean exists(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    List<UUID> findSavedEventIds(@Param("userId") UUID userId, @Param("eventIds") Collection<UUID> eventIds);

    /**
     * People who saved the event and were not sent {@code kind} yet, in a stable order.
     * {@code skipCheckedIn} leaves out the ones who already have a live check-in at it.
     */
    List<UUID> findSaversWithoutReminder(@Param("eventId") UUID eventId,
                                         @Param("kind") String kind,
                                         @Param("skipCheckedIn") boolean skipCheckedIn,
                                         @Param("limit") int limit);

    /** 1 when this reminder is now claimed for the person, 0 when it was sent before. */
    int insertReminder(@Param("eventId") UUID eventId,
                       @Param("userId") UUID userId,
                       @Param("kind") String kind,
                       @Param("sentAt") LocalDateTime sentAt);
}
