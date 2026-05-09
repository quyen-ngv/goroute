package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ActivityCreatedEvent extends TripEvent {
    private String activityName;
    private String actorName;
    private String tripName;
    
    @Override
    public String getTitle() {
        return "New Activity Added";
    }
    
    @Override
    public String getBody() {
        return String.format("%s added \"%s\" to %s", actorName, activityName, tripName);
    }
}
