package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PaymentAllMarkedEvent extends TripEvent {
    private UUID expenseId;
    private String expenseDescription;
    private String actorName;
    private Boolean isPaid;
    
    @Override
    public String getTitle() {
        return isPaid ? "All Payments Marked as Paid" : "All Payments Marked as Unpaid";
    }
    
    @Override
    public String getBody() {
        if (isPaid) {
            return String.format("%s marked all payments for \"%s\" as paid", actorName, expenseDescription);
        } else {
            return String.format("%s marked all payments for \"%s\" as unpaid", actorName, expenseDescription);
        }
    }
}
