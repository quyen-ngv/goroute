package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ExpenseDeletedEvent extends TripEvent {
    private UUID expenseId;
    private String description;
    private String actorName;
    private String tripName;
    
    @Override
    public String getTitle() {
        return "Expense Deleted";
    }
    
    @Override
    public String getBody() {
        return String.format("%s deleted expense \"%s\" from %s", actorName, description, tripName);
    }
}
