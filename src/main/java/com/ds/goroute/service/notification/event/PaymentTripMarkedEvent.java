package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PaymentTripMarkedEvent extends TripEvent {
    private String tripName;
    private String actorName;
    private Boolean isPaid;
    
    @Override
    public String getTitle() {
        return isPaid ? "All Trip Payments Settled" : "All Trip Payments Unsettled";
    }
    
    @Override
    public String getBody() {
        if (isPaid) {
            return String.format("%s marked all payments in %s as settled", actorName, tripName);
        } else {
            return String.format("%s marked all payments in %s as unsettled", actorName, tripName);
        }
    }
}
