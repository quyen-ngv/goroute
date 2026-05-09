package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class MemberAcceptedEvent extends TripEvent {
    private String memberName;
    private String actorName;
    private String tripName;
    
    @Override
    public String getTitle() {
        return "Member Request Accepted";
    }
    
    @Override
    public String getBody() {
        return String.format("%s accepted %s's request to join %s", actorName, memberName, tripName);
    }
}
