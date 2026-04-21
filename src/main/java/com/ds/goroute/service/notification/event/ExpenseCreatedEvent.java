package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ExpenseCreatedEvent extends TripEvent {
    private UUID expenseId;
    private String description;
    private BigDecimal amount;
    private String currency;
    private String actorName;
    private String tripName;
    
    @Override
    public String getTitle() {
        return "New Expense Added";
    }
    
    @Override
    public String getBody() {
        return String.format("%s added expense \"%s\" (%s %s) in %s", 
            actorName, description, amount, currency, tripName);
    }
}
