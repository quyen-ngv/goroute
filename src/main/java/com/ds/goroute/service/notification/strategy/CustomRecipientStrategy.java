package com.ds.goroute.service.notification.strategy;

import com.ds.goroute.service.notification.event.TripEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.LinkedHashSet;

/**
 * Strategy: Custom logic cho các case đặc biệt
 * Ví dụ: Member removed - gửi cho tất cả + người bị xóa
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomRecipientStrategy implements NotificationStrategy {
    
    private final AllMembersStrategy allMembersStrategy;
    
    @Override
    public List<UUID> getRecipients(TripEvent event) {
        Object value = event.getMetadata() == null ? null : event.getMetadata().get("removedMemberId");
        UUID removedMemberId = value instanceof UUID uuid ? uuid : parseUuid(value);
        
        // Lấy tất cả members hiện tại
        LinkedHashSet<UUID> allMembers = new LinkedHashSet<>(allMembersStrategy.getRecipients(event));
        
        // Thêm người bị xóa (nếu chưa có trong list)
        if (removedMemberId != null
                && !removedMemberId.equals(event.getActorId())
                && !allMembers.contains(removedMemberId)) {
            allMembers.add(removedMemberId);
        }
        
        return List.copyOf(allMembers);
    }

    private UUID parseUuid(Object value) {
        try {
            return value == null ? null : UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
