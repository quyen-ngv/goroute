package com.ds.goroute.service.notification.strategy;

import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.service.notification.event.TripEvent;
import com.ds.goroute.type.MemberStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.HashSet;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Strategy: Gửi notification cho tất cả members trong trip
 * Loại trừ actor (người thực hiện hành động)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AllMembersStrategy implements NotificationStrategy {
    
    private final TripMemberRepository tripMemberRepository;
    private final TripRepository tripRepository;
    
    @Override
    public List<UUID> getRecipients(TripEvent event) {
        Set<UUID> excluded = excludedRecipients(event);
        Set<UUID> recipients = new java.util.LinkedHashSet<>();
        tripRepository.findById(event.getTripId())
                .map(trip -> trip.getOwnerId())
                .filter(java.util.Objects::nonNull)
                .ifPresent(recipients::add);
        tripMemberRepository.findByTripId(event.getTripId()).stream()
                .filter(member -> member.getStatus() == MemberStatus.ACCEPTED)
                .map(member -> member.getUserId())
                .filter(java.util.Objects::nonNull)
                .forEach(recipients::add);
        recipients.remove(event.getActorId());
        recipients.removeAll(excluded);
        return List.copyOf(recipients);
    }

    private Set<UUID> excludedRecipients(TripEvent event) {
        Object value = event.getMetadata() == null ? null : event.getMetadata().get("excludedRecipientIds");
        if (!(value instanceof Collection<?> ids)) {
            return Set.of();
        }
        Set<UUID> result = new HashSet<>();
        for (Object id : ids) {
            try {
                result.add(id instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(id)));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed metadata instead of dropping the whole notification.
            }
        }
        return result;
    }
}
