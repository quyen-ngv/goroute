package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ActivityUpdatedEvent extends TripEvent {
    private String activityName;
    private String actorName;
    private String tripName;
    
    @Override
    public String getTitle() {
        return "Activity Updated";
    }
    
    @Override
    public String getBody() {
        return String.format("%s updated \"%s\" in %s", actorName, activityName, tripName);
    }
}
