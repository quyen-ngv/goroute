package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper
public interface ScheduledNotificationDeliveryMapper {

    @Insert("""
            INSERT INTO scheduled_notification_deliveries (
                id, recipient_id, trip_id, event_key, scheduled_for, delivered_at
            ) VALUES (
                #{id}, #{recipientId}, #{tripId}, #{eventKey}, #{scheduledFor}, NOW()
            )
            ON CONFLICT (recipient_id, event_key) DO NOTHING
            """)
    int claim(@Param("id") UUID id,
              @Param("recipientId") UUID recipientId,
              @Param("tripId") UUID tripId,
              @Param("eventKey") String eventKey,
              @Param("scheduledFor") LocalDateTime scheduledFor);
}
