package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CheckinEvent extends TripEvent {
    private String actorName;
    private String activityName;
    private String tripName;
    
    @Override
    public String getTitle() {
        return "New Check-in";
    }
    
    @Override
    public String getBody() {
        return String.format("%s checked in at \"%s\" in %s", actorName, activityName, tripName);
    }
}
