package com.ds.goroute.service.notification.strategy;

import com.ds.goroute.service.notification.event.TripEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Strategy: Gửi notification chỉ cho người được thanh toán (payee)
 * Dùng cho: Mark payment (1 split)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayeeOnlyStrategy implements NotificationStrategy {
    
    @Override
    public List<UUID> getRecipients(TripEvent event) {
        UUID payeeId = (UUID) event.getMetadata().get("payeeId");
        
        if (payeeId == null) {
            log.warn("PayeeId not found in event metadata");
            return List.of();
        }
        
        return List.of(payeeId);
    }
}
