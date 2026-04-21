package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class GuestLinkedEvent extends TripEvent {
    private String guestName;
    private String linkedUserName;
    private String tripName;
    
    @Override
    public String getTitle() {
        return "Guest Account Linked";
    }
    
    @Override
    public String getBody() {
        return String.format("%s has been linked to %s in %s", guestName, linkedUserName, tripName);
    }
}
