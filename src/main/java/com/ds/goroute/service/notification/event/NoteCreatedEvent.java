package com.ds.goroute.service.notification.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class NoteCreatedEvent extends TripEvent {
    private String actorName;
    private String noteContent;
    private String tripName;
    private String activityName; // Optional, null if trip-level note
    
    @Override
    public String getTitle() {
        return "New Note Added";
    }
    
    @Override
    public String getBody() {
        if (activityName != null) {
            return String.format("%s added a note to \"%s\" in %s", actorName, activityName, tripName);
        }
        return String.format("%s added a note to %s", actorName, tripName);
    }
}
