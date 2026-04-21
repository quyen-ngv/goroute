package com.ds.goroute.service.notification.strategy;

import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.service.notification.event.TripEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Strategy: Gửi notification cho members trong expense split
 * Loại trừ actor
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpenseMembersStrategy implements NotificationStrategy {
    
    private final ExpenseSplitRepository expenseSplitRepository;
    
    @Override
    public List<UUID> getRecipients(TripEvent event) {
        UUID expenseId = (UUID) event.getMetadata().get("expenseId");
        if (expenseId == null) {
            log.warn("ExpenseId not found in event metadata");
            return List.of();
        }
        
        return expenseSplitRepository.findByExpenseId(expenseId)
                .stream()
                .map(ExpenseSplit::getUserId)
                .filter(userId -> userId != null && !userId.equals(event.getActorId()))
                .distinct()
                .collect(Collectors.toList());
    }
}
