package com.ds.goroute.service.notification.strategy;

import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.service.notification.event.TripEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Strategy: Gửi notification cho tất cả members trong trip
 * Loại trừ actor (người thực hiện hành động)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AllMembersStrategy implements NotificationStrategy {
    
    private final TripMemberRepository tripMemberRepository;
    
    @Override
    public List<UUID> getRecipients(TripEvent event) {
        return tripMemberRepository.findByTripId(event.getTripId())
                .stream()
                .map(member -> member.getUserId())
                .filter(userId -> userId != null && !userId.equals(event.getActorId()))
                .distinct()
                .collect(Collectors.toList());
    }
}
