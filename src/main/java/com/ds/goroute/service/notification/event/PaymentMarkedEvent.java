package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PaymentMarkedEvent extends TripEvent {
    private UUID payeeId;
    private String payeeName;
    private String payerName;
    private BigDecimal amount;
    private String currency;
    private String expenseDescription;
    private Boolean isPaid;
    
    @Override
    public String getTitle() {
        return isPaid ? "Payment Marked as Paid" : "Payment Marked as Unpaid";
    }
    
    @Override
    public String getBody() {
        if (isPaid) {
            return String.format("%s marked your payment of %s %s for \"%s\" as paid", 
                payerName, amount, currency, expenseDescription);
        } else {
            return String.format("%s marked your payment of %s %s for \"%s\" as unpaid", 
                payerName, amount, currency, expenseDescription);
        }
    }
}
